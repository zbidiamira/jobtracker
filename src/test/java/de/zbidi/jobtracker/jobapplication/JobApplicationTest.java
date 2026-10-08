package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.company.Company;
import de.zbidi.jobtracker.user.AppUser;
import de.zbidi.jobtracker.user.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class JobApplicationTest {

	private final AppUser owner = new AppUser("alice@example.com", "$2a$10$hash", Role.USER);
	private final Company company = new Company("ACME GmbH", "Berlin", null);

	@Test
	void newApplicationStartsAsSaved() {
		JobApplication application = new JobApplication(owner, company, "Java Developer", null);

		assertThat(application.getStatus()).isEqualTo(Status.SAVED);
	}

	@Test
	void changeStatusFollowsAllowedTransition() {
		JobApplication application = new JobApplication(owner, company, "Java Developer", null);

		application.changeStatus(Status.APPLIED);
		application.changeStatus(Status.INTERVIEW);

		assertThat(application.getStatus()).isEqualTo(Status.INTERVIEW);
	}

	@Test
	void changeStatusRejectsForbiddenTransition() {
		JobApplication application = new JobApplication(owner, company, "Java Developer", null);

		assertThatExceptionOfType(InvalidStatusTransitionException.class)
				.isThrownBy(() -> application.changeStatus(Status.OFFER))
				.withMessageContaining("SAVED")
				.withMessageContaining("OFFER")
				.satisfies(e -> {
					assertThat(e.getCurrentStatus()).isEqualTo(Status.SAVED);
					assertThat(e.getRequestedStatus()).isEqualTo(Status.OFFER);
				});
		assertThat(application.getStatus()).isEqualTo(Status.SAVED);
	}

}
