package de.zbidi.jobtracker.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import de.zbidi.jobtracker.config.JwtProperties;
import de.zbidi.jobtracker.user.Role;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/**
 * Issues access tokens: {@code sub} = user id, {@code roles} = [role], {@code exp} = now + ttl, signed with RS256.
 */
@Service
public class TokenService {

	private final JwtEncoder jwtEncoder;
	private final Clock clock;
	private final JwtProperties properties;

	public TokenService(JwtEncoder jwtEncoder, Clock clock, JwtProperties properties) {
		this.jwtEncoder = jwtEncoder;
		this.clock = clock;
		this.properties = properties;
	}

	public AccessToken issue(Long userId, Role role) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(properties.issuer())
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(now.plus(properties.ttl()))
				.claim("roles", List.of(role.name()))
				.build();
		JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
		String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
		return new AccessToken(token, properties.ttl().toSeconds());
	}

}
