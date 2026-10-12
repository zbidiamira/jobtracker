package de.zbidi.jobtracker.posting;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.batch.infrastructure.item.validator.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostingItemProcessorTest {

	static final Instant NOW = Instant.parse("2026-10-11T06:00:00Z");

	JobPostingRepository repository = mock(JobPostingRepository.class);
	PostingItemProcessor processor;

	@BeforeEach
	void setUp() {
		when(repository.existsByUrl(anyString())).thenReturn(false);
		processor = new PostingItemProcessor(repository, Clock.fixed(NOW, ZoneId.of("Europe/Berlin")), "csv:jobs.csv");
	}

	@Test
	void trimsAllFieldsAndSetsSourceAndImportTime() {
		JobPosting posting = processor.process(new PostingCsvLine("  Java Developer ", " ACME ", " Berlin ",
				" https://acme.example/jobs/1 ", "  We need a Java developer.  "));

		assertThat(posting).isEqualTo(new JobPosting("Java Developer", "ACME", "Berlin",
				"https://acme.example/jobs/1", "We need a Java developer.", "csv:jobs.csv", NOW));
	}

	// --- description: the full job text the AI analyzes ---

	@Test
	void blankDescriptionIsInvalid() {
		assertThatThrownBy(() -> processor.process(
				new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/1", "  ")))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("description");
		assertThatThrownBy(() -> processor.process(
				new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/1", null)))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("description");
	}

	/** The same limit as a posting pasted into /api/postings/analyze. */
	@Test
	void descriptionLongerThan10000CharactersIsInvalid() {
		assertThat(processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/1",
				"d".repeat(10_000)))).isNotNull();
		assertThatThrownBy(() -> processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/2",
				"d".repeat(10_001))))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("description");
	}

	@Test
	void lineBreaksInTheDescriptionAreKept() {
		JobPosting posting = processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/1",
				"Tasks:\n- build APIs\n\nProfile:\n- Java"));

		assertThat(posting.description()).isEqualTo("Tasks:\n- build APIs\n\nProfile:\n- Java");
	}

	@Test
	void emptyCityBecomesNull() {
		assertThat(processor.process(new PostingCsvLine("Dev", "ACME", "  ", "https://acme.example/1", "We need a developer.")).city())
				.isNull();
		assertThat(processor.process(new PostingCsvLine("Dev", "ACME", null, "https://acme.example/2", "We need a developer.")).city())
				.isNull();
	}

	@Test
	void blankTitleIsInvalid() {
		assertThatThrownBy(() -> processor.process(new PostingCsvLine(" ", "ACME", "Berlin", "https://acme.example/1", "We need a developer.")))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("title");
	}

	@Test
	void blankCompanyIsInvalid() {
		assertThatThrownBy(() -> processor.process(new PostingCsvLine("Dev", null, "Berlin", "https://acme.example/1", "We need a developer.")))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("company");
	}

	@Test
	void blankUrlIsInvalid() {
		assertThatThrownBy(() -> processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", "", "We need a developer.")))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("url");
	}

	@ParameterizedTest
	@ValueSource(strings = { "acme.example/jobs/1", "ftp://acme.example/jobs/1", "https://", "https://acme example/1",
			"not a url" })
	void urlMustBeAnAbsoluteHttpUrl(String url) {
		assertThatThrownBy(() -> processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", url, "We need a developer.")))
				.isInstanceOf(ValidationException.class)
				.hasMessageContaining("url");
	}

	@Test
	void valuesLongerThanTheirColumnAreInvalid() {
		assertThatThrownBy(() -> processor.process(
				new PostingCsvLine("x".repeat(201), "ACME", "Berlin", "https://acme.example/1", "We need a developer.")))
				.isInstanceOf(ValidationException.class).hasMessageContaining("title");
		assertThatThrownBy(() -> processor.process(
				new PostingCsvLine("Dev", "x".repeat(201), "Berlin", "https://acme.example/1", "We need a developer.")))
				.isInstanceOf(ValidationException.class).hasMessageContaining("company");
		assertThatThrownBy(() -> processor.process(
				new PostingCsvLine("Dev", "ACME", "x".repeat(101), "https://acme.example/1", "We need a developer.")))
				.isInstanceOf(ValidationException.class).hasMessageContaining("city");
		assertThatThrownBy(() -> processor.process(
				new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/" + "x".repeat(480), "We need a developer.")))
				.isInstanceOf(ValidationException.class).hasMessageContaining("url");
	}

	@Test
	void valuesExactlyAtTheColumnLimitAreValid() {
		assertThat(processor.process(new PostingCsvLine("x".repeat(200), "y".repeat(200), "z".repeat(100),
				"https://acme.example/" + "u".repeat(479), "We need a developer."))).isNotNull();
	}

	@Test
	void urlAlreadyInTheDatabaseIsFiltered() {
		when(repository.existsByUrl("https://acme.example/1")).thenReturn(true);

		assertThat(processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/1", "We need a developer."))).isNull();
	}

	@Test
	void secondLineWithTheSameUrlInOneFileIsFiltered() {
		assertThat(processor.process(new PostingCsvLine("Dev", "ACME", "Berlin", "https://acme.example/1", "We need a developer.")))
				.isNotNull();
		assertThat(processor.process(new PostingCsvLine("Dev again", "ACME", "Berlin", " https://acme.example/1 ", "We need a developer.")))
				.isNull();
	}

}
