package de.zbidi.jobtracker.reminder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends {@link ReminderEvent}s to Kafka after reminded_at has been committed, like StatusEventPublisher.
 * Same trade-off: if the send fails after the commit, that reminder is lost (logged), never sent twice.
 */
@Component
public class ReminderEventPublisher {

	public static final String TOPIC = "application-reminders";

	private static final Logger log = LoggerFactory.getLogger(ReminderEventPublisher.class);

	private final KafkaTemplate<String, Object> kafkaTemplate;

	public ReminderEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
		this.kafkaTemplate = kafkaTemplate;
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void publish(ReminderEvent event) {
		kafkaTemplate.send(TOPIC, String.valueOf(event.applicationId()), event)
				.whenComplete((result, failure) -> {
					if (failure != null) {
						log.error("Reminder {} for application {} could not be sent to Kafka",
								event.eventId(), event.applicationId(), failure);
					}
				});
	}

}
