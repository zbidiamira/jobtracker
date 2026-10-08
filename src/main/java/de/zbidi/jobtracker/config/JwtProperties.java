package de.zbidi.jobtracker.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code jobtracker.jwt.*}. Provide the RSA key pair <b>either</b> inline <b>or</b> as files, or neither (dev only:
 * a temporary key pair is generated at startup).
 *
 * @param publicKey          RSA public key as PEM text ("-----BEGIN PUBLIC KEY-----"), e.g. from env JOBTRACKER_JWT_PUBLIC_KEY
 * @param privateKey         RSA private key as PKCS#8 PEM text ("-----BEGIN PRIVATE KEY-----"), env JOBTRACKER_JWT_PRIVATE_KEY
 * @param publicKeyLocation  where to read the public key PEM, e.g. {@code file:/run/secrets/jwt-public.pem}
 * @param privateKeyLocation where to read the private key PEM, e.g. {@code file:/run/secrets/jwt-private.pem}
 * @param issuer             the {@code iss} claim written and required
 * @param ttl                how long a token is valid
 */
@ConfigurationProperties("jobtracker.jwt")
public record JwtProperties(
		String publicKey,
		String privateKey,
		String publicKeyLocation,
		String privateKeyLocation,
		@DefaultValue("jobtracker") String issuer,
		@DefaultValue("1h") Duration ttl) {
}
