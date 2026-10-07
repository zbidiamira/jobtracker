package de.zbidi.jobtracker.company;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import de.zbidi.jobtracker.common.PageResponse;
import de.zbidi.jobtracker.common.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestcontainersConfiguration.class, CompanyService.class})
class CompanyServiceIntegrationTest {

	@Autowired
	CompanyService service;

	@Test
	void createReturnsResponseWithId() {
		CompanyResponse created = service.create(new CreateCompanyRequest("ACME GmbH", "Berlin", "https://acme.example"));

		assertThat(created.id()).isNotNull();
		assertThat(created.name()).isEqualTo("ACME GmbH");
		assertThat(service.get(created.id())).isEqualTo(created);
	}

	@Test
	void getUnknownIdThrowsNotFound() {
		assertThatExceptionOfType(ResourceNotFoundException.class)
				.isThrownBy(() -> service.get(999_999L))
				.withMessageContaining("Company 999999");
	}

	@Test
	void listReturnsRequestedPageSortedByName() {
		service.create(new CreateCompanyRequest("Globex", null, null));
		service.create(new CreateCompanyRequest("ACME GmbH", null, null));
		service.create(new CreateCompanyRequest("Initech", null, null));

		PageResponse<CompanyResponse> page = service.list(PageRequest.of(1, 2, Sort.by("name")));

		assertThat(page.content()).extracting(CompanyResponse::name).containsExactly("Initech");
		assertThat(page.page()).isEqualTo(1);
		assertThat(page.totalElements()).isEqualTo(3);
		assertThat(page.totalPages()).isEqualTo(2);
	}

}
