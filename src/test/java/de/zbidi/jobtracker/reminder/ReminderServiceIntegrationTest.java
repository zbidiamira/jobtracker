package de.zbidi.jobtracker.reminder;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.TopicProbe;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.jobapplication.Status;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.config.ScheduledTaskHolder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real database and broker. "Unchanged for n days" is set up by backdating updated_at in SQL; the scheduler itself
 * is switched off in tests (TestcontainersConfiguration), the service is called directly.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReminderServiceIntegrationTest {

	private static final String TOPIC = "application-reminders";
	private static final Duration EVENTUALLY = Duration.ofSeconds(15);

	@Autowired
	ReminderService reminderService;

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	CompanyRepository companyRepository;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	KafkaConnectionDetails kafka;

	@Autowired
	ScheduledTaskHolder scheduledTasks;

	TopicProbe probe;
	Long ownerId;
	Long companyId;

	@BeforeEach
	void setUp() {
		probe = new TopicProbe(String.join(",", kafka.getBootstrapServers()));
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ownerId = appUserRepository.save(new AppUser("reminder-" + suffix + "@example.com", "$2a$10$hash", Role.USER))
				.getId();
		companyId = companyRepository.save(new Company("Reminder " + suffix, null, null)).getId();
	}

	/** TestcontainersConfiguration sets the cron to "-": the scheduler exists but has no task. */
	@Test
	void schedulerIsOffInTests() {
		assertThat(scheduledTasks.getScheduledTasks())
				.extracting(task -> task.getTask().getRunnable().toString())
				.noneMatch(runnable -> runnable.contains(ReminderScheduler.class.getName()));
	}

	@Test
	void appliedAndUnchangedFor15DaysSendsOneReminderAsJson() {
		long id = application(Status.APPLIED, daysAgo(15));

		reminderService.sendDueReminders();

		TopicProbe.Message message = probe.awaitRecord(TOPIC, m -> m.key().equals(String.valueOf(id)), EVENTUALLY);
		assertThat((Integer) JsonPath.read(message.value(), "$.applicationId")).isEqualTo((int) id);
		assertThat((Integer) JsonPath.read(message.value(), "$.ownerId")).isEqualTo(ownerId.intValue());
		assertThat((String) JsonPath.read(message.value(), "$.eventId")).isNotBlank();
		assertThat(message.headers()).doesNotContainKey("__TypeId__");
		assertThat(remindedAt(id)).isNotNull();
	}

	@Test
	void reminderIsSentOnlyOnce() {
		long id = application(Status.APPLIED, daysAgo(20));

		reminderService.sendDueReminders();
		probe.awaitRecord(TOPIC, m -> m.key().equals(String.valueOf(id)), EVENTUALLY);
		reminderService.sendDueReminders();

		assertThat(probe.recordsWithin(TOPIC, m -> m.key().equals(String.valueOf(id)), Duration.ofSeconds(3)))
				.hasSize(1);
	}

	@Test
	void appliedButChangedWithin14DaysIsNotReminded() {
		long id = application(Status.APPLIED, daysAgo(13));

		reminderService.sendDueReminders();

		assertThat(remindedAt(id)).isNull();
		assertThat(probe.recordsWithin(TOPIC, m -> m.key().equals(String.valueOf(id)), Duration.ofSeconds(3)))
				.isEmpty();
	}

	@Test
	void otherStatusesAreNotReminded() {
		long interview = application(Status.INTERVIEW, daysAgo(20));
		long saved = application(Status.SAVED, daysAgo(20));

		reminderService.sendDueReminders();

		assertThat(remindedAt(interview)).isNull();
		assertThat(remindedAt(saved)).isNull();
	}

	@Test
	void markingAsRemindedDoesNotCountAsAChange() {
		Instant lastChange = daysAgo(15);
		long id = application(Status.APPLIED, lastChange);

		reminderService.sendDueReminders();

		// no new updated_at (it would hide how long the application has been waiting) and no new version
		// (the user's next status change with the version they read must not get a 409)
		Map<String, Object> row = jdbc.queryForMap("SELECT updated_at, version FROM job_application WHERE id = ?", id);
		assertThat(((Timestamp) row.get("updated_at")).toInstant()).isEqualTo(lastChange);
		assertThat(row.get("version")).isEqualTo(0L);
		assertThat(remindedAt(id)).isNotNull();
	}

	private long application(Status status, Instant updatedAt) {
		return jdbc.queryForObject("""
				INSERT INTO job_application (owner_id, company_id, position, status, version, created_at, updated_at)
				VALUES (?, ?, ?, ?, 0, ?, ?) RETURNING id
				""", Long.class, ownerId, companyId, "Dev " + UUID.randomUUID(), status.name(),
				Timestamp.from(updatedAt), Timestamp.from(updatedAt));
	}

	private Timestamp remindedAt(long id) {
		return jdbc.queryForObject("SELECT reminded_at FROM job_application WHERE id = ?", Timestamp.class, id);
	}

	private static Instant daysAgo(int days) {
		return Instant.now().minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.MICROS);
	}

}
