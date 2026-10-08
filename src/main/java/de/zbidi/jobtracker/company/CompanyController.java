package de.zbidi.jobtracker.company;

import java.net.URI;

import de.zbidi.jobtracker.common.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

	private final CompanyService companyService;

	public CompanyController(CompanyService companyService) {
		this.companyService = companyService;
	}

	@PostMapping
	public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CreateCompanyRequest request) {
		CompanyResponse created = companyService.create(request);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}").buildAndExpand(created.id()).toUri();
		return ResponseEntity.created(location).body(created);
	}

	@GetMapping("/{id}")
	public CompanyResponse get(@PathVariable Long id) {
		return companyService.get(id);
	}

	@GetMapping
	public PageResponse<CompanyResponse> list(@PageableDefault(size = 20, sort = "name") Pageable pageable) {
		return companyService.list(pageable);
	}

	/** ADMIN only (SecurityConfig); 409 while applications or recruiters still reference the company. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		companyService.delete(id);
	}

}
