package de.zbidi.jobtracker.matching;

import java.time.Instant;

import de.zbidi.jobtracker.analysis.PostingAnalysis;

/** The AI's result for one posting and user, ready to be stored (title, company and url for the event). */
public record PostingMatch(long postingId, long userId, String title, String companyName, String url,
		PostingAnalysis analysis, Instant analyzedAt) {
}
