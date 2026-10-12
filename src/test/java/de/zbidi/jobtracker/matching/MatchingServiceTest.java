package de.zbidi.jobtracker.matching;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;

import de.zbidi.jobtracker.analysis.AiUnavailableException;
import de.zbidi.jobtracker.analysis.PostingAnalyzer;
import de.zbidi.jobtracker.posting.PostingsImportedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.test.MetaDataInstanceFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class MatchingServiceTest {

	static final Instant NOW = Instant.parse("2026-10-12T05:00:00Z");

	JobOperator jobOperator = mock(JobOperator.class);
	JobRepository jobRepository = mock(JobRepository.class);
	Job job = mock(Job.class);
	PostingAnalyzer analyzer = mock(PostingAnalyzer.class);
	MatchingService service;

	@BeforeEach
	void setUp() {
		given(job.getName()).willReturn(PostingMatchJobConfig.JOB_NAME);
		given(analyzer.isAvailable()).willReturn(true);
		given(jobRepository.findRunningJobExecutions(PostingMatchJobConfig.JOB_NAME)).willReturn(Set.of());
		service = new MatchingService(jobOperator, jobRepository, job, analyzer, Clock.fixed(NOW, ZoneId.of("UTC")));
	}

	@Test
	void startsARunWithItsOwnParameters() throws Exception {
		JobExecution started = MetaDataInstanceFactory.createJobExecution(PostingMatchJobConfig.JOB_NAME, 1L, 9L);
		given(jobOperator.start(job, PostingMatchJobConfig.parameters(NOW))).willReturn(started);

		assertThat(service.start()).isSameAs(started);
	}

	@Test
	void withoutAModelNothingIsStarted() throws Exception {
		given(analyzer.isAvailable()).willReturn(false);

		assertThatThrownBy(service::start).isInstanceOf(AiUnavailableException.class);
		verify(jobOperator, never()).start(any(Job.class), any());
	}

	/** Two runs at once would analyze the same pairs and pay for them twice. */
	@Test
	void secondRunWhileOneIsRunningIsRefused() throws Exception {
		given(jobRepository.findRunningJobExecutions(PostingMatchJobConfig.JOB_NAME))
				.willReturn(Set.of(MetaDataInstanceFactory.createJobExecution()));

		assertThatThrownBy(service::start).isInstanceOf(MatchingAlreadyRunningException.class);
		verify(jobOperator, never()).start(any(Job.class), any());
	}

	@Test
	void importedPostingsStartARun() throws Exception {
		given(jobOperator.start(any(Job.class), any())).willReturn(MetaDataInstanceFactory.createJobExecution());

		service.onPostingsImported(new PostingsImportedEvent(5L, 12));

		verify(jobOperator).start(job, PostingMatchJobConfig.parameters(NOW));
	}

	/** The import has already succeeded: missing AI or a running match job must not turn that into an error. */
	@Test
	void importTriggerOnlyLogsWhenMatchingCantStart(CapturedOutput output) {
		given(analyzer.isAvailable()).willReturn(false);

		assertThatNoException().isThrownBy(() -> service.onPostingsImported(new PostingsImportedEvent(5L, 12)));
		assertThat(output).contains("Matching not started");
	}

}
