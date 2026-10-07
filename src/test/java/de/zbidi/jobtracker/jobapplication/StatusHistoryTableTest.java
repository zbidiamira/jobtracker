package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Flyway migrations produce a table named {@code status_history} (V4 renamed it from application_status_change).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class StatusHistoryTableTest {

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void historyTableIsCalledStatusHistory() {
		assertThat(tableExists("status_history")).isTrue();
		assertThat(tableExists("application_status_change")).isFalse();
	}

	@Test
	void historyTableHasTheExpectedColumns() {
		assertThat(jdbc.queryForList("""
				SELECT column_name FROM information_schema.columns
				WHERE table_schema = 'public' AND table_name = 'status_history'
				ORDER BY ordinal_position
				""", String.class))
				.containsExactly("id", "job_application_id", "from_status", "to_status", "changed_at");
	}

	private boolean tableExists(String name) {
		Integer count = jdbc.queryForObject(
				"SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
				Integer.class, name);
		return count != null && count > 0;
	}

}
