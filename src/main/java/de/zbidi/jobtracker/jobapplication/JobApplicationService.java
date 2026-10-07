package de.zbidi.jobtracker.jobapplication;

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

	public JobApplicationService(JobApplicationRepository jobApplicationRepository,
			CompanyRepository companyRepository,
			RecruiterRepository recruiterRepository) {
		this.jobApplicationRepository = jobApplicationRepository;
		this.companyRepository = companyRepository;
		this.recruiterRepository = recruiterRepository;
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
	 * @param status optional filter, {@code null} returns all applications
	 */
	@Transactional(readOnly = true)
	public PageResponse<JobApplicationResponse> list(Status status, Pageable pageable) {
		Page<JobApplication> page = status == null
				? jobApplicationRepository.findAll(pageable)
				: jobApplicationRepository.findByStatus(status, pageable);
		return PageResponse.from(page.map(JobApplicationResponse::from));
	}

	public JobApplicationResponse changeStatus(Long id, Status newStatus) {
		JobApplication application = find(id);

		Recruiter recruiter = application.getRecruiter();
		boolean becomesActive = newStatus != null && newStatus.isActive() && !application.getStatus().isActive();
		if (recruiter != null && becomesActive
				&& jobApplicationRepository.existsByRecruiterIdAndStatusInAndIdNot(recruiter.getId(), Status.ACTIVE, id)) {
			throw new RecruiterConflictException(
					"%s already has your CV for another active application".formatted(recruiter.getName()));
		}

		application.changeStatus(newStatus);
		// flush so the response carries the incremented version and updated_at
		return JobApplicationResponse.from(jobApplicationRepository.saveAndFlush(application));
	}

	private JobApplication find(Long id) {
		return jobApplicationRepository.findById(id)
				.orElseThrow(() -> new ResourceNotFoundException("Job application", id));
	}

}
