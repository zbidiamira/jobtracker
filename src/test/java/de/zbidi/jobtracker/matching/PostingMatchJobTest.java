package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.analysis.AiRejectedException;
import de.zbidi.jobtracker.analysis.AiUnavailableException;
import de.zbidi.jobtracker.analysis.PostingAnalysis;
import de.zbidi.jobtracker.analysis.PostingAnalyzer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willReturn;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * postingMatchJob with the analyzer mocked (no model). The database is shared with other test classes, so
 * assertions look at this test's own users and postings.
 */
@SpringBatchTest
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "jobtracker.matching.max-per-run=1000")
class PostingMatchJobTest {

	static final PostingAnalysis GOOD = new PostingAnalysis("Java Developer", List.of("Java"), List.of("Kafka"),
			List.of("Java"), List.of("Kafka"), 82, "Strong match.");

	@Autowired
	JobOperator jobOperator;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	Job postingMatchJob;

	@Autowired
	JdbcTemplate jdbc;

	@MockitoBean
	PostingAnalyzer analyzer;

	long alice;
	long noSkills;
	String marker;

	@BeforeEach
	void setUp() {
		marker = UUID.randomUUID().toString().substring(0, 8);
		alice = user(true);
		noSkills = user(false);
		given(analyzer.analyze(anyLong(), anyString())).willReturn(GOOD);
	}

	@AfterEach
	void removeJobExecutions() {
		new JobRepositoryTestUtils(jobRepository).removeJobExecutions();
	}

	public StepExecution getStepExecution() {
		return MetaDataInstanceFactory.createStepExecution();
	}

	public JobExecution getJobExecution() {
		return MetaDataInstanceFactory.createJobExecution();
	}

	@Test
	void analyzesEveryPostingForEveryUserWithSkillsAndStoresTheResult() throws Exception {
		long posting = posting("Java Developer", "We build Spring Boot services.");

		JobExecution execution = run();

		assertThat(execution.getStatus()).isEqualTo(BatchStatus.COMPLETED);
		// the AI gets title, company, city and the full description
		verify(analyzer).analyze(eq(alice), argThat(text -> text.contains("Java Developer " + marker)
				&& text.contains("ACME") && text.contains("Berlin") && text.contains("We build Spring Boot services.")));
		assertThat(jdbc.queryForMap("""
				SELECT status, match_score, analysis->>'summary' AS summary FROM posting_match
				WHERE posting_id = ? AND user_id = ?
				""", posting, alice))
				.containsEntry("status", "MATCHED")
				.containsEntry("match_score", 82)
				.containsEntry("summary", "Strong match.");
	}

	@Test
	void usersWithoutSkillsAndPostingsWithoutDescriptionAreLeftOut() throws Exception {
		posting("With text", "Java");
		posting("No text", null);

		run();

		verify(analyzer, never()).analyze(eq(noSkills), anyString());
		verify(analyzer, never()).analyze(anyLong(), contains("No text " + marker));
	}

	@Test
	void eachPairIsAnalyzedOnlyOnce() throws Exception {
		posting("Once", "Java");
		run();
		clearInvocations(analyzer);

		run();

		verify(analyzer, never()).analyze(eq(alice), contains("Once " + marker));
	}

	@Test
	void unusableAnswerIsStoredAsFailedAndNotRetried() throws Exception {
		long posting = posting("Broken", "Java");
		given(analyzer.analyze(eq(alice), contains("Broken " + marker)))
				.willThrow(new AiRejectedException("unusable answer", null));

		JobExecution first = run();

		assertThat(first.getStatus()).as("one bad posting doesn't stop the run").isEqualTo(BatchStatus.COMPLETED);
		assertThat(jdbc.queryForMap("SELECT status, match_score, error FROM posting_match WHERE posting_id = ? AND user_id = ?",
				posting, alice))
				.containsEntry("status", "FAILED")
				.containsEntry("match_score", null)
				.containsEntry("error", "unusable answer");
		clearInvocations(analyzer);
		run();
		verify(analyzer, never()).analyze(eq(alice), contains("Broken " + marker));
	}

	@Test
	void unavailableModelStopsTheRunAndTheNextRunContinues() throws Exception {
		long posting = posting("Later", "Java");
		given(analyzer.analyze(anyLong(), anyString())).willThrow(new AiUnavailableException("model down"));

		JobExecution failed = run();

		assertThat(failed.getStatus()).isEqualTo(BatchStatus.FAILED);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM posting_match WHERE posting_id = ?", Integer.class, posting))
				.as("nothing stored: the pair stays open").isZero();
		long callsBeforeFailing = mockingDetails(analyzer).getInvocations().stream()
				.filter(invocation -> invocation.getMethod().getName().equals("analyze")).count();
		assertThat(callsBeforeFailing).as("stops at the first unavailable answer").isEqualTo(1);

		// willReturn(..).given(..): given(analyzer.analyze(..)) would call the mock, which still throws
		willReturn(GOOD).given(analyzer).analyze(anyLong(), anyString());
		assertThat(run().getStatus()).isEqualTo(BatchStatus.COMPLETED);
		verify(analyzer, atLeastOnce()).analyze(eq(alice), contains("Later " + marker));
	}

	private JobExecution run() throws Exception {
		JobExecution started = jobOperator.start(postingMatchJob, PostingMatchJobConfig.parameters(Instant.now()));
		return await().atMost(Duration.ofMinutes(2)).until(() -> jobRepository.getJobExecution(started.getId()),
				execution -> !execution.isRunning() && execution.getStatus() != BatchStatus.STARTING);
	}

	private long user(boolean withSkills) {
		long id = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'USER') RETURNING id",
				Long.class, "matchjob-" + UUID.randomUUID() + "@example.com");
		if (withSkills) {
			jdbc.update("INSERT INTO skill (user_id, name, years, level) VALUES (?, 'Java', 5, 'EXPERT')", id);
		}
		return id;
	}

	private long posting(String title, String description) {
		return jdbc.queryForObject("""
				INSERT INTO job_posting (title, company_name, city, url, source, imported_at, description)
				VALUES (?, 'ACME', 'Berlin', ?, 'test', ?, ?) RETURNING id
				""", Long.class, title + " " + marker, "https://matchjob.example/" + UUID.randomUUID(),
				Timestamp.from(Instant.now()), description);
	}

}
