package de.zbidi.jobtracker.jobapplication;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The event as it travels through Kafka, with the Jackson 3 based serializers (the Jackson 2 based
 * JsonSerializer/JsonDeserializer don't match Boot 4's Jackson 3).
 */
class StatusChangedEventJsonTest {

	private static final StatusChangedEvent EVENT = new StatusChangedEvent(
			UUID.fromString("0b9e5a3e-6c1f-4c8e-9d0a-5f1e2d3c4b5a"), 5L, 42L,
			Status.SAVED, Status.APPLIED, Instant.parse("2026-10-07T10:15:30.123456Z"));

	@Test
	void roundTripKeepsAllFields() {
		byte[] json = serializer().serialize("application-status", new RecordHeaders(), EVENT);

		assertThat(deserializer().deserialize("application-status", new RecordHeaders(), json)).isEqualTo(EVENT);
	}

	@Test
	void jsonIsReadableAndHasNoJavaTypeInfo() {
		RecordHeaders headers = new RecordHeaders();
		String json = new String(serializer().serialize("application-status", headers, EVENT), StandardCharsets.UTF_8);

		assertThat(json)
				.contains("\"eventId\":\"0b9e5a3e-6c1f-4c8e-9d0a-5f1e2d3c4b5a\"")
				.contains("\"from\":\"SAVED\"", "\"to\":\"APPLIED\"")
				.contains("\"changedAt\":\"2026-10-07T10:15:30.123456Z\"");
		// other consumers (not Java, not this codebase) must be able to read it: no __TypeId__ header
		assertThat(headers.toArray()).isEmpty();
	}

	@Test
	void deserializerNeedsNoTypeHeader() {
		byte[] json = """
				{"eventId":"0b9e5a3e-6c1f-4c8e-9d0a-5f1e2d3c4b5a","applicationId":5,"ownerId":42,
				 "from":"SAVED","to":"APPLIED","changedAt":"2026-10-07T10:15:30.123456Z"}
				""".getBytes(StandardCharsets.UTF_8);

		assertThat(deserializer().deserialize("application-status", new RecordHeaders(), json)).isEqualTo(EVENT);
	}

	private static JacksonJsonSerializer<StatusChangedEvent> serializer() {
		return new JacksonJsonSerializer<StatusChangedEvent>().noTypeInfo();
	}

	/** Configured like the consumer in application.yaml. */
	private static JacksonJsonDeserializer<StatusChangedEvent> deserializer() {
		JacksonJsonDeserializer<StatusChangedEvent> deserializer = new JacksonJsonDeserializer<>();
		deserializer.configure(Map.of(
				JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, StatusChangedEvent.class.getName(),
				JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false), false);
		return deserializer;
	}

}
