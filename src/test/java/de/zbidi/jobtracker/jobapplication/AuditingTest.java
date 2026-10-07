package de.zbidi.jobtracker.jobapplication;

import java.time.Duration;
import java.time.Instant;

import de.zbidi.jobtracker.MutableClock;
import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.TestClockConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.config.JpaAuditingConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Audit timestamps come from the {@link java.time.Clock} bean, so tests can pin "now".
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class, TestClockConfiguration.class})
class AuditingTest {

	private static final Instant T0 = Instant.parse("2026-10-01T08:00:00Z");

	@Autowired
	JobApplicationRepository jobApplicationRepository;

	@Autowired
	StatusHistoryRepository statusHistoryRepository;

	@Autowired
	TestEntityManager em;

	@Autowired
	MutableClock clock;

	private Company company;

	@BeforeEach
	void setUp() {
		clock.setInstant(T0);
		company = em.persist(new Company("ACME GmbH", "Berlin", null));
	}

	@Test
	void createdAtAndUpdatedAtComeFromClock() {
		Long id = jobApplicationRepository.saveAndFlush(new JobApplication(company, "Java Developer", null)).getId();
		em.clear();

		JobApplication loaded = jobApplicationRepository.findById(id).orElseThrow();

		assertThat(loaded.getCreatedAt()).isEqualTo(T0);
		assertThat(loaded.getUpdatedAt()).isEqualTo(T0);
	}

	@Test
	void updateMovesUpdatedAtButKeepsCreatedAt() {
		JobApplication application = jobApplicationRepository.saveAndFlush(new JobApplication(company, "Java Developer", null));

		clock.advance(Duration.ofHours(1));
		application.changeStatus(Status.APPLIED);
		jobApplicationRepository.saveAndFlush(application);
		em.clear();

		JobApplication loaded = jobApplicationRepository.findById(application.getId()).orElseThrow();
		assertThat(loaded.getCreatedAt()).isEqualTo(T0);
		assertThat(loaded.getUpdatedAt()).isEqualTo(T0.plus(Duration.ofHours(1)));
	}

	@Test
	void statusChangeTimestampComesFromClock() {
		JobApplication application = jobApplicationRepository.saveAndFlush(new JobApplication(company, "Java Developer", null));
		clock.advance(Duration.ofMinutes(30));

		StatusHistory change = statusHistoryRepository.saveAndFlush(new StatusHistory(application, Status.SAVED, Status.APPLIED));

		assertThat(change.getChangedAt()).isEqualTo(T0.plus(Duration.ofMinutes(30)));
	}

}
