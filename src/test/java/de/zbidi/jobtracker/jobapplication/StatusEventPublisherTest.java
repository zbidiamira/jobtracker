package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StatusEventPublisherTest {

	private static final StatusChangedEvent EVENT = new StatusChangedEvent(
			UUID.randomUUID(), 5L, 42L, Status.SAVED, Status.APPLIED, Instant.parse("2026-10-07T10:00:00Z"));

	@Mock
	KafkaTemplate<String, Object> kafkaTemplate;

	@InjectMocks
	StatusEventPublisher publisher;

	@Test
	void sendsToApplicationStatusTopicWithApplicationIdAsKey() {
		given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());

		publisher.publish(EVENT);

		// same key => same partition => the events of one application are consumed in order
		verify(kafkaTemplate).send("application-status", "5", EVENT);
	}

	@Test
	void failedSendDoesNotThrowIntoTheAlreadyCommittedRequest() {
		given(kafkaTemplate.send(anyString(), anyString(), any()))
				.willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

		// the database commit already happened; the failure is logged, the HTTP request still succeeds
		assertThatNoException().isThrownBy(() -> publisher.publish(EVENT));
	}

}
