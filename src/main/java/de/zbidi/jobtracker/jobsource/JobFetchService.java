package de.zbidi.jobtracker.jobsource;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import de.zbidi.jobtracker.posting.PostingsImportedEvent;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * Asks every enabled {@link JobSource} for postings and saves the new ones. A portal that fails is reported and
 * doesn't stop the others. New postings are announced like an import, so the AI matching starts from them.
 */
@Service
public class JobFetchService {

	private static final Logger log = LoggerFactory.getLogger(JobFetchService.class);

	/** Per portal: how many postings it delivered and how many of them were new. */
	public record FetchResult(String source, int fetched, int saved, @Nullable String error) {
	}

	private final List<JobSource> sources;
	private final FetchedPostingRepository repository;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public JobFetchService(List<JobSource> sources, FetchedPostingRepository repository,
			ApplicationEventPublisher events, Clock clock) {
		this.sources = sources;
		this.repository = repository;
		this.events = events;
		this.clock = clock;
	}

	public List<FetchResult> fetchAll() {
		List<FetchResult> results = new ArrayList<>();
		for (JobSource source : sources) {
			if (source.enabled()) {
				results.add(fetch(source));
			}
		}
		int saved = results.stream().mapToInt(FetchResult::saved).sum();
		if (saved > 0) {
			events.publishEvent(new PostingsImportedEvent("fetch", saved));
		}
		return results;
	}

	private FetchResult fetch(JobSource source) {
		List<FetchedPosting> postings;
		try {
			postings = source.fetch();
		}
		catch (RuntimeException ex) {
			log.warn("Fetching from {} failed: {}", source.name(), ex.getMessage());
			return new FetchResult(source.name(), 0, 0, ex.getMessage());
		}
		Instant now = clock.instant();
		int saved = (int) postings.stream().filter(posting -> repository.insertIfNew(posting, now)).count();
		log.info("Fetched {} posting(s) from {}, {} new", postings.size(), source.name(), saved);
		return new FetchResult(source.name(), postings.size(), saved, null);
	}

}
