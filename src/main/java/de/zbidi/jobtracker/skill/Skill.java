package de.zbidi.jobtracker.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A skill of one user. Only the owner's id is kept: a skill is never shown together with its user.
 */
@Entity
@Table(name = "skill")
public class Skill {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long ownerId;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false)
	private int years;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private SkillLevel level;

	protected Skill() {
		// for JPA
	}

	public Skill(Long ownerId, String name, int years, SkillLevel level) {
		this.ownerId = ownerId;
		change(name, years, level);
	}

	public void change(String name, int years, SkillLevel level) {
		this.name = name.strip();
		this.years = years;
		this.level = level;
	}

	public Long getId() {
		return id;
	}

	public Long getOwnerId() {
		return ownerId;
	}

	public String getName() {
		return name;
	}

	public int getYears() {
		return years;
	}

	public SkillLevel getLevel() {
		return level;
	}

}
