package de.zbidi.jobtracker.company;

import de.zbidi.jobtracker.PostgresTestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestcontainersConfiguration.class)
class CompanyRepositoryTest {

	@Autowired
	CompanyRepository companyRepository;

	@Autowired
	TestEntityManager em;

	@Test
	void savesAndLoadsCompany() {
		Company saved = companyRepository.save(new Company("ACME GmbH", "Berlin", "https://acme.example"));
		em.flush();
		em.clear();

		Company loaded = companyRepository.findById(saved.getId()).orElseThrow();

		assertThat(loaded.getId()).isNotNull();
		assertThat(loaded.getName()).isEqualTo("ACME GmbH");
		assertThat(loaded.getCity()).isEqualTo("Berlin");
		assertThat(loaded.getWebsite()).isEqualTo("https://acme.example");
	}

	@Test
	void findsCompanyByNameIgnoringCase() {
		companyRepository.save(new Company("ACME GmbH", "Berlin", null));
		companyRepository.save(new Company("Globex", "Munich", null));
		em.flush();
		em.clear();

		assertThat(companyRepository.findByNameIgnoreCase("acme gmbh"))
				.get()
				.extracting(Company::getCity)
				.isEqualTo("Berlin");
		assertThat(companyRepository.findByNameIgnoreCase("Initech")).isEmpty();
	}

}
