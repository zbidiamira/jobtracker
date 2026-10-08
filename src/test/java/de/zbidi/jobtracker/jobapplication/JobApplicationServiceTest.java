package de.zbidi.jobtracker.jobapplication;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.recruiter.Recruiter;
import de.zbidi.jobtracker.recruiter.RecruiterRepository;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Fast unit test: repositories mocked, no Spring, no Docker.
 * {@link JobApplicationServiceIntegrationTest} covers the same rules against real Postgres.
 * Every lookup is scoped to the caller ({@link #OWNER_ID}); someone else's application is "not found".
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

	private static final long OWNER_ID = 42L;

	@Mock
	JobApplicationRepository jobApplicationRepository;

	@Mock
	CompanyRepository companyRepository;

	@Mock
	RecruiterRepository recruiterRepository;

	@Mock
	StatusHistoryRepository statusHistoryRepository;

	@Mock
	AppUserRepository appUserRepository;

	@Mock
	ApplicationEventPublisher events;

	JobApplicationService service;

	private final AppUser owner = withId(new AppUser("alice@example.com", "$2a$10$hash", Role.USER), OWNER_ID);
	private final Company acme = withId(new Company("ACME GmbH", "Berlin", null), 1L);

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneId.of("Europe/Berlin"));
		service = new JobApplicationService(jobApplicationRepository, companyRepository, recruiterRepository,
				statusHistoryRepository, appUserRepository, events, clock);
	}

	// --- ownership ---

	@Test
	void getOtherUsersApplicationThrowsNotFound() {
		// the application exists, but not for this owner: the repository query is scoped, so it returns nothing
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.get(OWNER_ID, 5L))
				.withMessage("Job application 5 not found");
	}

	@Test
	void changeStatusOnOtherUsersApplicationThrowsNotFoundAndSavesNothing() {
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.changeStatus(OWNER_ID, 5L, Status.APPLIED, 0L));
		verify(jobApplicationRepository, never()).saveAndFlush(any());
		verifyNoInteractions(statusHistoryRepository, events);
	}

	@Test
	void historyOfOtherUsersApplicationThrowsNotFound() {
		given(jobApplicationRepository.existsByIdAndOwnerId(5L, OWNER_ID)).willReturn(false);

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.history(OWNER_ID, 5L))
				.withMessage("Job application 5 not found");
		verifyNoInteractions(statusHistoryRepository, events);
	}

	@Test
	void createSetsOwnerAndChecksDuplicatesPerOwner() {
		given(companyRepository.findById(1L)).willReturn(Optional.of(acme));
		given(appUserRepository.getReferenceById(OWNER_ID)).willReturn(owner);
		given(jobApplicationRepository.save(any(JobApplication.class)))
				.willAnswer(invocation -> stored(invocation.getArgument(0)));

		service.create(OWNER_ID, new NewJobApplication(1L, null, "Java Developer", "https://acme.example/jobs/1"));

		ArgumentCaptor<JobApplication> saved = ArgumentCaptor.forClass(JobApplication.class);
		verify(jobApplicationRepository).save(saved.capture());
		assertThat(saved.getValue().getOwner()).isSameAs(owner);
		verify(jobApplicationRepository).existsByOwnerIdAndJobUrl(OWNER_ID, "https://acme.example/jobs/1");
		verify(jobApplicationRepository).existsByOwnerIdAndCompanyIdAndPositionIgnoreCase(OWNER_ID, 1L, "Java Developer");
	}

	@Test
	void searchIsAlwaysScopedToTheOwner() {
		Pageable pageable = PageRequest.of(1, 5);
		given(jobApplicationRepository.findAll(ArgumentMatchers.<Specification<JobApplication>>any(), eq(pageable)))
				.willReturn(new PageImpl<>(List.of(application()), pageable, 6));

		PageResponse<JobApplicationResponse> page = service.search(OWNER_ID,
				new JobApplicationSearch(Status.SAVED, "acme", null, null, null), pageable);

		assertThat(page.content()).extracting(JobApplicationResponse::position).containsExactly("Java Developer");
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalElements()).isEqualTo(6);
		// which rows the specification matches is covered against real SQL in ApplicationSpecificationsTest
	}

	// --- admin delete ---

	@Test
	void deleteRemovesHistoryThenApplication() {
		JobApplication application = application(Status.APPLIED);
		given(jobApplicationRepository.findById(5L)).willReturn(Optional.of(application));

		service.delete(5L);

		InOrder order = inOrder(statusHistoryRepository, jobApplicationRepository);
		order.verify(statusHistoryRepository).deleteByJobApplicationId(5L);
		order.verify(jobApplicationRepository).delete(application);
	}

	@Test
	void deleteUnknownIdThrowsNotFound() {
		given(jobApplicationRepository.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class).isThrownBy(() -> service.delete(99L));
		verifyNoInteractions(statusHistoryRepository, events);
	}

	// --- PATCH status ---

	@Test
	void changeStatusFromRejectedToInterviewThrowsAndDoesNotSave() {
		JobApplication rejected = application(Status.APPLIED, Status.REJECTED);
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.of(rejected));

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> service.changeStatus(OWNER_ID, 5L, Status.INTERVIEW, 0L))
				.withMessage("Cannot change status from REJECTED to INTERVIEW: REJECTED is a final status.")
				.satisfies(e -> {
					assertThat(e.getCurrentStatus()).isEqualTo(Status.REJECTED);
					assertThat(e.getRequestedStatus()).isEqualTo(Status.INTERVIEW);
				});
		assertThat(rejected.getStatus()).isEqualTo(Status.REJECTED);
		verify(jobApplicationRepository, never()).saveAndFlush(any());
		verifyNoInteractions(statusHistoryRepository, events);
	}

	@Test
	void changeStatusPublishesStatusChangedEvent() {
		JobApplication saved = application();
		Instant updatedAt = Instant.parse("2026-10-07T10:00:00.123456Z");
		ReflectionTestUtils.setField(saved, "updatedAt", updatedAt); // what auditing sets on flush
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.of(saved));
		given(jobApplicationRepository.saveAndFlush(saved)).willReturn(saved);

		JobApplicationResponse response = service.changeStatus(OWNER_ID, 5L, Status.APPLIED, 0L);

		assertThat(response.status()).isEqualTo(Status.APPLIED);
		assertThat(response.companyName()).isEqualTo("ACME GmbH");
		ArgumentCaptor<StatusChangedEvent> event = ArgumentCaptor.forClass(StatusChangedEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().eventId()).isNotNull();
		assertThat(event.getValue().applicationId()).isEqualTo(5L);
		assertThat(event.getValue().ownerId()).isEqualTo(OWNER_ID);
		assertThat(event.getValue().from()).isEqualTo(Status.SAVED);
		assertThat(event.getValue().to()).isEqualTo(Status.APPLIED);
		assertThat(event.getValue().changedAt()).isEqualTo(updatedAt);
		// the history row is written by the Kafka listener now, not here
		verifyNoInteractions(statusHistoryRepository);
	}

	@Test
	void everyChangeGetsItsOwnEventId() {
		JobApplication saved = application();
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.of(saved));
		given(jobApplicationRepository.saveAndFlush(saved)).willReturn(saved);

		service.changeStatus(OWNER_ID, 5L, Status.APPLIED, 0L);
		service.changeStatus(OWNER_ID, 5L, Status.INTERVIEW, 0L);

		ArgumentCaptor<StatusChangedEvent> events = ArgumentCaptor.forClass(StatusChangedEvent.class);
		verify(this.events, times(2)).publishEvent(events.capture());
		assertThat(events.getAllValues()).extracting(StatusChangedEvent::eventId).doesNotHaveDuplicates();
	}

	@Test
	void changeStatusWithStaleVersionThrowsAndDoesNotSave() {
		JobApplication current = application(Status.APPLIED);
		ReflectionTestUtils.setField(current, "version", 2L);
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.of(current));

		assertThatExceptionOfType(StaleVersionException.class)
				.isThrownBy(() -> service.changeStatus(OWNER_ID, 5L, Status.INTERVIEW, 1L))
				.satisfies(e -> {
					assertThat(e.getExpectedVersion()).isEqualTo(1L);
					assertThat(e.getCurrentVersion()).isEqualTo(2L);
				});
		assertThat(current.getStatus()).isEqualTo(Status.APPLIED);
		verify(jobApplicationRepository, never()).saveAndFlush(any());
		verifyNoInteractions(statusHistoryRepository, events);
	}

	@Test
	void changeStatusBlockedWhileRecruiterHasAnotherActiveApplicationOfTheSameOwner() {
		Recruiter anna = withId(new Recruiter("Anna Schmidt", "anna@acme.example", acme), 7L);
		JobApplication application = stored(new JobApplication(owner, acme, anna, "Kotlin Developer", null));
		given(jobApplicationRepository.findByIdAndOwnerId(5L, OWNER_ID)).willReturn(Optional.of(application));
		given(jobApplicationRepository.existsByOwnerIdAndRecruiterIdAndStatusInAndIdNot(
				eq(OWNER_ID), eq(7L), eq(Status.ACTIVE), eq(5L))).willReturn(true);

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> service.changeStatus(OWNER_ID, 5L, Status.APPLIED, 0L))
				.withMessageContaining("Anna Schmidt");
		verify(jobApplicationRepository, never()).saveAndFlush(any());
		verifyNoInteractions(events);
	}

	// --- POST / GET ---

	@Test
	void createWithUnknownCompanyThrowsNotFound() {
		given(companyRepository.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.create(OWNER_ID, new NewJobApplication(99L, null, "Java Developer", null)))
				.withMessage("Company 99 not found");
		verify(jobApplicationRepository, never()).save(any());
	}

	/** An application with id 5 owned by {@link #owner} that has gone through the given status changes. */
	private JobApplication application(Status... path) {
		JobApplication application = stored(new JobApplication(owner, acme, "Java Developer", null));
		for (Status status : path) {
			application.changeStatus(status);
		}
		return application;
	}

	/** As loaded from the database: id 5, version 0. */
	private static JobApplication stored(JobApplication application) {
		ReflectionTestUtils.setField(application, "version", 0L);
		return withId(application, 5L);
	}

	// ids are generated by the database; the mocks simulate that
	private static <T> T withId(T entity, Long id) {
		ReflectionTestUtils.setField(entity, "id", id);
		return entity;
	}

}
