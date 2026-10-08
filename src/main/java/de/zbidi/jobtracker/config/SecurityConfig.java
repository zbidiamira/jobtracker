package de.zbidi.jobtracker.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Stateless JWT resource server.
 * <ul>
 *     <li>public: /api/auth/**, Swagger UI, OpenAPI docs, /actuator/health</li>
 *     <li>DELETE: ADMIN only</li>
 *     <li>everything else: any authenticated user</li>
 * </ul>
 * 401 and 403 are answered with ProblemDetail by {@code GlobalExceptionHandler}, like every other error.
 */
@Configuration(proxyBeanMethods = false)
@Import(JwtConfig.class)
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		AuthenticationEntryPoint unauthorized = problemDetailEntryPoint(exceptionResolver);
		AccessDeniedHandler forbidden = problemDetailAccessDeniedHandler(exceptionResolver);
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
								"/actuator/health", "/actuator/health/**", "/error").permitAll()
						.requestMatchers(HttpMethod.DELETE, "/api/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(resourceServer -> resourceServer
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(unauthorized)
						.accessDeniedHandler(forbidden))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(unauthorized)
						.accessDeniedHandler(forbidden))
				// tokens travel in a header, not in cookies, so CSRF can't happen; no server-side session either
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
		return http.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	/** The token's {@code roles} claim (e.g. ["ADMIN"]) becomes the authority ROLE_ADMIN, so hasRole("ADMIN") works. */
	static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	/** 401: the standard {@code WWW-Authenticate: Bearer ...} header, plus a ProblemDetail body. */
	private static AuthenticationEntryPoint problemDetailEntryPoint(HandlerExceptionResolver exceptionResolver) {
		BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
		return (request, response, exception) -> {
			bearer.commence(request, response, exception);
			exceptionResolver.resolveException(request, response, null, exception);
		};
	}

	/** 403: the standard {@code WWW-Authenticate} header, plus a ProblemDetail body. */
	private static AccessDeniedHandler problemDetailAccessDeniedHandler(HandlerExceptionResolver exceptionResolver) {
		BearerTokenAccessDeniedHandler bearer = new BearerTokenAccessDeniedHandler();
		return (request, response, exception) -> {
			bearer.handle(request, response, exception);
			exceptionResolver.resolveException(request, response, null, exception);
		};
	}

}
