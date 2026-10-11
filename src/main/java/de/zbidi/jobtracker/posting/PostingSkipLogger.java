package de.zbidi.jobtracker.posting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.listener.SkipListener;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;

/**
 * Logs every line the import skips, so an admin can fix the file. The step execution only keeps the counts.
 */
public class PostingSkipLogger implements SkipListener<PostingCsvLine, JobPosting> {

	private static final Logger log = LoggerFactory.getLogger(PostingSkipLogger.class);

	@Override
	public void onSkipInRead(Throwable t) {
		if (t instanceof FlatFileParseException parse) {
			log.warn("Skipped line {}: {} [{}]", parse.getLineNumber(), parse.getMessage(), parse.getInput());
		}
		else {
			log.warn("Skipped unreadable line: {}", t.toString());
		}
	}

	@Override
	public void onSkipInProcess(PostingCsvLine line, Throwable t) {
		log.warn("Skipped posting {}: {}", line.url(), t.getMessage());
	}

	@Override
	public void onSkipInWrite(JobPosting posting, Throwable t) {
		log.warn("Skipped writing posting {}: {}", posting.url(), t.getMessage());
	}

}
