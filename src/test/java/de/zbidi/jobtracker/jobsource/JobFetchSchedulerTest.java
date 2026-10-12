package de.zbidi.jobtracker.jobsource;

import de.zbidi.jobtracker.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig
@Import({SchedulingConfig.class, JobFetchScheduler.class})
@TestPropertySource(properties = "jobtracker.jobs.fetch-cron=0 0 7 * * *")
class JobFetchSchedulerTest {

	@Autowired
	ScheduledTaskHolder scheduledTasks;

	@Autowired
	JobFetchScheduler scheduler;

	@MockitoBean
	JobFetchService jobFetchService;

	@Test
	void runsOnTheCronFromConfiguration() {
		assertThat(scheduledTasks.getScheduledTasks())
				.extracting(ScheduledTask::getTask)
				.filteredOn(CronTask.class::isInstance)
				.extracting(task -> ((CronTask) task).getExpression())
				.containsExactly("0 0 7 * * *");
	}

	@Test
	void cronIsEvaluatedInBerlinTime() throws NoSuchMethodException {
		Scheduled scheduled = JobFetchScheduler.class.getMethod("fetch").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("${jobtracker.jobs.fetch-cron}");
		assertThat(scheduled.zone()).isEqualTo("Europe/Berlin");
	}

	@Test
	void delegatesToTheService() {
		scheduler.fetch();

		verify(jobFetchService).fetchAll();
	}

}
