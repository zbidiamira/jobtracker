package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The unique event_id (V7) is the last line of defence against writing one event twice.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class StatusHistoryRepositoryTest {

	private static final Instant CHANGED_AT = Instant.parse("2026-10-07T10:00:00Z");

	@Autowired
	StatusHistoryRepository repository;

	@Autowired
	TestEntityManager em;

	private JobApplication application;

	@BeforeEach
	void setUp() {
		AppUser owner = em.persist(new AppUser("alice@example.com", "$2a$10$hash", Role.USER));
		Company company = em.persist(new Company("ACME GmbH", "Berlin", null));
		application = em.persist(new JobApplication(owner, company, "Java Developer", null));
	}

	@Test
	void existsByEventId() {
		UUID eventId = UUID.randomUUID();
		repository.saveAndFlush(new StatusHistory(application, Status.SAVED, Status.APPLIED, CHANGED_AT, eventId));

		assertThat(repository.existsByEventId(eventId)).isTrue();
		assertThat(repository.existsByEventId(UUID.randomUUID())).isFalse();
	}

	@Test
	void databaseRejectsTheSameEventIdTwice() {
		UUID eventId = UUID.randomUUID();
		repository.saveAndFlush(new StatusHistory(application, Status.SAVED, Status.APPLIED, CHANGED_AT, eventId));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> repository.saveAndFlush(
						new StatusHistory(application, Status.SAVED, Status.APPLIED, CHANGED_AT, eventId)));
	}

	@Test
	void changedAtIsStoredAsGivenNotAsInsertTime() {
		Instant anHourAgo = Instant.now().minusSeconds(3600).truncatedTo(ChronoUnit.MICROS);

		Long id = repository.saveAndFlush(
				new StatusHistory(application, Status.SAVED, Status.APPLIED, anHourAgo, UUID.randomUUID())).getId();
		em.clear();

		assertThat(repository.findById(id).orElseThrow().getChangedAt()).isEqualTo(anHourAgo);
	}

}
