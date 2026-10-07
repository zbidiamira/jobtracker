package de.zbidi.jobtracker.jobapplication;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Search filters for job applications; every field is optional and they are combined with AND.
 * Bound from query parameters, e.g. {@code /search?status=APPLIED&companyName=acme&createdFrom=2026-10-01}.
 *
 * @param companyName part of the company name, case-insensitive
 * @param position    part of the position title, case-insensitive
 * @param createdFrom first day (inclusive), in the business time zone (Europe/Berlin)
 * @param createdTo   last day (inclusive), in the business time zone (Europe/Berlin)
 */
public record JobApplicationSearch(
		Status status,
		@Size(max = 200) String companyName,
		@Size(max = 200) String position,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo) {

	/** No filters: matches every application. */
	public static final JobApplicationSearch NONE = new JobApplicationSearch(null, null, null, null, null);

	@AssertTrue(message = "createdFrom must not be after createdTo")
	boolean isDateRangeValid() {
		return createdFrom == null || createdTo == null || !createdFrom.isAfter(createdTo);
	}

}
