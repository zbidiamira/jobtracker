package de.zbidi.jobtracker.company;

/**
 * A company can't be deleted while job applications or recruiters still point to it; mapped to 409.
 */
public class CompanyInUseException extends RuntimeException {

	public CompanyInUseException(Long id) {
		super("Company %d still has job applications or recruiters and can't be deleted".formatted(id));
	}

}
