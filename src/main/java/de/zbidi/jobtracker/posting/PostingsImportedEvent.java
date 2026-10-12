package de.zbidi.jobtracker.posting;

/**
 * New postings arrived: from a CSV import ({@link PostingImportFinishedListener}) or the daily fetch.
 *
 * @param origin  for the log, e.g. {@code import 12} or {@code fetch}
 * @param written number of new postings
 */
public record PostingsImportedEvent(String origin, long written) {
}
