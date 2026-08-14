package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * When a plan finishes, and why that is not the sum of its parts.
 *
 * <p>
 * <strong>Everything here is arithmetic that can be checked by hand</strong>, which is
 * the point of the scheduler being separable from the sampler: no draws, no tolerances
 * that mean anything, no statistics to hide a mistake in. If a number below is wrong it
 * is wrong by whole hours.
 *
 * <p>
 * The test worth reading first is
 * {@code structureChangesTheAnswerMoreThanTheEstimatesDo}, which is `roadmap.md`'s
 * measurement — the same ten items finishing at wildly different times depending only on
 * how they are joined up — reduced to a unit test.
 */
class ScheduleTests {

	private static final double EXACT = 1e-9;

	@Test
	void aChainFinishesAtTheSumOfItsDurations() {
		Schedule chain = schedule(5, 1, edge(0, 1), edge(1, 2), edge(2, 3), edge(3, 4));

		assertThat(chain.finish(new double[] { 1, 2, 3, 4, 5 })).isCloseTo(15.0, within(EXACT));
	}

	/** More people cannot help with work that has to happen one piece at a time. */
	@Test
	void aChainFinishesAtTheSameMomentHoweverManyPeopleThereAre() {
		List<Precedence> links = List.of(edge(0, 1), edge(1, 2), edge(2, 3), edge(3, 4));
		double[] durations = { 1, 2, 3, 4, 5 };

		assertThat(schedule(5, 100, links.toArray(new Precedence[0])).finish(durations)).isCloseTo(15.0, within(EXACT));
	}

	/**
	 * <strong>The whole argument for scheduling rather than summing, as one
	 * test.</strong> Ten items, ten hours each, and nothing about them changes between
	 * these three numbers — only how many can be worked on at once. A summing aggregator
	 * answers 100 every time, which is a claim that one person does everything end to
	 * end, made by accident.
	 */
	@Test
	void structureChangesTheAnswerMoreThanTheEstimatesDo() {
		double[] durations = new double[10];
		java.util.Arrays.fill(durations, 10.0);

		assertThat(schedule(10, 1).finish(durations)).isCloseTo(100.0, within(EXACT));
		assertThat(schedule(10, 3).finish(durations)).isCloseTo(40.0, within(EXACT));
		assertThat(schedule(10, 10).finish(durations)).isCloseTo(10.0, within(EXACT));
	}

	/** The same ten items, joined up two different ways, at the same capacity. */
	@Test
	void theSameWorkJoinedUpDifferentlyFinishesAtDifferentTimes() {
		double[] durations = new double[10];
		java.util.Arrays.fill(durations, 10.0);
		Precedence[] oneChain = new Precedence[9];
		for (int at = 0; at < 9; at++) {
			oneChain[at] = edge(at, at + 1);
		}
		Precedence[] twoChains = { edge(0, 1), edge(1, 2), edge(2, 3), edge(3, 4), edge(5, 6), edge(6, 7), edge(7, 8),
				edge(8, 9) };

		assertThat(schedule(10, 2, oneChain).finish(durations)).isCloseTo(100.0, within(EXACT));
		assertThat(schedule(10, 2, twoChains).finish(durations)).isCloseTo(50.0, within(EXACT));
	}

	@Test
	void aDiamondFinishesAtTheLongerBranch() {
		Schedule diamond = schedule(4, 100, edge(0, 1), edge(0, 2), edge(1, 3), edge(2, 3));

		// 0 finishes at 1; the long branch finishes at 11; 3 cannot start before then.
		assertThat(diamond.finish(new double[] { 1, 10, 2, 1 })).isCloseTo(12.0, within(EXACT));
	}

	@Test
	void aLagDelaysASuccessorExactly() {
		Schedule waiting = schedule(2, 1, edge(0, 1, 5.0));

		// The first finishes at 2, the wait runs to 7, the second finishes at 10.
		assertThat(waiting.finish(new double[] { 2, 3 })).isCloseTo(10.0, within(EXACT));
	}

	/**
	 * <strong>A wait is not work, so it holds nobody up.</strong> With one person and a
	 * five-hour wait after the first task, the third task runs <em>during</em> the wait.
	 * Were the wait occupying the slot, nothing could, and the plan would finish at 11
	 * instead of 8.
	 */
	@Test
	void aWaitOccupiesNobody() {
		Schedule waiting = schedule(3, 1, edge(0, 1, 5.0));

		assertThat(waiting.finish(new double[] { 2, 1, 3 })).isCloseTo(8.0, within(EXACT));
	}

	/**
	 * <strong>A wait can elapse while the only person available is busy elsewhere, and it
	 * does not jump the queue when they are done.</strong> The wait is over at five and
	 * the work it was holding back could go at any time after that — but the hundred-hour
	 * task has the slot until 102, and nothing preempts it. That is the non-preemption
	 * assumption doing its job: it is what stops a plan being forecast as though people
	 * could be pulled off one thing and put on another the moment something else became
	 * possible.
	 */
	@Test
	void aWaitThatElapsesWhileEverybodyIsBusyStillHasToQueue() {
		Schedule waiting = schedule(3, 1, edge(0, 1, 3.0));

		assertThat(waiting.finish(new double[] { 2, 1, 100 })).isCloseTo(103.0, within(EXACT));
	}

	/**
	 * Work that has visibly started is under way whatever the plan says comes before it.
	 * Reality wins: a plan that says this could not have begun is wrong about the world.
	 * Were the order honoured, the first would run 0 to 5 and the second 5 to 8.
	 */
	@Test
	void workAlreadyUnderWayStartsAtOnceDespiteAnUnfinishedPredecessor() {
		Schedule started = Schedule.of(List.of(edge(0, 1)), new double[] { 5, 3 }, new boolean[] { false, true }, 2);

		assertThat(started.finish(new double[] { 5, 3 })).isCloseTo(5.0, within(EXACT));
	}

	/**
	 * And whatever the capacity is. Two things are under way and only one slot was
	 * claimed; both run, because both are already running. The constraint reasserts
	 * itself for everything that has not started yet.
	 */
	@Test
	void workAlreadyUnderWayStartsAtOnceDespiteAFullPlan() {
		Schedule started = Schedule.of(List.of(), new double[] { 4, 6 }, new boolean[] { true, true }, 1);

		assertThat(started.finish(new double[] { 4, 6 })).isCloseTo(6.0, within(EXACT));
	}

	@Test
	void nothingElseStartsUntilTheOverfilledPlanDrainsBackToItsCapacity() {
		Schedule started = Schedule.of(List.of(), new double[] { 4, 6, 1 }, new boolean[] { true, true, false }, 1);

		// The two under way run to 4 and 6; only then is there room for the third.
		assertThat(started.finish(new double[] { 4, 6, 1 })).isCloseTo(7.0, within(EXACT));
	}

	/**
	 * <strong>The priority rule, doing something.</strong> One person, and two tasks that
	 * could go first. Starting the one with work waiting behind it lets its five-hour
	 * wait run down while the other task is done; starting the other first pushes
	 * everything out by five hours, to 61.
	 */
	@Test
	void whatHasMostWorkWaitingBehindItGoesFirst() {
		Schedule plan = Schedule.of(List.of(edge(0, 2, 50.0)), new double[] { 5, 5, 1 }, new boolean[3], 1);

		assertThat(plan.finish(new double[] { 5, 5, 1 })).isCloseTo(56.0, within(EXACT));
	}

	/**
	 * <strong>And where two are level, the one written down first.</strong> Both tasks
	 * have exactly one hour of work waiting behind them, so the rule above cannot
	 * separate them; taking the earlier one gives 56, and taking the later one would give
	 * 61. The rule is arbitrary and it is <em>stated</em>, which is what makes two
	 * forecasts of one plan comparable.
	 */
	@Test
	void workLevelOnPriorityStartsInTheOrderItWasWrittenDown() {
		Schedule plan = Schedule.of(List.of(edge(0, 2, 50.0), edge(1, 3, 40.0)), new double[] { 5, 5, 1, 1 },
				new boolean[4], 1);

		assertThat(plan.finish(new double[] { 5, 5, 1, 1 })).isCloseTo(56.0, within(EXACT));
	}

	/** The same plan, with its edges handed over in a different order, twelve times. */
	@Test
	void theAnswerDoesNotDependOnTheOrderTheEdgesArrivedIn() {
		List<Precedence> links = new ArrayList<>(
				List.of(edge(0, 2, 50.0), edge(1, 3, 40.0), edge(2, 4), edge(3, 4), edge(0, 4)));
		double[] efforts = { 5, 5, 1, 1, 2 };
		double[] durations = { 5, 5, 1, 1, 2 };
		double expected = Schedule.of(links, efforts, new boolean[5], 1).finish(durations);
		Random shuffler = new Random(20260814L);

		for (int attempt = 0; attempt < 12; attempt++) {
			Collections.shuffle(links, shuffler);

			assertThat(Schedule.of(links, efforts, new boolean[5], 1).finish(durations)).isCloseTo(expected,
					within(EXACT));
		}
	}

	/**
	 * Ten things at once, all finishing at different moments, so the heap gets a workout.
	 */
	@Test
	void handlesEverythingRunningAtOnceAndFinishingOutOfOrder() {
		double[] durations = { 7, 3, 9, 1, 5, 2, 8, 4, 6, 10 };

		assertThat(schedule(10, 10).finish(durations)).isCloseTo(10.0, within(EXACT));
		// Four at a time, taken in write order as each slot frees: the last to start is
		// the ten-hour one, at nine, so the plan finishes at nineteen.
		assertThat(schedule(10, 4).finish(durations)).isCloseTo(19.0, within(EXACT));
	}

	@Test
	void workThatCostsNothingCostsNothing() {
		Schedule chain = schedule(3, 1, edge(0, 1), edge(1, 2));

		assertThat(chain.finish(new double[] { 0, 0, 0 })).isZero();
	}

	/**
	 * An unestimated item weighs nothing and still holds its place: the two either side
	 * of it stay in order, which is the whole reason step 2 keeps it in the graph.
	 */
	@Test
	void workNobodyEstimatedStillKeepsTheOrderAroundIt() {
		Schedule chain = schedule(3, 3, edge(0, 1), edge(1, 2));

		assertThat(chain.finish(new double[] { 4, 0, 4 })).isCloseTo(8.0, within(EXACT));
	}

	@Test
	void aPlanWithNothingInItFinishesAtOnce() {
		assertThat(Schedule.of(List.of(), new double[0], new boolean[0], 1).finish(new double[0])).isZero();
	}

	@Test
	void refusesAPlanThatWaitsForItself() {
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(2, 1, edge(0, 1), edge(1, 0)))
			.withMessageContaining("waits for itself");
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(3, 1, edge(0, 1), edge(1, 2), edge(2, 0)));
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(1, 1, edge(0, 0)));
	}

	/**
	 * A cycle off to one side is still a cycle, even where the rest of the plan is fine.
	 */
	@Test
	void refusesAPlanOnlyPartOfWhichWaitsForItself() {
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(4, 1, edge(0, 1), edge(2, 3), edge(3, 2)));
	}

	@Test
	void refusesAPlanNobodyCanWorkOn() {
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(2, 0));
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(2, -1));
	}

	@Test
	void refusesAnEdgeNamingWorkThatIsNotThere() {
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(2, 1, edge(0, 2)));
		assertThatIllegalArgumentException().isThrownBy(() -> schedule(2, 1, edge(-1, 1)));
	}

	@Test
	void refusesAPlanThatCannotAgreeHowManyItemsItHas() {
		assertThatIllegalArgumentException().isThrownBy(() -> Schedule.of(List.of(), new double[3], new boolean[2], 1));
	}

	@Test
	void refusesADrawWithTheWrongNumberOfDurations() {
		Schedule plan = schedule(3, 1);

		assertThatIllegalArgumentException().isThrownBy(() -> plan.finish(new double[2]));
		assertThatIllegalArgumentException().isThrownBy(() -> plan.finish(new double[4]));
	}

	@Test
	void refusesAWaitThatRunsBackwards() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Precedence(0, 1, -1.0));
	}

	/**
	 * Everything level on priority, so the ranking falls back to write order throughout.
	 */
	private static Schedule schedule(int items, int capacity, Precedence... edges) {
		return Schedule.of(List.of(edges), new double[items], new boolean[items], capacity);
	}

	private static Precedence edge(int predecessor, int successor) {
		return edge(predecessor, successor, 0.0);
	}

	private static Precedence edge(int predecessor, int successor, double lagHours) {
		return new Precedence(predecessor, successor, lagHours);
	}

}
