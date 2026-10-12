package de.zbidi.jobtracker.posting;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * CSV import of job postings, ADMIN only (SecurityConfig: /api/admin/**). Imports run in the background:
 * POST answers 202 with the location to poll.
 */
@RestController
@RequestMapping("/api/admin/imports")
public class ImportController {

	private final ImportService importService;

	public ImportController(ImportService importService) {
		this.importService = importService;
	}

	/** CSV with header {@code title,company,city,url,description}, as multipart part {@code file}. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ImportStatusResponse> start(@RequestParam("file") MultipartFile file) {
		if (file.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The uploaded file is empty");
		}
		return accepted(importService.start(file));
	}

	@GetMapping("/{executionId}")
	public ImportStatusResponse status(@PathVariable long executionId) {
		return importService.status(executionId);
	}

	/** Continues a failed import after its last committed chunk; answers with the new execution. */
	@PostMapping("/{executionId}/restart")
	public ResponseEntity<ImportStatusResponse> restart(@PathVariable long executionId) {
		return accepted(importService.restart(executionId));
	}

	private static ResponseEntity<ImportStatusResponse> accepted(ImportStatusResponse execution) {
		URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
				.path("/api/admin/imports/{id}").buildAndExpand(execution.executionId()).toUri();
		return ResponseEntity.accepted().location(location).body(execution);
	}

}
