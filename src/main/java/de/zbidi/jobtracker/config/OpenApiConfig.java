package de.zbidi.jobtracker.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8081/swagger-ui/index.html
 * Get a token from POST /api/auth/login, click "Authorize" and paste it (without "Bearer ").
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	private static final String BEARER_AUTH = "bearerAuth";

	@Bean
	OpenAPI jobTrackerOpenApi() {
		return new OpenAPI()
				.info(new Info()
						.title("JobTracker API")
						.description("Track companies, recruiters and job applications")
						.version("v1"))
				.components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
						.type(SecurityScheme.Type.HTTP)
						.scheme("bearer")
						.bearerFormat("JWT")))
				// applies to all operations; Swagger UI then sends the token with every request
				.addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
	}

}
