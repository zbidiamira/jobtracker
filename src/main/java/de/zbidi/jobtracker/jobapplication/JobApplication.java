package de.zbidi.jobtracker.jobapplication;

import java.time.Instant;
import java.util.Objects;

import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.recruiter.Recruiter;
import de.zbidi.jobtracker.user.AppUser;
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
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "job_application")
@EntityListeners(AuditingEntityListener.class)
public class JobApplication {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/** The user this application belongs to; only they can see or change it. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false, updatable = false)
	private AppUser owner;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "company_id", nullable = false)
	private Company company;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "recruiter_id")
	private Recruiter recruiter;

	@Column(nullable = false, length = 200)
	private String position;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private Status status;

	@Column(name = "job_url", length = 500)
	private String jobUrl;

	@Version
	private Long version;

	@CreatedDate
	@Column(name = "created_at", updatable = false)
	private Instant createdAt;

	@LastModifiedDate
	@Column(name = "updated_at")
	private Instant updatedAt;

	protected JobApplication() {
		// for JPA
	}

	public JobApplication(AppUser owner, Company company, String position, String jobUrl) {
		this(owner, company, null, position, jobUrl);
	}

	public JobApplication(AppUser owner, Company company, Recruiter recruiter, String position, String jobUrl) {
		this.owner = Objects.requireNonNull(owner, "owner must not be null");
		this.company = Objects.requireNonNull(company, "company must not be null");
		this.recruiter = recruiter;
		this.position = Objects.requireNonNull(position, "position must not be null");
		this.jobUrl = jobUrl;
		this.status = Status.SAVED;
	}

	/**
	 * Moves the application to a new status, enforcing {@link Status#canMoveTo(Status)}.
	 *
	 * @throws InvalidStatusTransitionException if the transition is not allowed
	 */
	public void changeStatus(Status newStatus) {
		if (!status.canMoveTo(newStatus)) {
			throw new InvalidStatusTransitionException(status, newStatus);
		}
		this.status = newStatus;
	}

	public Long getId() {
		return id;
	}

	public AppUser getOwner() {
		return owner;
	}

	public Company getCompany() {
		return company;
	}

	public Recruiter getRecruiter() {
		return recruiter;
	}

	public String getPosition() {
		return position;
	}

	public Status getStatus() {
		return status;
	}

	public String getJobUrl() {
		return jobUrl;
	}

	public Long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}
