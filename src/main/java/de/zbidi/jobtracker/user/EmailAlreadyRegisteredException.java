package de.zbidi.jobtracker.user;

/**
 * Registration with an email that already has an account; mapped to 409.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException(String email) {
		super("The email %s is already registered".formatted(email));
	}

}
