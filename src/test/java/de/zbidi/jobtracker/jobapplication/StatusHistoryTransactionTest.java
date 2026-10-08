package de.zbidi.jobtracker.jobapplication;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.TopicProbe;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Week 3 wrote status and history in one transaction. Since Week 5 the history follows <b>after</b> the commit,
 * through Kafka: the status change is the source of truth, the history catches up (eventually consistent), and a
 * failure while writing the history no longer undoes the status change (it ends up in the dead-letter topic).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StatusHistoryTransactionTest {

	private static final Duration EVENTUALLY = Duration.ofSeconds(15);

	@Autowired
	JobApplicationService service;

	@Autowired
	JobApplicationRepository jobApplicationRepository;

	@Autowired
	CompanyRepository companyRepository;

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	KafkaConnectionDetails kafka;

	@MockitoSpyBean
	StatusHistoryRepository statusHistoryRepository;

	private TopicProbe probe;
	private Long ownerId;
	private Long applicationId;

	@BeforeEach
	void setUp() {
		probe = new TopicProbe(String.join(",", kafka.getBootstrapServers()));
		// committed data, not rolled back: unique names per test
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ownerId = appUserRepository.save(new AppUser("tx-" + suffix + "@example.com", "$2a$10$hash", Role.USER)).getId();
		Company company = companyRepository.save(new Company("Tx " + suffix, null, null));
		applicationId = service.create(ownerId, new NewJobApplication(company.getId(), null, "Dev " + suffix, null)).id();
	}

	@Test
	void statusIsCommittedImmediatelyAndHistoryFollows() {
		change(applicationId, Status.APPLIED);

		assertThat(jobApplicationRepository.findById(applicationId).orElseThrow().getStatus()).isEqualTo(Status.APPLIED);
		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(service.history(ownerId, applicationId))
				.extracting(StatusHistoryResponse::fromStatus, StatusHistoryResponse::toStatus)
				.containsExactly(tuple(Status.SAVED, Status.APPLIED)));
	}

	/** Replaces Week 3's "failing history insert rolls back the status change", which is no longer true by design. */
	@Test
	void statusChangeIsKeptWhenHistoryWritingFails() {
		doThrow(new IllegalStateException("simulated failure while writing history"))
				.when(statusHistoryRepository).saveAndFlush(argThat(this::isForThisApplication));

		change(applicationId, Status.APPLIED);

		// the event is tried 1 + 3 times, then parked in the dead-letter topic with the reason
		TopicProbe.Message deadLetter = probe.awaitRecord("application-status.DLT",
				m -> String.valueOf(applicationId).equals(m.key()), EVENTUALLY);
		assertThat(deadLetter.headers().get("kafka_dlt-exception-message")).contains("simulated");
		verify(statusHistoryRepository, times(4)).saveAndFlush(argThat(this::isForThisApplication));
		// the status change itself was committed before the event was sent and stays
		assertThat(jobApplicationRepository.findById(applicationId).orElseThrow().getStatus()).isEqualTo(Status.APPLIED);
		assertThat(service.history(ownerId, applicationId)).isEmpty();
	}

	@Test
	void invalidTransitionPublishesNoEvent() {
		change(applicationId, Status.APPLIED);
		change(applicationId, Status.REJECTED);

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> change(applicationId, Status.INTERVIEW));

		// exactly the two successful changes reach the topic and the history
		assertThat(probe.recordsWithin("application-status", m -> String.valueOf(applicationId).equals(m.key()),
				Duration.ofSeconds(3))).hasSize(2);
		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(service.history(ownerId, applicationId)).hasSize(2));
	}

	@Test
	void historyIsReturnedOldestFirst() {
		change(applicationId, Status.APPLIED);
		change(applicationId, Status.INTERVIEW);

		await().atMost(EVENTUALLY).untilAsserted(() -> {
			List<StatusHistoryResponse> history = service.history(ownerId, applicationId);
			assertThat(history).extracting(StatusHistoryResponse::toStatus).containsExactly(Status.APPLIED, Status.INTERVIEW);
			assertThat(history).extracting(StatusHistoryResponse::changedAt).isSorted();
		});
	}

	private boolean isForThisApplication(StatusHistory history) {
		return history != null && applicationId.equals(history.getJobApplication().getId());
	}

	/** Like a real client: send the version it last read. */
	private JobApplicationResponse change(Long id, Status status) {
		return service.changeStatus(ownerId, id, status, service.get(ownerId, id).version());
	}

}
