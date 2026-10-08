package de.zbidi.jobtracker.jobapplication;

import java.util.UUID;

import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * {@code @Version} at the database level: two real transactions update the same row.
 * Deterministic: no threads; transaction A simply holds a stale copy while B commits.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OptimisticLockingTest {

	@Autowired
	JobApplicationRepository jobApplicationRepository;

	@Autowired
	CompanyRepository companyRepository;

	@Autowired
	AppUserRepository appUserRepository;

	@Autowired
	TransactionTemplate tx;

	@Test
	void secondWriterWithStaleEntityFails() {
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		AppUser owner = appUserRepository.save(new AppUser("lock-" + suffix + "@example.com", "$2a$10$hash", Role.USER));
		Company company = companyRepository.save(new Company("Lock " + suffix, null, null));
		Long id = jobApplicationRepository.save(new JobApplication(owner, company, "Dev " + suffix, null)).getId();

		// A reads version 0 ...
		JobApplication staleCopy = tx.execute(status -> jobApplicationRepository.findById(id).orElseThrow());
		assertThat(staleCopy.getVersion()).isZero();

		// ... B reads, changes and commits: version 1
		tx.executeWithoutResult(status -> jobApplicationRepository.findById(id).orElseThrow().changeStatus(Status.APPLIED));

		// ... A now saves its outdated copy
		staleCopy.changeStatus(Status.WITHDRAWN);
		assertThatExceptionOfType(ObjectOptimisticLockingFailureException.class)
				.isThrownBy(() -> tx.executeWithoutResult(status -> jobApplicationRepository.saveAndFlush(staleCopy)));

		JobApplication stored = jobApplicationRepository.findById(id).orElseThrow();
		assertThat(stored.getStatus()).isEqualTo(Status.APPLIED);
		assertThat(stored.getVersion()).isEqualTo(1L);
	}

}
