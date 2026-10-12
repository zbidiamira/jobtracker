package de.zbidi.jobtracker.analysis;

import java.util.List;

/**
 * The model's answer, as structured output (the JSON schema is derived from this record).
 *
 * @param matchedSkills skills of the user the posting asks for (always a subset of the user's skills)
 * @param missingSkills skills the posting asks for that the user doesn't have
 * @param matchScore    0 to 100
 */
public record PostingAnalysis(
		String title,
		List<String> mustHave,
		List<String> niceToHave,
		List<String> matchedSkills,
		List<String> missingSkills,
		Integer matchScore,
		String summary) {
}
