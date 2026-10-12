package de.zbidi.jobtracker.posting;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Announces a successful import with new postings as {@link PostingsImportedEvent}. The import doesn't know who
 * reacts (the AI matching starts from it).
 */
@Component
public class PostingImportFinishedListener implements JobExecutionListener {

	private final ApplicationEventPublisher events;

	public PostingImportFinishedListener(ApplicationEventPublisher events) {
		this.events = events;
	}

	@Override
	public void afterJob(JobExecution execution) {
		long written = execution.getStepExecutions().stream().mapToLong(StepExecution::getWriteCount).sum();
		if (execution.getStatus() == BatchStatus.COMPLETED && written > 0) {
			events.publishEvent(new PostingsImportedEvent("import " + execution.getId(), written));
		}
	}

}
