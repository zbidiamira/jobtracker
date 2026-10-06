package de.zbidi.jobtracker.jobapplication;

/**
 * Thrown when applying to a job while another application with the same recruiter is still active.
 */
public class RecruiterConflictException extends RuntimeException {

	public RecruiterConflictException(String message) {
		super(message);
	}

}
