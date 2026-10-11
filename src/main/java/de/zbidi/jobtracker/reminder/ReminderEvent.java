package de.zbidi.jobtracker.reminder;

import java.time.Instant;
import java.util.UUID;

/**
 * "You applied, nothing has happened for a while": sent once per application to the Kafka topic
 * {@code application-reminders} (JSON, key = application id). No consumer yet; a notification service would
 * subscribe.
 *
 * @param eventId       unique per reminder, for idempotent consumers
 * @param lastChangedAt the application's updated_at: since when it has been waiting
 * @param remindedAt    when the reminder was created
 */
public record ReminderEvent(
		UUID eventId,
		Long applicationId,
		Long ownerId,
		String position,
		Instant lastChangedAt,
		Instant remindedAt) {
}
