package de.zbidi.jobtracker.jobapplication;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a job application. Only forward moves are allowed;
 * ACCEPTED, REJECTED and WITHDRAWN are final.
 */
public enum Status {

	SAVED,
	APPLIED,
	INTERVIEW,
	OFFER,
	ACCEPTED,
	REJECTED,
	WITHDRAWN;

	/** Statuses in which the recruiter has the CV and the application is still running. */
	public static final Set<Status> ACTIVE = Set.of(APPLIED, INTERVIEW, OFFER);

	public boolean isActive() {
		return ACTIVE.contains(this);
	}

	public boolean canMoveTo(Status target) {
		return target != null && nextStatuses().contains(target);
	}

	public boolean isFinal() {
		return nextStatuses().isEmpty();
	}

	/** The statuses this one may move to, in pipeline order; empty for final statuses. */
	public Set<Status> nextStatuses() {
		EnumSet<Status> next = switch (this) {
			case SAVED -> EnumSet.of(APPLIED, WITHDRAWN);
			case APPLIED -> EnumSet.of(INTERVIEW, REJECTED, WITHDRAWN);
			case INTERVIEW -> EnumSet.of(OFFER, REJECTED, WITHDRAWN);
			case OFFER -> EnumSet.of(ACCEPTED, REJECTED, WITHDRAWN);
			case ACCEPTED, REJECTED, WITHDRAWN -> EnumSet.noneOf(Status.class);
		};
		return Collections.unmodifiableSet(next);
	}

}
