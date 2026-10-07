package de.zbidi.jobtracker.jobapplication;

/**
 * The client sent a change based on an outdated version (someone else saved in between); mapped to 409.
 */
public class StaleVersionException extends RuntimeException {

	private final Long expectedVersion;
	private final Long currentVersion;

	public StaleVersionException(Long id, Long expectedVersion, Long currentVersion) {
		super("Job application %d was changed by someone else (your version %d, current version %d). Reload and try again."
				.formatted(id, expectedVersion, currentVersion));
		this.expectedVersion = expectedVersion;
		this.currentVersion = currentVersion;
	}

	public Long getExpectedVersion() {
		return expectedVersion;
	}

	public Long getCurrentVersion() {
		return currentVersion;
	}

}
