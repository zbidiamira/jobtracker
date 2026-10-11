package de.zbidi.jobtracker.posting;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;
import org.springframework.batch.test.MetaDataInstanceFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ImportStatusResponseTest {

	/**
	 * An execution loaded from the JDBC job repository has no exception objects, only the stored exit descriptions
	 * (stack traces as text). The response shows their message lines, not the "at ..." frames.
	 */
	@Test
	void failuresComeFromTheStoredExitDescriptions() {
		JobExecution job = MetaDataInstanceFactory.createJobExecution();
		job.setStatus(BatchStatus.FAILED);
		StepExecution step = MetaDataInstanceFactory.createStepExecution(job, "postingImportStep", 11L);
		job.addStepExecution(step);
		step.setExitStatus(ExitStatus.FAILED.addExitDescription("""
				org.springframework.batch.core.step.FatalStepExecutionException: Unable to process chunk
				\tat org.springframework.batch.core.step.item.ChunkOrientedStep.processChunkSequentially(ChunkOrientedStep.java:546)
				\tat java.base/java.lang.Thread.run(Thread.java:1583)
				Caused by: java.lang.IllegalStateException: database unavailable
				\tat de.zbidi.jobtracker.Something.write(Something.java:1)
				\t... 12 more"""));

		ImportStatusResponse response = ImportStatusResponse.from(job);

		assertThat(response.failures()).containsExactly(
				"org.springframework.batch.core.step.FatalStepExecutionException: Unable to process chunk",
				"Caused by: java.lang.IllegalStateException: database unavailable");
	}

	@Test
	void completedExecutionHasNoFailures() {
		JobExecution job = MetaDataInstanceFactory.createJobExecution();
		job.setStatus(BatchStatus.COMPLETED);
		job.setExitStatus(ExitStatus.COMPLETED);
		StepExecution step = MetaDataInstanceFactory.createStepExecution(job, "postingImportStep", 11L);
		step.setExitStatus(ExitStatus.COMPLETED);
		job.addStepExecution(step);

		assertThat(ImportStatusResponse.from(job).failures()).isEmpty();
	}

}
