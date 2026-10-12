package de.zbidi.jobtracker.matching;

import java.time.Instant;

import de.zbidi.jobtracker.analysis.PostingAnalysis;

/** The AI's result for one posting and user, ready to be stored. */
public record PostingMatch(long postingId, long userId, PostingAnalysis analysis, Instant analyzedAt) {
}
