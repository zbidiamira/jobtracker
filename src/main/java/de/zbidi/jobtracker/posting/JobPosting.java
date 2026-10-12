package de.zbidi.jobtracker.posting;

import java.time.Instant;

/**
 * A validated posting, ready to be written to {@code job_posting}.
 *
 * @param description the full job text; this is what the AI analyzes
 * @param source      where it came from, e.g. {@code csv:postings.csv}
 */
public record JobPosting(String title, String companyName, String city, String url, String description,
		String source, Instant importedAt) {
}
