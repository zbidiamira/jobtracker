package de.zbidi.jobtracker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaConfigTest {

	private final KafkaConfig config = new KafkaConfig();

	@Test
	void statusTopicHasThreePartitions() {
		NewTopic topic = config.applicationStatusTopic();

		assertThat(topic.name()).isEqualTo("application-status");
		assertThat(topic.numPartitions()).isEqualTo(3);
	}

	@Test
	void deadLetterTopicHasTheSamePartitionCount() {
		// the DeadLetterPublishingRecoverer writes to the same partition number as the failed record
		NewTopic dlt = config.applicationStatusDeadLetterTopic();

		assertThat(dlt.name()).isEqualTo("application-status.DLT");
		assertThat(dlt.numPartitions()).isEqualTo(config.applicationStatusTopic().numPartitions());
	}

}
