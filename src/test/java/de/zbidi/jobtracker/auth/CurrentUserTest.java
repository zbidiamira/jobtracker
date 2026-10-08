package de.zbidi.jobtracker.auth;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The owner of every request is the token's {@code sub} claim (the user id written by TokenService).
 */
class CurrentUserTest {

	@Test
	void idIsTheSubjectClaim() {
		assertThat(CurrentUser.id(jwt("42"))).isEqualTo(42L);
	}

	@Test
	void nonNumericSubjectIsAnAuthenticationError() {
		// a correctly signed token we didn't issue (or an old format): 401, never a 500
		assertThatExceptionOfType(AuthenticationException.class)
				.isThrownBy(() -> CurrentUser.id(jwt("alice@example.com")))
				.withMessageContaining("subject");
	}

	@Test
	void missingTokenIsAnAuthenticationError() {
		assertThatExceptionOfType(AuthenticationException.class).isThrownBy(() -> CurrentUser.id(null));
	}

	private static Jwt jwt(String subject) {
		return Jwt.withTokenValue("token").header("alg", "RS256").subject(subject)
				.issuedAt(Instant.parse("2026-10-07T10:00:00Z")).expiresAt(Instant.parse("2026-10-07T11:00:00Z"))
				.build();
	}

}
