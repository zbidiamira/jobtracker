package de.zbidi.jobtracker.matching;

import de.zbidi.jobtracker.analysis.AnalyzePostingRequest;
import org.jspecify.annotations.Nullable;

/**
 * A posting that hasn't been analyzed yet for a user with skills.
 */
public record MatchCandidate(long postingId, long userId, String title, String companyName, @Nullable String city,
		String url, String description) {

	/** What the AI reads: the posting's facts and its full text, within the analyzer's input limit. */
	String toPostingText() {
		String text = """
				Title: %s
				Company: %s
				Location: %s

				%s""".formatted(title, companyName, city == null ? "not stated" : city, description);
		return text.length() <= AnalyzePostingRequest.MAX_LENGTH ? text
				: text.substring(0, AnalyzePostingRequest.MAX_LENGTH);
	}

}
