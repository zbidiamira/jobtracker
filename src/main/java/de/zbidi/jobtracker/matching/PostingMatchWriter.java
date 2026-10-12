package de.zbidi.jobtracker.matching;

import java.sql.Timestamp;
import java.util.UUID;

import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stores each result as MATCHED with the score and the whole analysis as JSON. ON CONFLICT: two runs at the same
 * time can't store a pair twice. A newly stored result at or above {@code jobtracker.matching.notify-score} is
 * announced as {@link GoodMatchFound} (sent to Kafka after the chunk commits).
 */
@Component
public class PostingMatchWriter implements ItemWriter<PostingMatch> {

	private final JdbcClient jdbc;
	private final JsonMapper json;
	private final ApplicationEventPublisher events;
	private final int notifyScore;

	public PostingMatchWriter(JdbcClient jdbc, JsonMapper json, ApplicationEventPublisher events,
			@Value("${jobtracker.matching.notify-score}") int notifyScore) {
		this.jdbc = jdbc;
		this.json = json;
		this.events = events;
		this.notifyScore = notifyScore;
	}

	@Override
	public void write(Chunk<? extends PostingMatch> matches) {
		for (PostingMatch match : matches) {
			int score = match.analysis().matchScore();
			boolean stored = jdbc.sql("""
							INSERT INTO posting_match (posting_id, user_id, status, match_score, analysis, analyzed_at)
							VALUES (?, ?, 'MATCHED', ?, CAST(? AS jsonb), ?)
							ON CONFLICT (posting_id, user_id) DO NOTHING
							""")
					.params(match.postingId(), match.userId(), score, json.writeValueAsString(match.analysis()),
							Timestamp.from(match.analyzedAt()))
					.update() == 1;
			if (stored && score >= notifyScore) {
				events.publishEvent(new GoodMatchFound(UUID.randomUUID(), match.userId(), match.postingId(),
						match.title(), match.companyName(), match.url(), score, match.analyzedAt()));
			}
		}
	}

}
