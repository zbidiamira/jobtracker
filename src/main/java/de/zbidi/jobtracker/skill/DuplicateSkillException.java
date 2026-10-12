package de.zbidi.jobtracker.skill;

/** The user already has a skill with this name (ignoring case). */
public class DuplicateSkillException extends RuntimeException {

	public DuplicateSkillException(String name) {
		super("You already have the skill '%s'".formatted(name));
	}

}
