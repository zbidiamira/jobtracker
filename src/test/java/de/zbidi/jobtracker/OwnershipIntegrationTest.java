package de.zbidi.jobtracker;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.jobapplication.StatusHistoryRepository;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static de.zbidi.jobtracker.ApiAuth.content;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Alice and Bob each only see their own applications. Someone else's application answers exactly like one that
 * doesn't exist (404), so ids can't be probed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OwnershipIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	StatusHistoryRepository statusHistoryRepository;

	private String alice;
	private String bob;
	private String suffix;
	private Integer companyId;

	@BeforeEach
	void setUp() {
		alice = ApiAuth.registerNewUser(mvc, "alice");
		bob = ApiAuth.registerNewUser(mvc, "bob");
		suffix = UUID.randomUUID().toString().substring(0, 8);
		// companies are shared: alice creates it, bob may use it too
		companyId = JsonPath.read(content(post(alice, "/api/companies", "{\"name\": \"Shared %s\"}".formatted(suffix))), "$.id");
	}

	@Test
	void bobCannotSeeAlicesApplication() {
		Integer id = createApplication(alice, "Java Developer");

		MvcTestResult forBob = get(bob, "/api/applications/" + id);
		MvcTestResult missing = get(bob, "/api/applications/999999999");

		assertThat(forBob).hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		// indistinguishable from an id that doesn't exist at all
		assertThat(forBob).bodyJson().extractingPath("$.title").isEqualTo("Not Found");
		assertThat(missing).bodyJson().extractingPath("$.title").isEqualTo("Not Found");
		assertThat(get(alice, "/api/applications/" + id)).hasStatus(HttpStatus.OK);
	}

	@Test
	void bobCannotChangeAlicesStatus() {
		Integer id = createApplication(alice, "Java Developer");

		MvcTestResult result = patch(bob, "/api/applications/" + id + "/status", "{\"status\": \"WITHDRAWN\", \"version\": 0}");

		assertThat(result).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(alice, "/api/applications/" + id)).bodyJson().extractingPath("$.status").isEqualTo("SAVED");
	}

	@Test
	void bobCannotReadAlicesHistory() {
		Integer id = createApplication(alice, "Java Developer");

		assertThat(get(bob, "/api/applications/" + id + "/history")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(alice, "/api/applications/" + id + "/history")).hasStatus(HttpStatus.OK);
	}

	@Test
	void listAndSearchOnlyReturnOwnApplications() {
		createApplication(alice, "Java Developer");
		createApplication(alice, "Kotlin Developer");
		createApplication(bob, "Java Developer");

		assertThat(get(alice, "/api/applications")).bodyJson().extractingPath("$.totalElements").isEqualTo(2);
		assertThat(get(bob, "/api/applications")).bodyJson().extractingPath("$.totalElements").isEqualTo(1);
		assertThat(get(alice, "/api/applications/search?companyName=" + suffix + "&position=java"))
				.bodyJson().extractingPath("$.totalElements").isEqualTo(1);
	}

	@Test
	void sameJobUrlAllowedForDifferentUsersButNotTwiceForOne() {
		String body = "{\"companyId\": %d, \"position\": \"Dev %s\", \"jobUrl\": \"https://jobs.example/%s\"}"
				.formatted(companyId, suffix, suffix);

		assertThat(post(alice, "/api/applications", body)).hasStatus(HttpStatus.CREATED);
		assertThat(post(bob, "/api/applications", body)).hasStatus(HttpStatus.CREATED);
		assertThat(post(alice, "/api/applications", body)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void userCannotDeleteEvenTheirOwnApplication() {
		Integer id = createApplication(alice, "Java Developer");

		assertThat(delete(alice, "/api/applications/" + id)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void adminCanDeleteAlicesApplicationIncludingItsHistory() {
		Integer id = createApplication(alice, "Java Developer");
		assertThat(patch(alice, "/api/applications/" + id + "/status", "{\"status\": \"APPLIED\", \"version\": 0}"))
				.hasStatus(HttpStatus.OK);
		String admin = adminToken();

		assertThat(delete(admin, "/api/applications/" + id)).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(get(alice, "/api/applications/" + id)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(statusHistoryRepository.findByJobApplicationIdOrderByChangedAtAscIdAsc(id.longValue())).isEmpty();
	}

	@Test
	void adminCannotDeleteCompanyThatStillHasApplications() {
		createApplication(alice, "Java Developer");

		assertThat(delete(adminToken(), "/api/companies/" + companyId)).hasStatus(HttpStatus.CONFLICT);
	}

	/** Admins can't register themselves: created directly in the database, then logged in through the API. */
	private String adminToken() {
		String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		appUserRepository.save(new AppUser(email, passwordEncoder.encode(ApiAuth.PASSWORD), Role.ADMIN));
		return ApiAuth.login(mvc, email);
	}

	private Integer createApplication(String user, String position) {
		MvcTestResult result = post(user, "/api/applications",
				"{\"companyId\": %d, \"position\": \"%s\"}".formatted(companyId, position));
		assertThat(result).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(content(result), "$.id");
	}

	private MvcTestResult get(String user, String uri) {
		return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, user).exchange();
	}

	private MvcTestResult post(String user, String uri, String json) {
		return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, user)
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult patch(String user, String uri, String json) {
		return mvc.patch().uri(uri).header(HttpHeaders.AUTHORIZATION, user)
				.contentType(MediaType.APPLICATION_JSON).content(json).exchange();
	}

	private MvcTestResult delete(String user, String uri) {
		return mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, user).exchange();
	}

}
