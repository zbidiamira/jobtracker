package de.zbidi.jobtracker.reminder;

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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Same contract as StatusEventPublisher: sent to Kafka only once the reminded_at update has committed.
 */
@SpringJUnitConfig
class ReminderEventPublisherTest {

	private static final ReminderEvent EVENT = new ReminderEvent(UUID.randomUUID(), 5L, 42L, "Java Developer",
			Instant.parse("2026-09-20T10:00:00Z"), Instant.parse("2026-10-11T06:00:00Z"));

	@Configuration(proxyBeanMethods = false)
	@EnableTransactionManagement
	@Import(ReminderEventPublisher.class)
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
	void sentToApplicationRemindersWithApplicationIdAsKeyOnCommit() {
		tx.executeWithoutResult(status -> {
			events.publishEvent(EVENT);
			verifyNoInteractions(kafkaTemplate);
		});

		verify(kafkaTemplate).send("application-reminders", "5", EVENT);
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
	void failedSendIsLoggedNotThrown() {
		given(kafkaTemplate.send(anyString(), anyString(), any()))
				.willReturn(CompletableFuture.failedFuture(new IllegalStateException("broker down")));

		assertThatNoException().isThrownBy(() -> tx.executeWithoutResult(status -> events.publishEvent(EVENT)));
	}

}
