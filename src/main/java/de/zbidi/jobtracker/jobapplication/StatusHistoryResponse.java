package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;

public record StatusHistoryResponse(Status fromStatus, Status toStatus, Instant changedAt) {

	public static StatusHistoryResponse from(StatusHistory change) {
		return new StatusHistoryResponse(change.getFromStatus(), change.getToStatus(), change.getChangedAt());
	}

}
