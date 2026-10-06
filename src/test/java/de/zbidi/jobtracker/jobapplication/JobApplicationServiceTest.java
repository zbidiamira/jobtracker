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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class, JobApplicationService.class})
class JobApplicationServiceTest {

	@Autowired
	JobApplicationService service;

	@Autowired
	TestEntityManager em;

	private Company acme;
	private Recruiter anna;

	@BeforeEach
	void setUp() {
		acme = em.persist(new Company("ACME GmbH", "Berlin", null));
		anna = em.persist(new Recruiter("Anna Schmidt", "anna@acme.example", acme));
	}

	@Test
	void createsApplicationAsSaved() {
		JobApplication created = service.create(
				new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", "https://acme.example/jobs/1"));

		assertThat(created.getId()).isNotNull();
		assertThat(created.getStatus()).isEqualTo(Status.SAVED);
		assertThat(created.getRecruiter()).isEqualTo(anna);
	}

	// --- same job twice ---

	@Test
	void rejectsSameJobUrlTwice() {
		service.create(new NewJobApplication(acme.getId(), null, "Java Developer", "https://acme.example/jobs/1"));

		assertThatExceptionOfType(DuplicateApplicationException.class)
				.isThrownBy(() -> service.create(
						new NewJobApplication(acme.getId(), null, "Senior Java Dev", "https://acme.example/jobs/1")))
				.withMessageContaining("https://acme.example/jobs/1");
	}

	@Test
	void rejectsSameCompanyAndPositionIgnoringCase() {
		service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null));

		assertThatExceptionOfType(DuplicateApplicationException.class)
				.isThrownBy(() -> service.create(
						new NewJobApplication(acme.getId(), null, "java developer", "https://acme.example/jobs/2")))
				.withMessageContaining("ACME GmbH");
	}

	@Test
	void allowsSamePositionAtAnotherCompany() {
		Company globex = em.persist(new Company("Globex", "Munich", null));
		service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null));

		JobApplication other = service.create(new NewJobApplication(globex.getId(), null, "Java Developer", null));

		assertThat(other.getId()).isNotNull();
	}

	// --- one active application per recruiter ---

	@Test
	void blocksApplyingToSecondJobOfSameRecruiterWhileFirstIsActive() {
		JobApplication first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null));
		service.changeStatus(first.getId(), Status.APPLIED);
		// saving a second job of the same recruiter is fine, only applying is not
		JobApplication second = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null));

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> service.changeStatus(second.getId(), Status.APPLIED))
				.withMessageContaining("Anna Schmidt");
		assertThat(second.getStatus()).isEqualTo(Status.SAVED);
	}

	@Test
	void stillBlockedWhileFirstIsInInterviewOrOffer() {
		JobApplication first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null));
		service.changeStatus(first.getId(), Status.APPLIED);
		service.changeStatus(first.getId(), Status.INTERVIEW);
		service.changeStatus(first.getId(), Status.OFFER);
		JobApplication second = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null));

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> service.changeStatus(second.getId(), Status.APPLIED));
	}

	@Test
	void allowsApplyingAgainOnceFirstIsRejected() {
		JobApplication first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null));
		service.changeStatus(first.getId(), Status.APPLIED);
		service.changeStatus(first.getId(), Status.REJECTED);
		JobApplication second = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null));

		JobApplication applied = service.changeStatus(second.getId(), Status.APPLIED);

		assertThat(applied.getStatus()).isEqualTo(Status.APPLIED);
	}

	@Test
	void differentRecruitersDoNotBlockEachOther() {
		Recruiter ben = em.persist(new Recruiter("Ben Meyer", "ben@acme.example", acme));
		JobApplication first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null));
		service.changeStatus(first.getId(), Status.APPLIED);
		JobApplication second = service.create(new NewJobApplication(acme.getId(), ben.getId(), "Kotlin Developer", null));

		assertThat(service.changeStatus(second.getId(), Status.APPLIED).getStatus()).isEqualTo(Status.APPLIED);
	}

	@Test
	void applicationsWithoutRecruiterAreNotRestricted() {
		JobApplication first = service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null));
		JobApplication second = service.create(new NewJobApplication(acme.getId(), null, "Kotlin Developer", null));

		service.changeStatus(first.getId(), Status.APPLIED);

		assertThat(service.changeStatus(second.getId(), Status.APPLIED).getStatus()).isEqualTo(Status.APPLIED);
	}

}
