package de.zbidi.jobtracker.jobapplication;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Thrown when a status change is not allowed by {@link Status#canMoveTo(Status)}.
 */
public class InvalidStatusTransitionException extends IllegalStateException {

	private final Status currentStatus;
	private final Status requestedStatus;

	public InvalidStatusTransitionException(Status currentStatus, Status requestedStatus) {
		super(message(currentStatus, requestedStatus));
		this.currentStatus = currentStatus;
		this.requestedStatus = requestedStatus;
	}

	public Status getCurrentStatus() {
		return currentStatus;
	}

	public Status getRequestedStatus() {
		return requestedStatus;
	}

	public Set<Status> getAllowedStatuses() {
		return currentStatus.nextStatuses();
	}

	/** Says what went wrong and what the user can do instead. */
	private static String message(Status current, Status requested) {
		String failed = "Cannot change status from %s to %s".formatted(current, requested);
		if (current.isFinal()) {
			return failed + ": %s is a final status.".formatted(current);
		}
		String allowed = current.nextStatuses().stream().map(Status::name).collect(Collectors.joining(", "));
		return failed + ". Allowed next statuses: " + allowed + ".";
	}

}
