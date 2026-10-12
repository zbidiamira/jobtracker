package de.zbidi.jobtracker.analysis;

import java.util.List;

import de.zbidi.jobtracker.TestJwt;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(PostingAnalysisController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class PostingAnalysisControllerTest {

	private static final long USER_ID = 42L;

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	PostingAnalyzer analyzer;

	@Test
	void returnsTheAnalysis() {
		given(analyzer.analyze(USER_ID, "We need Java.")).willReturn(new PostingAnalysis("Java Dev",
				List.of("Java"), List.of("Kafka"), List.of("Java"), List.of("Kafka"), 75, "Good match."));

		MvcTestResult result = analyze("""
				{"text": "We need Java."}
				""");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Java Dev");
		assertThat(result).bodyJson().extractingPath("$.mustHave[0]").isEqualTo("Java");
		assertThat(result).bodyJson().extractingPath("$.niceToHave[0]").isEqualTo("Kafka");
		assertThat(result).bodyJson().extractingPath("$.matchedSkills[0]").isEqualTo("Java");
		assertThat(result).bodyJson().extractingPath("$.missingSkills[0]").isEqualTo("Kafka");
		assertThat(result).bodyJson().extractingPath("$.matchScore").isEqualTo(75);
		assertThat(result).bodyJson().extractingPath("$.summary").isEqualTo("Good match.");
	}

	@Test
	void blankTextIsRejectedWithoutCallingTheModel() {
		assertThat(analyze("""
				{"text": "   "}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(analyzer);
	}

	@Test
	void exactly10000CharactersAreAccepted() {
		String text = "x".repeat(10_000);
		given(analyzer.analyze(USER_ID, text)).willReturn(new PostingAnalysis("Dev", List.of(), List.of(),
				List.of(), List.of(), 0, ""));

		assertThat(analyze("{\"text\": \"" + text + "\"}")).hasStatus(HttpStatus.OK);
	}

	@Test
	void moreThan10000CharactersAreRejectedWithoutCallingTheModel() {
		String text = "x".repeat(10_001);

		MvcTestResult result = analyze("{\"text\": \"" + text + "\"}");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[0].field").isEqualTo("text");
		verifyNoInteractions(analyzer);
	}

	@Test
	void unavailableModelReturns503WithRetryAfter() {
		given(analyzer.analyze(anyLong(), anyString()))
				.willThrow(new AiUnavailableException("The AI model did not answer within 30s"));

		MvcTestResult result = analyze("""
				{"text": "We need Java."}
				""");

		assertThat(result).hasStatus(HttpStatus.SERVICE_UNAVAILABLE).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Service Unavailable");
		// a fixed text: the internal reason (timeout, provider error) stays in the log
		assertThat(result).bodyJson().extractingPath("$.detail").isEqualTo("AI service currently unavailable");
	}

	@Test
	void rejectedOrUnusableAnswerReturns502() {
		given(analyzer.analyze(anyLong(), anyString())).willThrow(new AiRejectedException("unusable answer", null));

		MvcTestResult result = analyze("""
				{"text": "We need Java."}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_GATEWAY).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void withoutTokenReturns401() {
		assertThat(mvc.post().uri("/api/postings/analyze").contentType(MediaType.APPLICATION_JSON)
				.content("{\"text\": \"x\"}").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(analyzer);
	}

	private MvcTestResult analyze(String json) {
		return mvc.post().uri("/api/postings/analyze").with(TestJwt.user(USER_ID))
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
