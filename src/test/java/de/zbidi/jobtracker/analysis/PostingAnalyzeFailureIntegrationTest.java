package de.zbidi.jobtracker.analysis;

import java.time.Duration;

import de.zbidi.jobtracker.ApiAuth;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

/**
 * What a client sees over HTTP when the model fails or is too slow, and the metrics in /actuator/metrics.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "jobtracker.ai.timeout=500ms")
class PostingAnalyzeFailureIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	ChatModel chatModel;

	String bearer;

	@BeforeEach
	void setUp() {
		given(chatModel.getOptions()).willReturn(ToolCallingChatOptions.builder().build());
		bearer = ApiAuth.registerNewUser(mvc, "analyze-failure");
	}

	@Test
	void modelThrowsGives503() {
		given(chatModel.call(any(Prompt.class))).willThrow(new TransientAiException("503 overloaded"));

		assertUnavailable(analyze());
	}

	@Test
	void slowModelTimesOutWith503() {
		given(chatModel.call(any(Prompt.class))).willAnswer(invocation -> {
			Thread.sleep(3_000);
			return null;
		});

		long start = System.nanoTime();
		MvcTestResult result = analyze();

		assertUnavailable(result);
		assertThat(Duration.ofNanos(System.nanoTime() - start)).as("answered after the 500 ms timeout, not 3 s")
				.isLessThan(Duration.ofMillis(2_500));
	}

	@Test
	void metricsAreVisibleInActuator() {
		MvcTestResult duration = mvc.get().uri("/actuator/metrics/jobtracker.ai.duration")
				.header(HttpHeaders.AUTHORIZATION, bearer).exchange();
		MvcTestResult tokens = mvc.get().uri("/actuator/metrics/jobtracker.ai.tokens")
				.header(HttpHeaders.AUTHORIZATION, bearer).exchange();

		assertThat(duration).hasStatus(HttpStatus.OK);
		assertThat(duration).bodyJson().extractingPath("$.name").isEqualTo("jobtracker.ai.duration");
		assertThat(duration).bodyJson().extractingPath("$.availableTags[?(@.tag == 'outcome')].values[*]").asArray()
				.contains("success", "timeout", "unavailable", "rejected");
		assertThat(tokens).hasStatus(HttpStatus.OK);
		assertThat(tokens).bodyJson().extractingPath("$.name").isEqualTo("jobtracker.ai.tokens");
	}

	private MvcTestResult analyze() {
		return mvc.post().uri("/api/postings/analyze").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"text": "Backend developer, Java required."}
						""").exchange();
	}

	private static void assertUnavailable(MvcTestResult result) {
		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("AI service currently unavailable");
		assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
	}

}
