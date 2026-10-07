package de.zbidi.jobtracker.jobapplication;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message is what the user sees in the 409 response, so it must say what is allowed instead.
 */
class InvalidStatusTransitionExceptionTest {

	@Test
	void messageListsAllowedNextStatuses() {
		var ex = new InvalidStatusTransitionException(Status.APPLIED, Status.SAVED);

		assertThat(ex.getMessage())
				.isEqualTo("Cannot change status from APPLIED to SAVED. Allowed next statuses: INTERVIEW, REJECTED, WITHDRAWN.");
	}

	@Test
	void messageExplainsFinalStatus() {
		var ex = new InvalidStatusTransitionException(Status.REJECTED, Status.INTERVIEW);

		assertThat(ex.getMessage())
				.isEqualTo("Cannot change status from REJECTED to INTERVIEW: REJECTED is a final status.");
	}

	@Test
	void exposesAllowedStatuses() {
		assertThat(new InvalidStatusTransitionException(Status.APPLIED, Status.SAVED).getAllowedStatuses())
				.containsExactly(Status.INTERVIEW, Status.REJECTED, Status.WITHDRAWN);
		assertThat(new InvalidStatusTransitionException(Status.REJECTED, Status.INTERVIEW).getAllowedStatuses())
				.isEmpty();
	}

}
