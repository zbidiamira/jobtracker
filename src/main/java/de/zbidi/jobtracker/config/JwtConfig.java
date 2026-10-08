package de.zbidi.jobtracker.config;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.util.StringUtils;

/**
 * The app signs its own tokens (private key) and validates them as an OAuth2 resource server (public key).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

	private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

	@Bean
	KeyPair jwtKeyPair(JwtProperties properties, ResourceLoader resourceLoader) {
		return loadKeyPair(properties, resourceLoader);
	}

	/**
	 * In this order: inline PEM ({@code public-key}/{@code private-key}, typically env vars), PEM files
	 * ({@code public-key-location}/{@code private-key-location}, typically mounted secrets), or, if nothing is
	 * configured, a temporary generated key pair (dev only). Misconfiguration fails at startup, not at the first login.
	 */
	public static KeyPair loadKeyPair(JwtProperties properties, ResourceLoader resourceLoader) {
		boolean inline = bothOrNeither(properties.publicKey(), properties.privateKey(), "public-key", "private-key");
		boolean files = bothOrNeither(properties.publicKeyLocation(), properties.privateKeyLocation(),
				"public-key-location", "private-key-location");
		if (inline && files) {
			throw new IllegalStateException("Configure the JWT keys either inline (jobtracker.jwt.public-key/private-key) "
					+ "or as files (jobtracker.jwt.public-key-location/private-key-location), not both");
		}
		if (inline) {
			return new KeyPair(
					RsaKeyConverters.x509().convert(stream(properties.publicKey())),
					RsaKeyConverters.pkcs8().convert(stream(properties.privateKey())));
		}
		if (files) {
			return new KeyPair(
					RsaKeyConverters.x509().convert(stream(read(resourceLoader, properties.publicKeyLocation()))),
					RsaKeyConverters.pkcs8().convert(stream(read(resourceLoader, properties.privateKeyLocation()))));
		}
		log.warn("No JWT keys configured (jobtracker.jwt.*): generated a temporary RSA key pair. "
				+ "Tokens become invalid after a restart and are not shared between instances.");
		return generateRsaKeyPair();
	}

	@Bean
	JwtEncoder jwtEncoder(KeyPair jwtKeyPair) {
		return NimbusJwtEncoder.withKeyPair((RSAPublicKey) jwtKeyPair.getPublic(), (RSAPrivateKey) jwtKeyPair.getPrivate())
				.build();
	}

	@Bean
	JwtDecoder jwtDecoder(KeyPair jwtKeyPair, JwtProperties properties, ObjectProvider<Clock> clock) {
		return jwtDecoder((RSAPublicKey) jwtKeyPair.getPublic(), properties.issuer(), clock.getIfAvailable(Clock::systemUTC));
	}

	/**
	 * Checks the RS256 signature, that {@code exp} is present and not passed (no clock-skew allowance), and the issuer.
	 */
	public static JwtDecoder jwtDecoder(RSAPublicKey publicKey, String issuer, Clock clock) {
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).signatureAlgorithm(SignatureAlgorithm.RS256).build();
		JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
		timestamps.setClock(clock);
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				timestamps,
				new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
				new JwtIssuerValidator(issuer)));
		return decoder;
	}

	private static boolean bothOrNeither(String first, String second, String firstName, String secondName) {
		boolean hasFirst = StringUtils.hasText(first);
		if (hasFirst != StringUtils.hasText(second)) {
			throw new IllegalStateException("Configure both jobtracker.jwt.%s and jobtracker.jwt.%s, or neither"
					.formatted(firstName, secondName));
		}
		return hasFirst;
	}

	private static InputStream stream(String pem) {
		return new ByteArrayInputStream(pem.getBytes(StandardCharsets.UTF_8));
	}

	/** Reads the whole file and closes it (RsaKeyConverters doesn't close the streams it's given). */
	private static String read(ResourceLoader resourceLoader, String location) {
		Resource resource = resourceLoader.getResource(location);
		try (InputStream in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Can't read JWT key from " + location, ex);
		}
	}

	private static KeyPair generateRsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("RSA not available", ex);
		}
	}

}
