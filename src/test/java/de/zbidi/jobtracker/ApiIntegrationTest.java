package de.zbidi.jobtracker;

import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whole stack: HTTP → controller → service → Postgres, no test transaction (like production, open-in-view off).
 * Data is not rolled back, so every test uses unique names.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void fullFlow() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);

		MvcTestResult company = postJson("/api/companies", """
				{"name": "ACME %s", "city": "Berlin", "website": "https://acme.example"}
				""".formatted(suffix));
		assertThat(company).hasStatus(HttpStatus.CREATED);
		Integer companyId = JsonPath.read(contentOf(company), "$.id");

		MvcTestResult application = postJson("/api/applications", """
				{"companyId": %d, "position": "Java Developer %s", "jobUrl": "https://acme.example/jobs/%s"}
				""".formatted(companyId, suffix, suffix));
		assertThat(application).hasStatus(HttpStatus.CREATED);
		Integer applicationId = JsonPath.read(contentOf(application), "$.id");

		MvcTestResult applied = patchStatus(applicationId, "APPLIED");
		assertThat(applied).hasStatus(HttpStatus.OK);
		assertThat(applied).bodyJson().extractingPath("$.version").isEqualTo(1);

		MvcTestResult loaded = mvc.get().uri("/api/applications/{id}", applicationId).exchange();
		assertThat(loaded).hasStatus(HttpStatus.OK);
		assertThat(loaded).bodyJson().extractingPath("$.companyName").isEqualTo("ACME " + suffix);

		MvcTestResult list = mvc.get().uri("/api/applications?status=APPLIED&size=100").exchange();
		assertThat(list).hasStatus(HttpStatus.OK);
		List<Integer> ids = JsonPath.read(contentOf(list), "$.content[*].id");
		assertThat(ids).contains(applicationId);

		MvcTestResult backwards = patchStatus(applicationId, "SAVED");
		assertThat(backwards).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(backwards).bodyJson().extractingPath("$.currentStatus").isEqualTo("APPLIED");
	}

	@Test
	void rejectedApplicationCannotMoveToInterview() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Integer companyId = JsonPath.read(contentOf(postJson("/api/companies", """
				{"name": "Initech %s"}
				""".formatted(suffix))), "$.id");
		Integer applicationId = JsonPath.read(contentOf(postJson("/api/applications", """
				{"companyId": %d, "position": "Java Developer %s"}
				""".formatted(companyId, suffix))), "$.id");
		assertThat(patchStatus(applicationId, "APPLIED")).hasStatus(HttpStatus.OK);
		assertThat(patchStatus(applicationId, "REJECTED")).hasStatus(HttpStatus.OK);

		MvcTestResult result = patchStatus(applicationId, "INTERVIEW");

		assertThat(result).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(result).bodyJson().extractingPath("$.title").isEqualTo("Invalid status transition");
		assertThat(result).bodyJson().extractingPath("$.currentStatus").isEqualTo("REJECTED");
		assertThat(result).bodyJson().extractingPath("$.requestedStatus").isEqualTo("INTERVIEW");
		// nothing was changed in the database
		MvcTestResult reloaded = mvc.get().uri("/api/applications/{id}", applicationId).exchange();
		assertThat(reloaded).bodyJson().extractingPath("$.status").isEqualTo("REJECTED");
		assertThat(reloaded).bodyJson().extractingPath("$.version").isEqualTo(2);
	}

	@Test
	void duplicateApplicationReturns409() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		MvcTestResult company = postJson("/api/companies", """
				{"name": "Globex %s"}
				""".formatted(suffix));
		Integer companyId = JsonPath.read(contentOf(company), "$.id");
		String body = """
				{"companyId": %d, "position": "Backend %s"}
				""".formatted(companyId, suffix);

		assertThat(postJson("/api/applications", body)).hasStatus(HttpStatus.CREATED);
		assertThat(postJson("/api/applications", body)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void apiIsReachableWithoutAuthentication() {
		assertThat(mvc.get().uri("/api/companies").exchange()).hasStatus(HttpStatus.OK);
	}

	@Test
	void unknownSortPropertyReturns400() {
		MvcTestResult result = mvc.get().uri("/api/companies?sort=doesNotExist").exchange();

		assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void openApiDocsListAllEndpoints() {
		MvcTestResult result = mvc.get().uri("/v3/api-docs").exchange();

		assertThat(result).hasStatus(HttpStatus.OK);
		assertThat(result).bodyJson().extractingPath("$.paths").asMap()
				.containsKeys("/api/companies", "/api/companies/{id}",
						"/api/applications", "/api/applications/{id}", "/api/applications/{id}/status");
	}

	@Test
	void swaggerUiIsServed() {
		assertThat(mvc.get().uri("/swagger-ui/index.html").exchange()).hasStatus(HttpStatus.OK);
	}

	private MvcTestResult postJson(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult patchStatus(Integer id, String status) {
		return mvc.patch().uri("/api/applications/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"%s\"}".formatted(status)).exchange();
	}

	private static String contentOf(MvcTestResult result) {
		try {
			return result.getResponse().getContentAsString();
		}
		catch (UnsupportedEncodingException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
