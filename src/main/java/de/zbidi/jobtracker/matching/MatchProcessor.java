package de.zbidi.jobtracker.matching;

import java.time.Clock;

import de.zbidi.jobtracker.analysis.PostingAnalysis;
import de.zbidi.jobtracker.analysis.PostingAnalyzer;
import org.springframework.batch.infrastructure.item.ItemProcessor;
import org.springframework.stereotype.Component;

/**
 * One AI analysis per item, as the user (the analyzer's tool reads that user's skills).
 * <ul>
 *     <li>{@code AiRejectedException} (unusable answer): skipped by the step, stored as FAILED</li>
 *     <li>{@code AiUnavailableException} (model down, timeout): not skippable, the run stops; the pair stays open
 *     for the next run</li>
 * </ul>
 */
@Component
public class MatchProcessor implements ItemProcessor<MatchCandidate, PostingMatch> {

	private final PostingAnalyzer analyzer;
	private final Clock clock;

	public MatchProcessor(PostingAnalyzer analyzer, Clock clock) {
		this.analyzer = analyzer;
		this.clock = clock;
	}

	@Override
	public PostingMatch process(MatchCandidate candidate) {
		PostingAnalysis analysis = analyzer.analyze(candidate.userId(), candidate.toPostingText());
		return new PostingMatch(candidate.postingId(), candidate.userId(), analysis, clock.instant());
	}

}
