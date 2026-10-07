package de.zbidi.jobtracker.company;

import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CompanyService {

	private final CompanyRepository companyRepository;

	public CompanyService(CompanyRepository companyRepository) {
		this.companyRepository = companyRepository;
	}

	public CompanyResponse create(CreateCompanyRequest request) {
		Company company = companyRepository.save(new Company(request.name(), request.city(), request.website()));
		return CompanyResponse.from(company);
	}

	@Transactional(readOnly = true)
	public CompanyResponse get(Long id) {
		return companyRepository.findById(id)
				.map(CompanyResponse::from)
				.orElseThrow(() -> new ResourceNotFoundException("Company", id));
	}

	@Transactional(readOnly = true)
	public PageResponse<CompanyResponse> list(Pageable pageable) {
		return PageResponse.from(companyRepository.findAll(pageable).map(CompanyResponse::from));
	}

}
