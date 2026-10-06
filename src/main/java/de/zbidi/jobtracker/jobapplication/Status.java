package de.zbidi.jobtracker.jobapplication;

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
		return target != null && allowedTargets().contains(target);
	}

	public boolean isFinal() {
		return allowedTargets().isEmpty();
	}

	private Set<Status> allowedTargets() {
		return switch (this) {
			case SAVED -> Set.of(APPLIED, WITHDRAWN);
			case APPLIED -> Set.of(INTERVIEW, REJECTED, WITHDRAWN);
			case INTERVIEW -> Set.of(OFFER, REJECTED, WITHDRAWN);
			case OFFER -> Set.of(ACCEPTED, REJECTED, WITHDRAWN);
			case ACCEPTED, REJECTED, WITHDRAWN -> Set.of();
		};
	}

}
