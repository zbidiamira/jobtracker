package de.zbidi.jobtracker.posting;

import java.time.LocalDateTime;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.step.StepExecution;

/**
 * State of one batch run (import or matching). Counts are summed over its steps.
 *
 * @param skipCount   lines skipped as unreadable or invalid (at most 10, or the run fails)
 * @param filterCount postings dropped as duplicates (not an error)
 * @param failures    why the run failed, empty otherwise
 */
public record ImportStatusResponse(
		long executionId,
		String status,
		String exitCode,
		@Nullable LocalDateTime startTime,
		@Nullable LocalDateTime endTime,
		long readCount,
		long writeCount,
		long filterCount,
		long skipCount,
		List<String> failures) {

	public static ImportStatusResponse from(JobExecution execution) {
		var steps = execution.getStepExecutions();
		return new ImportStatusResponse(
				execution.getId(),
				execution.getStatus().name(),
				execution.getExitStatus().getExitCode(),
				execution.getStartTime(),
				execution.getEndTime(),
				steps.stream().mapToLong(StepExecution::getReadCount).sum(),
				steps.stream().mapToLong(StepExecution::getWriteCount).sum(),
				steps.stream().mapToLong(StepExecution::getFilterCount).sum(),
				steps.stream().mapToLong(StepExecution::getSkipCount).sum(),
				failures(execution));
	}

	/**
	 * Read from the stored exit descriptions: an execution loaded from the JDBC job repository has no exception
	 * objects, only their stack traces as text (EXIT_MESSAGE). Keeps the message and "Caused by" lines, drops frames.
	 */
	private static List<String> failures(JobExecution execution) {
		List<String> descriptions = execution.getStepExecutions().stream()
				.map(step -> step.getExitStatus().getExitDescription())
				.filter(description -> !description.isBlank())
				.toList();
		if (descriptions.isEmpty()) {
			// failed before any step ran, e.g. invalid parameters
			descriptions = List.of(execution.getExitStatus().getExitDescription());
		}
		return descriptions.stream()
				.flatMap(String::lines)
				.map(String::strip)
				.filter(line -> !line.isEmpty() && !line.startsWith("at ") && !line.startsWith("..."))
				.toList();
	}

}
