package de.zbidi.jobtracker.auth;

import java.util.List;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestTest {

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
		assertThat(validator.validate(new RegisterRequest("alice@example.com", "s3cret-pass"))).isEmpty();
	}

	@Test
	void blankEmailIsRejected() {
		assertThat(violatedFields(new RegisterRequest(" ", "s3cret-pass"))).contains("email");
	}

	@Test
	void invalidEmailIsRejected() {
		assertThat(violatedFields(new RegisterRequest("not-an-email", "s3cret-pass"))).containsExactly("email");
	}

	@Test
	void passwordShorterThan8IsRejected() {
		assertThat(violatedFields(new RegisterRequest("alice@example.com", "short"))).containsExactly("password");
	}

	@Test
	void passwordLongerThan72IsRejected() {
		// BCrypt only uses the first 72 bytes; longer passwords would silently be truncated
		assertThat(violatedFields(new RegisterRequest("alice@example.com", "x".repeat(73)))).containsExactly("password");
	}

	private List<String> violatedFields(RegisterRequest request) {
		return validator.validate(request).stream()
				.map(ConstraintViolation::getPropertyPath)
				.map(Object::toString)
				.toList();
	}

}
