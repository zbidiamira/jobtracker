package de.zbidi.jobtracker.posting;

/**
 * One record of an import CSV ({@code title,company,city,url,description}), as read: untrimmed and unvalidated.
 * The description may span several lines when it is quoted.
 */
public record PostingCsvLine(String title, String company, String city, String url, String description) {
}
