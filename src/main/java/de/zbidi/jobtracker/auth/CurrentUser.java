package de.zbidi.jobtracker.auth;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

/**
 * The calling user: the {@code sub} claim of the access token, which TokenService sets to the user id.
 * Anything else is an authentication problem (401), never a server error.
 */
public final class CurrentUser {

	private CurrentUser() {
	}

	/**
	 * @param jwt the authenticated principal, e.g. from {@code @AuthenticationPrincipal Jwt jwt}
	 * @return the user id, used as owner for all job application queries
	 */
	public static Long id(Jwt jwt) {
		if (jwt == null) {
			throw new AuthenticationCredentialsNotFoundException("No authenticated user");
		}
		try {
			return Long.valueOf(jwt.getSubject());
		}
		catch (NumberFormatException ex) {
			throw new InvalidBearerTokenException("Token subject is not a user id", ex);
		}
	}

}
