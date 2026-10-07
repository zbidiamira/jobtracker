package de.zbidi.jobtracker.jobapplication;

/**
 * Thrown when a status change is not allowed by {@link Status#canMoveTo(Status)}.
 */
public class InvalidStatusTransitionException extends IllegalStateException {

	private final Status currentStatus;
	private final Status requestedStatus;

	public InvalidStatusTransitionException(Status currentStatus, Status requestedStatus) {
		super("Cannot change status from %s to %s".formatted(currentStatus, requestedStatus));
		this.currentStatus = currentStatus;
		this.requestedStatus = requestedStatus;
	}

	public Status getCurrentStatus() {
		return currentStatus;
	}

	public Status getRequestedStatus() {
		return requestedStatus;
	}

}
