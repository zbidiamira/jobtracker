package de.zbidi.jobtracker.jobapplication;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NewJobApplicationTest {

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
	void validRequestHasNoViolations() {
		assertThat(validator.validate(new NewJobApplication(1L, 2L, "Java Developer", "https://acme.example/jobs/1")))
				.isEmpty();
	}

	@Test
	void nullOptionalFieldsAreAllowed() {
		assertThat(validator.validate(new NewJobApplication(1L, null, "Java Developer", null))).isEmpty();
	}

	@Test
	void missingCompanyIdIsRejected() {
		assertThat(violatedFields(new NewJobApplication(null, null, "Java Developer", null)))
				.containsExactly("companyId");
	}

	@Test
	void blankPositionIsRejected() {
		assertThat(violatedFields(new NewJobApplication(1L, null, "", null))).containsExactly("position");
	}

	@Test
	void positionLongerThan200IsRejected() {
		assertThat(violatedFields(new NewJobApplication(1L, null, "x".repeat(201), null)))
				.containsExactly("position");
	}

	@Test
	void invalidJobUrlIsRejected() {
		assertThat(violatedFields(new NewJobApplication(1L, null, "Java Developer", "acme jobs")))
				.containsExactly("jobUrl");
	}

	private List<String> violatedFields(NewJobApplication request) {
		return validator.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.toList();
	}

}
