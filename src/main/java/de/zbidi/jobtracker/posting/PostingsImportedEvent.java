package de.zbidi.jobtracker.posting;

/**
 * An import finished and wrote new postings (published by {@link PostingImportFinishedListener}).
 *
 * @param written number of new postings
 */
public record PostingsImportedEvent(long jobExecutionId, long written) {
}
