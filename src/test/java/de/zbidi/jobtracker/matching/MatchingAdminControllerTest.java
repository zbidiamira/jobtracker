package de.zbidi.jobtracker.matching;

import de.zbidi.jobtracker.TestJwt;
import de.zbidi.jobtracker.analysis.AiUnavailableException;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.config.SecurityConfig;
import de.zbidi.jobtracker.posting.ImportStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(MatchingAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MatchingAdminControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	MatchingService matchingService;

	@Test
	void adminStartsARunAndGets202WithLocation() {
		given(matchingService.start())
				.willReturn(MetaDataInstanceFactory.createJobExecution(PostingMatchJobConfig.JOB_NAME, 1L, 31L));

		MvcTestResult result = mvc.post().uri("/api/admin/matching/run").with(TestJwt.admin(1L)).exchange();

		assertThat(result).hasStatus(HttpStatus.ACCEPTED);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/admin/matching/31");
		assertThat(result).bodyJson().extractingPath("$.executionId").isEqualTo(31);
	}

	@Test
	void withoutAModelReturns503() {
		given(matchingService.start()).willThrow(new AiUnavailableException("AI is not configured"));

		assertThat(mvc.post().uri("/api/admin/matching/run").with(TestJwt.admin(1L)).exchange())
				.hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
	}

	@Test
	void runWhileOneIsRunningReturns409() {
		given(matchingService.start()).willThrow(new MatchingAlreadyRunningException());

		MvcTestResult result = mvc.post().uri("/api/admin/matching/run").with(TestJwt.admin(1L)).exchange();

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void statusOfARun() {
		given(matchingService.status(31L)).willReturn(ImportStatusResponse.from(
				MetaDataInstanceFactory.createJobExecution(PostingMatchJobConfig.JOB_NAME, 1L, 31L)));

		MvcTestResult result = mvc.get().uri("/api/admin/matching/31").with(TestJwt.admin(1L)).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.executionId").isEqualTo(31);
	}

	@Test
	void userCannotStartARun() {
		assertThat(mvc.post().uri("/api/admin/matching/run").with(TestJwt.user(7L)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(matchingService);
	}

}
