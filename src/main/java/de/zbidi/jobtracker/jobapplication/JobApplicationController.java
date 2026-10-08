package de.zbidi.jobtracker.jobapplication;

import java.net.URI;
import java.util.List;

import de.zbidi.jobtracker.auth.CurrentUser;
import de.zbidi.jobtracker.common.PageResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Every endpoint works on the caller's own applications: the owner is the token's {@code sub} claim.
 */
@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

	private final JobApplicationService jobApplicationService;

	public JobApplicationController(JobApplicationService jobApplicationService) {
		this.jobApplicationService = jobApplicationService;
	}

	@PostMapping
	public ResponseEntity<JobApplicationResponse> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody NewJobApplication request) {
		JobApplicationResponse created = jobApplicationService.create(CurrentUser.id(jwt), request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	public JobApplicationResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return jobApplicationService.get(CurrentUser.id(jwt), id);
	}

	/** All of the caller's applications, paged; newest first by default. */
	@GetMapping
	public PageResponse<JobApplicationResponse> list(@AuthenticationPrincipal Jwt jwt,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return jobApplicationService.search(CurrentUser.id(jwt), JobApplicationSearch.NONE, pageable);
	}

	/**
	 * Optional filters combined with AND, e.g.
	 * {@code /search?status=APPLIED&companyName=acme&position=java&createdFrom=2026-10-01&createdTo=2026-10-07}.
	 */
	@GetMapping("/search")
	public PageResponse<JobApplicationResponse> search(@AuthenticationPrincipal Jwt jwt,
			@Valid @ParameterObject JobApplicationSearch search,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return jobApplicationService.search(CurrentUser.id(jwt), search, pageable);
	}

	@GetMapping("/{id}/history")
	public List<StatusHistoryResponse> history(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
		return jobApplicationService.history(CurrentUser.id(jwt), id);
	}

	@PatchMapping("/{id}/status")
	public JobApplicationResponse changeStatus(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
			@Valid @RequestBody ChangeStatusRequest request) {
		return jobApplicationService.changeStatus(CurrentUser.id(jwt), id, request.status(), request.version());
	}

	/** ADMIN only (SecurityConfig): deletes any user's application, including its history. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		jobApplicationService.delete(id);
	}

}
