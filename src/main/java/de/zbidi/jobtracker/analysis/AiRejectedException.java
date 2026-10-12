package de.zbidi.jobtracker.analysis;

import org.jspecify.annotations.Nullable;

/**
 * The model provider refused the request (4xx: bad key, request too large) or answered something unusable.
 * Trying again won't help: 502.
 */
public class AiRejectedException extends RuntimeException {

	public AiRejectedException(String message, @Nullable Throwable cause) {
		super(message, cause);
	}

}
