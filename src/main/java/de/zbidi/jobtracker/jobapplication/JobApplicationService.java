package de.zbidi.jobtracker.jobapplication;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.recruiter.Recruiter;
import de.zbidi.jobtracker.recruiter.RecruiterRepository;
import de.zbidi.jobtracker.user.AppUserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the application rules, always for one owner ({@code ownerId} = the caller's user id):
 * <ul>
 *     <li>a user only sees and changes their own applications; anyone else's is "not found" (404, no id leak)</li>
 *     <li>never apply for the same job twice (same URL, or same company + position)</li>
 *     <li>only one active application per recruiter, so a recruiter never sees the CV for two jobs at once</li>
 * </ul>
 * The V6 unique indexes enforce the duplicate rules per owner in the database as a safety net.
 * Results are mapped to {@link JobApplicationResponse} inside the transaction (open-in-view is off).
 */
@Service
@Transactional
public class JobApplicationService {

	private final JobApplicationRepository jobApplicationRepository;
	private final CompanyRepository companyRepository;
	private final RecruiterRepository recruiterRepository;
	private final StatusHistoryRepository statusHistoryRepository;
	private final AppUserRepository appUserRepository;
	private final ApplicationEventPublisher events;
	private final Clock clock;

	public JobApplicationService(JobApplicationRepository jobApplicationRepository,
			CompanyRepository companyRepository,
			RecruiterRepository recruiterRepository,
			StatusHistoryRepository statusHistoryRepository,
			AppUserRepository appUserRepository,
			ApplicationEventPublisher events,
			Clock clock) {
		this.jobApplicationRepository = jobApplicationRepository;
		this.companyRepository = companyRepository;
		this.recruiterRepository = recruiterRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.appUserRepository = appUserRepository;
		this.events = events;
		this.clock = clock;
	}

	public JobApplicationResponse create(Long ownerId, NewJobApplication request) {
		Company company = companyRepository.findById(request.companyId())
				.orElseThrow(() -> new ResourceNotFoundException("Company", request.companyId()));
		Recruiter recruiter = request.recruiterId() == null ? null
				: recruiterRepository.findById(request.recruiterId())
						.orElseThrow(() -> new ResourceNotFoundException("Recruiter", request.recruiterId()));

		if (request.jobUrl() != null && jobApplicationRepository.existsByOwnerIdAndJobUrl(ownerId, request.jobUrl())) {
			throw new DuplicateApplicationException(
					"You already have an application for " + request.jobUrl());
		}
		if (jobApplicationRepository.existsByOwnerIdAndCompanyIdAndPositionIgnoreCase(ownerId, company.getId(), request.position())) {
			throw new DuplicateApplicationException(
					"You already have an application for '%s' at %s".formatted(request.position(), company.getName()));
		}

		// a reference is enough to set the foreign key; the user is not loaded
		JobApplication saved = jobApplicationRepository.save(new JobApplication(
				appUserRepository.getReferenceById(ownerId), company, recruiter, request.position(), request.jobUrl()));
		return JobApplicationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public JobApplicationResponse get(Long ownerId, Long id) {
		return JobApplicationResponse.from(find(ownerId, id));
	}

	/**
	 * Only the owner's applications. Filters are optional and combined with AND; dates are interpreted in the clock's
	 * (business) time zone.
	 */
	@Transactional(readOnly = true)
	public PageResponse<JobApplicationResponse> search(Long ownerId, JobApplicationSearch search, Pageable pageable) {
		Page<JobApplication> page = jobApplicationRepository.findAll(
				ApplicationSpecifications.matching(ownerId, search, clock.getZone()), pageable);
		return PageResponse.from(page.map(JobApplicationResponse::from));
	}

	/**
	 * @param expectedVersion the version the client based its change on
	 * @throws StaleVersionException if someone else changed the application in the meantime
	 */
	public JobApplicationResponse changeStatus(Long ownerId, Long id, Status newStatus, Long expectedVersion) {
		JobApplication application = find(ownerId, id);
		if (!Objects.equals(application.getVersion(), expectedVersion)) {
			throw new StaleVersionException(id, expectedVersion, application.getVersion());
		}

		Recruiter recruiter = application.getRecruiter();
		boolean becomesActive = newStatus != null && newStatus.isActive() && !application.getStatus().isActive();
		if (recruiter != null && becomesActive && jobApplicationRepository.existsByOwnerIdAndRecruiterIdAndStatusInAndIdNot(
				ownerId, recruiter.getId(), Status.ACTIVE, id)) {
			throw new RecruiterConflictException(
					"%s already has your CV for another active application".formatted(recruiter.getName()));
		}

		Status oldStatus = application.getStatus();
		application.changeStatus(newStatus);
		// flush so the response carries the incremented version and updated_at
		JobApplication saved = jobApplicationRepository.saveAndFlush(application);
		// The history is written by StatusHistoryListener from this event. The event goes to Kafka only after the
		// commit (StatusEventPublisher), so a rolled-back change never shows up in the history.
		events.publishEvent(new StatusChangedEvent(UUID.randomUUID(), saved.getId(), ownerId, oldStatus, newStatus,
				saved.getUpdatedAt()));
		return JobApplicationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<StatusHistoryResponse> history(Long ownerId, Long id) {
		if (!jobApplicationRepository.existsByIdAndOwnerId(id, ownerId)) {
			throw new ResourceNotFoundException("Job application", id);
		}
		return statusHistoryRepository.findByJobApplicationIdOrderByChangedAtAscIdAsc(id).stream()
				.map(StatusHistoryResponse::from)
				.toList();
	}

	/**
	 * ADMIN only (enforced in SecurityConfig), so not scoped to an owner. Removes the history first (foreign key).
	 */
	public void delete(Long id) {
		JobApplication application = jobApplicationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Job application", id));
		statusHistoryRepository.deleteByJobApplicationId(id);
		jobApplicationRepository.delete(application);
	}

	/** Someone else's application and a missing one look the same to the caller. */
	private JobApplication find(Long ownerId, Long id) {
		return jobApplicationRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Job application", id));
	}

}
