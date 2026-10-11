package de.zbidi.jobtracker.posting;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;

import javax.sql.DataSource;

import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.DefaultJobParametersValidator;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcBatchItemWriter;
import org.springframework.batch.infrastructure.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.infrastructure.item.file.FlatFileItemReader;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.infrastructure.item.validator.ValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * postingImportJob: CSV ({@code title,company,city,url} with header) → {@link PostingItemProcessor} → job_posting.
 * <ul>
 *     <li>chunks of 50, each in its own transaction</li>
 *     <li>unparsable lines and invalid postings are skipped and logged, at most 10; the 11th fails the job</li>
 *     <li>restartable: the reader stores its position with every commit, so a restart continues after the last
 *     committed chunk. Identifying parameters: file + run date (the same file can be imported again the next day)</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class PostingImportJobConfig {

	public static final String JOB_NAME = "postingImportJob";
	public static final String INPUT_FILE = "input.file";
	public static final String RUN_DATE = "run.date";
	public static final String SOURCE = "source";

	static final int CHUNK_SIZE = 50;
	static final int SKIP_LIMIT = 10;

	public static JobParameters parameters(Path file, LocalDate runDate, String source) {
		return new JobParametersBuilder()
				.addString(INPUT_FILE, file.toAbsolutePath().toString())
				.addLocalDate(RUN_DATE, runDate)
				.addString(SOURCE, source, false)
				.toJobParameters();
	}

	@Bean
	Job postingImportJob(JobRepository jobRepository, Step postingImportStep) {
		return new JobBuilder(JOB_NAME, jobRepository)
				.validator(new DefaultJobParametersValidator(new String[] { INPUT_FILE, RUN_DATE, SOURCE }, new String[0]))
				.start(postingImportStep)
				.build();
	}

	@Bean
	Step postingImportStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			FlatFileItemReader<PostingCsvLine> postingReader, PostingItemProcessor postingProcessor,
			JdbcBatchItemWriter<JobPosting> postingWriter) {
		return new StepBuilder("postingImportStep", jobRepository)
				.<PostingCsvLine, JobPosting>chunk(CHUNK_SIZE)
				.transactionManager(transactionManager)
				.reader(postingReader)
				.processor(postingProcessor)
				.writer(postingWriter)
				.faultTolerant()
				.skip(FlatFileParseException.class, ValidationException.class)
				.skipLimit(SKIP_LIMIT)
				.skipListener(new PostingSkipLogger())
				.build();
	}

	@Bean
	@StepScope
	FlatFileItemReader<PostingCsvLine> postingReader(@Value("#{jobParameters['" + INPUT_FILE + "']}") String inputFile) {
		return new FlatFileItemReaderBuilder<PostingCsvLine>()
				.name("postingReader") // key of the saved position in the step's execution context
				.resource(new FileSystemResource(inputFile))
				.encoding(StandardCharsets.UTF_8.name())
				.linesToSkip(1)
				.delimited()
				.names("title", "company", "city", "url")
				.targetType(PostingCsvLine.class)
				.build();
	}

	@Bean
	@StepScope
	PostingItemProcessor postingProcessor(JobPostingRepository repository, Clock clock,
			@Value("#{jobParameters['" + SOURCE + "']}") String source) {
		return new PostingItemProcessor(repository, clock, source);
	}

	/** ON CONFLICT: the processor already filters known urls; this only covers two imports running at once. */
	@Bean
	JdbcBatchItemWriter<JobPosting> postingWriter(DataSource dataSource) {
		return new JdbcBatchItemWriterBuilder<JobPosting>()
				.dataSource(dataSource)
				.sql("""
						INSERT INTO job_posting (title, company_name, city, url, source, imported_at)
						VALUES (:title, :companyName, :city, :url, :source, :importedAt)
						ON CONFLICT (url) DO NOTHING
						""")
				.itemSqlParameterSourceProvider(posting -> new MapSqlParameterSource()
						.addValue("title", posting.title())
						.addValue("companyName", posting.companyName())
						.addValue("city", posting.city())
						.addValue("url", posting.url())
						.addValue("source", posting.source())
						.addValue("importedAt", Timestamp.from(posting.importedAt())))
				.assertUpdates(false) // a conflict inserts 0 rows, that's fine
				.build();
	}

}
