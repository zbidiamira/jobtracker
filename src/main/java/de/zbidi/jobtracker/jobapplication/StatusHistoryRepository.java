package de.zbidi.jobtracker.jobapplication;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

	// id as tie-breaker: two changes can share the same timestamp
	List<StatusHistory> findByJobApplicationIdOrderByChangedAtAscIdAsc(Long jobApplicationId);

}
