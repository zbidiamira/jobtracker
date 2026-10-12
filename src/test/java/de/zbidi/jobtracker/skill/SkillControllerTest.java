package de.zbidi.jobtracker.skill;

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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@WebMvcTest(SkillController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class SkillControllerTest {

	private static final long USER_ID = 42L;
	private static final SkillResponse JAVA = new SkillResponse(3L, "Java", 6, SkillLevel.EXPERT);

	@Autowired
	MockMvcTester mvc;

	@MockitoBean
	SkillService skillService;

	@Test
	void createReturns201WithLocation() {
		given(skillService.create(USER_ID, new SkillRequest("Java", 6, SkillLevel.EXPERT))).willReturn(JAVA);

		MvcTestResult result = send(mvc.post().uri("/api/skills"), """
				{"name": "Java", "years": 6, "level": "EXPERT"}
				""");

		assertThat(result).hasStatus(HttpStatus.CREATED);
		assertThat(result.getResponse().getHeader("Location")).endsWith("/api/skills/3");
		assertThat(result).bodyJson().extractingPath("$.name").isEqualTo("Java");
		assertThat(result).bodyJson().extractingPath("$.level").isEqualTo("EXPERT");
	}

	@Test
	void invalidSkillReturns400WithFieldErrors() {
		MvcTestResult result = send(mvc.post().uri("/api/skills"), """
				{"name": " ", "years": -1}
				""");

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.errors[*].field").asArray()
				.containsExactlyInAnyOrder("name", "years", "level");
		verifyNoInteractions(skillService);
	}

	@Test
	void moreThan60YearsIsInvalid() {
		assertThat(send(mvc.post().uri("/api/skills"), """
				{"name": "Cobol", "years": 61, "level": "EXPERT"}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void unknownLevelIsRejected() {
		assertThat(send(mvc.post().uri("/api/skills"), """
				{"name": "Java", "years": 1, "level": "GURU"}
				""")).hasStatus(HttpStatus.BAD_REQUEST);
		verifyNoInteractions(skillService);
	}

	@Test
	void listReturnsOnlyTheCallersSkills() {
		given(skillService.list(USER_ID)).willReturn(List.of(JAVA));

		MvcTestResult result = mvc.get().uri("/api/skills").with(TestJwt.user(USER_ID)).exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$[0].name").isEqualTo("Java");
	}

	@Test
	void getOneReturns200() {
		given(skillService.get(USER_ID, 3L)).willReturn(JAVA);

		assertThat(mvc.get().uri("/api/skills/3").with(TestJwt.user(USER_ID)).exchange()).hasStatus(HttpStatus.OK);
	}

	@Test
	void someoneElsesSkillIsNotFound() {
		given(skillService.get(USER_ID, 9L)).willThrow(new ResourceNotFoundException("Skill", 9L));

		assertThat(mvc.get().uri("/api/skills/9").with(TestJwt.user(USER_ID)).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void updateReturns200() {
		given(skillService.update(USER_ID, 3L, new SkillRequest("Java", 7, SkillLevel.EXPERT)))
				.willReturn(new SkillResponse(3L, "Java", 7, SkillLevel.EXPERT));

		MvcTestResult result = send(mvc.put().uri("/api/skills/3"), """
				{"name": "Java", "years": 7, "level": "EXPERT"}
				""");

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.years").isEqualTo(7);
	}

	@Test
	void userMayDeleteTheirOwnSkill() {
		// DELETE is ADMIN-only elsewhere; skills are the user's own data
		assertThat(mvc.delete().uri("/api/skills/3").with(TestJwt.user(USER_ID)).exchange())
				.hasStatus(HttpStatus.NO_CONTENT);
		verify(skillService).delete(USER_ID, 3L);
	}

	@Test
	void duplicateNameReturns409() {
		given(skillService.create(anyLong(), any())).willThrow(new DuplicateSkillException("Java"));

		MvcTestResult result = send(mvc.post().uri("/api/skills"), """
				{"name": "java", "years": 1, "level": "BEGINNER"}
				""");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void withoutTokenReturns401() {
		assertThat(mvc.get().uri("/api/skills").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		verifyNoInteractions(skillService);
	}

	@Test
	void deleteOfUnknownSkillReturns404() {
		willThrow(new ResourceNotFoundException("Skill", 8L)).given(skillService).delete(USER_ID, 8L);

		assertThat(mvc.delete().uri("/api/skills/8").with(TestJwt.user(USER_ID)).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult send(MockMvcTester.MockMvcRequestBuilder request, String json) {
		return request.with(TestJwt.user(USER_ID)).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
