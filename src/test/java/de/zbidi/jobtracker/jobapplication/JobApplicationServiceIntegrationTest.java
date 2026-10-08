package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.config.ClockConfig;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, JpaAuditingConfig.class, ClockConfig.class, JobApplicationService.class})
class JobApplicationServiceIntegrationTest {

	@Autowired
	JobApplicationService service;

	@Autowired
	TestEntityManager em;

	private Long ownerId;
	private Company acme;
	private Recruiter anna;

	@BeforeEach
	void setUp() {
		ownerId = em.persist(new AppUser("alice@example.com", "$2a$10$hash", Role.USER)).getId();
		acme = em.persist(new Company("ACME GmbH", "Berlin", null));
		anna = em.persist(new Recruiter("Anna Schmidt", "anna@acme.example", acme));
	}

	@Test
	void createsApplicationAsSaved() {
		JobApplicationResponse created = service.create(ownerId, 
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
				.isThrownBy(() -> service.create(ownerId, new NewJobApplication(999_999L, null, "Java Developer", null)))
				.withMessageContaining("Company 999999");
	}

	@Test
	void createWithUnknownRecruiterThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.create(ownerId, new NewJobApplication(acme.getId(), 999_999L, "Java Developer", null)))
				.withMessageContaining("Recruiter 999999");
	}

	// --- get / list ---

	@Test
	void getReturnsResponseIncludingCompanyName() {
		Long id = service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();
		em.flush();
		em.clear();

		JobApplicationResponse found = service.get(ownerId, id);

		assertThat(found.position()).isEqualTo("Java Developer");
		assertThat(found.companyName()).isEqualTo("ACME GmbH");
	}

	@Test
	void getUnknownIdThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.get(ownerId, 999_999L))
				.withMessageContaining("Job application 999999");
	}

	@Test
	void listWithoutStatusReturnsAllPaged() {
		for (String position : new String[] {"A Dev", "B Dev", "C Dev"}) {
			service.create(ownerId, new NewJobApplication(acme.getId(), null, position, null));
		}

		PageResponse<JobApplicationResponse> page = service.search(ownerId, new JobApplicationSearch(null, null, null, null, null), PageRequest.of(0, 2, Sort.by("position")));

		assertThat(page.content()).extracting(JobApplicationResponse::position).containsExactly("A Dev", "B Dev");
		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.totalPages()).isEqualTo(2);
	}

	@Test
	void listWithStatusFiltersAndPages() {
		Long applied = service.create(ownerId, new NewJobApplication(acme.getId(), null, "A Dev", null)).id();
		change(applied, Status.APPLIED);
		service.create(ownerId, new NewJobApplication(acme.getId(), null, "B Dev", null));

		PageResponse<JobApplicationResponse> page = service.search(ownerId, new JobApplicationSearch(Status.APPLIED, null, null, null, null), PageRequest.of(0, 10));

		assertThat(page.content()).extracting(JobApplicationResponse::position).containsExactly("A Dev");
		assertThat(page.totalElements()).isEqualTo(1);
	}

	// --- status changes ---

	@Test
	void changeStatusReturnsUpdatedResponseWithNewVersion() {
		Long id = service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();

		JobApplicationResponse changed = change(id, Status.APPLIED);

		assertThat(changed.status()).isEqualTo(Status.APPLIED);
		assertThat(changed.version()).isEqualTo(1L);
	}

	@Test
	void changeStatusOnUnknownIdThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> change(999_999L, Status.APPLIED));
	}

	@Test
	void invalidTransitionThrowsInvalidStatusTransitionException() {
		Long id = service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> change(id, Status.OFFER));
	}

	// --- same job twice ---

	@Test
	void rejectsSameJobUrlTwice() {
		service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", "https://acme.example/jobs/1"));

		assertThatExceptionOfType(DuplicateApplicationException.class)
				.isThrownBy(() -> service.create(ownerId, 
						new NewJobApplication(acme.getId(), null, "Senior Java Dev", "https://acme.example/jobs/1")))
				.withMessageContaining("https://acme.example/jobs/1");
	}

	@Test
	void rejectsSameCompanyAndPositionIgnoringCase() {
		service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", null));

		assertThatExceptionOfType(DuplicateApplicationException.class)
				.isThrownBy(() -> service.create(ownerId, 
						new NewJobApplication(acme.getId(), null, "java developer", "https://acme.example/jobs/2")))
				.withMessageContaining("ACME GmbH");
	}

	@Test
	void allowsSamePositionAtAnotherCompany() {
		Company globex = em.persist(new Company("Globex", "Munich", null));
		service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", null));

		JobApplicationResponse other = service.create(ownerId, new NewJobApplication(globex.getId(), null, "Java Developer", null));

		assertThat(other.id()).isNotNull();
	}

	// --- one active application per recruiter ---

	@Test
	void blocksApplyingToSecondJobOfSameRecruiterWhileFirstIsActive() {
		Long first = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		change(first, Status.APPLIED);
		// saving a second job of the same recruiter is fine, only applying is not
		Long second = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null)).id();

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> change(second, Status.APPLIED))
				.withMessageContaining("Anna Schmidt");
		assertThat(service.get(ownerId, second).status()).isEqualTo(Status.SAVED);
	}

	@Test
	void stillBlockedWhileFirstIsInInterviewOrOffer() {
		Long first = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		change(first, Status.APPLIED);
		change(first, Status.INTERVIEW);
		change(first, Status.OFFER);
		Long second = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null)).id();

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> change(second, Status.APPLIED));
	}

	@Test
	void allowsApplyingAgainOnceFirstIsRejected() {
		Long first = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		change(first, Status.APPLIED);
		change(first, Status.REJECTED);
		Long second = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Kotlin Developer", null)).id();

		assertThat(change(second, Status.APPLIED).status()).isEqualTo(Status.APPLIED);
	}

	@Test
	void differentRecruitersDoNotBlockEachOther() {
		Recruiter ben = em.persist(new Recruiter("Ben Meyer", "ben@acme.example", acme));
		Long first = service.create(ownerId, new NewJobApplication(acme.getId(), anna.getId(), "Java Developer", null)).id();
		change(first, Status.APPLIED);
		Long second = service.create(ownerId, new NewJobApplication(acme.getId(), ben.getId(), "Kotlin Developer", null)).id();

		assertThat(change(second, Status.APPLIED).status()).isEqualTo(Status.APPLIED);
	}

	@Test
	void applicationsWithoutRecruiterAreNotRestricted() {
		Long first = service.create(ownerId, new NewJobApplication(acme.getId(), null, "Java Developer", null)).id();
		Long second = service.create(ownerId, new NewJobApplication(acme.getId(), null, "Kotlin Developer", null)).id();

		change(first, Status.APPLIED);

		assertThat(change(second, Status.APPLIED).status()).isEqualTo(Status.APPLIED);
	}

	/** Like a real client: send the version it last read. */
	private JobApplicationResponse change(Long id, Status status) {
		return service.changeStatus(ownerId, id, status, service.get(ownerId, id).version());
	}

}
