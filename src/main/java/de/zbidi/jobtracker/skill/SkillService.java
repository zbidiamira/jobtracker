package de.zbidi.jobtracker.skill;

import java.util.List;

import de.zbidi.jobtracker.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The caller's own skills. Name clashes are checked first (409); the unique index on (user_id, lower(name)) is the
 * safety net for two requests at once.
 */
@Service
@Transactional
public class SkillService {

	private final SkillRepository skillRepository;

	public SkillService(SkillRepository skillRepository) {
		this.skillRepository = skillRepository;
	}

	@Transactional(readOnly = true)
	public List<SkillResponse> list(Long ownerId) {
		return skillRepository.findByOwnerIdOrderByNameAsc(ownerId).stream().map(SkillResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public SkillResponse get(Long ownerId, Long id) {
		return SkillResponse.from(find(ownerId, id));
	}

	public SkillResponse create(Long ownerId, SkillRequest request) {
		String name = request.name().strip();
		if (skillRepository.existsByOwnerIdAndNameIgnoreCase(ownerId, name)) {
			throw new DuplicateSkillException(name);
		}
		Skill skill = new Skill(ownerId, name, request.years(), request.level());
		return SkillResponse.from(skillRepository.saveAndFlush(skill));
	}

	public SkillResponse update(Long ownerId, Long id, SkillRequest request) {
		Skill skill = find(ownerId, id);
		String name = request.name().strip();
		if (skillRepository.existsByOwnerIdAndNameIgnoreCaseAndIdNot(ownerId, name, id)) {
			throw new DuplicateSkillException(name);
		}
		skill.change(name, request.years(), request.level());
		return SkillResponse.from(skillRepository.saveAndFlush(skill));
	}

	public void delete(Long ownerId, Long id) {
		skillRepository.delete(find(ownerId, id));
	}

	private Skill find(Long ownerId, Long id) {
		return skillRepository.findByIdAndOwnerId(id, ownerId)
				.orElseThrow(() -> new ResourceNotFoundException("Skill", id));
	}

}
