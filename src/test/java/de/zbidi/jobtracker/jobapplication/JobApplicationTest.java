package de.zbidi.jobtracker.jobapplication;

import de.zbidi.jobtracker.company.Company;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class JobApplicationTest {

	private final Company company = new Company("ACME GmbH", "Berlin", null);

	@Test
	void newApplicationStartsAsSaved() {
		JobApplication application = new JobApplication(company, "Java Developer", null);

		assertThat(application.getStatus()).isEqualTo(Status.SAVED);
	}

	@Test
	void changeStatusFollowsAllowedTransition() {
		JobApplication application = new JobApplication(company, "Java Developer", null);

		application.changeStatus(Status.APPLIED);
		application.changeStatus(Status.INTERVIEW);

		assertThat(application.getStatus()).isEqualTo(Status.INTERVIEW);
	}

	@Test
	void changeStatusRejectsForbiddenTransition() {
		JobApplication application = new JobApplication(company, "Java Developer", null);

		assertThatIllegalStateException()
				.isThrownBy(() -> application.changeStatus(Status.OFFER))
				.withMessageContaining("SAVED")
				.withMessageContaining("OFFER");
		assertThat(application.getStatus()).isEqualTo(Status.SAVED);
	}

}
