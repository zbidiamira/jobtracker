package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * One entry in an application's status timeline. Immutable once written.
 */
@Entity
@Table(name = "status_history")
@EntityListeners(AuditingEntityListener.class)
public class StatusHistory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "job_application_id", nullable = false, updatable = false)
	private JobApplication jobApplication;

	@Enumerated(EnumType.STRING)
	@Column(name = "from_status", nullable = false, length = 20, updatable = false)
	private Status fromStatus;

	@Enumerated(EnumType.STRING)
	@Column(name = "to_status", nullable = false, length = 20, updatable = false)
	private Status toStatus;

	@CreatedDate
	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	protected StatusHistory() {
		// for JPA
	}

	public StatusHistory(JobApplication jobApplication, Status fromStatus, Status toStatus) {
		this.jobApplication = Objects.requireNonNull(jobApplication, "jobApplication must not be null");
		this.fromStatus = Objects.requireNonNull(fromStatus, "fromStatus must not be null");
		this.toStatus = Objects.requireNonNull(toStatus, "toStatus must not be null");
	}

	public Long getId() {
		return id;
	}

	public JobApplication getJobApplication() {
		return jobApplication;
	}

	public Status getFromStatus() {
		return fromStatus;
	}

	public Status getToStatus() {
		return toStatus;
	}

	public Instant getChangedAt() {
		return changedAt;
	}

}
