package de.zbidi.jobtracker.matching;

import java.time.Instant;
import java.util.List;

import de.zbidi.jobtracker.TestJwt;
import de.zbidi.jobtracker.common.GlobalExceptionHandler;
import de.zbidi.jobtracker.common.PageResponse;
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

@WebMvcTest(MatchController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class MatchControllerTest {

	private static final long USER_ID = 42L;
	private static final MatchResponse MATCH = new MatchResponse(5L, "Java Developer", "ACME", "Berlin",
			"https://acme.example/jobs/1", 82, List.of("Java"), List.of("Kafka"), List.of("Java"), List.of("Kafka"),
			"Strong match.", Instant.parse("2026-10-12T05:00:00Z"));

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	MatchQueryService matchQueryService;

	@Test
	void returnsTheCallersMatches() {
		given(matchQueryService.matches(USER_ID, 70, 0, 20)).willReturn(new PageResponse<>(List.of(MATCH), 0, 20, 1, 1));

		MvcTestResult result = mvc.get().uri("/api/matches?minScore=70").with(TestJwt.user(USER_ID)).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.content[0].title").isEqualTo("Java Developer");
		assertThat(result).bodyJson().extractingPath("$.content[0].matchScore").isEqualTo(82);
		assertThat(result).bodyJson().extractingPath("$.content[0].missingSkills[0]").isEqualTo("Kafka");
		assertThat(result).bodyJson().extractingPath("$.content[0].url").isEqualTo("https://acme.example/jobs/1");
	}

	@Test
	void defaultsToAllScoresFirstPage() {
		given(matchQueryService.matches(USER_ID, 0, 0, 20)).willReturn(new PageResponse<>(List.of(), 0, 20, 0, 0));

		assertThat(mvc.get().uri("/api/matches").with(TestJwt.user(USER_ID)).exchange()).hasStatus(HttpStatus.OK);
	}

	@Test
	void minScoreOutside0To100IsRejected() {
		assertThat(mvc.get().uri("/api/matches?minScore=101").with(TestJwt.user(USER_ID)).exchange())
				.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/matches?size=500").with(TestJwt.user(USER_ID)).exchange())
				.hasStatus(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(matchQueryService);
	}

	@Test
	void withoutTokenReturns401() {
		assertThat(mvc.get().uri("/api/matches").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(matchQueryService);
	}

}
