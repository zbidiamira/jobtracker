package de.zbidi.jobtracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: http://localhost:8081/swagger-ui/index.html
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

	@Bean
	OpenAPI jobTrackerOpenApi() {
		return new OpenAPI().info(new Info()
				.title("JobTracker API")
				.description("Track companies, recruiters and job applications")
				.version("v1"));
	}

}
