package de.zbidi.jobtracker;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real tokens end to end: issued by POST /api/auth/*, signed with the app's RSA key, checked by the resource server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthFlowIntegrationTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtEncoder appJwtEncoder;

	@Test
	void registerThenLoginThenCallApi() {
		String email = uniqueEmail();
		String fromRegister = ApiAuth.register(mvc, email);
		String fromLogin = ApiAuth.login(mvc, email);

		assertThat(get("/api/applications", fromRegister)).hasStatus(HttpStatus.OK);
		assertThat(get("/api/applications", fromLogin)).hasStatus(HttpStatus.OK);
	}

	@Test
	void storedPasswordIsBCryptHash() {
		String email = uniqueEmail();
		ApiAuth.register(mvc, email);

		AppUser stored = appUserRepository.findByEmailIgnoreCase(email).orElseThrow();
		assertThat(stored.getPasswordHash()).startsWith("$2").isNotEqualTo(ApiAuth.PASSWORD);
		assertThat(passwordEncoder.matches(ApiAuth.PASSWORD, stored.getPasswordHash())).isTrue();
	}

	@Test
	void loginWithWrongPasswordReturns401() {
		String email = uniqueEmail();
		ApiAuth.register(mvc, email);

		MvcTestResult result = mvc.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"wrong-password\"}".formatted(email)).exchange();

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
	}

	@Test
	void registeringTheSameEmailTwiceReturns409() {
		String email = uniqueEmail();
		ApiAuth.register(mvc, email);

		MvcTestResult again = mvc.post().uri("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"%s\", \"password\": \"another-pass\"}".formatted(email.toUpperCase())).exchange();

		assertThat(again).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void tokenSignedWithAnotherKeyIsRejected() throws Exception {
		KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(2048);
		var keys = generator.generateKeyPair();
		JwtEncoder attacker = NimbusJwtEncoder.withKeyPair((RSAPublicKey) keys.getPublic(), (RSAPrivateKey) keys.getPrivate()).build();

		// looks perfect (ADMIN even), but the signature doesn't match the app's key
		String forged = encode(attacker, Instant.now(), Duration.ofHours(1), "ADMIN");

		assertThat(get("/api/applications", "Bearer " + forged)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void expiredTokenIsRejected() {
		// correctly signed with the app's own key, but expired an hour ago
		String expired = encode(appJwtEncoder, Instant.now().minus(Duration.ofHours(2)), Duration.ofHours(1), "USER");

		MvcTestResult result = get("/api/applications", "Bearer " + expired);

		assertThat(result).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(result.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).contains("invalid_token");
	}

	private static String encode(JwtEncoder encoder, Instant issuedAt, Duration ttl, String role) {
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer("jobtracker")
				.subject("1")
				.issuedAt(issuedAt)
				.expiresAt(issuedAt.plus(ttl))
				.claim("roles", List.of(role))
				.build();
		return encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
	}

	private MvcTestResult get(String uri, String authorization) {
		return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, authorization).exchange();
	}

	private static String uniqueEmail() {
		return "flow-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
	}

}
