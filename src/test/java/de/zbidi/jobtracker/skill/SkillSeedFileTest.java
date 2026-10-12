package de.zbidi.jobtracker.skill;

import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the editable seed file: it must stay loadable after manual changes.
 */
class SkillSeedFileTest {

	List<SkillSeeder.SeedSkill> skills = SkillSeeder.parse(new ClassPathResource("seed/skills.csv"));

	@Test
	void containsAmirasSkills() {
		assertThat(skills).extracting(SkillSeeder.SeedSkill::name).containsExactly(
				"Java", "Spring Boot", "Spring Batch", "Java EE", "Quarkus", "Hibernate/JPA", "REST", "Angular",
				"React", "TypeScript", "PostgreSQL", "Oracle", "Flyway", "Docker", "OpenShift", "Jenkins", "JUnit",
				"Mockito", "Testcontainers", "Kafka", "Claude Code");
	}

	@Test
	void namesAreUniqueIgnoringCaseAndFitTheColumn() {
		assertThat(skills).extracting(skill -> skill.name().toLowerCase(Locale.ROOT)).doesNotHaveDuplicates();
		assertThat(skills).allSatisfy(skill -> assertThat(skill.name()).isNotBlank().hasSizeLessThanOrEqualTo(100));
	}

	@Test
	void yearsAndLevelsAreValid() {
		assertThat(skills).allSatisfy(skill -> {
			assertThat(skill.years()).isBetween(0, 60);
			assertThat(skill.level()).isNotNull();
		});
	}

}
