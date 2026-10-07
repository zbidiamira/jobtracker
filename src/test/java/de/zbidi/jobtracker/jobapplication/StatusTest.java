package de.zbidi.jobtracker.jobapplication;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class StatusTest {

	/**
	 * The workflow written out independently of the production switch: if the two ever disagree, a test fails.
	 */
	private static final Map<Status, Set<Status>> ALLOWED = Map.of(
			Status.SAVED, EnumSet.of(Status.APPLIED, Status.WITHDRAWN),
			Status.APPLIED, EnumSet.of(Status.INTERVIEW, Status.REJECTED, Status.WITHDRAWN),
			Status.INTERVIEW, EnumSet.of(Status.OFFER, Status.REJECTED, Status.WITHDRAWN),
			Status.OFFER, EnumSet.of(Status.ACCEPTED, Status.REJECTED, Status.WITHDRAWN),
			Status.ACCEPTED, EnumSet.noneOf(Status.class),
			Status.REJECTED, EnumSet.noneOf(Status.class),
			Status.WITHDRAWN, EnumSet.noneOf(Status.class));

	/** Every from/to pair: 7 x 7 = 49 cases, 11 allowed and 38 forbidden. */
	static Stream<Arguments> allTransitions() {
		return Arrays.stream(Status.values()).flatMap(from -> Arrays.stream(Status.values())
				.map(to -> Arguments.of(from, to, ALLOWED.get(from).contains(to))));
	}

	@ParameterizedTest(name = "{0} -> {1}: allowed={2}")
	@MethodSource("allTransitions")
	void transitionMatrix(Status from, Status to, boolean allowed) {
		assertThat(from.canMoveTo(to)).isEqualTo(allowed);
	}

	@Test
	void expectedTableHasElevenAllowedTransitions() {
		assertThat(allTransitions().filter(args -> (boolean) args.get()[2])).hasSize(11);
	}

	@ParameterizedTest
	@EnumSource(Status.class)
	void nextStatusesMatchTheWorkflow(Status from) {
		assertThat(from.nextStatuses()).isEqualTo(ALLOWED.get(from));
	}

	@Test
	void nextStatusesAreInPipelineOrder() {
		assertThat(Status.APPLIED.nextStatuses()).containsExactly(Status.INTERVIEW, Status.REJECTED, Status.WITHDRAWN);
		assertThat(Status.REJECTED.nextStatuses()).isEmpty();
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
