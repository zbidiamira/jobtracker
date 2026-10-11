package de.zbidi.jobtracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} (the daily ReminderScheduler). Kept out of {@code JobtrackerApplication} so slice
 * tests don't start schedulers.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {

}
