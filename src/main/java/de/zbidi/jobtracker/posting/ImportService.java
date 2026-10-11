package de.zbidi.jobtracker.posting;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;

import de.zbidi.jobtracker.common.ResourceNotFoundException;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * Starts, inspects and restarts postingImportJob. The JobOperator is async (see BatchConfig), so start and restart
 * return while the job is still running; clients poll {@link #status(long)}.
 */
@Service
public class ImportService {

	private static final int MAX_SOURCE_LENGTH = 255;

	private final JobOperator jobOperator;
	private final JobRepository jobRepository;
	private final Job postingImportJob;
	private final Clock clock;
	private final Path directory;

	public ImportService(JobOperator jobOperator, JobRepository jobRepository,
			@Qualifier("postingImportJob") Job postingImportJob, Clock clock,
			@Value("${jobtracker.imports.directory}") Path directory) {
		this.jobOperator = jobOperator;
		this.jobRepository = jobRepository;
		this.postingImportJob = postingImportJob;
		this.clock = clock;
		this.directory = directory;
	}

	/**
	 * Stores the upload under a new name (it must stay there for a restart) and starts the job. A new file name per
	 * upload means a new job instance, so the same CSV can be uploaded again.
	 */
	public ImportStatusResponse start(MultipartFile upload) {
		Path file = store(upload);
		var parameters = PostingImportJobConfig.parameters(file, LocalDate.now(clock), source(upload));
		try {
			return ImportStatusResponse.from(jobOperator.start(postingImportJob, parameters));
		}
		catch (JobInstanceAlreadyCompleteException | JobExecutionAlreadyRunningException | JobRestartException
				| InvalidJobParametersException ex) {
			// can't happen with a fresh file name and complete parameters
			throw new IllegalStateException("Could not start import of " + file, ex);
		}
	}

	public ImportStatusResponse status(long executionId) {
		return ImportStatusResponse.from(find(executionId));
	}

	/** Continues a FAILED or STOPPED run after its last committed chunk, as a new execution of the same instance. */
	public ImportStatusResponse restart(long executionId) {
		JobExecution execution = find(executionId);
		if (execution.isRunning()) {
			throw new ImportNotRestartableException(executionId, "it is still running");
		}
		try {
			return ImportStatusResponse.from(jobOperator.restart(execution));
		}
		catch (JobRestartException ex) {
			throw new ImportNotRestartableException(executionId, ex.getMessage());
		}
	}

	private JobExecution find(long executionId) {
		JobExecution execution;
		try {
			execution = jobRepository.getJobExecution(executionId);
		}
		catch (EmptyResultDataAccessException ex) {
			// the JDBC repository throws for an unknown id instead of returning null
			execution = null;
		}
		if (execution == null || !PostingImportJobConfig.JOB_NAME.equals(execution.getJobInstance().getJobName())) {
			throw new ResourceNotFoundException("Import", executionId);
		}
		return execution;
	}

	private Path store(MultipartFile upload) {
		try (InputStream content = upload.getInputStream()) {
			Files.createDirectories(directory);
			Path file = directory.resolve(UUID.randomUUID() + ".csv");
			Files.copy(content, file);
			return file;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not store upload", ex);
		}
	}

	private static String source(MultipartFile upload) {
		String name = StringUtils.getFilename(upload.getOriginalFilename());
		String source = "csv:" + (StringUtils.hasText(name) ? name : "upload");
		return source.length() > MAX_SOURCE_LENGTH ? source.substring(0, MAX_SOURCE_LENGTH) : source;
	}

}
