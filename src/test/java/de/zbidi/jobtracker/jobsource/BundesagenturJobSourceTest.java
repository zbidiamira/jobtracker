package de.zbidi.jobtracker.jobsource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import de.zbidi.jobtracker.config.ClockConfig;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Search, then one detail request per hit for the full text. Against recorded-format answers, never the real API.
 */
class BundesagenturJobSourceTest {

	static final String BASE = "https://rest.arbeitsagentur.de/jobboerse/jobsuche-service";

	RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
	MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

	BundesagenturJobSource source = new BundesagenturJobSource(builder.build(), new JobSourceProperties("-",
			List.of("Java"), 30,
			new JobSourceProperties.Arbeitnow(false, "https://example.invalid", 1),
			new JobSourceProperties.Bundesagentur(true, BASE, "jobboerse-jobsuche", "Java", "Berlin", 25, 1,
					Duration.ZERO)));

	@Test
	void searchesThenReadsTheFullTextOfEachHit() {
		search();
		detail("MTAwMDEtMTAwMDAwMDAwMS1T", true); // base64("10001-1000000001-S")
		detail("MTAwMDEtMTAwMDAwMDAwMi1T", false); // this one fails: skipped, the others are kept

		List<FetchedPosting> postings = source.fetch();

		server.verify();
		assertThat(postings).containsExactly(new FetchedPosting("bundesagentur", "10001-1000000001-S",
				"Java-Entwickler (m/w/d)", "Beispiel AG", "Berlin",
				"https://www.arbeitsagentur.de/jobsuche/jobdetail/10001-1000000001-S",
				"Ihre Aufgaben:\n- Entwicklung mit Java und Spring Boot\n- Betrieb auf OpenShift",
				LocalDate.of(2026, 10, 11).atStartOfDay(ClockConfig.BUSINESS_ZONE).toInstant(), null));
	}

	private void search() {
		server.expect(requestTo(startsWith(BASE + "/pc/v6/jobs?")))
				.andExpect(header("X-API-Key", "jobboerse-jobsuche"))
				.andExpect(queryParam("was", "Java"))
				.andExpect(queryParam("wo", "Berlin"))
				.andExpect(queryParam("umkreis", "25"))
				.andExpect(queryParam("veroeffentlichtseit", "1"))
				.andRespond(withSuccess(new ClassPathResource("jobsource/bundesagentur-search.json"),
						MediaType.APPLICATION_JSON));
	}

	private void detail(String encodedRefnr, boolean ok) {
		var request = server.expect(requestTo(BASE + "/pc/v4/jobdetails/" + encodedRefnr))
				.andExpect(header("X-API-Key", "jobboerse-jobsuche"));
		if (ok) {
			request.andRespond(withSuccess(new ClassPathResource("jobsource/bundesagentur-detail-1.json"),
					MediaType.APPLICATION_JSON));
		}
		else {
			request.andRespond(withServerError());
		}
	}

}
