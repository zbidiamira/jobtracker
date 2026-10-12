package de.zbidi.jobtracker.matching;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import de.zbidi.jobtracker.analysis.AiRejectedException;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.database.JdbcPagingItemReader;
import org.springframework.batch.infrastructure.item.database.Order;
import org.springframework.batch.infrastructure.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * postingMatchJob: every posting with a description × every user with at least one skill, without a stored result
 * yet → AI analysis ({@link MatchProcessor}) → posting_match.
 * <ul>
 *     <li>newest postings first, at most {@code jobtracker.matching.max-per-run} analyses per run (time and cost)</li>
 *     <li>chunk size 1: each analysis is committed at once, a later failure can't throw away finished work</li>
 *     <li>unusable answer → skipped and stored as FAILED; model unavailable → the run stops</li>
 *     <li>no restart state: which pairs are open is read from the database, so the next run simply continues</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
public class PostingMatchJobConfig {

	public static final String JOB_NAME = "postingMatchJob";
	static final String RUN_AT = "run.at";

	/** Pairs without a result; the subquery lets the reader page over plain column names. */
	private static final String CANDIDATES = """
			SELECT p.id AS posting_id, u.id AS user_id, p.title, p.company_name, p.city, p.url, p.description
			FROM job_posting p CROSS JOIN app_user u
			WHERE p.description IS NOT NULL
			  AND EXISTS (SELECT 1 FROM skill s WHERE s.user_id = u.id)
			  AND NOT EXISTS (SELECT 1 FROM posting_match m WHERE m.posting_id = p.id AND m.user_id = u.id)
			""";

	/** Each run is its own job instance. */
	public static JobParameters parameters(Instant runAt) {
		return new JobParametersBuilder().addString(RUN_AT, runAt.toString()).toJobParameters();
	}

	@Bean
	Job postingMatchJob(JobRepository jobRepository, Step postingMatchStep) {
		return new JobBuilder(JOB_NAME, jobRepository).start(postingMatchStep).build();
	}

	@Bean
	Step postingMatchStep(JobRepository jobRepository, PlatformTransactionManager transactionManager,
			JdbcPagingItemReader<MatchCandidate> matchCandidateReader, MatchProcessor matchProcessor,
			PostingMatchWriter postingMatchWriter, MatchFailureRecorder matchFailureRecorder) {
		return new StepBuilder("postingMatchStep", jobRepository)
				.<MatchCandidate, PostingMatch>chunk(1)
				.transactionManager(transactionManager)
				.reader(matchCandidateReader)
				.processor(matchProcessor)
				.writer(postingMatchWriter)
				.faultTolerant()
				.skip(AiRejectedException.class)
				.skipLimit(Integer.MAX_VALUE)
				.skipListener(matchFailureRecorder)
				.build();
	}

	@Bean
	@StepScope
	JdbcPagingItemReader<MatchCandidate> matchCandidateReader(DataSource dataSource,
			@Value("${jobtracker.matching.max-per-run}") int maxPerRun) throws Exception {
		Map<String, Order> sortKeys = new LinkedHashMap<>();
		sortKeys.put("posting_id", Order.DESCENDING);
		sortKeys.put("user_id", Order.ASCENDING);
		return new JdbcPagingItemReaderBuilder<MatchCandidate>()
				.name("matchCandidateReader")
				.dataSource(dataSource)
				.selectClause("SELECT posting_id, user_id, title, company_name, city, url, description")
				.fromClause("FROM (" + CANDIDATES + ") AS candidate")
				.sortKeys(sortKeys)
				.pageSize(20)
				.maxItemCount(maxPerRun)
				.saveState(false)
				.rowMapper((rs, row) -> new MatchCandidate(rs.getLong("posting_id"), rs.getLong("user_id"),
						rs.getString("title"), rs.getString("company_name"), rs.getString("city"),
						rs.getString("url"), rs.getString("description")))
				.build();
	}

}
