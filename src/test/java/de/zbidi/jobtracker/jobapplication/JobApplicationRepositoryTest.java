package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.config.JpaAuditingConfig;
import de.zbidi.jobtracker.recruiter.Recruiter;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class})
class JobApplicationRepositoryTest {

	@Autowired
	JobApplicationRepository jobApplicationRepository;

	@Autowired
	TestEntityManager em;

	private AppUser owner;
	private Company company;

	@BeforeEach
	void setUp() {
		owner = em.persist(new AppUser("alice@example.com", "$2a$10$hash", Role.USER));
		company = em.persist(new Company("ACME GmbH", "Berlin", null));
	}

	@Test
	void persistsWithAuditTimestampsAndInitialVersion() {
		JobApplication saved = jobApplicationRepository.save(
				new JobApplication(owner, company, "Java Developer", "https://acme.example/jobs/1"));
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
				new JobApplication(owner, company, "Java Developer", null)).getId();
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
		jobApplicationRepository.saveAndFlush(new JobApplication(owner, company, "Java Developer", "https://acme.example/jobs/1"));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> jobApplicationRepository.saveAndFlush(
						new JobApplication(owner, company, "Other title", "https://acme.example/jobs/1")));
	}

	@Test
	void databaseRejectsDuplicateCompanyAndPositionIgnoringCase() {
		jobApplicationRepository.saveAndFlush(new JobApplication(owner, company, "Java Developer", null));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> jobApplicationRepository.saveAndFlush(
						new JobApplication(owner, company, "JAVA DEVELOPER", null)));
	}

	@Test
	void databaseRejectsTwoActiveApplicationsForSameRecruiter() {
		Recruiter anna = em.persist(new Recruiter("Anna Schmidt", "anna@acme.example", company));
		JobApplication first = new JobApplication(owner, company, anna, "Java Developer", null);
		first.changeStatus(Status.APPLIED);
		jobApplicationRepository.saveAndFlush(first);

		JobApplication second = new JobApplication(owner, company, anna, "Kotlin Developer", null);
		second.changeStatus(Status.APPLIED);

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> jobApplicationRepository.saveAndFlush(second));
	}

	// --- the duplicate rules are per owner (V6): another user may track the same job ---

	@Test
	void sameJobUrlAllowedForDifferentOwners() {
		jobApplicationRepository.saveAndFlush(new JobApplication(owner, company, "Java Developer", "https://acme.example/jobs/1"));

		JobApplication bobs = jobApplicationRepository.saveAndFlush(
				new JobApplication(bob(), company, "Java Developer", "https://acme.example/jobs/1"));

		assertThat(bobs.getId()).isNotNull();
	}

	@Test
	void differentOwnersCanBothHaveAnActiveApplicationWithTheSameRecruiter() {
		Recruiter anna = em.persist(new Recruiter("Anna Schmidt", "anna@acme.example", company));
		JobApplication alices = new JobApplication(owner, company, anna, "Java Developer", null);
		alices.changeStatus(Status.APPLIED);
		jobApplicationRepository.saveAndFlush(alices);

		JobApplication bobs = new JobApplication(bob(), company, anna, "Java Developer", null);
		bobs.changeStatus(Status.APPLIED);

		assertThat(jobApplicationRepository.saveAndFlush(bobs).getId()).isNotNull();
	}

	@Test
	void findsOnlyTheOwnersApplicationById() {
		Long id = jobApplicationRepository.saveAndFlush(new JobApplication(owner, company, "Java Developer", null)).getId();

		assertThat(jobApplicationRepository.findByIdAndOwnerId(id, owner.getId())).isPresent();
		assertThat(jobApplicationRepository.findByIdAndOwnerId(id, bob().getId())).isEmpty();
	}

	private AppUser bob() {
		return em.persist(new AppUser("bob-" + System.nanoTime() + "@example.com", "$2a$10$hash", Role.USER));
	}

	@Test
	void findsByStatusAndByCompany() {
		JobApplication applied = new JobApplication(owner, company, "Backend Developer", null);
		applied.changeStatus(Status.APPLIED);
		jobApplicationRepository.save(applied);
		jobApplicationRepository.save(new JobApplication(owner, company, "Frontend Developer", null));
		em.flush();
		em.clear();

		assertThat(jobApplicationRepository.findByStatus(Status.APPLIED))
				.extracting(JobApplication::getPosition)
				.containsExactly("Backend Developer");
		assertThat(jobApplicationRepository.findByCompanyId(company.getId()))
				.hasSize(2);
	}

}
