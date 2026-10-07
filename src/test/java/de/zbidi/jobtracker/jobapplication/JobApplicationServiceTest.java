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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Fast unit test: repositories mocked, no Spring, no Docker.
 * {@link JobApplicationServiceIntegrationTest} covers the same rules against real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTest {

	@Mock
	JobApplicationRepository jobApplicationRepository;

	@Mock
	CompanyRepository companyRepository;

	@Mock
	RecruiterRepository recruiterRepository;

	@Mock
	StatusHistoryRepository statusHistoryRepository;

	JobApplicationService service;

	private final Company acme = withId(new Company("ACME GmbH", "Berlin", null), 1L);

	@BeforeEach
	void setUp() {
		Clock clock = Clock.fixed(Instant.parse("2026-10-07T10:00:00Z"), ZoneId.of("Europe/Berlin"));
		service = new JobApplicationService(jobApplicationRepository, companyRepository, recruiterRepository,
				statusHistoryRepository, clock);
	}

	// --- search ---

	@Test
	void searchPassesSpecificationAndPageableToRepository() {
		Pageable pageable = PageRequest.of(1, 5);
		given(jobApplicationRepository.findAll(ArgumentMatchers.<Specification<JobApplication>>any(), eq(pageable)))
				.willReturn(new PageImpl<>(List.of(application()), pageable, 6));

		PageResponse<JobApplicationResponse> page = service.search(
				new JobApplicationSearch(Status.SAVED, "acme", null, null, null), pageable);

		assertThat(page.content()).extracting(JobApplicationResponse::position).containsExactly("Java Developer");
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalElements()).isEqualTo(6);
		verify(jobApplicationRepository).findAll(ArgumentMatchers.<Specification<JobApplication>>any(), eq(pageable));
	}

	// --- PATCH status ---

	@Test
	void changeStatusFromRejectedToInterviewThrowsAndDoesNotSave() {
		JobApplication rejected = application(Status.APPLIED, Status.REJECTED);
		given(jobApplicationRepository.findById(5L)).willReturn(Optional.of(rejected));

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> service.changeStatus(5L, Status.INTERVIEW, 0L))
				.withMessage("Cannot change status from REJECTED to INTERVIEW: REJECTED is a final status.")
				.satisfies(e -> {
					assertThat(e.getCurrentStatus()).isEqualTo(Status.REJECTED);
					assertThat(e.getRequestedStatus()).isEqualTo(Status.INTERVIEW);
				});
		assertThat(rejected.getStatus()).isEqualTo(Status.REJECTED);
		verify(jobApplicationRepository, never()).saveAndFlush(any());
		verifyNoInteractions(statusHistoryRepository);
	}

	@Test
	void changeStatusSavesApplicationAndHistoryRow() {
		JobApplication saved = application();
		given(jobApplicationRepository.findById(5L)).willReturn(Optional.of(saved));
		given(jobApplicationRepository.saveAndFlush(saved)).willReturn(saved);

		JobApplicationResponse response = service.changeStatus(5L, Status.APPLIED, 0L);

		assertThat(response.status()).isEqualTo(Status.APPLIED);
		assertThat(response.companyName()).isEqualTo("ACME GmbH");
		ArgumentCaptor<StatusHistory> change = ArgumentCaptor.forClass(StatusHistory.class);
		verify(statusHistoryRepository).save(change.capture());
		assertThat(change.getValue().getJobApplication()).isSameAs(saved);
		assertThat(change.getValue().getFromStatus()).isEqualTo(Status.SAVED);
		assertThat(change.getValue().getToStatus()).isEqualTo(Status.APPLIED);
	}

	@Test
	void changeStatusWithStaleVersionThrowsAndDoesNotSave() {
		JobApplication current = application(Status.APPLIED);
		ReflectionTestUtils.setField(current, "version", 2L);
		given(jobApplicationRepository.findById(5L)).willReturn(Optional.of(current));

		assertThatExceptionOfType(StaleVersionException.class)
				.isThrownBy(() -> service.changeStatus(5L, Status.INTERVIEW, 1L))
				.satisfies(e -> {
					assertThat(e.getExpectedVersion()).isEqualTo(1L);
					assertThat(e.getCurrentVersion()).isEqualTo(2L);
				});
		assertThat(current.getStatus()).isEqualTo(Status.APPLIED);
		verify(jobApplicationRepository, never()).saveAndFlush(any());
		verifyNoInteractions(statusHistoryRepository);
	}

	@Test
	void historyForUnknownApplicationThrowsNotFound() {
		given(jobApplicationRepository.existsById(99L)).willReturn(false);

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.history(99L))
				.withMessage("Job application 99 not found");
		verifyNoInteractions(statusHistoryRepository);
	}

	@Test
	void changeStatusOnUnknownIdThrowsNotFound() {
		given(jobApplicationRepository.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.changeStatus(99L, Status.APPLIED, 0L))
				.withMessage("Job application 99 not found");
	}

	@Test
	void changeStatusBlockedWhileRecruiterHasAnotherActiveApplication() {
		Recruiter anna = withId(new Recruiter("Anna Schmidt", "anna@acme.example", acme), 7L);
		JobApplication application = stored(new JobApplication(acme, anna, "Kotlin Developer", null));
		given(jobApplicationRepository.findById(5L)).willReturn(Optional.of(application));
		given(jobApplicationRepository.existsByRecruiterIdAndStatusInAndIdNot(eq(7L), eq(Status.ACTIVE), eq(5L)))
				.willReturn(true);

		assertThatExceptionOfType(RecruiterConflictException.class)
				.isThrownBy(() -> service.changeStatus(5L, Status.APPLIED, 0L))
				.withMessageContaining("Anna Schmidt");
		verify(jobApplicationRepository, never()).saveAndFlush(any());
	}

	// --- POST / GET ---

	@Test
	void createWithUnknownCompanyThrowsNotFound() {
		given(companyRepository.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.create(new NewJobApplication(99L, null, "Java Developer", null)))
				.withMessage("Company 99 not found");
		verify(jobApplicationRepository, never()).save(any());
	}

	@Test
	void getUnknownIdThrowsNotFound() {
		given(jobApplicationRepository.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.get(99L));
	}

	/** An application with id 5 that has gone through the given status changes. */
	private JobApplication application(Status... path) {
		JobApplication application = stored(new JobApplication(acme, "Java Developer", null));
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
