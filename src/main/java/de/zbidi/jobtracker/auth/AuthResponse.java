package de.zbidi.jobtracker.auth;

/**
 * Send the token as {@code Authorization: Bearer <accessToken>}.
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

	static AuthResponse bearer(AccessToken token) {
		return new AuthResponse(token.value(), "Bearer", token.expiresInSeconds());
	}

}
