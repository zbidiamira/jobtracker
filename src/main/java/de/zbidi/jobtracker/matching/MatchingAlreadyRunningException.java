package de.zbidi.jobtracker.matching;

/** A matching run is already in progress; a second one would analyze (and pay for) the same pairs. */
public class MatchingAlreadyRunningException extends RuntimeException {

	public MatchingAlreadyRunningException() {
		super("A matching run is already in progress");
	}

}
