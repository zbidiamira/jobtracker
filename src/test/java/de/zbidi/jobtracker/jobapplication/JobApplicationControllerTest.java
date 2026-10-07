package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(JobApplicationController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class JobApplicationControllerTest {

	private static final Instant NOW = Instant.parse("2026-10-06T10:00:00Z");

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	JobApplicationService service;

	private static JobApplicationResponse response(Status status) {
		return new JobApplicationResponse(5L, 1L, "ACME GmbH", null, "Java Developer", status,
				"https://acme.example/jobs/1", 0L, NOW, NOW);
	}

	// --- POST /api/applications ---

	@Test
	void postCreatesApplicationReturns201WithLocation() {
		given(service.create(new NewJobApplication(1L, null, "Java Developer", "https://acme.example/jobs/1")))
				.willReturn(response(Status.SAVED));

		MvcTestResult result = post("""
				{"companyId": 1, "position": "Java Developer", "jobUrl": "https://acme.example/jobs/1"}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/applications/5");
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("SAVED");
		assertThat(result).bodyJson().extractingPath("$.companyName").isEqualTo("ACME GmbH");
	}

	@Test
	void postWithMissingCompanyIdReturns400() {
		MvcTestResult result = post("""
				{"position": "Java Developer"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("companyId");
		verifyNoInteractions(service);
	}

	@Test
	void postWithInvalidJobUrlReturns400() {
		MvcTestResult result = post("""
				{"companyId": 1, "position": "Java Developer", "jobUrl": "acme jobs"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("jobUrl");
	}

	@Test
	void postDuplicateReturns409() {
		given(service.create(any())).willThrow(new DuplicateApplicationException("You already have an application for x"));

		MvcTestResult result = post("""
				{"companyId": 1, "position": "Java Developer"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("You already have an application for x");
	}

	@Test
	void postRaceCaughtByUniqueIndexReturns409() {
		// two identical requests passed the service check at the same time; the V2 unique index rejected the second
		given(service.create(any())).willThrow(new DataIntegrityViolationException("ux_job_application_job_url"));

		MvcTestResult result = post("""
				{"companyId": 1, "position": "Java Developer"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Data conflict");
		// no constraint names or SQL leak to the client
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("The request conflicts with existing data.");
	}

	@Test
	void postWithUnknownCompanyReturns404() {
		given(service.create(any())).willThrow(new ResourceNotFoundException("Company", 1L));

		MvcTestResult result = post("""
				{"companyId": 1, "position": "Java Developer"}
				""");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- GET /api/applications/{id} ---

	@Test
	void getByIdReturns200() {
		given(service.get(5L)).willReturn(response(Status.APPLIED));

		MvcTestResult result = mvc.get().uri("/api/applications/5").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
		assertThat(result).bodyJson().extractingPath("$.createdAt").isEqualTo("2026-10-06T10:00:00Z");
	}

	@Test
	void getUnknownIdReturns404() {
		given(service.get(99L)).willThrow(new ResourceNotFoundException("Job application", 99L));

		MvcTestResult result = mvc.get().uri("/api/applications/99").exchange();

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	// --- GET /api/applications/{id}/history ---

	@Test
	void historyReturnsStatusChangesOldestFirst() {
		given(service.history(5L)).willReturn(List.of(
				new StatusHistoryResponse(Status.SAVED, Status.APPLIED, NOW),
				new StatusHistoryResponse(Status.APPLIED, Status.INTERVIEW, NOW.plusSeconds(60))));

		MvcTestResult result = mvc.get().uri("/api/applications/5/history").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$[0].fromStatus").isEqualTo("SAVED");
		assertThat(result).bodyJson().extractingPath("$[1].toStatus").isEqualTo("INTERVIEW");
		assertThat(result).bodyJson().extractingPath("$[1].changedAt").isEqualTo("2026-10-06T10:01:00Z");
	}

	@Test
	void historyOfUnknownApplicationReturns404() {
		given(service.history(99L)).willThrow(new ResourceNotFoundException("Job application", 99L));

		assertThat(mvc.get().uri("/api/applications/99/history").exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

	// --- GET /api/applications ---

	private static final JobApplicationSearch NO_FILTER = new JobApplicationSearch(null, null, null, null, null);

	@Test
	void listWithoutFiltersSearchesWithEmptyCriteria() {
		given(service.search(eq(NO_FILTER), any())).willReturn(new PageResponse<>(List.of(response(Status.SAVED)), 0, 20, 1, 1));

		MvcTestResult result = mvc.get().uri("/api/applications").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.content[0].id").isEqualTo(5);
		verify(service).search(eq(NO_FILTER), any());
	}

	@Test
	void plainListIgnoresFilterParameters() {
		given(service.search(eq(NO_FILTER), any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		MvcTestResult result = mvc.get().uri("/api/applications?status=APPLIED&companyName=acme").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		verify(service).search(eq(NO_FILTER), any());
	}

	@Test
	void searchBindsAllQueryParameters() {
		given(service.search(any(), any())).willReturn(new PageResponse<>(List.of(), 1, 5, 0, 0));

		MvcTestResult result = mvc.get().uri("/api/applications/search?status=APPLIED&companyName=acme&position=java"
				+ "&createdFrom=2026-10-01&createdTo=2026-10-07&page=1&size=5&sort=position").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(service).search(eq(new JobApplicationSearch(Status.APPLIED, "acme", "java",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 7))), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort()).isEqualTo(Sort.by("position"));
	}

	@Test
	void listWithUnknownStatusReturns400() {
		MvcTestResult result = mvc.get().uri("/api/applications/search?status=SENT").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("status");
		verifyNoInteractions(service);
	}

	@Test
	void searchWithInvalidDateReturns400() {
		MvcTestResult result = mvc.get().uri("/api/applications/search?createdFrom=07.10.2026").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("createdFrom");
		verifyNoInteractions(service);
	}

	@Test
	void searchWithFromAfterToReturns400() {
		MvcTestResult result = mvc.get().uri("/api/applications/search?createdFrom=2026-10-07&createdTo=2026-10-01").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].message")
				.isEqualTo("createdFrom must not be after createdTo");
		verifyNoInteractions(service);
	}

	// --- PATCH /api/applications/{id}/status ---

	@Test
	void patchStatusReturns200WithNewStatus() {
		given(service.changeStatus(5L, Status.APPLIED, 0L)).willReturn(response(Status.APPLIED));

		MvcTestResult result = patch(5L, """
				{"status": "APPLIED", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
	}

	@Test
	void patchWithMissingStatusReturns400() {
		MvcTestResult result = patch(5L, "{\"version\": 0}");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("status");
		verifyNoInteractions(service);
	}

	@Test
	void patchWithUnknownStatusValueReturns400() {
		MvcTestResult result = patch(5L, """
				{"status": "SENT", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(service);
	}

	@Test
	void patchInvalidTransitionReturns409WithCurrentAndRequestedStatus() {
		given(service.changeStatus(5L, Status.OFFER, 0L))
				.willThrow(new InvalidStatusTransitionException(Status.SAVED, Status.OFFER));

		MvcTestResult result = patch(5L, """
				{"status": "OFFER", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail")
				.isEqualTo("Cannot change status from SAVED to OFFER. Allowed next statuses: APPLIED, WITHDRAWN.");
		assertThat(result).bodyJson().extractingPath("$.currentStatus").isEqualTo("SAVED");
		assertThat(result).bodyJson().extractingPath("$.requestedStatus").isEqualTo("OFFER");
		assertThat(result).bodyJson().extractingPath("$.allowedStatuses").asArray().containsExactly("APPLIED", "WITHDRAWN");
	}

	@Test
	void patchFromRejectedToInterviewReturns409Problem() {
		given(service.changeStatus(5L, Status.INTERVIEW, 0L))
				.willThrow(new InvalidStatusTransitionException(Status.REJECTED, Status.INTERVIEW));

		MvcTestResult result = patch(5L, """
				{"status": "INTERVIEW", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(409);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid status transition");
		assertThat(result).bodyJson().extractingPath("$.detail")
				.isEqualTo("Cannot change status from REJECTED to INTERVIEW: REJECTED is a final status.");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/applications/5/status");
		assertThat(result).bodyJson().extractingPath("$.currentStatus").isEqualTo("REJECTED");
		assertThat(result).bodyJson().extractingPath("$.requestedStatus").isEqualTo("INTERVIEW");
		assertThat(result).bodyJson().extractingPath("$.allowedStatuses").asArray().isEmpty();
	}

	@Test
	void patchWithoutVersionReturns400() {
		MvcTestResult result = patch(5L, """
				{"status": "APPLIED"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("version");
		verifyNoInteractions(service);
	}

	@Test
	void patchWithStaleVersionReturns409WithBothVersions() {
		given(service.changeStatus(5L, Status.INTERVIEW, 1L)).willThrow(new StaleVersionException(5L, 1L, 2L));

		MvcTestResult result = patch(5L, """
				{"status": "INTERVIEW", "version": 1}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Concurrent modification");
		assertThat(result).bodyJson().extractingPath("$.expectedVersion").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.currentVersion").isEqualTo(2);
	}

	@Test
	void patchRecruiterConflictReturns409() {
		given(service.changeStatus(5L, Status.APPLIED, 0L))
				.willThrow(new RecruiterConflictException("Anna Schmidt already has your CV for another active application"));

		MvcTestResult result = patch(5L, """
				{"status": "APPLIED", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("Anna Schmidt");
	}

	@Test
	void patchUnknownIdReturns404() {
		given(service.changeStatus(99L, Status.APPLIED, 0L)).willThrow(new ResourceNotFoundException("Job application", 99L));

		MvcTestResult result = patch(99L, """
				{"status": "APPLIED", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void patchOptimisticLockFailureReturns409() {
		given(service.changeStatus(5L, Status.APPLIED, 0L))
				.willThrow(new ObjectOptimisticLockingFailureException(JobApplication.class, 5L));

		MvcTestResult result = patch(5L, """
				{"status": "APPLIED", "version": 0}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	private MvcTestResult post(String json) {
		return mvc.post().uri("/api/applications").contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult patch(Long id, String json) {
		return mvc.patch().uri("/api/applications/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
