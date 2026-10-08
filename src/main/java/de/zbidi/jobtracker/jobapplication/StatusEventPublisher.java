package de.zbidi.jobtracker.jobapplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link StatusChangedEvent}s to Kafka, but only once the database transaction has committed: a status change
 * that is rolled back must never be announced.
 * <p>
 * Trade-off: if the app dies between commit and send, or Kafka is unreachable, the event is lost (logged as error).
 * A transactional outbox would close that gap.
 */
@Component
public class StatusEventPublisher {

	public static final String TOPIC = "application-status";

	private static final Logger log = LoggerFactory.getLogger(StatusEventPublisher.class);

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public StatusEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(StatusChangedEvent event) {
		// key = application id: same key -> same partition -> events of one application are consumed in order
		kafkaTemplate.send(TOPIC, String.valueOf(event.applicationId()), event)
				.whenComplete((result, failure) -> {
					if (failure != null) {
						log.error("Status event {} for application {} could not be sent to Kafka; its history entry is missing",
								event.eventId(), event.applicationId(), failure);
					}
				});
	}

}
