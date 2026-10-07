package de.zbidi.jobtracker.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Single source of "now" and of the business time zone (used for date filters like createdFrom/createdTo).
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

	public static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Berlin");

	@Bean
	Clock clock() {
		return Clock.system(BUSINESS_ZONE);
	}

}
