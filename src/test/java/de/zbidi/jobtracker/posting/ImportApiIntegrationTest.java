package de.zbidi.jobtracker.posting;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.ApiAuth;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Upload → async job → poll the status, with a real admin token (admins can't self-register: promoted in the DB).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ImportApiIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	JobRepository jobRepository;

	@Value("${jobtracker.imports.directory}")
	Path importDirectory;

	String admin;
	String urlPrefix;

	@BeforeEach
	void setUp() {
		String email = "admin-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
		ApiAuth.register(mvc, email);
		jdbc.update("UPDATE app_user SET role = 'ADMIN' WHERE email = ?", email);
		admin = ApiAuth.login(mvc, email);
		urlPrefix = "https://api-import.example/" + UUID.randomUUID() + "/";
	}

	@Test
	void uploadedFileIsSavedAndImportedInTheBackground() throws Exception {
		String csv = """
				title,company,city,url,description
				Java Developer,ACME,Berlin,%1$s1,"Spring Boot services, PostgreSQL"
				Kotlin Developer,Initech,Hamburg,%1$s2,Android apps
				broken line
				""".formatted(urlPrefix);
		MvcTestResult started = upload(csv);

		assertThat(started).hasStatus(HttpStatus.ACCEPTED);
		String location = started.getResponse().getHeader(HttpHeaders.LOCATION);
		Integer executionId = JsonPath.read(ApiAuth.content(started), "$.executionId");
		assertThat(location).endsWith("/api/admin/imports/" + executionId);

		// the upload is kept on disk (a restart reads it again) and is what the job imports
		String inputFile = jobRepository.getJobExecution(executionId).getJobParameters()
				.getString(PostingImportJobConfig.INPUT_FILE);
		assertThat(Path.of(inputFile)).startsWith(importDirectory.toAbsolutePath()).hasContent(csv);

		String body = await().atMost(Duration.ofSeconds(30)).until(() -> ApiAuth.content(get(location)),
				json -> "COMPLETED".equals(JsonPath.read(json, "$.status")));

		assertThat((Integer) JsonPath.read(body, "$.readCount")).as("the broken line is a read skip").isEqualTo(2);
		assertThat((Integer) JsonPath.read(body, "$.writeCount")).isEqualTo(2);
		assertThat((Integer) JsonPath.read(body, "$.skipCount")).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM job_posting WHERE url LIKE ? AND source = ?",
				Integer.class, urlPrefix + "%", "csv:postings.csv")).isEqualTo(2);
	}

	/** A real token from /api/auth/register carries roles=["USER"]: the admin API refuses it. */
	@Test
	void userTokenCannotUpload() {
		String user = ApiAuth.registerNewUser(mvc, "import-user");

		MvcTestResult result = mvc.post().uri("/api/admin/imports")
				.multipart().file(new MockMultipartFile("file", "postings.csv", "text/csv",
						"title,company,city,url,description\nDev,ACME,Berlin,%s1,Java backend\n".formatted(urlPrefix).getBytes()))
				.header(HttpHeaders.AUTHORIZATION, user)
				.exchange();

		assertThat(result).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM job_posting WHERE url LIKE ?", Integer.class,
				urlPrefix + "%")).isZero();
	}

	@Test
	void completedImportCannotBeRestarted() {
		MvcTestResult started = upload("title,company,city,url,description\nDev,ACME,Berlin,%s1,Java backend\n"
				.formatted(urlPrefix));
		String location = started.getResponse().getHeader(HttpHeaders.LOCATION);
		await().atMost(Duration.ofSeconds(30)).until(() -> ApiAuth.content(get(location)),
				json -> "COMPLETED".equals(JsonPath.read(json, "$.status")));

		MvcTestResult restart = mvc.post().uri(location + "/restart").header(HttpHeaders.AUTHORIZATION, admin).exchange();

		assertThat(restart).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void unknownExecutionReturns404() {
		assertThat(get("/api/admin/imports/999999999")).hasStatus(HttpStatus.NOT_FOUND);
	}

	private MvcTestResult upload(String csv) {
		return mvc.post().uri("/api/admin/imports")
				.multipart().file(new MockMultipartFile("file", "postings.csv", "text/csv", csv.getBytes()))
				.header(HttpHeaders.AUTHORIZATION, admin)
				.exchange();
	}

	private MvcTestResult get(String uri) {
		return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, admin).exchange();
	}

}
