package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V13: one AI result per posting and user; failed analyses are stored too, so they aren't repeated every run.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class PostingMatchTableTest {

	@Autowired
	JdbcTemplate jdbc;

	long postingId;
	long userId;

	@BeforeEach
	void setUp() {
		userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'USER') RETURNING id",
				Long.class, "match-" + UUID.randomUUID() + "@example.com");
		postingId = jdbc.queryForObject("""
				INSERT INTO job_posting (title, company_name, url, source, imported_at, description)
				VALUES ('Dev', 'ACME', ?, 'test', ?, 'Java') RETURNING id
				""", Long.class, "https://match.example/" + UUID.randomUUID(), Timestamp.from(Instant.now()));
	}

	@Test
	void hasTheExpectedColumns() {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'posting_match'
				ORDER BY ordinal_position
				""", String.class))
				.containsExactly("id", "posting_id", "user_id", "status", "match_score", "analysis", "error",
						"analyzed_at");
	}

	@Test
	void onePerPostingAndUser() {
		insert("MATCHED", 80);

		assertThatThrownBy(() -> insert("FAILED", null)).isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void scoreMustBeBetween0And100() {
		assertThatThrownBy(() -> insert("MATCHED", 101)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void statusIsMatchedOrFailed() {
		assertThatThrownBy(() -> insert("MAYBE", 50)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingThePostingDeletesItsMatches() {
		insert("MATCHED", 80);

		jdbc.update("DELETE FROM job_posting WHERE id = ?", postingId);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM posting_match WHERE posting_id = ?", Integer.class,
				postingId)).isZero();
	}

	private void insert(String status, Integer score) {
		jdbc.update("""
				INSERT INTO posting_match (posting_id, user_id, status, match_score, analysis, analyzed_at)
				VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
				""", postingId, userId, status, score, score == null ? null : "{\"matchScore\": " + score + "}",
				Timestamp.from(Instant.now()));
	}

}
