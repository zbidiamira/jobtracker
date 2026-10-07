package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;

public record JobApplicationResponse(
		Long id,
		Long companyId,
		String companyName,
		Long recruiterId,
		String position,
		Status status,
		String jobUrl,
		Long version,
		Instant createdAt,
		Instant updatedAt) {

	/** Must be called inside a transaction: reads the lazy {@code company}. */
	public static JobApplicationResponse from(JobApplication application) {
		return new JobApplicationResponse(
				application.getId(),
				application.getCompany().getId(),
				application.getCompany().getName(),
				application.getRecruiter() == null ? null : application.getRecruiter().getId(),
				application.getPosition(),
				application.getStatus(),
				application.getJobUrl(),
				application.getVersion(),
				application.getCreatedAt(),
				application.getUpdatedAt());
	}

}
