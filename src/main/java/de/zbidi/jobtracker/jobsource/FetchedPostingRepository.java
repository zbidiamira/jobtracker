package de.zbidi.jobtracker.jobsource;

import java.sql.Timestamp;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Saves fetched postings into job_posting. Values are cut to the column sizes (portals don't know them); a posting
 * whose url doesn't fit is not saved, since the url is what identifies it.
 */
@Repository
public class FetchedPostingRepository {

	static final int MAX_TITLE = 200;
	static final int MAX_COMPANY = 200;
	static final int MAX_CITY = 100;
	static final int MAX_URL = 500;
	static final int MAX_EXTERNAL_ID = 200;
	/** Same limit as the CSV import and the analyzer's input. */
	static final int MAX_DESCRIPTION = 10_000;

	private final JdbcClient jdbc;

	public FetchedPostingRepository(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** @return true if it was new; false if its url is already known (or too long to store) */
	public boolean insertIfNew(FetchedPosting posting, Instant importedAt) {
		if (posting.url().length() > MAX_URL) {
			return false;
		}
		return jdbc.sql("""
						INSERT INTO job_posting (title, company_name, city, url, description, source, external_id,
						                         published_at, remote, imported_at)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						ON CONFLICT (url) DO NOTHING
						""")
				.params(cut(posting.title(), MAX_TITLE), cut(posting.companyName(), MAX_COMPANY),
						cut(posting.city(), MAX_CITY), posting.url(), cut(posting.description(), MAX_DESCRIPTION),
						posting.source(), cut(posting.externalId(), MAX_EXTERNAL_ID),
						posting.publishedAt() == null ? null : Timestamp.from(posting.publishedAt()), posting.remote(),
						Timestamp.from(importedAt))
				.update() == 1;
	}

	private static @Nullable String cut(@Nullable String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

}
