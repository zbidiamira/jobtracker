package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores each result as MATCHED with the score and the whole analysis as JSON. ON CONFLICT: two runs at the same
 * time can't store a pair twice.
 */
@Component
public class PostingMatchWriter implements ItemWriter<PostingMatch> {

	private final JdbcClient jdbc;
	private final JsonMapper json;

	public PostingMatchWriter(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	@Override
	public void write(Chunk<? extends PostingMatch> matches) {
		for (PostingMatch match : matches) {
			jdbc.sql("""
							INSERT INTO posting_match (posting_id, user_id, status, match_score, analysis, analyzed_at)
							VALUES (?, ?, 'MATCHED', ?, CAST(? AS jsonb), ?)
							ON CONFLICT (posting_id, user_id) DO NOTHING
							""")
					.params(match.postingId(), match.userId(), match.analysis().matchScore(),
							json.writeValueAsString(match.analysis()), Timestamp.from(match.analyzedAt()))
					.update();
		}
	}

}
