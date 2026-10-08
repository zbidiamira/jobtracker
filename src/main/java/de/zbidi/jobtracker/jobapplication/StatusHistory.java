package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One entry in an application's status timeline, written from a {@link StatusChangedEvent}. Immutable once written.
 */
@Entity
@Table(name = "status_history")
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

	/** When the status changed; taken from the event, not the time this row is inserted. */
	@Column(name = "changed_at", nullable = false, updatable = false)
	private Instant changedAt;

	/** The event this row was written from; unique, so a redelivered event can't create a second row. */
	@Column(name = "event_id", nullable = false, updatable = false, unique = true)
	private UUID eventId;

	protected StatusHistory() {
		// for JPA
	}

	public StatusHistory(JobApplication jobApplication, Status fromStatus, Status toStatus, Instant changedAt, UUID eventId) {
		this.jobApplication = Objects.requireNonNull(jobApplication, "jobApplication must not be null");
		this.fromStatus = Objects.requireNonNull(fromStatus, "fromStatus must not be null");
		this.toStatus = Objects.requireNonNull(toStatus, "toStatus must not be null");
		this.changedAt = Objects.requireNonNull(changedAt, "changedAt must not be null");
		this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
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

	public UUID getEventId() {
		return eventId;
	}

}
