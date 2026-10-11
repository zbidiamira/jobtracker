package de.zbidi.jobtracker.posting;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.batch.infrastructure.item.file.FlatFileParseException;
import org.springframework.batch.infrastructure.item.validator.ValidationException;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class PostingSkipLoggerTest {

	PostingSkipLogger logger = new PostingSkipLogger();

	@Test
	void unparsableLineIsLoggedWithLineNumberAndInput(CapturedOutput output) {
		logger.onSkipInRead(new FlatFileParseException("Parsing error", "Dev;ACME", 7));

		assertThat(output).contains("WARN").contains("line 7").contains("Dev;ACME");
	}

	@Test
	void invalidPostingIsLoggedWithUrlAndReason(CapturedOutput output) {
		logger.onSkipInProcess(new PostingCsvLine("", "ACME", "Berlin", "https://acme.example/1"),
				new ValidationException("title is required"));

		assertThat(output).contains("WARN").contains("https://acme.example/1").contains("title is required");
	}

}
