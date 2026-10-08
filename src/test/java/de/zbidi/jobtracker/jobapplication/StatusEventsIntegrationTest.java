package de.zbidi.jobtracker.jobapplication;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import de.zbidi.jobtracker.ApiAuth;
import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.TopicProbe;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.kafka.autoconfigure.KafkaConnectionDetails;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionTemplate;

import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/**
 * The whole path with a real broker and database: status change → commit → Kafka → listener → status_history.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StatusEventsIntegrationTest {

	private static final String TOPIC = "application-status";
	private static final String DLT = "application-status.DLT";
	private static final Duration EVENTUALLY = Duration.ofSeconds(15);

	@Autowired
	JobApplicationService service;

	@Autowired
	StatusHistoryRepository statusHistoryRepository;

	@Autowired
	CompanyRepository companyRepository;

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	KafkaTemplate<String, Object> kafkaTemplate;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	KafkaConnectionDetails kafka;

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	private TopicProbe probe;
	private Long ownerId;
	private Long applicationId;

	@BeforeEach
	void setUp() {
		probe = new TopicProbe(String.join(",", kafka.getBootstrapServers()));
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ownerId = appUserRepository.save(new AppUser("events-" + suffix + "@example.com", "$2a$10$hash", Role.USER)).getId();
		Company company = companyRepository.save(new Company("Events " + suffix, null, null));
		applicationId = service.create(ownerId, new NewJobApplication(company.getId(), null, "Dev " + suffix, null)).id();
	}

	@Test
	void statusChangeEventuallyWritesHistory() {
		change(Status.APPLIED);

		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(service.history(ownerId, applicationId))
				.extracting(StatusHistoryResponse::fromStatus, StatusHistoryResponse::toStatus)
				.containsExactly(tuple(Status.SAVED, Status.APPLIED)));
	}

	/** The full path from the outside: HTTP with a real token → service → commit → Kafka → listener → table. */
	@Test
	void statusChangeViaTheApiEventuallyCreatesAHistoryRow() {
		String bearer = ApiAuth.registerNewUser(mvc, "events-api");
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		Integer companyId = JsonPath.read(ApiAuth.content(mvc.post().uri("/api/companies")
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Api Events %s\"}".formatted(suffix)).exchange()), "$.id");
		Integer id = JsonPath.read(ApiAuth.content(mvc.post().uri("/api/applications")
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"companyId\": %d, \"position\": \"Dev %s\"}".formatted(companyId, suffix)).exchange()), "$.id");

		MvcTestResult patch = mvc.patch().uri("/api/applications/{id}/status", id)
				.header(HttpHeaders.AUTHORIZATION, bearer).contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\": \"APPLIED\", \"version\": 0}").exchange();
		assertThat(patch).hasStatus(HttpStatus.OK);

		// written asynchronously by the Kafka listener: check the table itself, a few seconds at most
		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(jdbc.queryForList(
				"SELECT from_status, to_status FROM status_history WHERE job_application_id = ?", id))
				.singleElement()
				.satisfies(row -> {
					assertThat(row.get("from_status")).isEqualTo("SAVED");
					assertThat(row.get("to_status")).isEqualTo("APPLIED");
				}));
	}

	@Test
	void eventIsPublishedWithApplicationIdAsKeyAndPlainJson() {
		change(Status.APPLIED);

		TopicProbe.Message message = probe.awaitRecord(TOPIC, m -> String.valueOf(applicationId).equals(m.key()), EVENTUALLY);

		assertThat(message.value())
				.contains("\"applicationId\":" + applicationId, "\"ownerId\":" + ownerId)
				.contains("\"from\":\"SAVED\"", "\"to\":\"APPLIED\"", "\"eventId\":\"");
		assertThat(message.headers()).doesNotContainKey("__TypeId__");
	}

	@Test
	void eventsOfOneApplicationStayInOrder() {
		change(Status.APPLIED);
		change(Status.INTERVIEW);
		change(Status.OFFER);

		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(service.history(ownerId, applicationId))
				.extracting(StatusHistoryResponse::toStatus)
				.containsExactly(Status.APPLIED, Status.INTERVIEW, Status.OFFER));
	}

	@Test
	void rolledBackChangePublishesNothing() {
		tx.executeWithoutResult(status -> {
			change(Status.APPLIED);
			status.setRollbackOnly(); // e.g. a later step in the same transaction failed
		});

		// AFTER_COMMIT never fires for a rollback: nothing on the topic, no history, status unchanged
		assertThat(probe.recordsWithin(TOPIC, m -> String.valueOf(applicationId).equals(m.key()), Duration.ofSeconds(3)))
				.isEmpty();
		assertThat(service.get(ownerId, applicationId).status()).isEqualTo(Status.SAVED);
		assertThat(service.history(ownerId, applicationId)).isEmpty();
	}

	@Test
	void sameEventDeliveredTwiceWritesOneRow() {
		StatusChangedEvent event = new StatusChangedEvent(UUID.randomUUID(), applicationId, ownerId,
				Status.SAVED, Status.APPLIED, Instant.now());

		// Kafka guarantees "at least once": a redelivery looks exactly like this
		kafkaTemplate.send(TOPIC, String.valueOf(applicationId), event);
		kafkaTemplate.send(TOPIC, String.valueOf(applicationId), event);

		await().atMost(EVENTUALLY).until(() -> statusHistoryRepository.existsByEventId(event.eventId()));
		// give the second copy time to be processed too, then check there is still exactly one row
		await().during(Duration.ofSeconds(2)).atMost(Duration.ofSeconds(5)).untilAsserted(() ->
				assertThat(statusHistoryRepository.findByJobApplicationIdOrderByChangedAtAscIdAsc(applicationId)).hasSize(1));
	}

	@Test
	void poisonMessageGoesStraightToDltWithoutBlockingThePartition() {
		String key = String.valueOf(applicationId);
		sendRaw(key, "this is not json");

		TopicProbe.Message deadLetter = probe.awaitRecord(DLT, m -> key.equals(m.key()), EVENTUALLY);
		assertThat(deadLetter.value()).isEqualTo("this is not json");
		assertThat(deadLetter.headers()).containsKey("kafka_dlt-exception-message");

		// same key => same partition: the next good event behind the poison message is still processed
		change(Status.APPLIED);
		await().atMost(EVENTUALLY).untilAsserted(() -> assertThat(service.history(ownerId, applicationId)).hasSize(1));
	}

	private void change(Status status) {
		service.changeStatus(ownerId, applicationId, status, service.get(ownerId, applicationId).version());
	}

	/** Bypasses the app's serializer, like a misbehaving producer would. */
	private void sendRaw(String key, String value) {
		try (KafkaProducer<String, String> producer = new KafkaProducer<>(Map.ofEntries(
				entry(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, String.join(",", kafka.getBootstrapServers())),
				entry(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class),
				entry(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)))) {
			producer.send(new ProducerRecord<>(TOPIC, key, value));
		}
	}

}
