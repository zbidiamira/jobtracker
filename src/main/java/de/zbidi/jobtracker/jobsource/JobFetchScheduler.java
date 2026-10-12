package de.zbidi.jobtracker.jobsource;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Fetches new postings on {@code jobtracker.jobs.fetch-cron} (default: every day at 07:00 Berlin time; "-" = off,
 * as in tests). New postings then start the AI matching.
 */
@Component
public class JobFetchScheduler {

	private final JobFetchService jobFetchService;

	public JobFetchScheduler(JobFetchService jobFetchService) {
		this.jobFetchService = jobFetchService;
	}

	@Scheduled(cron = "${jobtracker.jobs.fetch-cron}", zone = "Europe/Berlin")
	public void fetch() {
		jobFetchService.fetchAll();
	}

}
