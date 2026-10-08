package de.zbidi.jobtracker;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Reads a topic straight from the broker, the way an outside consumer would see it: raw key and value bytes,
 * headers, no Spring. Reads all partitions from the beginning without a consumer group, so it never interferes
 * with the application's own listener.
 */
public final class TopicProbe {

	private final String bootstrapServers;

	public TopicProbe(String bootstrapServers) {
		this.bootstrapServers = bootstrapServers;
	}

	/** A record as plain text, for assertions. */
	public record Message(String key, String value, Map<String, String> headers) {
	}

	/** Waits until a matching record appears (or fails after {@code timeout}). */
	public Message awaitRecord(String topic, Predicate<Message> matching, Duration timeout) {
		List<Message> found = read(topic, matching, timeout, true);
		if (found.isEmpty()) {
			throw new AssertionError("No matching record on " + topic + " within " + timeout);
		}
		return found.getFirst();
	}

	/** All matching records that show up within {@code duration} (reads for the whole duration). */
	public List<Message> recordsWithin(String topic, Predicate<Message> matching, Duration duration) {
		return read(topic, matching, duration, false);
	}

	private List<Message> read(String topic, Predicate<Message> matching, Duration timeout, boolean stopAtFirst) {
		Map<String, Object> config = Map.of(
				ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class,
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		List<Message> found = new ArrayList<>();
		try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config)) {
			List<TopicPartition> partitions = consumer.partitionsFor(topic).stream()
					.map(info -> new TopicPartition(topic, info.partition()))
					.toList();
			consumer.assign(partitions);
			consumer.seekToBeginning(partitions);
			Instant deadline = Instant.now().plus(timeout);
			while (Instant.now().isBefore(deadline)) {
				for (ConsumerRecord<String, byte[]> record : consumer.poll(Duration.ofMillis(200))) {
					Message message = toMessage(record);
					if (matching.test(message)) {
						found.add(message);
						if (stopAtFirst) {
							return found;
						}
					}
				}
			}
		}
		return found;
	}

	private static Message toMessage(ConsumerRecord<String, byte[]> record) {
		Map<String, String> headers = new LinkedHashMap<>();
		for (Header header : record.headers()) {
			headers.put(header.key(), header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8));
		}
		String value = record.value() == null ? null : new String(record.value(), StandardCharsets.UTF_8);
		return new Message(record.key(), value, headers);
	}

}
