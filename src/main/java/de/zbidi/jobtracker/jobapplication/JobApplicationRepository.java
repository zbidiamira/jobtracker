package de.zbidi.jobtracker.jobapplication;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long> {

	List<JobApplication> findByStatus(Status status);

	List<JobApplication> findByCompanyId(Long companyId);

	boolean existsByJobUrl(String jobUrl);

	boolean existsByCompanyIdAndPositionIgnoreCase(Long companyId, String position);

	boolean existsByRecruiterIdAndStatusInAndIdNot(Long recruiterId, Collection<Status> statuses, Long id);

}
