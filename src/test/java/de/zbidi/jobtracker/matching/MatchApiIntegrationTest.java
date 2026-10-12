package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.ApiAuth;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real token and database: /api/matches shows only the caller's successful results, best first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MatchApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	String alice;
	long aliceId;
	long bobId;

	@BeforeEach
	void setUp() {
		String email = "matches-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		alice = ApiAuth.register(mvc, email);
		aliceId = jdbc.queryForObject("SELECT id FROM app_user WHERE email = ?", Long.class, email);
		bobId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'USER') RETURNING id",
				Long.class, "bob-" + UUID.randomUUID() + "@example.com");
	}

	@Test
	void showsOnlyMyMatchesBestFirstWithoutFailures() {
		match(aliceId, posting("Good"), "MATCHED", 90);
		match(aliceId, posting("Okay"), "MATCHED", 60);
		match(aliceId, posting("Broken"), "FAILED", null);
		match(bobId, posting("Bobs"), "MATCHED", 99);

		MvcTestResult result = mvc.get().uri("/api/matches").header(HttpHeaders.AUTHORIZATION, alice).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.content[*].title").asArray()
				.containsExactly("Good", "Okay");
		assertThat(result).bodyJson().extractingPath("$.content[0].summary").isEqualTo("Summary of Good");
		assertThat(result).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
	}

	@Test
	void minScoreFilters() {
		match(aliceId, posting("Good"), "MATCHED", 90);
		match(aliceId, posting("Okay"), "MATCHED", 60);

		MvcTestResult result = mvc.get().uri("/api/matches?minScore=75").header(HttpHeaders.AUTHORIZATION, alice)
				.exchange();

		assertThat((java.util.List<String>) JsonPath.read(ApiAuth.content(result), "$.content[*].title"))
				.containsExactly("Good");
	}

	private long posting(String title) {
		return jdbc.queryForObject("""
				INSERT INTO job_posting (title, company_name, city, url, source, imported_at, description)
				VALUES (?, 'ACME', 'Berlin', ?, 'test', ?, 'text') RETURNING id
				""", Long.class, title, "https://matches.example/" + UUID.randomUUID(), Timestamp.from(Instant.now()));
	}

	private void match(long userId, long postingId, String status, Integer score) {
		String title = jdbc.queryForObject("SELECT title FROM job_posting WHERE id = ?", String.class, postingId);
		String analysis = score == null ? null : """
				{"title": "%s", "mustHave": ["Java"], "niceToHave": [], "matchedSkills": ["Java"],
				 "missingSkills": [], "matchScore": %d, "summary": "Summary of %s"}""".formatted(title, score, title);
		jdbc.update("""
				INSERT INTO posting_match (posting_id, user_id, status, match_score, analysis, analyzed_at)
				VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
				""", postingId, userId, status, score, analysis, Timestamp.from(Instant.now()));
	}

}
