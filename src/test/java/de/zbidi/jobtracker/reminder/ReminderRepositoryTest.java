package de.zbidi.jobtracker.reminder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.reminder.ReminderRepository.DueApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The due query against real PostgreSQL. The cutoff is passed in, so the boundary can be checked to the second.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, ReminderRepository.class})
class ReminderRepositoryTest {

	static final Instant CUTOFF = Instant.parse("2026-09-27T06:00:00Z");

	@Autowired
	ReminderRepository repository;

	@Autowired
	JdbcTemplate jdbc;

	long ownerId;
	long companyId;

	@BeforeEach
	void setUp() {
		ownerId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'USER') RETURNING id",
				Long.class, "reminder-" + UUID.randomUUID() + "@example.com");
		companyId = jdbc.queryForObject("INSERT INTO company (name) VALUES (?) RETURNING id", Long.class,
				"Company " + UUID.randomUUID());
	}

	@Test
	void appliedAndOlderThanTheCutoffIsDue() {
		long id = application("APPLIED", CUTOFF.minusSeconds(1), null);

		assertThat(repository.findDue(CUTOFF))
				.containsExactly(new DueApplication(id, ownerId, "Dev " + id, CUTOFF.minusSeconds(1)));
	}

	@Test
	void changedExactlyAtTheCutoffIsNotDueYet() {
		application("APPLIED", CUTOFF, null);

		assertThat(repository.findDue(CUTOFF)).isEmpty();
	}

	@Test
	void onlyAppliedApplicationsAreDue() {
		application("SAVED", CUTOFF.minus(Duration.ofDays(30)), null);
		application("INTERVIEW", CUTOFF.minus(Duration.ofDays(30)), null);

		assertThat(repository.findDue(CUTOFF)).isEmpty();
	}

	@Test
	void alreadyRemindedIsNotDue() {
		application("APPLIED", CUTOFF.minus(Duration.ofDays(30)), CUTOFF);

		assertThat(repository.findDue(CUTOFF)).isEmpty();
	}

	@Test
	void markRemindedSucceedsOnlyOnce() {
		long id = application("APPLIED", CUTOFF.minus(Duration.ofDays(1)), null);

		assertThat(repository.markReminded(id, CUTOFF)).isTrue();
		assertThat(repository.markReminded(id, CUTOFF.plusSeconds(60))).as("second run, other instance").isFalse();
		assertThat(jdbc.queryForObject("SELECT reminded_at FROM job_application WHERE id = ?", Timestamp.class, id)
				.toInstant()).isEqualTo(CUTOFF);
	}

	/** position = "Dev <id>" so the expected DueApplication can be built from the id. */
	private long application(String status, Instant updatedAt, Instant remindedAt) {
		long id = jdbc.queryForObject("""
				INSERT INTO job_application (owner_id, company_id, position, status, version, created_at, updated_at,
				                             reminded_at)
				VALUES (?, ?, 'tmp', ?, 0, ?, ?, ?) RETURNING id
				""", Long.class, ownerId, companyId, status, Timestamp.from(updatedAt), Timestamp.from(updatedAt),
				remindedAt == null ? null : Timestamp.from(remindedAt));
		jdbc.update("UPDATE job_application SET position = ? WHERE id = ?", "Dev " + id, id);
		return id;
	}

}
