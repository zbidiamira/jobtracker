package de.zbidi.jobtracker.jobsource;

import java.util.List;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Fetch from the job portals now instead of waiting for the schedule; ADMIN only (/api/admin/**). */
@RestController
public class JobFetchAdminController {

	private final JobFetchService jobFetchService;

	public JobFetchAdminController(JobFetchService jobFetchService) {
		this.jobFetchService = jobFetchService;
	}

	@PostMapping("/api/admin/jobs/fetch")
	public List<JobFetchService.FetchResult> fetch() {
		return jobFetchService.fetchAll();
	}

}
