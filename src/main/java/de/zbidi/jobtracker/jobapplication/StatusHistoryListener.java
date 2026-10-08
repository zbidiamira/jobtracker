package de.zbidi.jobtracker.jobapplication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Writes the status timeline from {@link StatusChangedEvent}s. Kafka delivers at least once, so this must be
 * idempotent: an event id that is already stored is ignored.
 * <p>
 * Exceptions are retried by the DefaultErrorHandler (see KafkaConfig) and finally sent to the dead-letter topic.
 * Each repository call runs in its own transaction, so a lost duplicate race can still be checked afterwards.
 */
@Component
public class StatusHistoryListener {

	private static final Logger log = LoggerFactory.getLogger(StatusHistoryListener.class);

	private final StatusHistoryRepository statusHistoryRepository;
	private final JobApplicationRepository jobApplicationRepository;

	public StatusHistoryListener(StatusHistoryRepository statusHistoryRepository,
			JobApplicationRepository jobApplicationRepository) {
		this.statusHistoryRepository = statusHistoryRepository;
		this.jobApplicationRepository = jobApplicationRepository;
	}

	// group id: spring.kafka.consumer.group-id
	@KafkaListener(topics = StatusEventPublisher.TOPIC)
	public void onStatusChanged(StatusChangedEvent event) {
		if (statusHistoryRepository.existsByEventId(event.eventId())) {
			log.debug("Event {} already recorded, ignoring redelivery", event.eventId());
			return;
		}
		if (!jobApplicationRepository.existsById(event.applicationId())) {
			// deleted (by an admin) while the event was on its way: nothing left to record, and retrying won't help
			log.info("Application {} no longer exists, ignoring event {}", event.applicationId(), event.eventId());
			return;
		}
		StatusHistory entry = new StatusHistory(jobApplicationRepository.getReferenceById(event.applicationId()),
				event.from(), event.to(), event.changedAt(), event.eventId());
		try {
			statusHistoryRepository.saveAndFlush(entry);
		}
		catch (DataIntegrityViolationException ex) {
			if (statusHistoryRepository.existsByEventId(event.eventId())) {
				// two deliveries of the same event raced; the unique event_id let exactly one of them in
				log.debug("Event {} was recorded concurrently, ignoring", event.eventId());
				return;
			}
			throw ex;
		}
	}

}
