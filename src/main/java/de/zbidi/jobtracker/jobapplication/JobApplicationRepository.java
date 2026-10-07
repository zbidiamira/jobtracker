package de.zbidi.jobtracker.jobapplication;

import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobApplicationRepository extends JpaRepository<JobApplication, Long>,
		JpaSpecificationExecutor<JobApplication> {

	// search results fetch the company in the same query (avoids one extra query per row)
	@Override
	@EntityGraph(attributePaths = "company")
	Page<JobApplication> findAll(Specification<JobApplication> spec, Pageable pageable);

	List<JobApplication> findByStatus(Status status);

	List<JobApplication> findByCompanyId(Long companyId);

	boolean existsByJobUrl(String jobUrl);

	boolean existsByCompanyIdAndPositionIgnoreCase(Long companyId, String position);

	boolean existsByRecruiterIdAndStatusInAndIdNot(Long recruiterId, Collection<Status> statuses, Long id);

}
