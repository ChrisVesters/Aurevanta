package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.item.WorkItemStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <strong>{@code cuttingOneItemMovesNoOtherNumberInTheRun} is the whole of this step, and
 * it is the assertion the milestone's honesty rests on.</strong> An inverse query
 * compares a plan with a plan-minus-something, and two runs with different random numbers
 * differ by more than most cuts are worth — measured, the same plan re-seeded moves the
 * answer by more than a point at ten thousand runs while a cut worth having buys about
 * five. So the counterfactual has to draw the <em>same</em> numbers, and this is what
 * says it does: every other item, every run, exactly the duration it had before.
 *
 * <p>
 * Nothing else in the suite would notice it breaking. A decoupled comparison still
 * produces a ranking, and the ranking still looks like an answer.
 */
class CutTests {

	private static final long SEED = 20260818L;

	private static final int RUNS = 2_000;

	/**
	 * Every path {@link ItemModel#sample} can take, because they draw different numbers
	 * of times and a cut has to preserve each of them: a plain estimate, two estimators
	 * disagreeing, work under way with hours against it, work already finished, and work
	 * nobody costed.
	 */
	private static List<ItemModel> plan() {
		return List.of(item(List.of(LogNormalFit.from(8.0, 40.0)), WorkItemStatus.NOT_STARTED, 0.0),
				item(List.of(LogNormalFit.from(10.0, 30.0), LogNormalFit.from(20.0, 25.0)), WorkItemStatus.NOT_STARTED,
						0.0),
				item(List.of(LogNormalFit.from(18.0, 22.0)), WorkItemStatus.IN_PROGRESS, 6.0),
				item(List.of(LogNormalFit.from(4.0, 9.0)), WorkItemStatus.DONE, 0.0),
				item(List.of(), WorkItemStatus.NOT_STARTED, 0.0));
	}

	private static List<Precedence> edges() {
		return List.of(new Precedence(0, 1, 0.0), new Precedence(2, 4, 0.0));
	}

	/**
	 * <strong>The property this step exists for.</strong> One item cut, the same seed,
	 * and every other item draws exactly what it drew before — down to the last bit,
	 * because "the same random numbers" is the claim and anything short of identical is
	 * not it. The shared stretch and the number of items discovered are checked too: both
	 * come off the generator before the plan does, so a cut that disturbed them would
	 * have disturbed everything.
	 */
	@Test
	void cuttingOneItemMovesNoOtherNumberInTheRun() {
		for (int cutting = 0; cutting < plan().size(); cutting++) {
			List<Run> before = runsOf(plan());
			List<Run> after = runsOf(withCut(cutting));

			assertThat(after).as("cutting item %d", cutting).hasSameSizeAs(before);
			for (int run = 0; run < before.size(); run++) {
				Run was = before.get(run);
				Run now = after.get(run);
				assertThat(now.stretch()).as("stretch, run %d, cutting %d", run, cutting).isEqualTo(was.stretch());
				assertThat(now.found()).as("discovered, run %d, cutting %d", run, cutting).isEqualTo(was.found());
				for (int at = 0; at < was.durations().length; at++) {
					if (at != cutting) {
						assertThat(now.durations()[at]).as("item %d, run %d, cutting %d", at, run, cutting)
							.isEqualTo(was.durations()[at]);
					}
				}
			}
		}
	}

	/** And the cut item itself is worth nothing, in every run, exactly. */
	@Test
	void aCutItemCostsNothingAtAll() {
		List<Run> after = runsOf(withCut(0));

		for (Run run : after) {
			assertThat(run.durations()[0]).isEqualTo(0.0);
		}
	}

	/**
	 * A plan with everything imagined away takes no time — which is worth pinning because
	 * it is the one answer a cut can give that needs no scheduler at all, and because a
	 * discovered item is not cut and would show up here if scope growth were left on.
	 */
	@Test
	void aPlanWithEverythingCutFinishesAtOnce() {
		List<ItemModel> nothing = new ArrayList<>();
		for (ItemModel item : plan()) {
			nothing.add(item.asCut());
		}

		Forecast forecast = Engine.run(nothing, edges(), 2, TeamFactor.NONE, ScopeGrowth.NONE, RUNS, SEED);

		assertThat(forecast.p95Hours()).isEqualTo(0.0);
		assertThat(forecast.meanHours()).isEqualTo(0.0);
	}

	/**
	 * <strong>Decision 3, asserted where it can be.</strong> The scheduler ranks by
	 * typical effort, and that must not move: a cut which reordered the queue would leave
	 * the counterfactual differing from its baseline in two ways at once, with no way to
	 * tell which produced the difference — and it would still look like an answer.
	 */
	@Test
	void aCutDoesNotChangeWhereTheSchedulerRanksIt() {
		for (ItemModel item : plan()) {
			assertThat(item.asCut().typicalEffortHours()).as("%s", item.status()).isEqualTo(item.typicalEffortHours());
		}
	}

	/**
	 * A cut item is still an example of what this team's work costs. Dropping it from the
	 * reference class would move every draw taken from it — and would be untrue besides,
	 * since imagining a task away says nothing about the size of the ones nobody has
	 * thought of.
	 */
	@Test
	void aCutItemStillSaysWhatNewWorkWouldCost() {
		ItemModel estimated = plan().getFirst();

		assertThat(estimated.asCut().sampleAsNewWork(new java.util.Random(SEED)))
			.isEqualTo(estimated.sampleAsNewWork(new java.util.Random(SEED)));
	}

	/**
	 * Cutting work that already weighed nothing changes nothing, including the draws —
	 * there were none to take, and the flag does not invent any.
	 */
	@Test
	void cuttingWorkThatAlreadyWeighedNothingIsANoOp() {
		Forecast plain = Engine.run(plan(), edges(), 2, TeamFactor.NONE, ScopeGrowth.NONE, RUNS, SEED);

		Forecast withFinishedCut = Engine.run(withCut(3), edges(), 2, TeamFactor.NONE, ScopeGrowth.NONE, RUNS, SEED);
		Forecast withUnestimatedCut = Engine.run(withCut(4), edges(), 2, TeamFactor.NONE, ScopeGrowth.NONE, RUNS, SEED);

		assertThat(withFinishedCut).isEqualTo(plain);
		assertThat(withUnestimatedCut).isEqualTo(plain);
	}

	/**
	 * Cutting something that is actually on the path shortens the plan, or none of this
	 * would be worth doing.
	 */
	@Test
	void cuttingWorkThatWasHoldingThePlanUpShortensIt() {
		Forecast plain = Engine.run(plan(), edges(), 1, TeamFactor.NONE, ScopeGrowth.NONE, RUNS, SEED);

		Forecast cut = Engine.run(withCut(0), edges(), 1, TeamFactor.NONE, ScopeGrowth.NONE, RUNS, SEED);

		assertThat(cut.p90Hours()).isLessThan(plain.p90Hours());
	}

	/** One run of a forecast, as the observer saw it happen. */
	private record Run(double[] durations, int found, double stretch) {
	}

	/**
	 * Every run of a forecast, kept. The engine reuses its own array, so each row is
	 * copied out — an observer that held onto it would hold whatever the last run put
	 * there.
	 */
	private static List<Run> runsOf(List<ItemModel> plan) {
		List<Run> seen = new ArrayList<>(RUNS);
		Engine.run(plan, edges(), 2, TeamFactor.from(30.0), ScopeGrowth.from(20.0, 60.0), RUNS, SEED,
				(durations, items, discoveredHours, stretch, completion) -> seen
					.add(new Run(Arrays.copyOf(durations, items), durations.length, stretch)));
		return seen;
	}

	private static List<ItemModel> withCut(int at) {
		List<ItemModel> cut = new ArrayList<>(plan());
		cut.set(at, cut.get(at).asCut());
		return cut;
	}

	private static ItemModel item(List<LogNormalFit> estimates, WorkItemStatus status, double spentHours) {
		return new ItemModel(UUID.randomUUID(), estimates, status, spentHours);
	}

}
