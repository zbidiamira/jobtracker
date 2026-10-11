package de.zbidi.jobtracker.posting;

import java.time.Instant;

/**
 * A validated posting, ready to be written to {@code job_posting}.
 *
 * @param source where it came from, e.g. {@code csv:postings.csv}
 */
public record JobPosting(String title, String companyName, String city, String url, String source,
		Instant importedAt) {
}
