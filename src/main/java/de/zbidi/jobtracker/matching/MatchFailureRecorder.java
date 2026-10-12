package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;
import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * A skipped pair (the AI answered something unusable) is stored as FAILED: it isn't analyzed again on every run.
 */
@Component
public class MatchFailureRecorder implements SkipListener<MatchCandidate, PostingMatch> {

	private static final Logger log = LoggerFactory.getLogger(MatchFailureRecorder.class);
	private static final int MAX_ERROR = 500;

	private final JdbcClient jdbc;
	private final Clock clock;

	public MatchFailureRecorder(JdbcClient jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	@Override
	public void onSkipInProcess(MatchCandidate candidate, Throwable failure) {
		String error = String.valueOf(failure.getMessage());
		log.warn("Posting {} for user {} could not be analyzed: {}", candidate.postingId(), candidate.userId(), error);
		jdbc.sql("""
						INSERT INTO posting_match (posting_id, user_id, status, error, analyzed_at)
						VALUES (?, ?, 'FAILED', ?, ?)
						ON CONFLICT (posting_id, user_id) DO NOTHING
						""")
				.params(candidate.postingId(), candidate.userId(),
						error.length() <= MAX_ERROR ? error : error.substring(0, MAX_ERROR),
						Timestamp.from(clock.instant()))
				.update();
	}

}
