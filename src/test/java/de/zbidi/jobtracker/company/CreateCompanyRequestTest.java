package de.zbidi.jobtracker.company;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CreateCompanyRequestTest {

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
		assertThat(validator.validate(new CreateCompanyRequest("ACME GmbH", "Berlin", "https://acme.example")))
				.isEmpty();
	}

	@Test
	void nullOptionalFieldsAreAllowed() {
		assertThat(validator.validate(new CreateCompanyRequest("ACME GmbH", null, null))).isEmpty();
	}

	@Test
	void blankNameIsRejected() {
		assertThat(violatedFields(new CreateCompanyRequest("  ", null, null))).containsExactly("name");
	}

	@Test
	void nameLongerThan200IsRejected() {
		assertThat(violatedFields(new CreateCompanyRequest("x".repeat(201), null, null))).containsExactly("name");
	}

	@Test
	void cityLongerThan100IsRejected() {
		assertThat(violatedFields(new CreateCompanyRequest("ACME", "x".repeat(101), null))).containsExactly("city");
	}

	@Test
	void invalidWebsiteUrlIsRejected() {
		assertThat(violatedFields(new CreateCompanyRequest("ACME", null, "not a url"))).containsExactly("website");
	}

	private List<String> violatedFields(CreateCompanyRequest request) {
		return validator.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.toList();
	}

}
