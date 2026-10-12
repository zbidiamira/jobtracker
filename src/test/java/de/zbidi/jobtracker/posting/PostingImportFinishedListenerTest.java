package de.zbidi.jobtracker.posting;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * After a successful import with new postings, the rest of the app hears about it (matching starts from that).
 */
class PostingImportFinishedListenerTest {

	ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
	PostingImportFinishedListener listener = new PostingImportFinishedListener(events);

	@Test
	void completedImportWithNewPostingsIsAnnounced() {
		listener.afterJob(execution(BatchStatus.COMPLETED, 12));

		verify(events).publishEvent(new PostingsImportedEvent(7L, 12));
	}

	@Test
	void failedImportIsNotAnnounced() {
		listener.afterJob(execution(BatchStatus.FAILED, 12));

		verify(events, never()).publishEvent(any(Object.class));
	}

	@Test
	void importWithoutNewPostingsIsNotAnnounced() {
		listener.afterJob(execution(BatchStatus.COMPLETED, 0));

		verify(events, never()).publishEvent(any(Object.class));
	}

	private static JobExecution execution(BatchStatus status, long written) {
		JobExecution execution = MetaDataInstanceFactory.createJobExecution("postingImportJob", 1L, 7L);
		execution.setStatus(status);
		StepExecution step = MetaDataInstanceFactory.createStepExecution(execution, "postingImportStep", 3L);
		step.setWriteCount(written);
		execution.addStepExecution(step);
		return execution;
	}

}
