package de.zbidi.jobtracker.config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Where the signing keys come from: inline PEM (env var), PEM files (mounted secrets), or generated for dev.
 */
class JwtKeyLoadingTest {

	private static final Duration TTL = Duration.ofHours(1);

	private final KeyPair keys = rsaKeyPair();

	@TempDir
	Path dir;

	@Test
	void loadsKeysFromFiles() throws Exception {
		Path publicPem = Files.writeString(dir.resolve("public.pem"), publicPem(keys));
		Path privatePem = Files.writeString(dir.resolve("private.pem"), privatePem(keys));

		KeyPair loaded = load(new JwtProperties(null, null, "file:" + publicPem, "file:" + privatePem, "jobtracker", TTL));

		assertThat(loaded.getPublic().getEncoded()).isEqualTo(keys.getPublic().getEncoded());
		assertThat(loaded.getPrivate().getEncoded()).isEqualTo(keys.getPrivate().getEncoded());
		assertThat(signAndVerify(loaded)).isEqualTo("7");
	}

	@Test
	void loadsKeysFromInlinePem() {
		KeyPair loaded = load(new JwtProperties(publicPem(keys), privatePem(keys), null, null, "jobtracker", TTL));

		assertThat(loaded.getPublic().getEncoded()).isEqualTo(keys.getPublic().getEncoded());
		assertThat(signAndVerify(loaded)).isEqualTo("7");
	}

	@Test
	void generatesTemporaryKeysWhenNothingConfigured() {
		KeyPair generated = load(new JwtProperties(null, null, null, null, "jobtracker", TTL));

		assertThat(generated.getPublic()).isInstanceOf(RSAPublicKey.class);
		assertThat(((RSAPublicKey) generated.getPublic()).getModulus().bitLength()).isEqualTo(2048);
		assertThat(signAndVerify(generated)).isEqualTo("7");
	}

	@Test
	void failsFastWhenOnlyOneKeyConfigured() {
		assertThatIllegalStateException()
				.isThrownBy(() -> load(new JwtProperties(publicPem(keys), null, null, null, "jobtracker", TTL)))
				.withMessageContaining("both");
	}

	@Test
	void failsFastWhenInlineAndFileAreMixed() {
		assertThatIllegalStateException()
				.isThrownBy(() -> load(new JwtProperties(publicPem(keys), privatePem(keys), "file:/x.pem", "file:/y.pem", "jobtracker", TTL)))
				.withMessageContaining("either");
	}

	@Test
	void failsFastWhenKeyFileMissing() {
		String missing = "file:" + dir.resolve("does-not-exist.pem");

		assertThatIllegalStateException()
				.isThrownBy(() -> load(new JwtProperties(null, null, missing, missing, "jobtracker", TTL)))
				.withMessageContaining(missing);
	}

	private static KeyPair load(JwtProperties properties) {
		return JwtConfig.loadKeyPair(properties, new DefaultResourceLoader());
	}

	/** Signs a token with the private key and returns the subject after verifying it with the public key. */
	private static String signAndVerify(KeyPair pair) {
		Instant now = Instant.now();
		String token = NimbusJwtEncoder.withKeyPair((RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate()).build()
				.encode(JwtEncoderParameters.from(JwtClaimsSet.builder()
						.issuer("jobtracker").subject("7").issuedAt(now).expiresAt(now.plus(TTL)).build()))
				.getTokenValue();
		return JwtConfig.jwtDecoder((RSAPublicKey) pair.getPublic(), "jobtracker", Clock.systemUTC()).decode(token).getSubject();
	}

	private static String publicPem(KeyPair pair) {
		return pem("PUBLIC KEY", pair.getPublic().getEncoded());       // X.509 SubjectPublicKeyInfo
	}

	private static String privatePem(KeyPair pair) {
		return pem("PRIVATE KEY", pair.getPrivate().getEncoded());     // PKCS#8
	}

	private static String pem(String type, byte[] der) {
		String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
		return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
	}

	private static KeyPair rsaKeyPair() {
		try {
			KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
