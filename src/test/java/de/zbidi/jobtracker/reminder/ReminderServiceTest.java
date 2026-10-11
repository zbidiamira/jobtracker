package de.zbidi.jobtracker.reminder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import de.zbidi.jobtracker.reminder.ReminderRepository.DueApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * "Now" is fixed through the injected Clock, so the 14-day cutoff and the reminder time are exact.
 */
class ReminderServiceTest {

	static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z"); // 08:00 in Berlin
	static final Instant CUTOFF = Instant.parse("2026-09-27T06:00:00Z"); // 14 days earlier
	static final DueApplication DUE = new DueApplication(5L, 42L, "Java Developer",
			Instant.parse("2026-09-20T10:00:00Z"));

	ReminderRepository repository = mock(ReminderRepository.class);
	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	ReminderService service;

	@BeforeEach
	void setUp() {
		service = new ReminderService(repository, events, Clock.fixed(NOW, ZoneId.of("Europe/Berlin")),
				Duration.ofDays(14));
	}

	@Test
	void findOverdueAsksForApplicationsUnchangedSince14DaysBeforeNow() {
		given(repository.findDue(CUTOFF)).willReturn(List.of(DUE));

		assertThat(service.findOverdue()).containsExactly(DUE);
		verify(repository).findDue(CUTOFF);
	}

	@Test
	void sendDueRemindersMarksAndPublishesWithTheFixedTime() {
		given(repository.findDue(CUTOFF)).willReturn(List.of(DUE));
		given(repository.markReminded(5L, NOW)).willReturn(true);

		int sent = service.sendDueReminders();

		assertThat(sent).isEqualTo(1);
		ArgumentCaptor<ReminderEvent> event = ArgumentCaptor.forClass(ReminderEvent.class);
		verify(events).publishEvent(event.capture());
		assertThat(event.getValue().eventId()).isNotNull();
		assertThat(event.getValue())
				.usingRecursiveComparison().ignoringFields("eventId")
				.isEqualTo(new ReminderEvent(null, 5L, 42L, "Java Developer", Instant.parse("2026-09-20T10:00:00Z"), NOW));
	}

	@Test
	void alreadyRemindedByAnotherRunIsNotPublished() {
		given(repository.findDue(CUTOFF)).willReturn(List.of(DUE));
		given(repository.markReminded(5L, NOW)).willReturn(false);

		assertThat(service.sendDueReminders()).isZero();
		verify(events, never()).publishEvent(any(Object.class));
	}

}
