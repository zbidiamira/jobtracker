package de.zbidi.jobtracker.analysis;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The AI settings as written in the YAML files: safe defaults, no key in the repository, short retries.
 */
class AiConfigTest {

	@Test
	void withoutAProfileNoModelIsUsed() throws IOException {
		assertThat(load("application.yaml").getProperty("spring.ai.model.chat")).isEqualTo("none");
	}

	@Test
	void retriesAreFewAndShortAndNeverOnClientErrors() throws IOException {
		PropertySource<?> app = load("application.yaml");

		// Spring AI's default is 10 attempts with up to 3 minutes between them: far beyond the 30 s timeout
		assertThat(app.getProperty("spring.ai.retry.max-attempts")).isEqualTo(2);
		assertThat(app.getProperty("spring.ai.retry.backoff.initial-interval")).isEqualTo("1s");
		assertThat(app.getProperty("spring.ai.retry.on-client-errors")).isEqualTo(false);
		assertThat(app.getProperty("jobtracker.ai.timeout")).isEqualTo("30s");
	}

	@Test
	void localProfileUsesOllama() throws IOException {
		PropertySource<?> local = load("application-local.yaml");

		assertThat(local.getProperty("spring.ai.model.chat")).isEqualTo("ollama");
		assertThat(local.getProperty("spring.ai.ollama.chat.model")).isEqualTo("qwen2.5:3b");
	}

	@Test
	void cloudProfileReadsTheKeyOnlyFromTheEnvironment() throws IOException {
		PropertySource<?> cloud = load("application-cloud.yaml");

		assertThat(cloud.getProperty("spring.ai.model.chat")).isEqualTo("anthropic");
		// exactly the placeholder, without a default: a key can never end up in git through this file
		assertThat(cloud.getProperty("spring.ai.anthropic.api-key")).isEqualTo("${ANTHROPIC_API_KEY}");
	}

	private static PropertySource<?> load(String file) throws IOException {
		List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
		assertThat(sources).hasSize(1);
		return sources.getFirst();
	}

}
