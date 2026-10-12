package de.zbidi.jobtracker.skill;

import java.net.URI;
import java.util.List;

import de.zbidi.jobtracker.auth.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** The caller's own skills; the AI posting analyzer matches postings against them. */
@RestController
@RequestMapping("/api/skills")
public class SkillController {

	private final SkillService skillService;

	public SkillController(SkillService skillService) {
		this.skillService = skillService;
	}

	@GetMapping
	public List<SkillResponse> list(@AuthenticationPrincipal Jwt jwt) {
		return skillService.list(CurrentUser.id(jwt));
	}

	@GetMapping("/{id}")
	public SkillResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return skillService.get(CurrentUser.id(jwt), id);
	}

	@PostMapping
	public ResponseEntity<SkillResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody SkillRequest request) {
		SkillResponse created = skillService.create(CurrentUser.id(jwt), request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@PutMapping("/{id}")
	public SkillResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody SkillRequest request) {
		return skillService.update(CurrentUser.id(jwt), id, request);
	}

	/** Any authenticated user, for their own skills (SecurityConfig makes other DELETEs ADMIN-only). */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		skillService.delete(CurrentUser.id(jwt), id);
	}

}
