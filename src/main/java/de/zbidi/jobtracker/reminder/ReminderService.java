package de.zbidi.jobtracker.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reminds about applications that are APPLIED and unchanged for {@code jobtracker.reminder.after} (14 days).
 * reminded_at is set in the same transaction; the {@link ReminderEvent} goes to Kafka after the commit
 * ({@link ReminderEventPublisher}), so a rolled-back run sends nothing and a committed one is never repeated.
 */
@Service
public class ReminderService {

	private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

	private final ReminderRepository repository;
	private final ApplicationEventPublisher events;
	private final Clock clock;
	private final Duration after;

	public ReminderService(ReminderRepository repository, ApplicationEventPublisher events, Clock clock,
			@Value("${jobtracker.reminder.after}") Duration after) {
		this.repository = repository;
		this.events = events;
		this.clock = clock;
		this.after = after;
	}

	/** APPLIED applications unchanged for longer than {@code after} (as of the injected clock), not reminded yet. */
	@Transactional(readOnly = true)
	public List<ReminderRepository.DueApplication> findOverdue() {
		return repository.findDue(clock.instant().minus(after));
	}

	/** @return how many reminders were created */
	@Transactional
	public int sendDueReminders() {
		Instant now = clock.instant();
		int sent = 0;
		for (ReminderRepository.DueApplication due : findOverdue()) {
			if (repository.markReminded(due.applicationId(), now)) {
				events.publishEvent(new ReminderEvent(UUID.randomUUID(), due.applicationId(), due.ownerId(),
						due.position(), due.lastChangedAt(), now));
				sent++;
			}
		}
		log.info("{} reminder(s) for applications unchanged since {}", sent, now.minus(after));
		return sent;
	}

}
