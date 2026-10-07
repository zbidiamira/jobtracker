package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.List;

import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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

	// --- GET /api/applications ---

	@Test
	void listWithoutStatusCallsServiceWithEmptyFilter() {
		given(service.list(isNull(), any())).willReturn(new PageResponse<>(List.of(response(Status.SAVED)), 0, 20, 1, 1));

		MvcTestResult result = mvc.get().uri("/api/applications").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.content[0].id").isEqualTo(5);
		verify(service).list(isNull(), any());
	}

	@Test
	void listWithStatusFilterPassesStatus() {
		given(service.list(eq(Status.APPLIED), any())).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		MvcTestResult result = mvc.get().uri("/api/applications?status=APPLIED&page=0&size=20").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		verify(service).list(eq(Status.APPLIED), any());
	}

	@Test
	void listWithUnknownStatusReturns400() {
		MvcTestResult result = mvc.get().uri("/api/applications?status=SENT").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(service);
	}

	// --- PATCH /api/applications/{id}/status ---

	@Test
	void patchStatusReturns200WithNewStatus() {
		given(service.changeStatus(5L, Status.APPLIED)).willReturn(response(Status.APPLIED));

		MvcTestResult result = patch(5L, """
				{"status": "APPLIED"}
				""");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
	}

	@Test
	void patchWithMissingStatusReturns400() {
		MvcTestResult result = patch(5L, "{}");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("status");
		verifyNoInteractions(service);
	}

	@Test
	void patchWithUnknownStatusValueReturns400() {
		MvcTestResult result = patch(5L, """
				{"status": "SENT"}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(service);
	}

	@Test
	void patchInvalidTransitionReturns409WithCurrentAndRequestedStatus() {
		given(service.changeStatus(5L, Status.OFFER))
				.willThrow(new InvalidStatusTransitionException(Status.SAVED, Status.OFFER));

		MvcTestResult result = patch(5L, """
				{"status": "OFFER"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Cannot change status from SAVED to OFFER");
		assertThat(result).bodyJson().extractingPath("$.currentStatus").isEqualTo("SAVED");
		assertThat(result).bodyJson().extractingPath("$.requestedStatus").isEqualTo("OFFER");
	}

	@Test
	void patchFromRejectedToInterviewReturns409Problem() {
		given(service.changeStatus(5L, Status.INTERVIEW))
				.willThrow(new InvalidStatusTransitionException(Status.REJECTED, Status.INTERVIEW));

		MvcTestResult result = patch(5L, """
				{"status": "INTERVIEW"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo(409);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid status transition");
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("Cannot change status from REJECTED to INTERVIEW");
		assertThat(result).bodyJson().extractingPath("$.instance").isEqualTo("/api/applications/5/status");
		assertThat(result).bodyJson().extractingPath("$.currentStatus").isEqualTo("REJECTED");
		assertThat(result).bodyJson().extractingPath("$.requestedStatus").isEqualTo("INTERVIEW");
	}

	@Test
	void patchRecruiterConflictReturns409() {
		given(service.changeStatus(5L, Status.APPLIED))
				.willThrow(new RecruiterConflictException("Anna Schmidt already has your CV for another active application"));

		MvcTestResult result = patch(5L, """
				{"status": "APPLIED"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT);
		assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("Anna Schmidt");
	}

	@Test
	void patchUnknownIdReturns404() {
		given(service.changeStatus(99L, Status.APPLIED)).willThrow(new ResourceNotFoundException("Job application", 99L));

		MvcTestResult result = patch(99L, """
				{"status": "APPLIED"}
				""");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void patchOptimisticLockFailureReturns409() {
		given(service.changeStatus(5L, Status.APPLIED))
				.willThrow(new ObjectOptimisticLockingFailureException(JobApplication.class, 5L));

		MvcTestResult result = patch(5L, """
				{"status": "APPLIED"}
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
