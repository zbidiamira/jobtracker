package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The AFTER_COMMIT timing in isolation: a minimal Spring context (no database, no broker) with a transaction
 * manager that holds no resources and a mocked KafkaTemplate. Exactly the publisher's contract: nothing while the
 * transaction runs, one send when it commits, nothing if it rolls back.
 */
@SpringJUnitConfig
class StatusEventPublisherAfterCommitTest {

	private static final StatusChangedEvent EVENT = new StatusChangedEvent(
			UUID.randomUUID(), 5L, 42L, Status.SAVED, Status.APPLIED, Instant.parse("2026-10-07T10:00:00Z"));

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@Import(StatusEventPublisher.class)
	static class Config {

		@Bean
		PlatformTransactionManager transactionManager() {
			return new ResourcelessTransactionManager();
		}

	}

	@Autowired
	ApplicationEventPublisher events;

	@Autowired
	PlatformTransactionManager transactionManager;

	@MockitoBean
	KafkaTemplate<String, Object> kafkaTemplate;

	private TransactionTemplate tx;

	@BeforeEach
	void setUp() {
		tx = new TransactionTemplate(transactionManager);
		given(kafkaTemplate.send(anyString(), anyString(), any())).willReturn(new CompletableFuture<>());
	}

	@Test
	void nothingIsSentWhileTheTransactionIsStillRunning() {
		tx.executeWithoutResult(status -> {
			events.publishEvent(EVENT);

			// the service has published the event, but the change isn't committed yet
			verifyNoInteractions(kafkaTemplate);
		});
	}

	@Test
	void sentToApplicationStatusWithApplicationIdAsKeyOnCommit() {
		tx.executeWithoutResult(status -> events.publishEvent(EVENT));

		verify(kafkaTemplate).send("application-status", "5", EVENT);
	}

	@Test
	void nothingIsSentWhenTheTransactionRollsBack() {
		tx.executeWithoutResult(status -> {
			events.publishEvent(EVENT);
			status.setRollbackOnly();
		});

		verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
	}

	@Test
	void eventPublishedOutsideAnyTransactionIsNotSent() {
		// @TransactionalEventListener ignores events without a transaction (fallbackExecution = false):
		// a status change always happens inside the service's transaction, so this would be a bug elsewhere
		events.publishEvent(EVENT);

		verifyNoInteractions(kafkaTemplate);
	}

}
