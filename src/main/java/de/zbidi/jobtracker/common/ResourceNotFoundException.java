package de.zbidi.jobtracker.common;

/**
 * Thrown when a requested entity does not exist; mapped to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

	public ResourceNotFoundException(String resource, Long id) {
		super("%s %d not found".formatted(resource, id));
	}

}
