package de.zbidi.jobtracker.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link GoodMatchFound} to Kafka once the match result is committed (the match job's chunk transaction), like
 * the status and reminder events: a rolled-back result is never announced.
 */
@Component
public class MatchEvents {

	public static final String TOPIC = "posting-matches";

	private static final Logger log = LoggerFactory.getLogger(MatchEvents.class);

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public MatchEvents(KafkaTemplate<String, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	/** Key = user id: all good matches of one user arrive in order. */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(GoodMatchFound event) {
		kafkaTemplate.send(TOPIC, String.valueOf(event.userId()), event)
				.whenComplete((result, failure) -> {
					if (failure != null) {
						log.error("Good match {} (posting {}, user {}) could not be sent to Kafka", event.eventId(),
								event.postingId(), event.userId(), failure);
					}
				});
	}

}
