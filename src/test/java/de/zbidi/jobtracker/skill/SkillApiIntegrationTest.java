package de.zbidi.jobtracker.skill;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.ApiAuth;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real tokens and database: every user manages only their own skills.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SkillApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void eachUserSeesAndChangesOnlyTheirOwnSkills() {
		String alice = ApiAuth.registerNewUser(mvc, "skills-alice");
		String bob = ApiAuth.registerNewUser(mvc, "skills-bob");

		MvcTestResult created = post(alice, """
				{"name": "Java", "years": 6, "level": "EXPERT"}
				""");
		assertThat(created).hasStatus(HttpStatus.CREATED);
		Integer id = JsonPath.read(ApiAuth.content(created), "$.id");
		assertThat(post(bob, """
				{"name": "Java", "years": 1, "level": "BEGINNER"}
				""")).as("same name, other user").hasStatus(HttpStatus.CREATED);

		assertThat(mvc.get().uri("/api/skills").header(HttpHeaders.AUTHORIZATION, alice).exchange())
				.bodyJson().extractingPath("$[*].level").asArray().containsExactly("EXPERT");
		assertThat(mvc.get().uri("/api/skills/{id}", id).header(HttpHeaders.AUTHORIZATION, bob).exchange())
				.as("someone else's skill").hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.delete().uri("/api/skills/{id}", id).header(HttpHeaders.AUTHORIZATION, bob).exchange())
				.hasStatus(HttpStatus.NOT_FOUND);

		MvcTestResult updated = mvc.put().uri("/api/skills/{id}", id).header(HttpHeaders.AUTHORIZATION, alice)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name": "Java", "years": 7, "level": "EXPERT"}
						""").exchange();
		assertThat(updated).hasStatus(HttpStatus.OK);
		assertThat(updated).bodyJson().extractingPath("$.years").isEqualTo(7);

		assertThat(post(alice, """
				{"name": "JAVA", "years": 1, "level": "BEGINNER"}
				""")).as("duplicate, ignoring case").hasStatus(HttpStatus.CONFLICT);

		assertThat(mvc.delete().uri("/api/skills/{id}", id).header(HttpHeaders.AUTHORIZATION, alice).exchange())
				.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.get().uri("/api/skills").header(HttpHeaders.AUTHORIZATION, alice).exchange())
				.bodyJson().extractingPath("$").asArray().isEmpty();
	}

	private MvcTestResult post(String bearer, String json) {
		return mvc.post().uri("/api/skills").header(HttpHeaders.AUTHORIZATION, bearer)
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

}
