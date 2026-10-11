package de.zbidi.jobtracker.posting;

/**
 * Only a FAILED or STOPPED import can be restarted.
 */
public class ImportNotRestartableException extends RuntimeException {

	public ImportNotRestartableException(long executionId, String reason) {
		super("Import %d can't be restarted: %s".formatted(executionId, reason));
	}

}
