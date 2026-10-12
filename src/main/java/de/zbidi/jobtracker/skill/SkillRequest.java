package de.zbidi.jobtracker.skill;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Create or replace a skill. */
public record SkillRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull @Min(0) @Max(60) Integer years,
		@NotNull SkillLevel level) {
}
