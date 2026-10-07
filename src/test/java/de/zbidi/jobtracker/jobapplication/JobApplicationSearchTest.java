package de.zbidi.jobtracker.jobapplication;

import java.time.LocalDate;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobApplicationSearchTest {

	private static final LocalDate OCT_1 = LocalDate.of(2026, 10, 1);
	private static final LocalDate OCT_7 = LocalDate.of(2026, 10, 7);

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	@Test
	void companyFilterIsCalledCompanyName() {
		// the record component name is the query parameter name: ?companyName=acme
		assertThat(new JobApplicationSearch(null, "acme", null, null, null).companyName()).isEqualTo("acme");
	}

	@Test
	void rangeInOrderIsValid() {
		assertThat(validator.validate(new JobApplicationSearch(null, null, null, OCT_1, OCT_7))).isEmpty();
	}

	@Test
	void sameDayIsValid() {
		assertThat(validator.validate(new JobApplicationSearch(null, null, null, OCT_1, OCT_1))).isEmpty();
	}

	@Test
	void openRangesAreValid() {
		assertThat(validator.validate(new JobApplicationSearch(null, null, null, OCT_7, null))).isEmpty();
		assertThat(validator.validate(new JobApplicationSearch(null, null, null, null, OCT_1))).isEmpty();
	}

	@Test
	void fromAfterToIsRejected() {
		assertThat(validator.validate(new JobApplicationSearch(null, null, null, OCT_7, OCT_1)))
				.extracting(ConstraintViolation::getMessage)
				.containsExactly("createdFrom must not be after createdTo");
	}

	@Test
	void textFiltersLongerThan200AreRejected() {
		assertThat(validator.validate(new JobApplicationSearch(null, "x".repeat(201), null, null, null))).hasSize(1);
	}

}
