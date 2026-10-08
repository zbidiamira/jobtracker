package de.zbidi.jobtracker.auth;

/**
 * A signed JWT and how many seconds it stays valid.
 */
public record AccessToken(String value, long expiresInSeconds) {
}
