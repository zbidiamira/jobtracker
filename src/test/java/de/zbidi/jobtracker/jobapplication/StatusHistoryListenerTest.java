package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Kafka delivers at least once: the same event can arrive twice. The listener must write exactly one row.
 */
@ExtendWith(MockitoExtension.class)
class StatusHistoryListenerTest {

	private static final UUID EVENT_ID = UUID.fromString("0b9e5a3e-6c1f-4c8e-9d0a-5f1e2d3c4b5a");
	private static final Instant CHANGED_AT = Instant.parse("2026-10-07T10:00:00Z");
	private static final StatusChangedEvent EVENT =
			new StatusChangedEvent(EVENT_ID, 5L, 42L, Status.SAVED, Status.APPLIED, CHANGED_AT);

	@Mock
	StatusHistoryRepository statusHistoryRepository;

	@Mock
	JobApplicationRepository jobApplicationRepository;

	@InjectMocks
	StatusHistoryListener listener;

	@Test
	void writesHistoryRowFromEvent() {
		JobApplication application = mock(JobApplication.class);
		given(jobApplicationRepository.existsById(5L)).willReturn(true);
		given(jobApplicationRepository.getReferenceById(5L)).willReturn(application);

		listener.onStatusChanged(EVENT);

		ArgumentCaptor<StatusHistory> saved = ArgumentCaptor.forClass(StatusHistory.class);
		verify(statusHistoryRepository).saveAndFlush(saved.capture());
		assertThat(saved.getValue().getJobApplication()).isSameAs(application);
		assertThat(saved.getValue().getFromStatus()).isEqualTo(Status.SAVED);
		assertThat(saved.getValue().getToStatus()).isEqualTo(Status.APPLIED);
		// the moment of the change, not the moment the consumer happened to run
		assertThat(saved.getValue().getChangedAt()).isEqualTo(CHANGED_AT);
		assertThat(saved.getValue().getEventId()).isEqualTo(EVENT_ID);
	}

	@Test
	void duplicateEventIdIsSkipped() {
		given(statusHistoryRepository.existsByEventId(EVENT_ID)).willReturn(true);

		listener.onStatusChanged(EVENT);

		verify(statusHistoryRepository, never()).saveAndFlush(any());
	}

	@Test
	void uniqueViolationOnConcurrentDuplicateIsTreatedAsDone() {
		// two deliveries raced: both passed the existsByEventId check, the unique index stopped the second insert
		given(jobApplicationRepository.existsById(5L)).willReturn(true);
		given(jobApplicationRepository.getReferenceById(5L)).willReturn(mock(JobApplication.class));
		given(statusHistoryRepository.existsByEventId(EVENT_ID)).willReturn(false, true);
		given(statusHistoryRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("ux_status_history_event_id"));

		assertThatNoException().isThrownBy(() -> listener.onStatusChanged(EVENT));
	}

	@Test
	void otherDatabaseErrorsAreRethrownForRetry() {
		given(jobApplicationRepository.existsById(5L)).willReturn(true);
		given(jobApplicationRepository.getReferenceById(5L)).willReturn(mock(JobApplication.class));
		given(statusHistoryRepository.existsByEventId(EVENT_ID)).willReturn(false, false);
		given(statusHistoryRepository.saveAndFlush(any())).willThrow(new DataIntegrityViolationException("something else"));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
				.isThrownBy(() -> listener.onStatusChanged(EVENT));
	}

	@Test
	void eventForDeletedApplicationIsSkipped() {
		// an admin deleted the application while its event was still on the way: nothing to record, no retry loop
		given(jobApplicationRepository.existsById(5L)).willReturn(false);

		listener.onStatusChanged(EVENT);

		verify(statusHistoryRepository, never()).saveAndFlush(any());
	}

}
