package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class, JobApplicationService.class})
class JobApplicationServiceIntegrationTest {

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
		JobApplicationResponse created = service.create(
				new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", "https://acme.example/jobs/1"));

		assertThat(created.id()).isNotNull();
		assertThat(created.status()).isEqualTo(Status.SAVED);
		assertThat(created.companyId()).isEqualTo(acme.getId());
		assertThat(created.companyName()).isEqualTo("ACME GmbH");
		assertThat(created.recruiterId()).isEqualTo(anna.getId());
		assertThat(created.version()).isZero();
		assertThat(created.createdAt()).isNotNull();
	}

	@Test
	void createWithUnknownCompanyThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.create(new NewJobApplication(999_999L, null, "Java Developer", null)))
				.withMessageContaining("Company 999999");
	}

	@Test
	void createWithUnknownRecruiterThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.create(new NewJobApplication(acme.getId(), 999_999L, "Java Developer", null)))
				.withMessageContaining("Recruiter 999999");
	}

	// --- get / list ---

	@Test
	void getReturnsResponseIncludingCompanyName() {
		Long id = service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();
		em.flush();
		em.clear();

		JobApplicationResponse found = service.get(id);

		assertThat(found.position()).isEqualTo("Java Developer");
		assertThat(found.companyName()).isEqualTo("ACME GmbH");
	}

	@Test
	void getUnknownIdThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.get(999_999L))
				.withMessageContaining("Job application 999999");
	}

	@Test
	void listWithoutStatusReturnsAllPaged() {
		for (String position : new String[] {"A Dev", "B Dev", "C Dev"}) {
			service.create(new NewJobApplication(acme.getId(), null, position, null));
		}

		PageResponse<JobApplicationResponse> page = service.list(null, PageRequest.of(0, 2, Sort.by("position")));

		assertThat(page.content()).extracting(JobApplicationResponse::position).containsExactly("A Dev", "B Dev");
		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.totalPages()).isEqualTo(2);
	}

	@Test
	void listWithStatusFiltersAndPages() {
		Long applied = service.create(new NewJobApplication(acme.getId(), null, "A Dev", null)).id();
		service.changeStatus(applied, Status.APPLIED);
		service.create(new NewJobApplication(acme.getId(), null, "B Dev", null));

		PageResponse<JobApplicationResponse> page = service.list(Status.APPLIED, PageRequest.of(0, 10));

		assertThat(page.content()).extracting(JobApplicationResponse::position).containsExactly("A Dev");
		assertThat(page.totalElements()).isEqualTo(1);
	}

	// --- status changes ---

	@Test
	void changeStatusReturnsUpdatedResponseWithNewVersion() {
		Long id = service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();

		JobApplicationResponse changed = service.changeStatus(id, Status.APPLIED);

		assertThat(changed.status()).isEqualTo(Status.APPLIED);
		assertThat(changed.version()).isEqualTo(1L);
	}

	@Test
	void changeStatusOnUnknownIdThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.changeStatus(999_999L, Status.APPLIED));
	}

	@Test
	void invalidTransitionThrowsInvalidStatusTransitionException() {
		Long id = service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> service.changeStatus(id, Status.OFFER));
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

		JobApplicationResponse other = service.create(new NewJobApplication(globex.getId(), null, "Java Developer", null));

		assertThat(other.id()).isNotNull();
	}

	// --- one active application per recruiter ---

	@Test
	void blocksApplyingToSecondJobOfSameRecruiterWhileFirstIsActive() {
		Long first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		service.changeStatus(first, Status.APPLIED);
		// saving a second job of the same recruiter is fine, only applying is not
		Long second = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null)).id();

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> service.changeStatus(second, Status.APPLIED))
				.withMessageContaining("Anna Schmidt");
		assertThat(service.get(second).status()).isEqualTo(Status.SAVED);
	}

	@Test
	void stillBlockedWhileFirstIsInInterviewOrOffer() {
		Long first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		service.changeStatus(first, Status.APPLIED);
		service.changeStatus(first, Status.INTERVIEW);
		service.changeStatus(first, Status.OFFER);
		Long second = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null)).id();

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> service.changeStatus(second, Status.APPLIED));
	}

	@Test
	void allowsApplyingAgainOnceFirstIsRejected() {
		Long first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		service.changeStatus(first, Status.APPLIED);
		service.changeStatus(first, Status.REJECTED);
		Long second = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null)).id();

		assertThat(service.changeStatus(second, Status.APPLIED).status()).isEqualTo(Status.APPLIED);
	}

	@Test
	void differentRecruitersDoNotBlockEachOther() {
		Recruiter ben = em.persist(new Recruiter("Ben Meyer", "ben@acme.example", acme));
		Long first = service.create(new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		service.changeStatus(first, Status.APPLIED);
		Long second = service.create(new NewJobApplication(acme.getId(), ben.getId(), "Kotlin Developer", null)).id();

		assertThat(service.changeStatus(second, Status.APPLIED).status()).isEqualTo(Status.APPLIED);
	}

	@Test
	void applicationsWithoutRecruiterAreNotRestricted() {
		Long first = service.create(new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();
		Long second = service.create(new NewJobApplication(acme.getId(), null, "Kotlin Developer", null)).id();

		service.changeStatus(first, Status.APPLIED);

		assertThat(service.changeStatus(second, Status.APPLIED).status()).isEqualTo(Status.APPLIED);
	}

}
