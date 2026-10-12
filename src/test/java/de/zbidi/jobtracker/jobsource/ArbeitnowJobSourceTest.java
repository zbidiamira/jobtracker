package de.zbidi.jobtracker.jobsource;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Against recorded-format answers (src/test/resources/jobsource), never the real site.
 */
class ArbeitnowJobSourceTest {

	static final String PAGE = "https://www.arbeitnow.com/api/job-board-api?page=";

	RestClient.Builder builder = RestClient.builder().baseUrl("https://www.arbeitnow.com");
	MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	ArbeitnowJobSource source(int maxPerSource, int pages) {
		JobSourceProperties properties = new JobSourceProperties("-", List.of("Java", "Spring"), maxPerSource,
				new JobSourceProperties.Arbeitnow(true, "https://www.arbeitnow.com", pages),
				new JobSourceProperties.Bundesagentur(false, "https://example.invalid", "key", "Java", "Berlin", 25, 1,
						Duration.ZERO));
		return new ArbeitnowJobSource(builder.build(), properties);
	}

	@Test
	void readsPagesAndKeepsOnlyPostingsMatchingAKeyword() {
		page(1);
		page(2);

		List<FetchedPosting> postings = source(30, 2).fetch();

		server.verify();
		// "Marketing Manager" mentions neither Java nor Spring; the Kotlin job has the tag "Java"
		assertThat(postings).extracting(FetchedPosting::title)
				.containsExactly("Java Backend Developer (m/w/d)", "Kotlin Developer", "Senior Spring Engineer");
	}

	@Test
	void mapsTheFields() {
		page(1);

		FetchedPosting posting = source(1, 1).fetch().getFirst();

		assertThat(posting).isEqualTo(new FetchedPosting("arbeitnow", "java-backend-berlin-1",
				"Java Backend Developer (m/w/d)", "ACME GmbH", "Berlin",
				"https://www.arbeitnow.com/jobs/companies/x/java-backend-berlin-1",
				"Your tasks\n- Build REST APIs with Spring Boot\n- Run PostgreSQL & Kafka",
				Instant.ofEpochSecond(1791500000), false));
	}

	@Test
	void stopsAtTheLimitWithoutReadingMorePages() {
		page(1);

		assertThat(source(2, 5).fetch()).hasSize(2);
		server.verify(); // no request for page 2
	}

	@Test
	void stopsWhenThereIsNoNextPage() {
		page(1);
		page(2);

		assertThat(source(30, 5).fetch()).hasSize(3);
		server.verify(); // page 2 says next = null: no page 3
	}

	@Test
	void serverErrorIsReported() {
		server.expect(requestTo(PAGE + 1)).andExpect(method(HttpMethod.GET)).andRespond(withServerError());

		assertThatThrownBy(() -> source(30, 1).fetch()).isInstanceOf(HttpServerErrorException.class);
	}

	private void page(int number) {
		server.expect(requestTo(PAGE + number)).andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(new ClassPathResource("jobsource/arbeitnow-page" + number + ".json"),
						MediaType.APPLICATION_JSON));
	}

}
