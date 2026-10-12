package de.zbidi.jobtracker.jobsource;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The settings bind and both portals become JobSource beans with their own RestClient.
 */
class JobSourceConfigTest {

	ApplicationContextRunner context = new ApplicationContextRunner()
			.withUserConfiguration(JobSourceConfig.class)
			.withPropertyValues(
					"jobtracker.jobs.fetch-cron=0 0 7 * * *",
					"jobtracker.jobs.keywords=Java,Spring",
					"jobtracker.jobs.max-per-source=30",
					"jobtracker.jobs.arbeitnow.enabled=true",
					"jobtracker.jobs.arbeitnow.base-url=https://www.arbeitnow.com",
					"jobtracker.jobs.arbeitnow.pages=2",
					"jobtracker.jobs.bundesagentur.enabled=false",
					"jobtracker.jobs.bundesagentur.base-url=https://rest.arbeitsagentur.de/jobboerse/jobsuche-service",
					"jobtracker.jobs.bundesagentur.api-key=jobboerse-jobsuche",
					"jobtracker.jobs.bundesagentur.was=Java",
					"jobtracker.jobs.bundesagentur.wo=Berlin",
					"jobtracker.jobs.bundesagentur.umkreis=50",
					"jobtracker.jobs.bundesagentur.published-within-days=1",
					"jobtracker.jobs.bundesagentur.request-delay=200ms");

	@Test
	void bindsTheSettingsAndCreatesBothSources() {
		context.run(ctx -> {
			JobSourceProperties properties = ctx.getBean(JobSourceProperties.class);
			assertThat(properties.keywords()).containsExactly("Java", "Spring");
			assertThat(properties.bundesagentur().requestDelay().toMillis()).isEqualTo(200);
			assertThat(ctx.getBeansOfType(JobSource.class).values())
					.extracting(JobSource::name, JobSource::enabled)
					.containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("arbeitnow", true),
							org.assertj.core.groups.Tuple.tuple("bundesagentur", false));
		});
	}

}
