package de.zbidi.jobtracker;

import java.time.Instant;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Replaces the system clock in slice tests. Tests set the time in {@code @BeforeEach}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestClockConfiguration {

	@Bean
	MutableClock clock() {
		return new MutableClock(Instant.parse("2026-10-01T08:00:00Z"), ZoneId.of("Europe/Berlin"));
	}

}
