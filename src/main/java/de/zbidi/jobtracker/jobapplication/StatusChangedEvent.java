package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.UUID;

/**
 * Published after a status change was committed; sent to the Kafka topic {@code application-status} as JSON with
 * the application id as key, so all events of one application land in the same partition, in order.
 *
 * @param eventId   unique per change; consumers use it to ignore redeliveries
 * @param changedAt when the status changed (the application's updatedAt), not when the event is processed
 */
public record StatusChangedEvent(
		UUID eventId,
		Long applicationId,
		Long ownerId,
		Status from,
		Status to,
		Instant changedAt) {
}
