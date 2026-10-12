package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.TopicProbe;
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
import org.springframework.batch.test.JobRepositoryTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;

/**
 * The whole level-3 path with real Kafka and database (only the AI is mocked): match job → score ≥ 75 →
 * GoodMatchFound on posting-matches → listener → SAVED application.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = {"jobtracker.matching.max-per-run=1000", "jobtracker.matching.notify-score=75"})
class GoodMatchIntegrationTest {

	static final Duration EVENTUALLY = Duration.ofSeconds(20);

	@Autowired
	JobOperator jobOperator;

	@Autowired
	JobRepository jobRepository;

	@Autowired
	Job postingMatchJob;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	KafkaConnectionDetails kafka;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@MockitoBean
	PostingAnalyzer analyzer;

	long userId;
	String marker;

	@BeforeEach
	void setUp() {
		marker = UUID.randomUUID().toString().substring(0, 8);
		userId = jdbc.queryForObject("INSERT INTO app_user (email, password_hash, role) VALUES (?, 'x', 'USER') RETURNING id",
				Long.class, "goodmatch-" + marker + "@example.com");
		jdbc.update("INSERT INTO skill (user_id, name, years, level) VALUES (?, 'Java', 5, 'EXPERT')", userId);
		given(analyzer.analyze(anyLong(), anyString())).willReturn(analysis(40));
	}

	@AfterEach
	void removeJobExecutions() {
		new JobRepositoryTestUtils(jobRepository).removeJobExecutions();
	}

	@Test
	void goodMatchIsAnnouncedAndBecomesASavedApplication() throws Exception {
		long good = posting("Great Job", "Great Corp " + marker);
		long weak = posting("Weak Job", "Weak Corp " + marker);
		given(analyzer.analyze(anyLong(), contains("Great Job " + marker))).willReturn(analysis(82));
		given(analyzer.analyze(anyLong(), contains("Weak Job " + marker))).willReturn(analysis(60));

		assertThat(runMatchJob().getStatus()).isEqualTo(BatchStatus.COMPLETED);

		// announced on Kafka, key = user id, plain JSON (other users with skills in the shared database get their
		// own events for the same posting)
		TopicProbe.Message message = new TopicProbe(String.join(",", kafka.getBootstrapServers())).awaitRecord(
				MatchEvents.TOPIC, m -> m.value().contains("\"postingId\":" + good)
						&& m.value().contains("\"userId\":" + userId + ","), EVENTUALLY);
		assertThat(message.key()).isEqualTo(String.valueOf(userId));
		assertThat((Integer) JsonPath.read(message.value(), "$.matchScore")).isEqualTo(82);
		assertThat(message.headers()).doesNotContainKey("__TypeId__");

		// and turned into a SAVED application for that user, with the posting's link
		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(jdbc.queryForList("""
				SELECT a.status FROM job_application a JOIN job_posting p ON p.url = a.job_url
				WHERE a.owner_id = ? AND p.id = ?
				""", String.class, userId, good)).containsExactly("SAVED"));
		// score 60 is below the threshold: no application
		assertThat(jdbc.queryForObject("""
				SELECT count(*) FROM job_application a JOIN job_posting p ON p.url = a.job_url
				WHERE a.owner_id = ? AND p.id = ?
				""", Integer.class, userId, weak)).isZero();
	}

	@Test
	void theSameEventTwiceCreatesOneApplication() {
		long posting = posting("Twice Job", "Twice Corp " + marker);
		String url = jdbc.queryForObject("SELECT url FROM job_posting WHERE id = ?", String.class, posting);
		GoodMatchFound event = new GoodMatchFound(UUID.randomUUID(), userId, posting, "Twice Job " + marker,
				"Twice Corp " + marker, url, 90, Instant.now());

		kafkaTemplate.send(MatchEvents.TOPIC, String.valueOf(userId), event);
		kafkaTemplate.send(MatchEvents.TOPIC, String.valueOf(userId), event);

		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(jdbc.queryForObject(
				"SELECT count(*) FROM job_application WHERE owner_id = ? AND job_url = ?", Integer.class, userId, url))
				.isEqualTo(1));
		// still one after both deliveries had time to arrive
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).until(() -> jdbc.queryForObject(
				"SELECT count(*) FROM job_application WHERE owner_id = ? AND job_url = ?", Integer.class, userId,
				url) == 1);
	}

	private JobExecution runMatchJob() throws Exception {
		JobExecution started = jobOperator.start(postingMatchJob, PostingMatchJobConfig.parameters(Instant.now()));
		return await().atMost(Duration.ofMinutes(2)).until(() -> jobRepository.getJobExecution(started.getId()),
				execution -> !execution.isRunning() && execution.getStatus() != BatchStatus.STARTING);
	}

	private long posting(String title, String company) {
		return jdbc.queryForObject("""
				INSERT INTO job_posting (title, company_name, city, url, source, imported_at, description)
				VALUES (?, ?, 'Berlin', ?, 'test', ?, 'Java and Spring') RETURNING id
				""", Long.class, title + " " + marker, company, "https://goodmatch.example/" + UUID.randomUUID(),
				Timestamp.from(Instant.now()));
	}

	private static PostingAnalysis analysis(int score) {
		return new PostingAnalysis("Java Developer", List.of("Java"), List.of(), List.of("Java"), List.of(), score,
				"summary");
	}

}
