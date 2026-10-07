package de.zbidi.jobtracker.company;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

public record CreateCompanyRequest(
		@NotBlank @Size(max = 200) String name,
		@Size(max = 100) String city,
		@Size(max = 300) @URL(regexp = "^https?://.*") String website) {
}
