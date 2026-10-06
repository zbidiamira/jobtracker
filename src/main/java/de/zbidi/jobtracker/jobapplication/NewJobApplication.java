package de.zbidi.jobtracker.jobapplication;

/**
 * Input for creating a job application. {@code recruiterId} and {@code jobUrl} are optional.
 */
public record NewJobApplication(Long companyId, Long recruiterId, String position, String jobUrl) {
}
