package de.zbidi.jobtracker.skill;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Every lookup is scoped to an owner: someone else's skill is simply not found. */
public interface SkillRepository extends JpaRepository<Skill, Long> {

	List<Skill> findByOwnerIdOrderByNameAsc(Long ownerId);

	Optional<Skill> findByIdAndOwnerId(Long id, Long ownerId);

	boolean existsByOwnerIdAndNameIgnoreCase(Long ownerId, String name);

	boolean existsByOwnerIdAndNameIgnoreCaseAndIdNot(Long ownerId, String name, Long id);

}
