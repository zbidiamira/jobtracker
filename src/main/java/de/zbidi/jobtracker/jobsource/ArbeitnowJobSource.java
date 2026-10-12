package de.zbidi.jobtracker.jobsource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;
import org.springframework.web.client.RestClient;

/**
 * arbeitnow.com job board API: {@code GET /api/job-board-api?page=n}, no key, the full text is included. There is no
 * search, so postings are filtered here by the keywords; reading stops at the limit or the last page.
 */
public class ArbeitnowJobSource implements JobSource {

	static final String NAME = "arbeitnow";

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Page(@Nullable List<Job> data, @Nullable Links links) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Links(@Nullable String next) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Job(String slug, @JsonProperty("company_name") String companyName, String title,
			@Nullable String description, boolean remote, String url, @Nullable List<String> tags,
			@Nullable String location, @JsonProperty("created_at") long createdAt) {
	}

	private final RestClient restClient;
	private final JobSourceProperties properties;

	public ArbeitnowJobSource(RestClient restClient, JobSourceProperties properties) {
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public boolean enabled() {
		return properties.arbeitnow().enabled();
	}

	@Override
	public List<FetchedPosting> fetch() {
		List<FetchedPosting> postings = new ArrayList<>();
		for (int page = 1; page <= properties.arbeitnow().pages(); page++) {
			Page response = restClient.get().uri("/api/job-board-api?page={page}", page).retrieve().body(Page.class);
			if (response == null || response.data() == null || response.data().isEmpty()) {
				break;
			}
			for (Job job : response.data()) {
				String text = HtmlToText.convert(job.description());
				String tags = job.tags() == null ? "" : String.join(" ", job.tags());
				if (!text.isBlank() && JobSource.mentionsAny(properties.keywords(), job.title(), tags, text)) {
					postings.add(new FetchedPosting(NAME, job.slug(), job.title(), job.companyName(), job.location(),
							job.url(), text, Instant.ofEpochSecond(job.createdAt()), job.remote()));
					if (postings.size() >= properties.maxPerSource()) {
						return postings;
					}
				}
			}
			if (response.links() == null || response.links().next() == null) {
				break;
			}
		}
		return postings;
	}

}
