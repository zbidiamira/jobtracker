package de.zbidi.jobtracker.jobapplication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

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

	// --- scoped to one owner: someone else's application is simply not found ---

	Optional<JobApplication> findByIdAndOwnerId(Long id, Long ownerId);

	boolean existsByIdAndOwnerId(Long id, Long ownerId);

	boolean existsByOwnerIdAndJobUrl(Long ownerId, String jobUrl);

	boolean existsByOwnerIdAndCompanyIdAndPositionIgnoreCase(Long ownerId, Long companyId, String position);

	boolean existsByOwnerIdAndRecruiterIdAndStatusInAndIdNot(Long ownerId, Long recruiterId, Collection<Status> statuses, Long id);

}
