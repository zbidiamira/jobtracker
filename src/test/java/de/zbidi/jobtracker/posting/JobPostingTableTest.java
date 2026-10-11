package de.zbidi.jobtracker.posting;

import java.sql.Timestamp;
import java.time.Instant;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * V8 creates job_posting, V10 the Spring Batch metadata tables (managed by Flyway, not by Boot's initializer).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class JobPostingTableTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void jobPostingTableHasTheExpectedColumns() {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'job_posting'
				ORDER BY ordinal_position
				""", String.class))
				.containsExactly("id", "title", "company_name", "city", "url", "source", "imported_at");
	}

	@Test
	void urlIsUnique() {
		insert("https://example.com/jobs/1");

		assertThatThrownBy(() -> insert("https://example.com/jobs/1"))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void springBatchMetadataTablesExist() {
		assertThat(jdbc.queryForList("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_name LIKE 'batch\\_%'
				""", String.class))
				.contains("batch_job_instance", "batch_job_execution", "batch_job_execution_params",
						"batch_job_execution_context", "batch_step_execution", "batch_step_execution_context");
	}

	private void insert(String url) {
		jdbc.update("""
				INSERT INTO job_posting (title, company_name, city, url, source, imported_at)
				VALUES ('Java Developer', 'ACME', 'Berlin', ?, 'test', ?)
				""", url, Timestamp.from(Instant.parse("2026-10-01T08:00:00Z")));
	}

}
