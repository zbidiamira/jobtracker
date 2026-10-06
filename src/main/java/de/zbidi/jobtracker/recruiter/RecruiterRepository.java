package de.zbidi.jobtracker.recruiter;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecruiterRepository extends JpaRepository<Recruiter, Long> {

	Optional<Recruiter> findByEmailIgnoreCase(String email);

}
