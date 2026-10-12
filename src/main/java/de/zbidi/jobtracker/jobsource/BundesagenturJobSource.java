package de.zbidi.jobtracker.jobsource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.zbidi.jobtracker.config.ClockConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Bundesagentur für Arbeit Jobsuche: search {@code /pc/v6/jobs} (was/wo/umkreis/veroeffentlichtseit), then one
 * {@code /pc/v4/jobdetails/{base64(referenznummer)}} request per hit for the full text. The client id goes in the
 * {@code X-API-Key} header. A hit whose details can't be read is skipped; the others are kept.
 */
public class BundesagenturJobSource implements JobSource {

	static final String NAME = "bundesagentur";
	static final String PUBLIC_PAGE = "https://www.arbeitsagentur.de/jobsuche/jobdetail/";

	private static final Logger log = LoggerFactory.getLogger(BundesagenturJobSource.class);

	@JsonIgnoreProperties(ignoreUnknown = true)
	record SearchResult(@Nullable List<Hit> ergebnisliste) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Hit(String referenznummer, @Nullable String stellenangebotsTitel, @Nullable String firma,
			@Nullable List<Location> stellenlokationen, @Nullable String datumErsteVeroeffentlichung) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Location(@Nullable Address adresse) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Address(@Nullable String ort) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Details(@Nullable String stellenangebotsTitel, @Nullable String stellenangebotsBeschreibung) {
	}

	private final RestClient restClient;
	private final JobSourceProperties properties;

	public BundesagenturJobSource(RestClient restClient, JobSourceProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public boolean enabled() {
		return properties.bundesagentur().enabled();
	}

	@Override
	public List<FetchedPosting> fetch() {
		JobSourceProperties.Bundesagentur config = properties.bundesagentur();
		SearchResult result = restClient.get()
				.uri(uri -> uri.path("/pc/v6/jobs")
						.queryParam("was", config.was())
						.queryParam("wo", config.wo())
						.queryParam("umkreis", config.umkreis())
						.queryParam("veroeffentlichtseit", config.publishedWithinDays())
						.queryParam("size", properties.maxPerSource())
						.queryParam("page", 1)
						.build())
				.header("X-API-Key", config.apiKey())
				.retrieve()
				.body(SearchResult.class);
		List<FetchedPosting> postings = new ArrayList<>();
		if (result == null || result.ergebnisliste() == null) {
			return postings;
		}
		for (Hit hit : result.ergebnisliste()) {
			if (postings.size() >= properties.maxPerSource()) {
				break;
			}
			pause(config);
			Details details = details(hit, config);
			if (details == null) {
				continue;
			}
			String title = details.stellenangebotsTitel() != null ? details.stellenangebotsTitel()
					: hit.stellenangebotsTitel();
			String text = HtmlToText.convert(details.stellenangebotsBeschreibung());
			if (title == null || hit.firma() == null || text.isBlank()
					|| !JobSource.mentionsAny(properties.keywords(), title, text)) {
				continue;
			}
			postings.add(new FetchedPosting(NAME, hit.referenznummer(), title, hit.firma(), city(hit),
					PUBLIC_PAGE + hit.referenznummer(), text, published(hit), null));
		}
		return postings;
	}

	private @Nullable Details details(Hit hit, JobSourceProperties.Bundesagentur config) {
		String encoded = Base64.getEncoder()
				.encodeToString(hit.referenznummer().getBytes(StandardCharsets.UTF_8));
		try {
			return restClient.get().uri("/pc/v4/jobdetails/{code}", encoded)
					.header("X-API-Key", config.apiKey())
					.retrieve()
					.body(Details.class);
		}
		catch (RestClientException ex) {
			log.warn("Bundesagentur: details of {} not readable, skipped: {}", hit.referenznummer(), ex.getMessage());
			return null;
		}
	}

	private static @Nullable String city(Hit hit) {
		if (hit.stellenlokationen() == null || hit.stellenlokationen().isEmpty()) {
			return null;
		}
		Address address = hit.stellenlokationen().getFirst().adresse();
		return address == null ? null : address.ort();
	}

	private static @Nullable Instant published(Hit hit) {
		return hit.datumErsteVeroeffentlichung() == null ? null
				: LocalDate.parse(hit.datumErsteVeroeffentlichung()).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant();
	}

	private static void pause(JobSourceProperties.Bundesagentur config) {
		if (config.requestDelay().isZero()) {
			return;
		}
		try {
			Thread.sleep(config.requestDelay());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
