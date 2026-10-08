package de.zbidi.jobtracker.config;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The converter the resource server applies to every validated token: the {@code roles} claim decides the
 * authorities, so {@code hasRole("ADMIN")} works. (The @WebMvcTests use TestJwt, which sets authorities directly.)
 */
class RolesClaimConverterTest {

	private final JwtAuthenticationConverter converter = SecurityConfig.jwtAuthenticationConverter();

	@Test
	void userRoleBecomesRoleUser() {
		assertThat(roles(token("7", List.of("USER")))).containsExactly("ROLE_USER");
	}

	@Test
	void adminRoleBecomesRoleAdmin() {
		assertThat(roles(token("1", List.of("ADMIN")))).containsExactly("ROLE_ADMIN");
	}

	@Test
	void noRolesClaimMeansNoAuthorities() {
		assertThat(roles(token("7", null))).isEmpty();
	}

	/** Spring Security 7 records how the user authenticated (multi-factor support): here, with a bearer token. */
	@Test
	void bearerFactorIsAddedBySpringSecurity() {
		assertThat(authorities(token("7", List.of("USER")))).containsExactlyInAnyOrder("ROLE_USER", "FACTOR_BEARER");
	}

	@Test
	void principalNameIsTheSubject() {
		assertThat(converter.convert(token("42", List.of("USER"))).getName()).isEqualTo("42");
	}

	/** Only the role authorities (what hasRole(...) checks). */
	private List<String> roles(Jwt jwt) {
		return authorities(jwt).stream().filter(authority -> authority.startsWith("ROLE_")).toList();
	}

	private List<String> authorities(Jwt jwt) {
		AbstractAuthenticationToken authentication = converter.convert(jwt);
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}

	private static Jwt token(String subject, List<String> roles) {
		Jwt.Builder builder = Jwt.withTokenValue("token")
				.header("alg", "RS256")
				.subject(subject)
				.issuedAt(Instant.parse("2026-10-07T10:00:00Z"))
				.expiresAt(Instant.parse("2026-10-07T11:00:00Z"));
		if (roles != null) {
			builder.claim("roles", roles);
		}
		return builder.build();
	}

}
