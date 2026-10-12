package de.zbidi.jobtracker.analysis;

/**
 * The model can't answer right now (not configured, down, overloaded, too slow). The client may try again later:
 * 503 with Retry-After.
 */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message) {
		super(message);
	}

	public AiUnavailableException(String message, Throwable cause) {
		super(message, cause);
	}

}
