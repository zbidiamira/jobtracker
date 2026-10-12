package de.zbidi.jobtracker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
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

	@Test
	void postingMatchesTopicAndItsDeadLetterTopic() {
		assertThat(config.postingMatchesTopic().name()).isEqualTo("posting-matches");
		assertThat(config.postingMatchesDeadLetterTopic().name()).isEqualTo("posting-matches.DLT");
		assertThat(config.postingMatchesDeadLetterTopic().numPartitions())
				.isEqualTo(config.postingMatchesTopic().numPartitions());
	}

	/** Each topic has its own dead-letter topic: a broken match event must not end up among the status events. */
	@Test
	void failedRecordsGoToTheDeadLetterTopicOfTheirOwnTopic() {
		assertThat(KafkaConfig.deadLetterDestination(new ConsumerRecord<>("posting-matches", 2, 0L, "k", "v")))
				.isEqualTo(new TopicPartition("posting-matches.DLT", 2));
		assertThat(KafkaConfig.deadLetterDestination(new ConsumerRecord<>("application-status", 1, 0L, "k", "v")))
				.isEqualTo(new TopicPartition("application-status.DLT", 1));
	}

	@Test
	void reminderTopicHasThreePartitions() {
		NewTopic topic = config.applicationRemindersTopic();

		assertThat(topic.name()).isEqualTo("application-reminders");
		assertThat(topic.numPartitions()).isEqualTo(3);
	}

}
