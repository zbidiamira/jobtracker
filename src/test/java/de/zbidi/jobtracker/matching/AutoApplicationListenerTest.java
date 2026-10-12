package de.zbidi.jobtracker.matching;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.jobapplication.DuplicateApplicationException;
import de.zbidi.jobtracker.jobapplication.JobApplicationService;
import de.zbidi.jobtracker.jobapplication.NewJobApplication;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class AutoApplicationListenerTest {

	static final GoodMatchFound EVENT = new GoodMatchFound(UUID.randomUUID(), 7L, 5L, "Java Developer", "ACME GmbH",
			"https://jobs.example/5", 82, Instant.parse("2026-10-12T05:00:00Z"));

	CompanyRepository companies = mock(CompanyRepository.class);
	JobApplicationService applications = mock(JobApplicationService.class);
	AutoApplicationListener listener = new AutoApplicationListener(companies, applications);

	@Test
	void createsASavedApplicationAtAnExistingCompany() {
		given(companies.findByNameIgnoreCase("ACME GmbH")).willReturn(Optional.of(company(3L, "ACME GmbH")));

		listener.onGoodMatch(EVENT);

		verify(applications).create(7L, new NewJobApplication(3L, null, "Java Developer", "https://jobs.example/5"));
		verify(companies, never()).save(any());
	}

	@Test
	void createsTheCompanyWhenItIsNew() {
		given(companies.findByNameIgnoreCase("ACME GmbH")).willReturn(Optional.empty());
		given(companies.save(any(Company.class))).willAnswer(invocation -> {
			Company saved = invocation.getArgument(0);
			ReflectionTestUtils.setField(saved, "id", 11L);
			return saved;
		});

		listener.onGoodMatch(EVENT);

		verify(applications).create(7L, new NewJobApplication(11L, null, "Java Developer", "https://jobs.example/5"));
	}

	/** Kafka delivers at least once: the second delivery finds the application and does nothing. */
	@Test
	void anApplicationThatAlreadyExistsIsLeftAlone() {
		given(companies.findByNameIgnoreCase("ACME GmbH")).willReturn(Optional.of(company(3L, "ACME GmbH")));
		given(applications.create(anyLong(), any())).willThrow(new DuplicateApplicationException("exists"));

		assertThatNoException().isThrownBy(() -> listener.onGoodMatch(EVENT));
	}

	private static Company company(long id, String name) {
		Company company = new Company(name, null, null);
		ReflectionTestUtils.setField(company, "id", id);
		return company;
	}

}
