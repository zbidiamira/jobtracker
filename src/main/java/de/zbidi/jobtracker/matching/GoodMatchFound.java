package de.zbidi.jobtracker.matching;

import java.time.Instant;
import java.util.UUID;

/**
 * The AI rated a posting at or above {@code jobtracker.matching.notify-score} for a user. Sent to the Kafka topic
 * {@code posting-matches} (key = user id) after the result is committed.
 *
 * @param eventId unique per event, for idempotent consumers
 */
public record GoodMatchFound(UUID eventId, long userId, long postingId, String title, String companyName,
		String url, int matchScore, Instant analyzedAt) {
}
