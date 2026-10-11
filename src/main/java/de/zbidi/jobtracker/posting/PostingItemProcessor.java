package de.zbidi.jobtracker.posting;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.util.HashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.batch.infrastructure.item.validator.ValidationException;

/**
 * Trims and validates a CSV line.
 * <ul>
 *     <li>invalid line → {@link ValidationException}: the step skips it (counted, logged, limited)</li>
 *     <li>url already imported, or seen earlier in this file → {@code null}: filtered, that's not an error</li>
 * </ul>
 * Step scoped (one instance per execution), so the set of urls seen covers exactly this run. After a restart it
 * starts empty, which is fine: the urls of committed chunks are found in the database.
 */
public class PostingItemProcessor implements ItemProcessor<PostingCsvLine, JobPosting> {

	static final int MAX_TITLE = 200;
	static final int MAX_COMPANY = 200;
	static final int MAX_CITY = 100;
	static final int MAX_URL = 500;

	private final JobPostingRepository repository;
	private final Clock clock;
	private final String source;
	private final Set<String> seenUrls = new HashSet<>();

	public PostingItemProcessor(JobPostingRepository repository, Clock clock, String source) {
		this.repository = repository;
		this.clock = clock;
		this.source = source;
	}

	@Override
	public @Nullable JobPosting process(PostingCsvLine line) {
		String title = required("title", line.title(), MAX_TITLE);
		String company = required("company", line.company(), MAX_COMPANY);
		String city = optional("city", line.city(), MAX_CITY);
		String url = required("url", line.url(), MAX_URL);
		requireHttpUrl(url);

		if (!seenUrls.add(url) || repository.existsByUrl(url)) {
			return null;
		}
		return new JobPosting(title, company, city, url, source, clock.instant());
	}

	private static String required(String field, @Nullable String value, int maxLength) {
		String trimmed = optional(field, value, maxLength);
		if (trimmed == null) {
			throw new ValidationException(field + " is required");
		}
		return trimmed;
	}

	private static @Nullable String optional(String field, @Nullable String value, int maxLength) {
		if (value == null || value.isBlank()) {
			return null;
		}
		String trimmed = value.strip();
		if (trimmed.length() > maxLength) {
			throw new ValidationException("%s is longer than %d characters".formatted(field, maxLength));
		}
		return trimmed;
	}

	private static void requireHttpUrl(String url) {
		try {
			URI uri = new URI(url);
			boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
			if (!http || uri.getHost() == null) {
				throw new ValidationException("url must be an absolute http(s) URL: " + url);
			}
		}
		catch (URISyntaxException ex) {
			throw new ValidationException("url is not a valid URL: " + url, ex);
		}
	}

}
