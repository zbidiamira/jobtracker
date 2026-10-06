package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.config.JpaAuditingConfig;
import de.zbidi.jobtracker.recruiter.Recruiter;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class})
class JobApplicationRepositoryTest {

	@Autowired
	JobApplicationRepository jobApplicationRepository;

	@Autowired
	TestEntityManager em;

	private Company company;

	@BeforeEach
	void setUp() {
		company = em.persist(new Company("ACME GmbH", "Berlin", null));
	}

	@Test
	void persistsWithAuditTimestampsAndInitialVersion() {
		JobApplication saved = jobApplicationRepository.save(
				new JobApplication(company, "Java Developer", "https://acme.example/jobs/1"));
		em.flush();
		em.clear();

		JobApplication loaded = jobApplicationRepository.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getStatus()).isEqualTo(Status.SAVED);
		assertThat(loaded.getCompany().getName()).isEqualTo("ACME GmbH");
		assertThat(loaded.getVersion()).isZero();
		assertThat(loaded.getCreatedAt()).isNotNull();
		assertThat(loaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void updateIncrementsVersionAndKeepsCreatedAt() {
		Long id = jobApplicationRepository.saveAndFlush(
				new JobApplication(company, "Java Developer", null)).getId();
		em.clear();
		// read back from the DB: Postgres stores microseconds, the JVM clock may have more precision
		JobApplication saved = jobApplicationRepository.findById(id).orElseThrow();
		var createdAt = saved.getCreatedAt();

		saved.changeStatus(Status.APPLIED);
		jobApplicationRepository.saveAndFlush(saved);
		em.clear();

		JobApplication loaded = jobApplicationRepository.findById(id).orElseThrow();
		assertThat(loaded.getStatus()).isEqualTo(Status.APPLIED);
		assertThat(loaded.getVersion()).isEqualTo(1L);
		assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
		assertThat(loaded.getUpdatedAt()).isAfterOrEqualTo(createdAt);
	}

	// --- database safety net (V2 unique indexes), independent of the service checks ---

	@Test
	void databaseRejectsDuplicateJobUrl() {
		jobApplicationRepository.saveAndFlush(new JobApplication(company, "Java Developer", "https://acme.example/jobs/1"));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> jobApplicationRepository.saveAndFlush(
						new JobApplication(company, "Other title", "https://acme.example/jobs/1")));
	}

	@Test
	void databaseRejectsDuplicateCompanyAndPositionIgnoringCase() {
		jobApplicationRepository.saveAndFlush(new JobApplication(company, "Java Developer", null));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> jobApplicationRepository.saveAndFlush(
						new JobApplication(company, "JAVA DEVELOPER", null)));
	}

	@Test
	void databaseRejectsTwoActiveApplicationsForSameRecruiter() {
		Recruiter anna = em.persist(new Recruiter("Anna Schmidt", "anna@acme.example", company));
		JobApplication first = new JobApplication(company, anna, "Java Developer", null);
		first.changeStatus(Status.APPLIED);
		jobApplicationRepository.saveAndFlush(first);

		JobApplication second = new JobApplication(company, anna, "Kotlin Developer", null);
		second.changeStatus(Status.APPLIED);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> jobApplicationRepository.saveAndFlush(second));
	}

	@Test
	void findsByStatusAndByCompany() {
		JobApplication applied = new JobApplication(company, "Backend Developer", null);
		applied.changeStatus(Status.APPLIED);
		jobApplicationRepository.save(applied);
		jobApplicationRepository.save(new JobApplication(company, "Frontend Developer", null));
		em.flush();
		em.clear();

		assertThat(jobApplicationRepository.findByStatus(Status.APPLIED))
				.extracting(JobApplication::getPosition)
				.containsExactly("Backend Developer");
		assertThat(jobApplicationRepository.findByCompanyId(company.getId()))
				.hasSize(2);
	}

}
