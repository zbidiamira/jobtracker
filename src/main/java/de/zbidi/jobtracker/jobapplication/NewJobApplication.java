package de.zbidi.jobtracker.jobapplication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Input for creating a job application. {@code recruiterId} and {@code jobUrl} are optional.
 */
public record NewJobApplication(
		@NotNull Long companyId,
		Long recruiterId,
		@NotBlank @Size(max = 200) String position,
		@Size(max = 500) @URL(regexp = "^https?://.*") String jobUrl) {
}
