package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/**
 * Building blocks for searching job applications. Each method is one WHERE condition;
 * {@link #matching} combines the ones the user filled in with AND.
 */
public final class ApplicationSpecifications {

	private static final char ESCAPE = '\\';

	private ApplicationSpecifications() {
	}

	/**
	 * The owner condition is always included: a search never sees other users' applications.
	 */
	public static Specification<JobApplication> matching(Long ownerId, JobApplicationSearch search, ZoneId zone) {
		List<Specification<JobApplication>> conditions = new ArrayList<>();
		conditions.add(ownedBy(ownerId));
		if (search.status() != null) {
			conditions.add(hasStatus(search.status()));
		}
		if (hasText(search.companyName())) {
			conditions.add(companyNameContains(search.companyName()));
		}
		if (hasText(search.position())) {
			conditions.add(positionContains(search.position()));
		}
		if (search.createdFrom() != null) {
			conditions.add(createdOnOrAfter(search.createdFrom(), zone));
		}
		if (search.createdTo() != null) {
			conditions.add(createdOnOrBefore(search.createdTo(), zone));
		}
		return Specification.allOf(conditions);
	}

	/** {@code owner_id = ?}; the FK column is compared directly, no join needed. */
	public static Specification<JobApplication> ownedBy(Long ownerId) {
		return (root, query, cb) -> cb.equal(root.get("owner").get("id"), ownerId);
	}

	public static Specification<JobApplication> hasStatus(Status status) {
		return (root, query, cb) -> cb.equal(root.get("status"), status);
	}

	public static Specification<JobApplication> companyNameContains(String text) {
		return (root, query, cb) -> cb.like(cb.lower(root.join("company").get("name")), containsPattern(text), ESCAPE);
	}

	public static Specification<JobApplication> positionContains(String text) {
		return (root, query, cb) -> cb.like(cb.lower(root.get("position")), containsPattern(text), ESCAPE);
	}

	/** From the start of {@code day} in {@code zone}, inclusive. */
	public static Specification<JobApplication> createdOnOrAfter(LocalDate day, ZoneId zone) {
		Instant start = day.atStartOfDay(zone).toInstant();
		return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), start);
	}

	/** Up to the end of {@code day} in {@code zone}, inclusive (i.e. before the next day starts). */
	public static Specification<JobApplication> createdOnOrBefore(LocalDate day, ZoneId zone) {
		Instant nextDayStart = day.plusDays(1).atStartOfDay(zone).toInstant();
		return (root, query, cb) -> cb.lessThan(root.get("createdAt"), nextDayStart);
	}

	/** "%text%" in lower case, with LIKE wildcards in the user's input escaped so they match literally. */
	private static String containsPattern(String text) {
		String escaped = text.strip().toLowerCase(Locale.ROOT)
				.replace("\\", "\\\\")
				.replace("%", "\\%")
				.replace("_", "\\_");
		return "%" + escaped + "%";
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

}
