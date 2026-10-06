package de.zbidi.jobtracker.jobapplication;

/**
 * Thrown when an application for the same job (same URL, or same company + position) already exists.
 */
public class DuplicateApplicationException extends RuntimeException {

	public DuplicateApplicationException(String message) {
		super(message);
	}

}
