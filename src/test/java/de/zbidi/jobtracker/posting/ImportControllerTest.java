package de.zbidi.jobtracker.posting;

import java.time.LocalDateTime;
import java.util.List;

import de.zbidi.jobtracker.TestJwt;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(ImportController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class ImportControllerTest {

	private static final MockMultipartFile CSV = new MockMultipartFile("file", "postings.csv", "text/csv",
			"title,company,city,url\nDev,ACME,Berlin,https://acme.example/1\n".getBytes());

	private static final ImportStatusResponse STARTING = new ImportStatusResponse(42L, "STARTING", "UNKNOWN",
			null, null, 0, 0, 0, 0, List.of());

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	ImportService importService;

	@Test
	void adminUploadStartsImportAndReturns202WithLocation() {
		given(importService.start(any())).willReturn(STARTING);

		MvcTestResult result = mvc.post().uri("/api/admin/imports").multipart().file(CSV)
				.with(TestJwt.admin(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/admin/imports/42");
		assertThat(result).bodyJson().extractingPath("$.executionId").isEqualTo(42);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("STARTING");
	}

	@Test
	void userCannotImport() {
		MvcTestResult result = mvc.post().uri("/api/admin/imports").multipart().file(CSV)
				.with(TestJwt.user(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(importService);
	}

	@Test
	void importWithoutTokenReturns401() {
		MvcTestResult result = mvc.post().uri("/api/admin/imports").multipart().file(CSV).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(importService);
	}

	@Test
	void emptyFileReturns400() {
		MvcTestResult result = mvc.post().uri("/api/admin/imports")
				.multipart().file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0]))
				.with(TestJwt.admin(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		verifyNoInteractions(importService);
	}

	@Test
	void missingFilePartReturns400() {
		MvcTestResult result = mvc.post().uri("/api/admin/imports").multipart().with(TestJwt.admin(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(importService);
	}

	@Test
	void statusReturnsCounts() {
		given(importService.status(42L)).willReturn(new ImportStatusResponse(42L, "COMPLETED", "COMPLETED",
				LocalDateTime.of(2026, 10, 11, 8, 0), LocalDateTime.of(2026, 10, 11, 8, 1), 12, 9, 1, 2, List.of()));

		MvcTestResult result = mvc.get().uri("/api/admin/imports/42").with(TestJwt.admin(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("COMPLETED");
		assertThat(result).bodyJson().extractingPath("$.readCount").isEqualTo(12);
		assertThat(result).bodyJson().extractingPath("$.writeCount").isEqualTo(9);
		assertThat(result).bodyJson().extractingPath("$.filterCount").isEqualTo(1);
		assertThat(result).bodyJson().extractingPath("$.skipCount").isEqualTo(2);
	}

	@Test
	void unknownExecutionReturns404() {
		given(importService.status(7L)).willThrow(new ResourceNotFoundException("Import", 7L));

		assertThat(mvc.get().uri("/api/admin/imports/7").with(TestJwt.admin(1)).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void userCannotReadImportStatus() {
		assertThat(mvc.get().uri("/api/admin/imports/42").with(TestJwt.user(1)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(importService);
	}

	@Test
	void restartReturns202WithTheNewExecution() {
		given(importService.restart(42L)).willReturn(new ImportStatusResponse(43L, "STARTING", "UNKNOWN",
				null, null, 0, 0, 0, 0, List.of()));

		MvcTestResult result = mvc.post().uri("/api/admin/imports/42/restart").with(TestJwt.admin(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/admin/imports/43");
	}

	@Test
	void restartOfAFinishedImportReturns409() {
		given(importService.restart(42L)).willThrow(new ImportNotRestartableException(42L, "already completed"));

		MvcTestResult result = mvc.post().uri("/api/admin/imports/42/restart").with(TestJwt.admin(1)).exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail").asString().contains("already completed");
	}

}
