package de.zbidi.jobtracker.skill;

public record SkillResponse(Long id, String name, int years, SkillLevel level) {

	static SkillResponse from(Skill skill) {
		return new SkillResponse(skill.getId(), skill.getName(), skill.getYears(), skill.getLevel());
	}

}
