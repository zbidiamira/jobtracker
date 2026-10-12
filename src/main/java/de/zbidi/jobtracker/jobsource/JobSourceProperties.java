package de.zbidi.jobtracker.jobsource;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code jobtracker.jobs.*}: what the daily fetch looks for and where.
 *
 * @param fetchCron    when the fetch runs ("-" switches it off)
 * @param keywords     a posting is kept if it mentions one of them (title, tags or text)
 * @param maxPerSource at most this many postings per portal and run (each one costs AI time later)
 */
@ConfigurationProperties("jobtracker.jobs")
public record JobSourceProperties(String fetchCron, List<String> keywords, int maxPerSource, Arbeitnow arbeitnow,
		Bundesagentur bundesagentur) {

	/** arbeitnow.com: public, no key; "please do not abuse", so only a few pages per run. */
	public record Arbeitnow(boolean enabled, String baseUrl, int pages) {
	}

	/**
	 * Bundesagentur für Arbeit Jobsuche (documented by the community project bundesAPI).
	 *
	 * @param apiKey              the public client id the API expects in {@code X-API-Key}; not a secret
	 * @param publishedWithinDays only postings published in the last n days
	 * @param requestDelay        pause between the detail requests, to be polite
	 */
	public record Bundesagentur(boolean enabled, String baseUrl, String apiKey, String was, String wo, int umkreis,
			int publishedWithinDays, Duration requestDelay) {
	}

}
