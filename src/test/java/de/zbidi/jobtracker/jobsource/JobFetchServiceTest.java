package de.zbidi.jobtracker.jobsource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.posting.PostingsImportedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Saving fetched postings in the real job_posting table; the portals are stand-ins.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, FetchedPostingRepository.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class JobFetchServiceTest {

	static final Instant NOW = Instant.parse("2026-10-12T05:00:00Z");

	@Autowired
	FetchedPostingRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	String prefix;

	@BeforeEach
	void setUp() {
		prefix = "https://fetch.example/" + UUID.randomUUID() + "/";
	}

	@Test
	void savesNewPostingsWithAllTheirDetails() {
		FetchedPosting posting = posting("1", "Java Developer");

		List<JobFetchService.FetchResult> results = service(source("arbeitnow", posting)).fetchAll();

		assertThat(results).containsExactly(new JobFetchService.FetchResult("arbeitnow", 1, 1, null));
		Map<String, Object> row = jdbc.queryForMap("""
				SELECT title, company_name, city, description, source, external_id, published_at, remote, imported_at
				FROM job_posting WHERE url = ?
				""", prefix + "1");
		assertThat(row).containsEntry("title", "Java Developer").containsEntry("company_name", "ACME")
				.containsEntry("city", "Berlin").containsEntry("description", "Build Java services.")
				.containsEntry("source", "arbeitnow").containsEntry("external_id", "ext-1")
				.containsEntry("remote", true);
		assertThat(((java.sql.Timestamp) row.get("published_at")).toInstant())
				.isEqualTo(Instant.parse("2026-10-11T10:00:00Z"));
		assertThat(((java.sql.Timestamp) row.get("imported_at")).toInstant()).isEqualTo(NOW);
	}

	@Test
	void knownUrlsAreNotSavedAgain() {
		JobFetchService service = service(source("arbeitnow", posting("1", "Java Developer")));
		service.fetchAll();

		assertThat(service.fetchAll()).containsExactly(new JobFetchService.FetchResult("arbeitnow", 1, 0, null));
		assertThat(jdbc.queryForObject("SELECT count(*) FROM job_posting WHERE url = ?", Integer.class, prefix + "1"))
				.isEqualTo(1);
	}

	@Test
	void aFailingPortalIsReportedAndTheOthersStillRun() {
		JobSource broken = new StubSource("bundesagentur", true, null);

		List<JobFetchService.FetchResult> results = service(broken, source("arbeitnow", posting("1", "Java"))).fetchAll();

		assertThat(results).containsExactly(
				new JobFetchService.FetchResult("bundesagentur", 0, 0, "I/O error: connection refused"),
				new JobFetchService.FetchResult("arbeitnow", 1, 1, null));
	}

	@Test
	void disabledSourcesAreNotAsked() {
		StubSource disabled = new StubSource("arbeitnow", false, List.of(posting("1", "Java")));

		assertThat(service(disabled).fetchAll()).isEmpty();
		assertThat(disabled.asked).isFalse();
	}

	/** New postings go straight to the AI matching (MatchingService listens to this event). */
	@Test
	void newPostingsAreAnnounced() {
		service(source("arbeitnow", posting("1", "Java"), posting("2", "Spring"))).fetchAll();

		verify(events).publishEvent(new PostingsImportedEvent("fetch", 2));
	}

	@Test
	void nothingNewNothingAnnounced() {
		JobFetchService service = service(source("arbeitnow", posting("1", "Java")));
		service.fetchAll();
		org.mockito.Mockito.clearInvocations(events);

		service.fetchAll();

		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void valuesAreCutToTheColumnSizes() {
		FetchedPosting longOne = new FetchedPosting("arbeitnow", "x".repeat(300), "T".repeat(300), "C".repeat(300),
				"B".repeat(150), prefix + "long", "d".repeat(12_000), null, null);

		service(source("arbeitnow", longOne)).fetchAll();

		Map<String, Object> row = jdbc.queryForMap("""
				SELECT length(title) AS t, length(company_name) AS c, length(city) AS ci, length(external_id) AS e,
				       length(description) AS d
				FROM job_posting WHERE url = ?
				""", prefix + "long");
		assertThat(row).containsEntry("t", 200).containsEntry("c", 200).containsEntry("ci", 100)
				.containsEntry("e", 200).containsEntry("d", 10_000);
	}

	@Test
	void postingWithAnOverlongUrlIsSkipped() {
		FetchedPosting badUrl = new FetchedPosting("arbeitnow", "1", "Java", "ACME", null, prefix + "u".repeat(600),
				"text", null, null);

		assertThat(service(source("arbeitnow", badUrl)).fetchAll())
				.containsExactly(new JobFetchService.FetchResult("arbeitnow", 1, 0, null));
	}

	private JobFetchService service(JobSource... sources) {
		return new JobFetchService(List.of(sources), repository, events, Clock.fixed(NOW, ZoneId.of("UTC")));
	}

	private FetchedPosting posting(String id, String title) {
		return new FetchedPosting("arbeitnow", "ext-" + id, title, "ACME", "Berlin", prefix + id,
				"Build Java services.", Instant.parse("2026-10-11T10:00:00Z"), true);
	}

	private static StubSource source(String name, FetchedPosting... postings) {
		return new StubSource(name, true, List.of(postings));
	}

	static final class StubSource implements JobSource {

		final String name;
		final boolean enabled;
		final List<FetchedPosting> postings;
		boolean asked;

		StubSource(String name, boolean enabled, List<FetchedPosting> postings) {
			this.name = name;
			this.enabled = enabled;
			this.postings = postings;
		}

		@Override
		public String name() {
			return name;
		}

		@Override
		public boolean enabled() {
			return enabled;
		}

		@Override
		public List<FetchedPosting> fetch() {
			asked = true;
			if (postings == null) {
				throw new ResourceAccessException("I/O error: connection refused");
			}
			return postings;
		}

	}

}
