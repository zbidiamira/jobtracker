package de.zbidi.jobtracker.skill;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(OutputCaptureExtension.class)
class SkillSeederTest {

	static final String EMAIL = "zbidi.amira1994@gmail.com";
	static final ByteArrayResource SEED = csv("""
			# comment lines and blank lines are ignored
			name,years,level

			Java,8,EXPERT
			Kafka,1,BEGINNER
			Claude Code,1,INTERMEDIATE
			""");

	AppUserRepository users = mock(AppUserRepository.class);
	SkillRepository skills = mock(SkillRepository.class);

	@BeforeEach
	void ownerExists() {
		AppUser amira = new AppUser(EMAIL, "$2a$10$hash", Role.USER);
		ReflectionTestUtils.setField(amira, "id", 5L);
		given(users.findByEmailIgnoreCase(EMAIL)).willReturn(Optional.of(amira));
	}

	@Test
	void addsAllSkillsForTheOwner(CapturedOutput output) {
		seeder(EMAIL, SEED).run(new DefaultApplicationArguments());

		ArgumentCaptor<Skill> saved = ArgumentCaptor.forClass(Skill.class);
		verify(skills, times(3)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(Skill::getOwnerId).containsOnly(5L);
		assertThat(saved.getAllValues()).extracting(Skill::getName, Skill::getYears, Skill::getLevel)
				.containsExactly(tuple("Java", 8, SkillLevel.EXPERT),
						tuple("Kafka", 1, SkillLevel.BEGINNER),
						tuple("Claude Code", 1, SkillLevel.INTERMEDIATE));
		assertThat(output).contains("3 skill(s) added");
	}

	/** Runs at every start: skills that exist (maybe edited in the app since) are left alone. */
	@Test
	void existingSkillsAreNeverChanged(CapturedOutput output) {
		given(skills.existsByOwnerIdAndNameIgnoreCase(5L, "Java")).willReturn(true);

		seeder(EMAIL, SEED).run(new DefaultApplicationArguments());

		ArgumentCaptor<Skill> saved = ArgumentCaptor.forClass(Skill.class);
		verify(skills, times(2)).save(saved.capture());
		assertThat(saved.getAllValues()).extracting(Skill::getName).containsExactly("Kafka", "Claude Code");
		assertThat(output).contains("2 skill(s) added, 1 already present");
	}

	@Test
	void unknownOwnerIsSkippedWithAHint(CapturedOutput output) {
		seeder("nobody@example.com", SEED).run(new DefaultApplicationArguments());

		verify(skills, never()).save(any());
		verify(skills, never()).existsByOwnerIdAndNameIgnoreCase(anyLong(), anyString());
		assertThat(output).contains("WARN").contains("nobody@example.com").contains("register");
	}

	@Test
	void ownerEmailIsMatchedIgnoringCase() {
		seeder("Zbidi.Amira1994@Gmail.com", SEED).run(new DefaultApplicationArguments());

		verify(users).findByEmailIgnoreCase("Zbidi.Amira1994@Gmail.com");
	}

	@Test
	void brokenLineIsReportedWithItsNumber() {
		ByteArrayResource broken = csv("""
				name,years,level
				Java,8,EXPERT
				Kotlin,many,EXPERT
				""");

		assertThatThrownBy(() -> SkillSeeder.parse(broken))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("line 3");
	}

	/** Unknown emails: the mocked repository returns Optional.empty(). */
	private SkillSeeder seeder(String ownerEmail, ByteArrayResource seed) {
		return new SkillSeeder(users, skills, ownerEmail, seed);
	}

	private static ByteArrayResource csv(String content) {
		return new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8));
	}

}
