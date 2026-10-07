package de.zbidi.jobtracker.company;

import java.util.List;
import java.util.Optional;

import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Fast unit test: repository mocked, no Spring, no Docker.
 * {@link CompanyServiceIntegrationTest} covers the same service against real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

	@Mock
	CompanyRepository companyRepository;

	@InjectMocks
	CompanyService companyService;

	@Test
	void createSavesCompanyAndReturnsResponse() {
		// the database would generate the id; the mock simulates that
		given(companyRepository.save(any(Company.class)))
				.willAnswer(invocation -> withId(invocation.getArgument(0), 1L));

		CompanyResponse response = companyService.create(
				new CreateCompanyRequest("ACME GmbH", "Berlin", "https://acme.example"));

		ArgumentCaptor<Company> saved = ArgumentCaptor.forClass(Company.class);
		verify(companyRepository).save(saved.capture());
		assertThat(saved.getValue().getName()).isEqualTo("ACME GmbH");
		assertThat(saved.getValue().getCity()).isEqualTo("Berlin");
		assertThat(saved.getValue().getWebsite()).isEqualTo("https://acme.example");
		assertThat(response).isEqualTo(new CompanyResponse(1L, "ACME GmbH", "Berlin", "https://acme.example"));
	}

	@Test
	void getReturnsResponseForExistingCompany() {
		given(companyRepository.findById(1L)).willReturn(Optional.of(withId(new Company("ACME GmbH", "Berlin", null), 1L)));

		assertThat(companyService.get(1L)).isEqualTo(new CompanyResponse(1L, "ACME GmbH", "Berlin", null));
	}

	@Test
	void getUnknownIdThrowsResourceNotFound() {
		given(companyRepository.findById(99L)).willReturn(Optional.empty());

		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> companyService.get(99L))
				.withMessage("Company 99 not found");
	}

	@Test
	void listMapsPageToPageResponse() {
		Pageable pageable = PageRequest.of(0, 20);
		Company acme = withId(new Company("ACME GmbH", "Berlin", null), 1L);
		given(companyRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(acme), pageable, 1));

		PageResponse<CompanyResponse> page = companyService.list(pageable);

		assertThat(page.content()).containsExactly(new CompanyResponse(1L, "ACME GmbH", "Berlin", null));
		assertThat(page.page()).isZero();
		assertThat(page.size()).isEqualTo(20);
		assertThat(page.totalElements()).isEqualTo(1);
		assertThat(page.totalPages()).isEqualTo(1);
	}

	private static Company withId(Company company, Long id) {
		ReflectionTestUtils.setField(company, "id", id);
		return company;
	}

}
