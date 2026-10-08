package de.zbidi.jobtracker;

import java.util.List;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/**
 * Authenticated requests for {@code @WebMvcTest}s: an already-validated JWT, the way the resource server would
 * present it (sub = user id, ROLE_ authorities from the roles claim). No signing involved.
 */
public final class TestJwt {

	private TestJwt() {
	}

	public static JwtRequestPostProcessor user(long userId) {
		return withRole(userId, "USER");
	}

	public static JwtRequestPostProcessor admin(long userId) {
		return withRole(userId, "ADMIN");
	}

	private static JwtRequestPostProcessor withRole(long userId, String role) {
		return jwt()
				.jwt(token -> token.subject(String.valueOf(userId)).claim("roles", List.of(role)))
				.authorities(new SimpleGrantedAuthority("ROLE_" + role));
	}

}
