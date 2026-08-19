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

	/**
	 * <strong>Too few is refused; too many is a caller reusing its room.</strong> This
	 * asked for exactly one duration per item until scope growth arrived, and a run that
	 * discovers eleven pieces of work followed by one that discovers three reads fewer
	 * entries of the same array rather than allocating a smaller one. Handing over a draw
	 * from the wrong plan is still caught, because that draw is short.
	 */
	@Test
	void refusesADrawWithTooFewDurations() {
		Schedule plan = schedule(3, 1);

		assertThatIllegalArgumentException().isThrownBy(() -> plan.finish(new double[2]));
		assertThatIllegalArgumentException().isThrownBy(() -> plan.finish(new double[3], new int[] { 0 }, 1));
	}

	// Work nobody had thought of ---------------------------------------------

	/**
	 * <strong>Where it lands is the whole of decision 3, and this is that decision as
	 * arithmetic.</strong> The same discovered four hours costs the plan four hours or
	 * nothing at all, depending only on which piece of work it was found behind: hung off
	 * the ten-hour task it runs from ten to fourteen, and hung off the three-hour one it
	 * fits inside the time the plan was taking anyway.
	 */
	@Test
	void discoveredWorkWaitsForWhateverItWasFoundBehind() {
		Schedule plan = schedule(2, 10);
		double[] durations = { 10, 3, 4 };

		assertThat(plan.finish(durations, new int[] { 0 }, 1)).isCloseTo(14.0, within(EXACT));
		assertThat(plan.finish(durations, new int[] { 1 }, 1)).isCloseTo(10.0, within(EXACT));
		assertThat(plan.finish(durations)).isCloseTo(10.0, within(EXACT));
	}

	/**
	 * <strong>Decision 6's mechanism, in four numbers.</strong> Two slots and three tasks
	 * of 2, 5 and 5 hours finish at 7. Discover three hours more behind the first of them
	 * and the plan finishes at 8 — not because the new work was on the end, but because
	 * it took the slot the five-hour task would otherwise have had to itself. That is the
	 * thing a multiplier on every duration cannot express, and the reason scope growth is
	 * modelled as items rather than as a number.
	 */
	@Test
	void discoveredWorkTakesASlotFromWhatWasWaitingForOne() {
		Schedule plan = schedule(3, 2);
		double[] durations = { 2, 5, 5, 3 };

		assertThat(plan.finish(durations)).isCloseTo(7.0, within(EXACT));
		assertThat(plan.finish(durations, new int[] { 0 }, 1)).isCloseTo(8.0, within(EXACT));
	}

	/**
	 * Two pieces found behind one task are two ordinary pieces of work: side by side
	 * where there is room, and one after the other where there is not.
	 */
	@Test
	void twoThingsFoundBehindOneTaskQueueUpLikeAnythingElse() {
		double[] durations = { 2, 3, 4 };
		int[] behindTheFirst = { 0, 0 };

		assertThat(schedule(1, 2).finish(durations, behindTheFirst, 2)).isCloseTo(6.0, within(EXACT));
		assertThat(schedule(1, 1).finish(durations, behindTheFirst, 2)).isCloseTo(9.0, within(EXACT));
	}

	/**
	 * <strong>Discovered work is picked up after everything somebody planned</strong>,
	 * and this is the one arrangement where that is visible rather than merely true. The
	 * parent is already under way, so it finishes at half an hour and releases five hours
	 * of new work while the one-hour task is still waiting for the only slot. The planned
	 * task goes first, its twenty-hour wait starts at 1.5 and the plan ends at 22.5; had
	 * the new work jumped the queue the wait would have started at 6.5 and the answer
	 * would be 27.5.
	 */
	@Test
	void plannedWorkIsPickedUpBeforeWorkDiscoveredAMomentAgo() {
		Schedule plan = Schedule.of(List.of(edge(1, 2, 20.0)), new double[] { 1, 1, 1 },
				new boolean[] { true, false, false }, 1);

		assertThat(plan.finish(new double[] { 0.5, 1, 1, 5 }, new int[] { 0 }, 1)).isCloseTo(22.5, within(EXACT));
	}

	/**
	 * Nothing discovered is the plan exactly as it was, which is what the simulation
	 * engine forecast.
	 */
	@Test
	void discoveringNothingIsTheSamePlan() {
		Schedule plan = schedule(3, 2, edge(0, 1));
		double[] durations = { 4, 5, 6 };

		assertThat(plan.finish(durations, new int[] { 2 }, 0)).isEqualTo(plan.finish(durations));
	}

	@Test
	void refusesWorkFoundBehindSomethingThatIsNotInThePlan() {
		Schedule plan = schedule(2, 1);
		double[] durations = { 1, 1, 1 };

		assertThatIllegalArgumentException().isThrownBy(() -> plan.finish(durations, new int[] { 2 }, 1));
		assertThatIllegalArgumentException().isThrownBy(() -> plan.finish(durations, new int[] { -1 }, 1));
	}

	@Test
	void refusesAWaitThatRunsBackwards() {
		assertThatIllegalArgumentException().isThrownBy(() -> new Precedence(0, 1, -1.0));
	}

	// Resources: when the slots stop being interchangeable ----------------------

	/**
	 * <strong>The oracle for the whole version bump, and it is exact numbers rather than
	 * an argument.</strong> These six finishes were read off this class <em>before</em>
	 * it knew what a resource was — a twelve-item plan with lags, work already under way,
	 * two pieces of discovered work and five drawn sets of durations. A scheduler that
	 * reached the same answers by a different route would not be the same scheduler; one
	 * that reaches these, to the last bit, is.
	 *
	 * <p>
	 * It is what says {@code Engine.VERSION} 3 contains version 2, and so what says every
	 * forecast this product has already stored can still be explained, weighed and
	 * compared.
	 */
	@Test
	void answersExactlyWhatItAnsweredBeforeThereWereResources() {
		int items = 12;
		List<Precedence> links = new ArrayList<>();
		for (int at = 3; at < items; at++) {
			links.add(edge(at - 3, at, (at % 2 == 0) ? 0.0 : 4.0));
		}
		double[] typical = new double[items];
		boolean[] underWay = new boolean[items];
		for (int at = 0; at < items; at++) {
			typical[at] = 3 + at;
		}
		underWay[1] = true;
		Schedule schedule = Schedule.of(links, typical, underWay, 3);
		Random random = new Random(20260819L);
		double[] expected = { 60.07938562992277, 62.81544289970311, 40.037653116245984, 78.87836910477925,
				80.82830260869801 };

		for (int run = 0; run < expected.length; run++) {
			double[] durations = new double[items + 2];
			for (int at = 0; at < items; at++) {
				durations[at] = 2 + 20 * random.nextDouble();
			}
			durations[items] = 7.5;
			durations[items + 1] = 3.25;

			assertThat(schedule.finish(durations, new int[] { 4, 0 }, 2)).as("run %d", run).isEqualTo(expected[run]);
		}
		assertThat(schedule.finish(new double[] { 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5 })).isEqualTo(28.0);
	}

	/**
	 * And the same said the other way: one pool of <em>n</em> units with nothing named is
	 * capacity <em>n</em>, not approximately and not usually.
	 */
	@Test
	void onePoolOfManyUnitsIsTheCapacityItReplaces() {
		double[] durations = { 4, 6, 3, 9, 2, 7, 5, 1 };
		List<Precedence> links = List.of(edge(0, 4), edge(1, 5), edge(2, 6));

		for (int capacity = 1; capacity <= 4; capacity++) {
			Schedule counted = Schedule.of(links, new double[8], new boolean[8], capacity);
			Schedule pooled = Schedule.of(links, new double[8], new boolean[8],
					Resourcing.of(new int[] { capacity }, new int[8][1]));

			assertThat(pooled.finish(durations)).as("capacity %d", capacity).isEqualTo(counted.finish(durations));
		}
	}

	/**
	 * <strong>A team nobody has annotated is the capacity it adds up to, however many
	 * pools it has</strong> — which is the property that makes describing a team safe,
	 * and the one that says why doing so alone changes nothing.
	 *
	 * <p>
	 * Work that names nothing takes one unit of whichever pool has one free, so when
	 * <em>nothing</em> is named the pools are interchangeable and only the total matters.
	 * It was found the hard way: a decomposition rebuilt without its team gave identical
	 * answers on a fixture where no item named anything, and the bug it was hiding needed
	 * a requirement before it would show at all.
	 */
	@Test
	void poolsNobodyHasNamedAreTheCapacityTheyAddUpTo() {
		double[] durations = { 4, 6, 3, 9, 2, 7 };
		List<Precedence> links = List.of(edge(0, 3), edge(1, 4));

		Schedule counted = Schedule.of(links, new double[6], new boolean[6], 5);

		for (int[] pools : new int[][] { { 5 }, { 2, 3 }, { 1, 1, 3 }, { 1, 1, 1, 1, 1 } }) {
			Schedule typed = Schedule.of(links, new double[6], new boolean[6],
					Resourcing.of(pools, new int[6][pools.length]));

			assertThat(typed.finish(durations)).as("%d pools", pools.length).isEqualTo(counted.finish(durations));
		}
	}

	/** And one requirement is all it takes for that to stop being true. */
	@Test
	void oneNamedRequirementIsEnoughToTellThemApart() {
		double[] durations = { 10, 10 };
		int[][] bothOnTheFirst = { { 1, 0 }, { 1, 0 } };

		Schedule counted = Schedule.of(List.of(), new double[2], new boolean[2], 2);
		Schedule typed = Schedule.of(List.of(), new double[2], new boolean[2],
				Resourcing.of(new int[] { 1, 1 }, bothOnTheFirst));

		assertThat(counted.finish(durations)).isCloseTo(10.0, within(EXACT));
		assertThat(typed.finish(durations)).isCloseTo(20.0, within(EXACT));
	}

	/**
	 * <strong>The measurement this work exists for, as a case anybody can check by
	 * hand.</strong> Four items of ten hours and four units of capacity: interchangeable,
	 * they all run at once and the plan takes ten. Split into two pools of two with three
	 * items needing the first, and the third of them waits — because a unit of the other
	 * pool is not a unit anybody can use.
	 */
	@Test
	void workThatCannotCrossPoolsFinishesLater() {
		double[] durations = { 10, 10, 10, 10 };
		int[][] threeOnTheFirst = { { 1, 0 }, { 1, 0 }, { 1, 0 }, { 0, 1 } };

		Schedule interchangeable = Schedule.of(List.of(), new double[4], new boolean[4], 4);
		Schedule typed = Schedule.of(List.of(), new double[4], new boolean[4],
				Resourcing.of(new int[] { 2, 2 }, threeOnTheFirst));

		assertThat(interchangeable.finish(durations)).isCloseTo(10.0, within(EXACT));
		assertThat(typed.finish(durations)).isCloseTo(20.0, within(EXACT));
	}

	/** Work that ties up a whole pool runs alone, however much else is ready. */
	@Test
	void workNeedingEveryUnitOfAPoolRunsAlone() {
		double[] durations = { 10, 10, 10 };
		int[][] oneWantsBoth = { { 2 }, { 1 }, { 1 } };

		Schedule schedule = Schedule.of(List.of(), new double[3], new boolean[3],
				Resourcing.of(new int[] { 2 }, oneWantsBoth));

		// Ten hours alone, then the other two together.
		assertThat(schedule.finish(durations)).isCloseTo(20.0, within(EXACT));
	}

	/**
	 * <strong>Decision 7, and it is the assumption a reader is most likely to disagree
	 * with.</strong> The first item cannot have what it needs, and the plan does not sit
	 * idle waiting for it — the work behind it starts instead. A team that will not do
	 * available work because one person is busy is not a team anybody has.
	 */
	@Test
	void workThatCannotStartDoesNotHoldUpTheWorkBehindIt() {
		// The first item is the highest priority and needs both units of a pool that has
		// one of them tied up by something already under way.
		double[] durations = { 10, 4, 4 };
		int[][] needs = { { 2 }, { 1 }, { 1 } };
		boolean[] underWay = { false, false, true };

		// Nothing waits behind anything here, so the priority order is write order —
		// which
		// is a property of the ranking worth knowing: it only bites on a graph.
		Schedule schedule = Schedule.of(List.of(), new double[3], underWay, Resourcing.of(new int[] { 2 }, needs));

		// The third is running from the start; the second takes the free unit rather than
		// holding it for the first, which begins at four and ends at fourteen.
		assertThat(schedule.finish(durations)).isCloseTo(14.0, within(EXACT));
	}

	/**
	 * Work already under way holds what it needs whether or not the pools have it — it
	 * has visibly begun, and a plan that said otherwise would be wrong about the world.
	 * The units go below nothing and nothing new starts until enough come back.
	 */
	@Test
	void workAlreadyUnderWayHoldsUnitsThePoolsDoNotHave() {
		double[] durations = { 6, 6, 6, 1 };
		boolean[] underWay = { true, true, true, false };

		Schedule schedule = Schedule.of(List.of(), new double[4], underWay,
				Resourcing.of(new int[] { 1 }, new int[4][1]));

		// Three are running over a pool of one, so the fourth waits for all three.
		assertThat(schedule.finish(durations)).isCloseTo(7.0, within(EXACT));
	}

	/**
	 * <strong>Work that names nothing takes one unit of whatever is free, in declaration
	 * order</strong> — and gives that same unit back rather than one of somebody else's.
	 * Two generic items against two pools of one run together; a third waits.
	 */
	@Test
	void workThatNamesNothingTakesOneUnitOfWhateverIsFree() {
		double[] durations = { 5, 5, 5 };

		Schedule schedule = Schedule.of(List.of(), new double[3], new boolean[3],
				Resourcing.of(new int[] { 1, 1 }, new int[3][2]));

		assertThat(schedule.finish(durations)).isCloseTo(10.0, within(EXACT));
	}

	/**
	 * And it is genuinely returned to the pool it came from: a named item afterwards can
	 * only run if the right unit is free again.
	 */
	@Test
	void aGenericPieceOfWorkGivesBackTheUnitItTook() {
		double[] durations = { 5, 5 };
		// The first names nothing and takes the first pool; the second needs the first
		// pool
		// and can only start once that same unit is back.
		int[][] needs = { { 0, 0 }, { 1, 0 } };

		Schedule schedule = Schedule.of(List.of(), new double[2], new boolean[2],
				Resourcing.of(new int[] { 1, 1 }, needs));

		assertThat(schedule.finish(durations)).isCloseTo(10.0, within(EXACT));
	}

	/**
	 * Work nobody had thought of is scheduled by the requirement of the item it was found
	 * behind: work discovered behind a backend task is backend work.
	 */
	@Test
	void discoveredWorkNeedsWhatItsParentNeeded() {
		double[] durations = { 4, 4, 6, 6 };
		int[][] needs = { { 1, 0 }, { 0, 1 } };

		Schedule schedule = Schedule.of(List.of(), new double[2], new boolean[2],
				Resourcing.of(new int[] { 1, 1 }, needs));

		// One piece found behind each item, each needing the pool its parent needed — so
		// each pool runs its two in sequence and the plan takes four and then six.
		assertThat(schedule.finish(durations, new int[] { 0, 1 }, 2)).isCloseTo(10.0, within(EXACT));
	}

	/**
	 * <strong>Refused when the schedule is prepared rather than never starting.</strong>
	 * The loop has no guard against work that cannot fit and its termination argument
	 * depends on there being none — with nothing running every unit is free, so anything
	 * that can ever start can start then. Work asking for more of a pool than exists
	 * would break that quietly, by waiting for ever.
	 */
	@Test
	void workNeedingMoreOfAPoolThanExistsIsRefused() {
		assertThatIllegalArgumentException().isThrownBy(() -> Resourcing.of(new int[] { 2 }, new int[][] { { 3 } }))
			.withMessageContaining("holds 2");
	}

	/**
	 * A declaration made for a different plan is refused rather than read against this
	 * one.
	 */
	@Test
	void aDeclarationForADifferentPlanIsRefused() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> Schedule.of(List.of(), new double[3], new boolean[3], Resourcing.pooled(2, 4)));
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
