package de.zbidi.jobtracker.matching;

import java.time.Clock;

import de.zbidi.jobtracker.analysis.AiUnavailableException;
import de.zbidi.jobtracker.analysis.PostingAnalyzer;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.posting.ImportStatusResponse;
import de.zbidi.jobtracker.posting.PostingsImportedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;

/**
 * Starts postingMatchJob in the background: on demand (admin), after an import and from the daily fetch.
 * Never starts without a configured model, nor while another run is going.
 */
@Service
public class MatchingService {

	private static final Logger log = LoggerFactory.getLogger(MatchingService.class);

	private final JobOperator jobOperator;
	private final JobRepository jobRepository;
	private final Job postingMatchJob;
	private final PostingAnalyzer analyzer;
	private final Clock clock;

	public MatchingService(JobOperator jobOperator, JobRepository jobRepository,
			@Qualifier("postingMatchJob") Job postingMatchJob, PostingAnalyzer analyzer, Clock clock) {
		this.jobOperator = jobOperator;
		this.jobRepository = jobRepository;
		this.postingMatchJob = postingMatchJob;
		this.analyzer = analyzer;
		this.clock = clock;
	}

	/**
	 * @return the started execution (status STARTING: it runs in the background)
	 * @throws AiUnavailableException without a configured model
	 * @throws MatchingAlreadyRunningException while a run is in progress
	 */
	public JobExecution start() {
		if (!analyzer.isAvailable()) {
			throw new AiUnavailableException("AI is not configured: matching needs a model (profile local or cloud)");
		}
		if (!jobRepository.findRunningJobExecutions(PostingMatchJobConfig.JOB_NAME).isEmpty()) {
			throw new MatchingAlreadyRunningException();
		}
		try {
			return jobOperator.start(postingMatchJob, PostingMatchJobConfig.parameters(clock.instant()));
		}
		catch (Exception ex) {
			throw new IllegalStateException("Could not start " + PostingMatchJobConfig.JOB_NAME, ex);
		}
	}

	public ImportStatusResponse status(long executionId) {
		JobExecution execution;
		try {
			execution = jobRepository.getJobExecution(executionId);
		}
		catch (EmptyResultDataAccessException ex) {
			execution = null;
		}
		if (execution == null || !PostingMatchJobConfig.JOB_NAME.equals(execution.getJobInstance().getJobName())) {
			throw new ResourceNotFoundException("Matching run", executionId);
		}
		return ImportStatusResponse.from(execution);
	}

	/** New postings are analyzed right away. The import/fetch already succeeded: a refusal here is only logged. */
	@EventListener
	public void onPostingsImported(PostingsImportedEvent event) {
		try {
			JobExecution execution = start();
			log.info("{} new posting(s) ({}): matching run {} started", event.written(), event.origin(), execution.getId());
		}
		catch (AiUnavailableException | MatchingAlreadyRunningException ex) {
			log.info("Matching not started after {}: {}", event.origin(), ex.getMessage());
		}
	}

}
