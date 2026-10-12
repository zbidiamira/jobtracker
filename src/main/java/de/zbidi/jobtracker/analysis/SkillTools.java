package de.zbidi.jobtracker.analysis;

import java.util.List;

import de.zbidi.jobtracker.skill.SkillLevel;
import de.zbidi.jobtracker.skill.SkillRepository;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

/**
 * Tools the model may call during an analysis. The user is taken from the {@link ToolContext}, which the server
 * fills from the token and which is never sent to the model: the model can't ask for another user's skills.
 */
@Component
public class SkillTools {

	static final String USER_ID = "userId";

	/** What the model sees of a skill. */
	public record SkillView(String name, int years, SkillLevel level) {
	}

	private final SkillRepository skillRepository;

	public SkillTools(SkillRepository skillRepository) {
		this.skillRepository = skillRepository;
	}

	@Tool(name = "getMySkills", description = """
			Returns the skills of the user who asked for this analysis: name, years of experience and level \
			(BEGINNER, INTERMEDIATE, ADVANCED, EXPERT). Takes no arguments.""")
	public List<SkillView> getMySkills(ToolContext toolContext) {
		if (!(toolContext.getContext().get(USER_ID) instanceof Long userId)) {
			return List.of();
		}
		return skillRepository.findByOwnerIdOrderByNameAsc(userId).stream()
				.map(skill -> new SkillView(skill.getName(), skill.getYears(), skill.getLevel()))
				.toList();
	}

}
