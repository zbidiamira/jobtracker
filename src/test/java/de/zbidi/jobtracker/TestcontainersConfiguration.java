package de.zbidi.jobtracker;

import java.util.UUID;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Full-app tests ({@code @SpringBootTest}): PostgreSQL + Kafka.
 * <p>
 * The containers are <b>static singletons</b>: every Spring test context that imports this class shares the same two
 * containers instead of starting its own (Kafka is memory-hungry; Docker Desktop here has ~4 GB). Tests use unique
 * data, so sharing is safe.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:latest"));

	// JVM image: kafka-native (GraalVM) segfaulted on startup under Docker Desktop
	static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1"))
			.withEnv("KAFKA_HEAP_OPTS", "-Xmx256m -Xms256m");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return POSTGRES;
	}

	@Bean
	@ServiceConnection
	KafkaContainer kafkaContainer() {
		return KAFKA;
	}

	/**
	 * Each cached Spring context runs its own @KafkaListener. With one shared group they would split the partitions
	 * between them, and an event sent by one test could be processed by another test's context. A group per context
	 * means every context sees every event (harmless: the listener is idempotent).
	 */
	@Bean
	DynamicPropertyRegistrar consumerGroupPerContext() {
		String group = "jobtracker-test-" + UUID.randomUUID();
		return registry -> registry.add("spring.kafka.consumer.group-id", () -> group);
	}

}
