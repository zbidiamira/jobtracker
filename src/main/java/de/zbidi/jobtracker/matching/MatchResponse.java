package de.zbidi.jobtracker.matching;

import java.time.Instant;
import java.util.List;

import org.jspecify.annotations.Nullable;

/** One analyzed posting for the caller: the posting's facts and the AI's verdict. */
public record MatchResponse(
		long postingId,
		String title,
		String companyName,
		@Nullable String city,
		String url,
		int matchScore,
		List<String> mustHave,
		List<String> niceToHave,
		List<String> matchedSkills,
		List<String> missingSkills,
		String summary,
		Instant analyzedAt) {
}
