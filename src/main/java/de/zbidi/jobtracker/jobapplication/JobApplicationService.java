package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.recruiter.Recruiter;
import de.zbidi.jobtracker.recruiter.RecruiterRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Enforces the application rules:
 * <ul>
 *     <li>never apply for the same job twice (same URL, or same company + position)</li>
 *     <li>only one active application per recruiter, so a recruiter never sees the CV for two jobs at once</li>
 * </ul>
 * The V2 unique indexes enforce the same rules in the database as a safety net.
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

	public JobApplication create(NewJobApplication request) {
		Company company = companyRepository.findById(request.companyId())
				.orElseThrow(() -> new IllegalArgumentException("Company " + request.companyId() + " not found"));
		Recruiter recruiter = request.recruiterId() == null ? null
				: recruiterRepository.findById(request.recruiterId())
						.orElseThrow(() -> new IllegalArgumentException("Recruiter " + request.recruiterId() + " not found"));

		if (request.jobUrl() != null && jobApplicationRepository.existsByJobUrl(request.jobUrl())) {
			throw new DuplicateApplicationException(
					"You already have an application for " + request.jobUrl());
		}
		if (jobApplicationRepository.existsByCompanyIdAndPositionIgnoreCase(company.getId(), request.position())) {
			throw new DuplicateApplicationException(
					"You already have an application for '%s' at %s".formatted(request.position(), company.getName()));
		}

		return jobApplicationRepository.save(
				new JobApplication(company, recruiter, request.position(), request.jobUrl()));
	}

	public JobApplication changeStatus(Long id, Status newStatus) {
		JobApplication application = jobApplicationRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Job application " + id + " not found"));

		Recruiter recruiter = application.getRecruiter();
		boolean becomesActive = newStatus != null && newStatus.isActive() && !application.getStatus().isActive();
		if (recruiter != null && becomesActive
				&& jobApplicationRepository.existsByRecruiterIdAndStatusInAndIdNot(recruiter.getId(), Status.ACTIVE, id)) {
			throw new RecruiterConflictException(
					"%s already has your CV for another active application".formatted(recruiter.getName()));
		}

		application.changeStatus(newStatus);
		return application;
	}

}
