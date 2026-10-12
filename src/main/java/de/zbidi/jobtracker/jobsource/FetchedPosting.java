package de.zbidi.jobtracker.jobsource;

import java.time.Instant;

import org.jspecify.annotations.Nullable;

/**
 * A posting as a job portal delivers it, already reduced to what job_posting stores.
 *
 * @param source      the portal, e.g. {@code arbeitnow}
 * @param externalId  the portal's own id
 * @param url         the posting's public page (links back to the portal)
 * @param description the full job text as plain text
 * @param remote      null when the portal doesn't say
 */
public record FetchedPosting(String source, String externalId, String title, String companyName,
		@Nullable String city, String url, String description, @Nullable Instant publishedAt,
		@Nullable Boolean remote) {
}
