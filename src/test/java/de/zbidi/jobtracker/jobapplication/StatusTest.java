package de.zbidi.jobtracker.jobapplication;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTest {

	@ParameterizedTest
	@CsvSource({
			"SAVED,     APPLIED",
			"SAVED,     WITHDRAWN",
			"APPLIED,   INTERVIEW",
			"APPLIED,   REJECTED",
			"APPLIED,   WITHDRAWN",
			"INTERVIEW, OFFER",
			"INTERVIEW, REJECTED",
			"INTERVIEW, WITHDRAWN",
			"OFFER,     ACCEPTED",
			"OFFER,     REJECTED",
			"OFFER,     WITHDRAWN"
	})
	void allowsValidTransitions(Status from, Status to) {
		assertThat(from.canMoveTo(to)).isTrue();
	}

	@ParameterizedTest
	@CsvSource({
			"SAVED,     INTERVIEW",
			"SAVED,     OFFER",
			"APPLIED,   SAVED",
			"APPLIED,   OFFER",
			"INTERVIEW, APPLIED",
			"OFFER,     INTERVIEW"
	})
	void rejectsSkippingOrGoingBack(Status from, Status to) {
		assertThat(from.canMoveTo(to)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {"ACCEPTED", "REJECTED", "WITHDRAWN"})
	void finalStatusesCannotMoveAnywhere(Status terminal) {
		for (Status target : Status.values()) {
			assertThat(terminal.canMoveTo(target)).as("%s -> %s", terminal, target).isFalse();
		}
		assertThat(terminal.isFinal()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(Status.class)
	void neverMovesBackToAnEarlierStage(Status from) {
		// relies on the enum being declared in pipeline order
		for (Status earlier : Status.values()) {
			if (earlier.ordinal() < from.ordinal()) {
				assertThat(from.canMoveTo(earlier)).as("%s -> %s", from, earlier).isFalse();
			}
		}
	}

	@ParameterizedTest
	@EnumSource(Status.class)
	void noStatusCanMoveToItself(Status status) {
		assertThat(status.canMoveTo(status)).isFalse();
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {"APPLIED", "INTERVIEW", "OFFER"})
	void cvIsWithRecruiterWhileApplicationIsActive(Status status) {
		assertThat(status.isActive()).isTrue();
	}

	@ParameterizedTest
	@EnumSource(value = Status.class, names = {"SAVED", "ACCEPTED", "REJECTED", "WITHDRAWN"})
	void otherStatusesAreNotActive(Status status) {
		assertThat(status.isActive()).isFalse();
	}

	@Test
	void nullTargetIsRejected() {
		assertThat(Status.APPLIED.canMoveTo(null)).isFalse();
	}

	@Test
	void namesFitIntoStatusColumn() {
		// job_application.status is VARCHAR(20)
		for (Status status : Status.values()) {
			assertThat(status.name()).hasSizeLessThanOrEqualTo(20);
		}
	}

}
