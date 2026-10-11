package de.zbidi.jobtracker.posting;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import de.zbidi.jobtracker.TestcontainersConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobOperatorTestUtils;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * postingImportJob against real PostgreSQL (job repository and job_posting). The app's JobOperator is async,
 * so every run is awaited until it has finished.
 */
@SpringBatchTest
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostingImportJobTest {

	private static final String HEADER = "title,company,city,url";
	private static final LocalDate RUN_DATE = LocalDate.of(2026, 10, 11);
	private static final String FIXTURE_URLS = "https://fixtures.example/";

	@Autowired
	JobOperator jobOperator;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	Job postingImportJob;

	@Autowired
	PostingItemProcessor postingProcessor;

	@Autowired
	JdbcTemplate jdbc;

	@TempDir
	Path dir;

	JobOperatorTestUtils jobs;
	JobRepositoryTestUtils repositoryUtils;

	/** Unique per test: job_posting is shared by all tests on the same database. */
	String urlPrefix;

	@BeforeEach
	void setUp() {
		jobs = new JobOperatorTestUtils(jobOperator, jobRepository);
		jobs.setJob(postingImportJob);
		repositoryUtils = new JobRepositoryTestUtils(jobRepository);
		urlPrefix = "https://jobs.example/" + UUID.randomUUID() + "/";
		// the fixtures have fixed urls and the database is shared: remove what an earlier run imported
		jdbc.update("DELETE FROM job_posting WHERE url LIKE ?", FIXTURE_URLS + "%");
	}

	@AfterEach
	void removeJobExecutions() {
		repositoryUtils.removeJobExecutions();
	}

	/**
	 * @SpringBatchTest opens a step scope around every test method with this execution, so step-scoped beans can be
	 * used directly in a test. Its listeners pick the first method returning a StepExecution/JobExecution unless
	 * one is named like this.
	 */
	public StepExecution getStepExecution() {
		return MetaDataInstanceFactory.createStepExecution(parameters(Path.of("scope-test.csv")));
	}

	public JobExecution getJobExecution() {
		return MetaDataInstanceFactory.createJobExecution();
	}

	@Test
	void stepScopedProcessorGetsItsSourceFromTheJobParameters() {
		JobPosting posting = postingProcessor.process(new PostingCsvLine("Dev", "ACME", "Berlin", urlPrefix + "s"));

		assertThat(posting.source()).isEqualTo("csv:test.csv");
	}

	// --- fixtures in src/test/resources/postings ---

	@Test
	void validCsvImportsAll120RowsInThreeChunks() throws Exception {
		JobExecution execution = run(fixture("valid.csv"));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		StepExecution step = step(execution);
		assertThat(step.getReadCount()).isEqualTo(120);
		assertThat(step.getWriteCount()).isEqualTo(120);
		assertThat(step.getCommitCount()).as("chunks of 50: 50 + 50 + 20").isEqualTo(3);
		assertThat(fixtureRows("valid")).isEqualTo(120);
	}

	@Test
	void invalidCsvSkipsTheThreeBrokenLines() throws Exception {
		JobExecution execution = run(fixture("invalid.csv"));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		StepExecution step = step(execution);
		assertThat(step.getSkipCount()).isEqualTo(3);
		assertThat(step.getReadSkipCount()).as("wrong column count").isEqualTo(2);
		assertThat(step.getProcessSkipCount()).as("invalid url").isEqualTo(1);
		assertThat(step.getWriteCount()).isEqualTo(7);
		assertThat(fixtureRows("invalid")).isEqualTo(7);
	}

	@Test
	void duplicatesCsvFiltersRepeatedAndKnownUrls() throws Exception {
		insertExisting(FIXTURE_URLS + "duplicates/known");

		JobExecution execution = run(fixture("duplicates.csv"));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		StepExecution step = step(execution);
		assertThat(step.getFilterCount()).as("a and b repeated in the file, known already imported").isEqualTo(3);
		assertThat(step.getSkipCount()).isZero();
		assertThat(step.getWriteCount()).isEqualTo(3);
		assertThat(fixtureRows("duplicates")).isEqualTo(4);
	}

	// --- generated files: exact counts, restart ---

	@Test
	void storesTrimmedValuesWithSource() throws Exception {
		Path file = csv(List.of(" Java Developer , ACME ,  , " + urlPrefix + "1 "));

		run(file);

		assertThat(jdbc.queryForMap("SELECT title, company_name, city, source FROM job_posting WHERE url = ?",
				urlPrefix + "1"))
				.containsEntry("title", "Java Developer")
				.containsEntry("company_name", "ACME")
				.containsEntry("city", null)
				.containsEntry("source", "csv:test.csv");
	}

	@Test
	void tenSkippedLinesAreStillAccepted() throws Exception {
		List<String> lines = new ArrayList<>(validLines(0, 5));
		lines.addAll(badLines(10));

		JobExecution execution = run(csv(lines));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(step(execution).getSkipCount()).isEqualTo(10);
	}

	@Test
	void elevenSkippedLinesFailTheJob() throws Exception {
		List<String> lines = new ArrayList<>(validLines(0, 5));
		lines.addAll(badLines(11));

		JobExecution execution = run(csv(lines));

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.FAILED);
	}

	@Test
	void restartContinuesAfterTheLastCommittedChunk() throws Exception {
		// lines 1-100 fill two chunks; the 11 bad lines exceed the skip limit in the third chunk
		List<String> lines = new ArrayList<>(validLines(0, 100));
		lines.addAll(badLines(11));
		lines.addAll(validLines(111, 131));
		Path file = csv(lines);

		JobExecution failed = run(file);

		assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(importedCount()).isEqualTo(100);

		// the admin fixes the file, then restarts the same job instance
		Files.write(file, withHeader(validLines(0, 131)));
		JobExecution restarted = awaitFinished(jobOperator.restart(failed));

		assertThat(restarted.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(restarted.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
		StepExecution step = step(restarted);
		assertThat(step.getReadCount()).as("only the lines after the committed chunks").isEqualTo(31);
		assertThat(step.getWriteCount()).isEqualTo(31);
		assertThat(importedCount()).isEqualTo(131);
	}

	@Test
	void sameFileAndRunDateCannotBeImportedTwice() throws Exception {
		Path file = csv(validLines(0, 3));
		assertThat(run(file).getStatus()).isEqualTo(BatchStatus.COMPLETED);

		assertThatThrownBy(() -> jobOperator.start(postingImportJob, parameters(file)))
				.isInstanceOf(JobInstanceAlreadyCompleteException.class);
	}

	private JobExecution run(Path file) throws Exception {
		return awaitFinished(jobs.startJob(parameters(file)));
	}

	private static JobParameters parameters(Path file) {
		return PostingImportJobConfig.parameters(file, RUN_DATE, "csv:test.csv");
	}

	private JobExecution awaitFinished(JobExecution started) {
		return await().atMost(Duration.ofSeconds(30)).until(
				() -> jobRepository.getJobExecution(started.getId()),
				execution -> !execution.isRunning() && execution.getStatus() != BatchStatus.STARTING);
	}

	private StepExecution step(JobExecution execution) {
		assertThat(execution.getStepExecutions()).hasSize(1);
		return execution.getStepExecutions().iterator().next();
	}

	private List<String> validLines(int from, int to) {
		List<String> lines = new ArrayList<>();
		for (int i = from; i < to; i++) {
			lines.add("Developer " + i + ",Company " + i + ",Berlin," + urlPrefix + i);
		}
		return lines;
	}

	private static List<String> badLines(int count) {
		List<String> lines = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			lines.add("broken line " + i);
		}
		return lines;
	}

	private Path csv(List<String> lines) throws IOException {
		return Files.write(dir.resolve(UUID.randomUUID() + ".csv"), withHeader(lines));
	}

	private static List<String> withHeader(List<String> lines) {
		List<String> all = new ArrayList<>();
		all.add(HEADER);
		all.addAll(lines);
		return all;
	}

	/** The job reads a file path; test resources are plain files under target/test-classes. */
	private static Path fixture(String name) throws IOException {
		return new ClassPathResource("postings/" + name).getFile().toPath();
	}

	private int fixtureRows(String fixture) {
		return jdbc.queryForObject("SELECT count(*) FROM job_posting WHERE url LIKE ?", Integer.class,
				FIXTURE_URLS + fixture + "/%");
	}

	private int importedCount() {
		return jdbc.queryForObject("SELECT count(*) FROM job_posting WHERE url LIKE ?", Integer.class, urlPrefix + "%");
	}

	private void insertExisting(String url) {
		jdbc.update("""
				INSERT INTO job_posting (title, company_name, city, url, source, imported_at)
				VALUES ('Old', 'ACME', 'Berlin', ?, 'earlier', ?)
				""", url, Timestamp.from(Instant.parse("2026-10-01T08:00:00Z")));
	}

}
