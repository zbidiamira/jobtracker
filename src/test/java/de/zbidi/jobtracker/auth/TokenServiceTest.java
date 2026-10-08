package de.zbidi.jobtracker.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import de.zbidi.jobtracker.config.JwtConfig;
import de.zbidi.jobtracker.config.JwtProperties;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Real signing and verification with a throw-away RSA key pair; no Spring.
 */
class TokenServiceTest {

	private static final Instant T0 = Instant.parse("2026-10-07T10:00:00Z");
	private static final JwtProperties PROPERTIES = new JwtProperties(null, null, null, null, "jobtracker", Duration.ofHours(1));

	private final KeyPair keys = rsaKeyPair();
	private final TokenService tokenService = new TokenService(
			NimbusJwtEncoder.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()).build(),
			Clock.fixed(T0, ZoneOffset.UTC), PROPERTIES);

	@Test
	void tokenContainsSubjectRolesAndExpiry() {
		AccessToken token = tokenService.issue(7L, Role.USER);

		Jwt jwt = decoderAt(T0, keys).decode(token.value());

		assertThat(jwt.getSubject()).isEqualTo("7");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
		assertThat(jwt.getIssuedAt()).isEqualTo(T0);
		assertThat(jwt.getExpiresAt()).isEqualTo(T0.plus(Duration.ofHours(1)));
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("jobtracker");
		assertThat(token.expiresInSeconds()).isEqualTo(3600);
	}

	@Test
	void adminTokenCarriesAdminRole() {
		Jwt jwt = decoderAt(T0, keys).decode(tokenService.issue(1L, Role.ADMIN).value());

		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("ADMIN");
	}

	@Test
	void tokenSignedWithOtherKeyIsRejected() {
		String token = tokenService.issue(7L, Role.USER).value();

		assertThatExceptionOfType(JwtException.class)
				.isThrownBy(() -> decoderAt(T0, rsaKeyPair()).decode(token));
	}

	@Test
	void expiredTokenIsRejected() {
		String token = tokenService.issue(7L, Role.USER).value();

		assertThatExceptionOfType(JwtException.class)
				.isThrownBy(() -> decoderAt(T0.plus(Duration.ofHours(1)).plusSeconds(1), keys).decode(token))
				.withMessageContaining("expired");
	}

	@Test
	void tokenFromOtherIssuerIsRejected() {
		TokenService otherIssuer = new TokenService(
				NimbusJwtEncoder.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()).build(),
				Clock.fixed(T0, ZoneOffset.UTC), new JwtProperties(null, null, null, null, "someone-else", Duration.ofHours(1)));

		assertThatExceptionOfType(JwtException.class)
				.isThrownBy(() -> decoderAt(T0, keys).decode(otherIssuer.issue(7L, Role.USER).value()));
	}

	private static JwtDecoder decoderAt(Instant now, KeyPair keys) {
		return JwtConfig.jwtDecoder((RSAPublicKey) keys.getPublic(), "jobtracker", Clock.fixed(now, ZoneOffset.UTC));
	}

	private static KeyPair rsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
