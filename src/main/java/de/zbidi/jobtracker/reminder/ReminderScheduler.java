package de.zbidi.jobtracker.reminder;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers the reminders on {@code jobtracker.reminder.cron} (default: every day at 08:00 Berlin time).
 * Set the cron to "-" to switch it off (tests do). Safe with several instances: reminded_at is set at most once.
 */
@Component
public class ReminderScheduler {

	private final ReminderService reminderService;

	public ReminderScheduler(ReminderService reminderService) {
		this.reminderService = reminderService;
	}

	@Scheduled(cron = "${jobtracker.reminder.cron}", zone = "Europe/Berlin")
	public void sendDueReminders() {
		reminderService.sendDueReminders();
	}

}
