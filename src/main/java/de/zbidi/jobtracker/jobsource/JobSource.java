package de.zbidi.jobtracker.jobsource;

import java.util.List;
import java.util.Locale;

/**
 * A job portal the daily fetch reads from. Implementations return at most
 * {@code jobtracker.jobs.max-per-source} postings that mention one of the keywords.
 */
public interface JobSource {

	String name();

	boolean enabled();

	/** @throws org.springframework.web.client.RestClientException if the portal can't be read at all */
	List<FetchedPosting> fetch();

	/** True if one of the keywords occurs in one of the texts, ignoring case; no keywords: everything matches. */
	static boolean mentionsAny(List<String> keywords, String... texts) {
		if (keywords.isEmpty()) {
			return true;
		}
		String all = String.join("\n", texts).toLowerCase(Locale.ROOT);
		return keywords.stream().anyMatch(keyword -> all.contains(keyword.toLowerCase(Locale.ROOT)));
	}

}
