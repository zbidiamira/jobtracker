package de.zbidi.jobtracker;

import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
	void patchWithStaleVersionReturns409() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Integer companyId = JsonPath.read(contentOf(postJson("/api/companies", """
				{"name": "Umbrella %s"}
				""".formatted(suffix))), "$.id");
		Integer applicationId = JsonPath.read(contentOf(postJson("/api/applications", """
				{"companyId": %d, "position": "Java Developer %s"}
				""".formatted(companyId, suffix))), "$.id");

		// both clients read version 0; client 1 saves first
		assertThat(patchStatus(applicationId, "APPLIED", 0)).hasStatus(HttpStatus.OK);
		MvcTestResult client2 = patchStatus(applicationId, "WITHDRAWN", 0);

		assertThat(client2).hasStatus(HttpStatus.CONFLICT).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(client2).bodyJson().extractingPath("$.title").isEqualTo("Concurrent modification");
		assertThat(client2).bodyJson().extractingPath("$.currentVersion").isEqualTo(1);
		// client 1's change is kept, client 2's is not applied
		MvcTestResult reloaded = mvc.get().uri("/api/applications/{id}", applicationId).exchange();
		assertThat(reloaded).bodyJson().extractingPath("$.status").isEqualTo("APPLIED");
	}

	@Test
	void searchCombinesFiltersAndPaging() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Integer companyId = JsonPath.read(contentOf(postJson("/api/companies", """
				{"name": "Search %s"}
				""".formatted(suffix))), "$.id");
		for (String position : new String[] {"Java A", "Java B", "Java C", "Kotlin D"}) {
			Integer id = JsonPath.read(contentOf(postJson("/api/applications", """
					{"companyId": %d, "position": "%s"}
					""".formatted(companyId, position))), "$.id");
			assertThat(patchStatus(id, "APPLIED")).hasStatus(HttpStatus.OK);
		}
		String today = LocalDate.now(ZoneId.of("Europe/Berlin")).toString();

		MvcTestResult page = mvc.get().uri("/api/applications/search?status=APPLIED&companyName=" + suffix
				+ "&position=java&createdFrom=" + today + "&createdTo=" + today + "&size=2&page=1&sort=position").exchange();

		assertThat(page).hasStatus(HttpStatus.OK);
		assertThat(page).bodyJson().extractingPath("$.totalElements").isEqualTo(3);
		assertThat(page).bodyJson().extractingPath("$.totalPages").isEqualTo(2);
		List<String> positions = JsonPath.read(contentOf(page), "$.content[*].position");
		assertThat(positions).containsExactly("Java C");
	}

	/**
	 * AuditingTest imports JpaAuditingConfig explicitly; this proves auditing is also active in the real app
	 * (component scan), e.g. if @Configuration were removed or the class moved out of the scanned package.
	 */
	@Test
	void auditingIsActiveInTheRunningApplication() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Integer companyId = JsonPath.read(contentOf(postJson("/api/companies", """
				{"name": "Audit %s"}
				""".formatted(suffix))), "$.id");
		Integer applicationId = JsonPath.read(contentOf(postJson("/api/applications", """
				{"companyId": %d, "position": "Java Developer %s"}
				""".formatted(companyId, suffix))), "$.id");

		// read back from the database (Postgres stores microseconds, so compare stored values with each other)
		String afterInsert = contentOf(mvc.get().uri("/api/applications/{id}", applicationId).exchange());
		String createdAt = JsonPath.read(afterInsert, "$.createdAt");
		String updatedAtAfterInsert = JsonPath.read(afterInsert, "$.updatedAt");
		assertThat(createdAt).as("createdAt set on insert").isNotNull();
		assertThat(updatedAtAfterInsert).as("updatedAt set on insert").isEqualTo(createdAt);

		assertThat(patchStatus(applicationId, "APPLIED")).hasStatus(HttpStatus.OK);

		String afterUpdate = contentOf(mvc.get().uri("/api/applications/{id}", applicationId).exchange());
		assertThat(Instant.parse(JsonPath.read(afterUpdate, "$.updatedAt")))
				.as("updatedAt moves on update").isAfter(Instant.parse(updatedAtAfterInsert));
		assertThat((String) JsonPath.read(afterUpdate, "$.createdAt")).as("createdAt unchanged").isEqualTo(createdAt);
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
						"/api/applications", "/api/applications/{id}", "/api/applications/{id}/status",
						"/api/applications/{id}/history", "/api/applications/search");

		// search filters appear as individual query parameters in Swagger
		List<String> searchParams = JsonPath.read(contentOf(result), "$.paths['/api/applications/search'].get.parameters[*].name");
		assertThat(searchParams)
				.contains("status", "companyName", "position", "createdFrom", "createdTo", "page", "size", "sort")
				.doesNotContain("search", "dateRangeValid");
		// the plain list only pages
		List<String> listParams = JsonPath.read(contentOf(result), "$.paths['/api/applications'].get.parameters[*].name");
		assertThat(listParams).containsExactlyInAnyOrder("page", "size", "sort");
	}

	@Test
	void swaggerUiIsServed() {
		assertThat(mvc.get().uri("/swagger-ui/index.html").exchange()).hasStatus(HttpStatus.OK);
	}

	private MvcTestResult postJson(String uri, String json) {
		return mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	/** Like a real client: GET the current version, then PATCH with it. */
	private MvcTestResult patchStatus(Integer id, String status) {
		Integer version = JsonPath.read(contentOf(mvc.get().uri("/api/applications/{id}", id).exchange()), "$.version");
		return patchStatus(id, status, version);
	}

	private MvcTestResult patchStatus(Integer id, String status, Integer version) {
		return mvc.patch().uri("/api/applications/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"%s\", \"version\": %d}".formatted(status, version))
				.exchange();
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
