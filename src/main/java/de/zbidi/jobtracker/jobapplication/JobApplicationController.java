package de.zbidi.jobtracker.jobapplication;

import java.net.URI;
import java.util.List;

import de.zbidi.jobtracker.common.PageResponse;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/applications")
public class JobApplicationController {

	private final JobApplicationService jobApplicationService;

	public JobApplicationController(JobApplicationService jobApplicationService) {
		this.jobApplicationService = jobApplicationService;
	}

	@PostMapping
	public ResponseEntity<JobApplicationResponse> create(@Valid @RequestBody NewJobApplication request) {
		JobApplicationResponse created = jobApplicationService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	public JobApplicationResponse get(@PathVariable Long id) {
		return jobApplicationService.get(id);
	}

	/** All applications, paged; newest first by default. */
	@GetMapping
	public PageResponse<JobApplicationResponse> list(
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return jobApplicationService.search(JobApplicationSearch.NONE, pageable);
	}

	/**
	 * Optional filters combined with AND, e.g.
	 * {@code /search?status=APPLIED&companyName=acme&position=java&createdFrom=2026-10-01&createdTo=2026-10-07}.
	 */
	@GetMapping("/search")
	public PageResponse<JobApplicationResponse> search(
			@Valid @ParameterObject JobApplicationSearch search,
			@ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
		return jobApplicationService.search(search, pageable);
	}

	@GetMapping("/{id}/history")
	public List<StatusHistoryResponse> history(@PathVariable Long id) {
		return jobApplicationService.history(id);
	}

	@PatchMapping("/{id}/status")
	public JobApplicationResponse changeStatus(@PathVariable Long id, @Valid @RequestBody ChangeStatusRequest request) {
		return jobApplicationService.changeStatus(id, request.status(), request.version());
	}

}
