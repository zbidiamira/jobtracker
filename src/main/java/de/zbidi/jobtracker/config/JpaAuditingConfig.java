package de.zbidi.jobtracker.config;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept out of {@code JobtrackerApplication} so web/other slice tests don't need JPA.
 * Audit timestamps come from the {@link Clock} bean (system clock if none), so tests can control "now".
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaAuditingConfig {

	@Bean
	DateTimeProvider auditingDateTimeProvider(ObjectProvider<Clock> clock) {
		return () -> Optional.of(Instant.now(clock.getIfAvailable(Clock::systemUTC)));
	}

}
