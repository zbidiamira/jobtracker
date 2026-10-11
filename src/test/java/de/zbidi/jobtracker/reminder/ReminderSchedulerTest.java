package de.zbidi.jobtracker.reminder;

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
@Import({SchedulingConfig.class, ReminderScheduler.class})
@TestPropertySource(properties = "jobtracker.reminder.cron=0 0 8 * * *")
class ReminderSchedulerTest {

	@Autowired
	ScheduledTaskHolder scheduledTasks;

	@Autowired
	ReminderScheduler scheduler;

	@MockitoBean
	ReminderService reminderService;

	@Test
	void runsOnTheCronFromConfiguration() {
		assertThat(scheduledTasks.getScheduledTasks())
				.extracting(ScheduledTask::getTask)
				.filteredOn(CronTask.class::isInstance)
				.extracting(task -> ((CronTask) task).getExpression())
				.containsExactly("0 0 8 * * *");
	}

	@Test
	void cronIsEvaluatedInBerlinTime() throws NoSuchMethodException {
		Scheduled scheduled = ReminderScheduler.class.getMethod("sendDueReminders").getAnnotation(Scheduled.class);

		assertThat(scheduled.cron()).isEqualTo("${jobtracker.reminder.cron}");
		assertThat(scheduled.zone()).isEqualTo("Europe/Berlin");
	}

	@Test
	void delegatesToTheService() {
		scheduler.sendDueReminders();

		verify(reminderService).sendDueReminders();
	}

}
