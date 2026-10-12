package de.zbidi.jobtracker.jobsource;

import java.util.List;

import de.zbidi.jobtracker.TestJwt;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(JobFetchAdminController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class JobFetchAdminControllerTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	JobFetchService jobFetchService;

	@Test
	void adminFetchesNowAndSeesTheResultPerPortal() {
		given(jobFetchService.fetchAll()).willReturn(List.of(
				new JobFetchService.FetchResult("arbeitnow", 12, 4, null),
				new JobFetchService.FetchResult("bundesagentur", 0, 0, "I/O error")));

		MvcTestResult result = mvc.post().uri("/api/admin/jobs/fetch").with(TestJwt.admin(1L)).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$[0].source").isEqualTo("arbeitnow");
		assertThat(result).bodyJson().extractingPath("$[0].saved").isEqualTo(4);
		assertThat(result).bodyJson().extractingPath("$[1].error").isEqualTo("I/O error");
	}

	@Test
	void userCannotTriggerAFetch() {
		assertThat(mvc.post().uri("/api/admin/jobs/fetch").with(TestJwt.user(7L)).exchange())
				.hasStatus(HttpStatus.FORBIDDEN);
		verifyNoInteractions(jobFetchService);
	}

}
