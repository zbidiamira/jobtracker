package de.zbidi.jobtracker.analysis;

import java.util.List;
import java.util.Map;

import de.zbidi.jobtracker.skill.Skill;
import de.zbidi.jobtracker.skill.SkillLevel;
import de.zbidi.jobtracker.skill.SkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SkillToolsTest {

	SkillRepository repository = mock(SkillRepository.class);
	SkillTools tools = new SkillTools(repository);

	@Test
	void returnsTheSkillsOfTheUserInTheToolContext() {
		given(repository.findByOwnerIdOrderByNameAsc(7L)).willReturn(List.of(
				new Skill(7L, "Java", 6, SkillLevel.EXPERT), new Skill(7L, "Kafka", 1, SkillLevel.BEGINNER)));

		assertThat(tools.getMySkills(new ToolContext(Map.of(SkillTools.USER_ID, 7L)))).containsExactly(
				new SkillTools.SkillView("Java", 6, SkillLevel.EXPERT),
				new SkillTools.SkillView("Kafka", 1, SkillLevel.BEGINNER));
	}

	/** The user id comes from the server (tool context), never from the model: no context, no skills. */
	@Test
	void withoutUserIdInTheContextReturnsNothing() {
		assertThat(tools.getMySkills(new ToolContext(Map.of()))).isEmpty();
		assertThat(tools.getMySkills(new ToolContext(Map.of(SkillTools.USER_ID, "7")))).isEmpty();
		verifyNoInteractions(repository);
	}

	@Test
	void toolIsExposedAsGetMySkills() throws NoSuchMethodException {
		Tool tool = SkillTools.class.getMethod("getMySkills", ToolContext.class).getAnnotation(Tool.class);

		assertThat(tool.name()).isEqualTo("getMySkills");
		assertThat(tool.description()).isNotBlank();
	}

}
