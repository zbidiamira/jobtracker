package de.zbidi.jobtracker.jobapplication;

import java.time.Clock;
import java.util.List;
import java.util.Objects;

import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.recruiter.Recruiter;
import de.zbidi.jobtracker.recruiter.RecruiterRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the application rules:
 * <ul>
 *     <li>never apply for the same job twice (same URL, or same company + position)</li>
 *     <li>only one active application per recruiter, so a recruiter never sees the CV for two jobs at once</li>
 * </ul>
 * The V2 unique indexes enforce the same rules in the database as a safety net.
 * Results are mapped to {@link JobApplicationResponse} inside the transaction (open-in-view is off).
 */
@Service
@Transactional
public class JobApplicationService {

	private final JobApplicationRepository jobApplicationRepository;
	private final CompanyRepository companyRepository;
	private final RecruiterRepository recruiterRepository;
	private final StatusHistoryRepository statusHistoryRepository;
	private final Clock clock;

	public JobApplicationService(JobApplicationRepository jobApplicationRepository,
			CompanyRepository companyRepository,
			RecruiterRepository recruiterRepository,
			StatusHistoryRepository statusHistoryRepository,
			Clock clock) {
		this.jobApplicationRepository = jobApplicationRepository;
		this.companyRepository = companyRepository;
		this.recruiterRepository = recruiterRepository;
		this.statusHistoryRepository = statusHistoryRepository;
		this.clock = clock;
	}

	public JobApplicationResponse create(NewJobApplication request) {
		Company company = companyRepository.findById(request.companyId())
				.orElseThrow(() -> new ResourceNotFoundException("Company", request.companyId()));
		Recruiter recruiter = request.recruiterId() == null ? null
				: recruiterRepository.findById(request.recruiterId())
						.orElseThrow(() -> new ResourceNotFoundException("Recruiter", request.recruiterId()));

		if (request.jobUrl() != null && jobApplicationRepository.existsByJobUrl(request.jobUrl())) {
			throw new DuplicateApplicationException(
					"You already have an application for " + request.jobUrl());
		}
		if (jobApplicationRepository.existsByCompanyIdAndPositionIgnoreCase(company.getId(), request.position())) {
			throw new DuplicateApplicationException(
					"You already have an application for '%s' at %s".formatted(request.position(), company.getName()));
		}

		JobApplication saved = jobApplicationRepository.save(
				new JobApplication(company, recruiter, request.position(), request.jobUrl()));
		return JobApplicationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public JobApplicationResponse get(Long id) {
		return JobApplicationResponse.from(find(id));
	}

	/**
	 * Filters are optional and combined with AND; dates are interpreted in the clock's (business) time zone.
	 */
	@Transactional(readOnly = true)
	public PageResponse<JobApplicationResponse> search(JobApplicationSearch search, Pageable pageable) {
		Page<JobApplication> page = jobApplicationRepository.findAll(
				ApplicationSpecifications.matching(search, clock.getZone()), pageable);
		return PageResponse.from(page.map(JobApplicationResponse::from));
	}

	/**
	 * @param expectedVersion the version the client based its change on
	 * @throws StaleVersionException if someone else changed the application in the meantime
	 */
	public JobApplicationResponse changeStatus(Long id, Status newStatus, Long expectedVersion) {
		JobApplication application = find(id);
		if (!Objects.equals(application.getVersion(), expectedVersion)) {
			throw new StaleVersionException(id, expectedVersion, application.getVersion());
		}

		Recruiter recruiter = application.getRecruiter();
		boolean becomesActive = newStatus != null && newStatus.isActive() && !application.getStatus().isActive();
		if (recruiter != null && becomesActive
				&& jobApplicationRepository.existsByRecruiterIdAndStatusInAndIdNot(recruiter.getId(), Status.ACTIVE, id)) {
			throw new RecruiterConflictException(
					"%s already has your CV for another active application".formatted(recruiter.getName()));
		}

		Status oldStatus = application.getStatus();
		application.changeStatus(newStatus);
		// flush so the response carries the incremented version and updated_at
		JobApplication saved = jobApplicationRepository.saveAndFlush(application);
		// same transaction: if writing the history fails, the status update above is rolled back too
		statusHistoryRepository.save(new StatusHistory(saved, oldStatus, newStatus));
		return JobApplicationResponse.from(saved);
	}

	@Transactional(readOnly = true)
	public List<StatusHistoryResponse> history(Long id) {
		if (!jobApplicationRepository.existsById(id)) {
			throw new ResourceNotFoundException("Job application", id);
		}
		return statusHistoryRepository.findByJobApplicationIdOrderByChangedAtAscIdAsc(id).stream()
				.map(StatusHistoryResponse::from)
				.toList();
	}

	private JobApplication find(Long id) {
		return jobApplicationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Job application", id));
	}

}
