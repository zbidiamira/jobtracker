package de.zbidi.jobtracker.skill;

import de.zbidi.jobtracker.user.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * The seed only exists in the dev profile: never in tests, cloud or production.
 */
class SkillSeederProfileTest {

	ApplicationContextRunner context = new ApplicationContextRunner()
			.withBean(AppUserRepository.class, () -> mock(AppUserRepository.class))
			.withBean(SkillRepository.class, () -> mock(SkillRepository.class))
			.withPropertyValues("jobtracker.seed.skills.owner-email=someone@example.com")
			.withUserConfiguration(SkillSeeder.class);

	@Test
	void activeInTheDevProfile() {
		context.withPropertyValues("spring.profiles.active=dev")
				.run(ctx -> assertThat(ctx).hasSingleBean(SkillSeeder.class));
	}

	@Test
	void absentWithoutAProfile() {
		context.run(ctx -> assertThat(ctx).doesNotHaveBean(SkillSeeder.class));
	}

	@Test
	void absentInOtherProfiles() {
		context.withPropertyValues("spring.profiles.active=local,cloud")
				.run(ctx -> assertThat(ctx).doesNotHaveBean(SkillSeeder.class));
	}

}
