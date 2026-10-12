package de.zbidi.jobtracker.matching;

import java.util.List;

import de.zbidi.jobtracker.analysis.PostingAnalysis;
import de.zbidi.jobtracker.common.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Reads the caller's successful matches, best first. FAILED analyses are not shown. */
@Service
public class MatchQueryService {

	private final JdbcClient jdbc;
	private final JsonMapper json;

	public MatchQueryService(JdbcClient jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public PageResponse<MatchResponse> matches(long userId, int minScore, int page, int size) {
		List<MatchResponse> content = jdbc.sql("""
						SELECT p.id, p.title, p.company_name, p.city, p.url, m.match_score, m.analysis, m.analyzed_at
						FROM posting_match m JOIN job_posting p ON p.id = m.posting_id
						WHERE m.user_id = ? AND m.status = 'MATCHED' AND m.match_score >= ?
						ORDER BY m.match_score DESC, m.analyzed_at DESC, p.id DESC
						LIMIT ? OFFSET ?
						""")
				.params(userId, minScore, size, (long) page * size)
				.query((rs, row) -> {
					PostingAnalysis analysis = json.readValue(rs.getString("analysis"), PostingAnalysis.class);
					return new MatchResponse(rs.getLong("id"), rs.getString("title"), rs.getString("company_name"),
							rs.getString("city"), rs.getString("url"), rs.getInt("match_score"),
							orEmpty(analysis.mustHave()), orEmpty(analysis.niceToHave()),
							orEmpty(analysis.matchedSkills()), orEmpty(analysis.missingSkills()),
							analysis.summary() == null ? "" : analysis.summary(),
							rs.getTimestamp("analyzed_at").toInstant());
				})
				.list();
		long total = jdbc.sql("""
						SELECT count(*) FROM posting_match
						WHERE user_id = ? AND status = 'MATCHED' AND match_score >= ?
						""")
				.params(userId, minScore)
				.query(Long.class)
				.single();
		return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
	}

	private static List<String> orEmpty(List<String> list) {
		return list == null ? List.of() : list;
	}

}
