package de.zbidi.jobtracker.posting;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Restart after an infrastructure failure: the real writer, wrapped in a spy that fails exactly once, on chunk 2.
 * Own class because the spy needs its own Spring context.
 */
@SpringBatchTest
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostingImportRestartTest {

	private static final int LINES = 120; // chunks of 50: 50 + 50 + 20

	@Autowired
	JobOperator jobOperator;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	Job postingImportJob;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoSpyBean(name = "postingWriter")
	JdbcBatchItemWriter<JobPosting> writer;

	@TempDir
	Path dir;

	String urlPrefix;

	@BeforeEach
	void setUp() throws Exception {
		urlPrefix = "https://restart.example/" + UUID.randomUUID() + "/";
		// 1st write (chunk 1) works, 2nd write (chunk 2) fails, every later write works again
		doCallRealMethod()
				.doThrow(new IllegalStateException("database unavailable"))
				.doCallRealMethod()
				.when(writer).write(any());
	}

	@AfterEach
	void removeJobExecutions() {
		new JobRepositoryTestUtils(jobRepository).removeJobExecutions();
	}

	/** See PostingImportJobTest: keeps @SpringBatchTest from treating other methods as scope factories. */
	public StepExecution getStepExecution() {
		return MetaDataInstanceFactory.createStepExecution();
	}

	public JobExecution getJobExecution() {
		return MetaDataInstanceFactory.createJobExecution();
	}

	@Test
	void writerFailureOnChunk2FailsTheJobAndARunWithTheSameParametersCompletesIt() throws Exception {
		JobParameters parameters = PostingImportJobConfig.parameters(csv(), LocalDate.of(2026, 10, 11), "csv:restart.csv");

		// --- run 1: chunk 1 committed, chunk 2 rolled back ---
		JobExecution failed = awaitFinished(jobOperator.start(postingImportJob, parameters));

		assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(importedCount()).isEqualTo(50);
		StepExecution failedStep = storedStep(failed);
		// exceptions aren't stored, only their text (BATCH_STEP_EXECUTION.EXIT_MESSAGE)
		assertThat(failedStep.getExitStatus().getExitDescription()).contains("database unavailable");
		// the reader's position, saved in BATCH_STEP_EXECUTION_CONTEXT together with chunk 1's commit
		assertThat(failedStep.getExecutionContext().getInt("postingReader.read.count")).isEqualTo(50);
		assertThat(failedStep.getCommitCount()).isEqualTo(1);
		assertThat(failedStep.getRollbackCount()).isEqualTo(1);

		// --- run 2: same parameters = same JobInstance, so Spring Batch restarts it ---
		JobExecution completed = awaitFinished(jobOperator.start(postingImportJob, parameters));

		assertThat(completed.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		assertThat(completed.getJobInstanceId()).isEqualTo(failed.getJobInstanceId());
		assertThat(completed.getId()).isNotEqualTo(failed.getId());

		// not imported twice: the reader resumed at line 51. A reader starting over would read 120 lines and the
		// processor would filter the 50 known urls; the table alone can't show that (duplicates are filtered and
		// ON CONFLICT DO NOTHING), the step counts can.
		StepExecution resumed = storedStep(completed);
		assertThat(resumed.getReadCount()).isEqualTo(70);
		assertThat(resumed.getFilterCount()).isZero();
		assertThat(resumed.getWriteCount()).isEqualTo(70);
		assertThat(importedCount()).isEqualTo(LINES);
		assertThat(jdbc.queryForObject("SELECT count(DISTINCT url) FROM job_posting WHERE url LIKE ?", Integer.class,
				urlPrefix + "%")).isEqualTo(LINES);

		// run 1: chunk 1 ok + chunk 2 failed; run 2: chunk 2 + chunk 3. Chunk 1 was never written again.
		verify(writer, times(4)).write(any());
	}

	private JobExecution awaitFinished(JobExecution started) {
		return await().atMost(Duration.ofSeconds(30)).until(
				() -> jobRepository.getJobExecution(started.getId()),
				execution -> !execution.isRunning() && execution.getStatus() != BatchStatus.STARTING);
	}

	/** The step execution as stored, including its ExecutionContext. */
	private StepExecution storedStep(JobExecution execution) {
		assertThat(execution.getStepExecutions()).hasSize(1);
		StepExecution step = execution.getStepExecutions().iterator().next();
		return jobRepository.getStepExecution(step.getId());
	}

	private Path csv() throws Exception {
		List<String> lines = new ArrayList<>();
		lines.add("title,company,city,url,description");
		for (int i = 1; i <= LINES; i++) {
			lines.add("Developer " + i + ",Company " + i + ",Berlin," + urlPrefix + i + ",Build Java services " + i);
		}
		return Files.write(dir.resolve("restart.csv"), lines);
	}

	private int importedCount() {
		return jdbc.queryForObject("SELECT count(*) FROM job_posting WHERE url LIKE ?", Integer.class, urlPrefix + "%");
	}

}
