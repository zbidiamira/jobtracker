package de.zbidi.jobtracker.config;

import java.util.LinkedHashMap;
import java.util.Map;

import de.zbidi.jobtracker.jobapplication.StatusChangedEvent;
import de.zbidi.jobtracker.jobapplication.StatusEventPublisher;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

/**
 * Topics, serialization and error handling for the status events. Everything else (bootstrap servers, consumer
 * deserializers, group id) is in application.yaml.
 */
@Configuration(proxyBeanMethods = false)
public class KafkaConfig {

	public static final String DEAD_LETTER_TOPIC = StatusEventPublisher.TOPIC + ".DLT";
	static final int PARTITIONS = 3;
	static final int RETRIES = 3;

	@Bean
	NewTopic applicationStatusTopic() {
		return TopicBuilder.name(StatusEventPublisher.TOPIC).partitions(PARTITIONS).replicas(1).build();
	}

	/** Same partition count: the recoverer writes a failed record to the same partition number of the DLT. */
	@Bean
	NewTopic applicationStatusDeadLetterTopic() {
		return TopicBuilder.name(DEAD_LETTER_TOPIC).partitions(PARTITIONS).replicas(1).build();
	}

	/**
	 * The producer serializes by type: events as JSON (Jackson 3 based {@link JacksonJsonSerializer}, without Java type
	 * headers so non-Java consumers can read them), and raw bytes unchanged. Raw bytes are what the dead-letter
	 * recoverer sends for a message that couldn't even be deserialized.
	 */
	@Bean
	DefaultKafkaProducerFactoryCustomizer valueSerializerByType() {
		Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
		serializers.put(StatusChangedEvent.class, new JacksonJsonSerializer<StatusChangedEvent>().noTypeInfo());
		serializers.put(byte[].class, new ByteArraySerializer());
		return factory -> asObjectFactory(factory).setValueSerializer(new DelegatingByTypeSerializer(serializers));
	}

	/**
	 * Failed records are retried 3 times (backoff 200 ms, 400 ms, 800 ms), then published to
	 * {@code application-status.DLT} with the exception in the headers. Records that can't be deserialized are not
	 * retried (it would never help) and go to the DLT immediately. Boot attaches this handler to the listener container.
	 */
	@Bean
	DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
		ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(RETRIES);
		backOff.setInitialInterval(200);
		backOff.setMultiplier(2.0);
		return new DefaultErrorHandler(deadLetterRecoverer(kafkaTemplate), backOff);
	}

	/**
	 * Explicit destination: Spring Kafka 4's default would be {@code application-status-dlt}; we use the classic
	 * {@code application-status.DLT}, keeping the partition number of the failed record.
	 */
	static DeadLetterPublishingRecoverer deadLetterRecoverer(KafkaTemplate<Object, Object> kafkaTemplate) {
		return new DeadLetterPublishingRecoverer(kafkaTemplate,
				(record, exception) -> new TopicPartition(DEAD_LETTER_TOPIC, record.partition()));
	}

	@SuppressWarnings("unchecked")
	private static DefaultKafkaProducerFactory<Object, Object> asObjectFactory(DefaultKafkaProducerFactory<?, ?> factory) {
		return (DefaultKafkaProducerFactory<Object, Object>) factory;
	}

}
