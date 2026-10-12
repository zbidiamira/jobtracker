package de.zbidi.jobtracker.matching;

import java.net.URI;

import de.zbidi.jobtracker.posting.ImportStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Start the AI matching by hand and follow a run, ADMIN only (SecurityConfig: /api/admin/**). */
@RestController
@RequestMapping("/api/admin/matching")
public class MatchingAdminController {

	private final MatchingService matchingService;

	public MatchingAdminController(MatchingService matchingService) {
		this.matchingService = matchingService;
	}

	/** 202 + Location; 503 without a model; 409 while a run is in progress. */
	@PostMapping("/run")
	public ResponseEntity<ImportStatusResponse> run() {
		ImportStatusResponse started = ImportStatusResponse.from(matchingService.start());
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/admin/matching/{id}").buildAndExpand(started.executionId()).toUri();
		return ResponseEntity.accepted().location(location).body(started);
	}

	@GetMapping("/{executionId}")
	public ImportStatusResponse status(@PathVariable long executionId) {
		return matchingService.status(executionId);
	}

}
