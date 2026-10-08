package de.zbidi.jobtracker.jobapplication;

import java.util.List;
import java.util.UUID;

import de.zbidi.jobtracker.TestcontainersConfiguration;
import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.company.CompanyRepository;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.AppUserRepository;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Real transactions: no test-managed transaction here (unlike @DataJpaTest), so the service's own
 * {@code @Transactional} decides what is committed and what is rolled back.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StatusHistoryTransactionTest {

	@Autowired
	JobApplicationService service;

	@Autowired
	JobApplicationRepository jobApplicationRepository;

	@Autowired
	CompanyRepository companyRepository;

	@Autowired
	AppUserRepository appUserRepository;

	@MockitoSpyBean
	StatusHistoryRepository statusHistoryRepository;

	private Long ownerId;
	private Long applicationId;

	@BeforeEach
	void setUp() {
		// committed data, not rolled back: unique names per test
		String suffix = UUID.randomUUID().toString().substring(0, 8);
		ownerId = appUserRepository.save(new AppUser("tx-" + suffix + "@example.com", "$2a$10$hash", Role.USER)).getId();
		Company company = companyRepository.save(new Company("Tx " + suffix, null, null));
		applicationId = service.create(ownerId, new NewJobApplication(company.getId(), null, "Dev " + suffix, null)).id();
	}

	@Test
	void changeStatusWritesStatusAndHistoryTogether() {
		change(applicationId, Status.APPLIED);

		assertThat(jobApplicationRepository.findById(applicationId).orElseThrow().getStatus()).isEqualTo(Status.APPLIED);
		assertThat(service.history(ownerId, applicationId))
				.extracting(StatusHistoryResponse::fromStatus, StatusHistoryResponse::toStatus)
				.containsExactly(tuple(Status.SAVED, Status.APPLIED));
	}

	@Test
	void failingHistoryInsertRollsBackTheStatusChange() {
		doThrow(new IllegalStateException("simulated failure while writing history"))
				.when(statusHistoryRepository).save(any());

		assertThatIllegalStateException()
				.isThrownBy(() -> change(applicationId, Status.APPLIED))
				.withMessageContaining("simulated");

		// the status update was flushed before the failure, and is rolled back together with it
		JobApplication reloaded = jobApplicationRepository.findById(applicationId).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(Status.SAVED);
		assertThat(reloaded.getVersion()).isZero();
		assertThat(service.history(ownerId, applicationId)).isEmpty();
	}

	@Test
	void invalidTransitionWritesNoHistory() {
		change(applicationId, Status.APPLIED);
		change(applicationId, Status.REJECTED);

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> change(applicationId, Status.INTERVIEW));

		assertThat(service.history(ownerId, applicationId)).hasSize(2);
	}

	@Test
	void historyIsReturnedOldestFirst() {
		change(applicationId, Status.APPLIED);
		change(applicationId, Status.INTERVIEW);

		List<StatusHistoryResponse> history = service.history(ownerId, applicationId);

		assertThat(history).extracting(StatusHistoryResponse::toStatus).containsExactly(Status.APPLIED, Status.INTERVIEW);
		assertThat(history).extracting(StatusHistoryResponse::changedAt).isSorted();
	}

	/** Like a real client: send the version it last read. */
	private JobApplicationResponse change(Long id, Status status) {
		return service.changeStatus(ownerId, id, status, service.get(ownerId, id).version());
	}

}
