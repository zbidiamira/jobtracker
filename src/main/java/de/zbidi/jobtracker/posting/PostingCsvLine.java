package de.zbidi.jobtracker.posting;

/**
 * One data line of an import CSV ({@code title,company,city,url}), as read: untrimmed and unvalidated.
 */
public record PostingCsvLine(String title, String company, String city, String url) {
}
