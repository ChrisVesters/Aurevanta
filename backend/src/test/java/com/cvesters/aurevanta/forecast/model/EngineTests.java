package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.item.WorkItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

/**
 * How anybody knows the simulator is right.
 *
 * <p>
 * <strong>{@code aSumOfIndependentWorkConvergesOnItsExactMoments} is the test this whole
 * milestone was arranged around.</strong> A schedule at capacity one is a sum of
 * independent draws, and a sum has an exactly known mean and variance whatever shapes
 * went into it — so the sampler can be measured rather than trusted. `m3a-plan.md`
 * decision 1 splits the milestone precisely at the point where that check stops being
 * available.
 *
 * <p>
 * <strong>{@code fortyTightTasksLandWhereTheyWereMeasuredToLand} is the other
 * half.</strong> `roadmap.md` measured forty tasks of 18 to 22 days at a true P90 of
 * 811.1, using a closed form that nothing here shares a line of code with. Two methods,
 * arrived at independently, meeting on one number.
 */
class EngineTests {

	private static final long SEED = 20260814L;

	/**
	 * More runs than a forecast does, because these are checking convergence rather than
	 * demonstrating it: at fifty thousand the sampling error is around a third of a
	 * percent, which is what the tolerances below are set against.
	 */
	private static final int MEASURED = 50_000;

	@Test
	void aSumOfIndependentWorkConvergesOnItsExactMoments() {
		List<LogNormalFit> fits = List.of(LogNormalFit.from(8.0, 40.0), LogNormalFit.from(2.0, 30.0),
				LogNormalFit.from(18.0, 22.0), LogNormalFit.from(1.0, 3.0), LogNormalFit.from(40.0, 120.0));
		double expectedMean = fits.stream().mapToDouble(LogNormalFit::mean).sum();
		double expectedVariance = fits.stream().mapToDouble(LogNormalFit::variance).sum();

		Forecast forecast = Engine.run(chainOf(fits), chainLinks(fits.size()), 1, TeamFactor.NONE, ScopeGrowth.NONE,
				MEASURED, SEED);

		assertThat(forecast.meanHours()).isCloseTo(expectedMean, withinPercentage(1.0));
		assertThat(forecast.standardDeviationHours()).isCloseTo(Math.sqrt(expectedVariance), withinPercentage(2.0));
	}

	/**
	 * The measurement `roadmap.md` carries, reproduced. Forty tasks tight enough that the
	 * central limit theorem does all the work, which is the one regime where the closed
	 * form it rejected is exact — so the two must agree, and if they do not, one of them
	 * is wrong in a way no amount of internal consistency would reveal.
	 *
	 * <p>
	 * <strong>Measured: 811.08 sampled, 811.12 from the closed form, 811.1 from the
	 * roadmap.</strong> Three routes to one number, agreeing to five thousandths of a
	 * percent, none of them sharing a line of code with the others. The tolerance below
	 * is deliberately far looser than that — it is set from sampling error, about a third
	 * of a percent at fifty thousand runs, so that it stays a test of the engine rather
	 * than a detector of any change to the seed.
	 */
	@Test
	void fortyTightTasksLandWhereTheyWereMeasuredToLand() {
		List<LogNormalFit> fits = new ArrayList<>();
		for (int at = 0; at < 40; at++) {
			fits.add(LogNormalFit.from(18.0, 22.0));
		}
		double mean = fits.stream().mapToDouble(LogNormalFit::mean).sum();
		double deviation = Math.sqrt(fits.stream().mapToDouble(LogNormalFit::variance).sum());

		Forecast forecast = Engine.run(chainOf(fits), chainLinks(fits.size()), 1, TeamFactor.NONE, ScopeGrowth.NONE,
				MEASURED, SEED);

		assertThat(forecast.p90Hours()).isCloseTo(811.1, withinPercentage(1.0));
		// And against the closed form itself, since the normal approximation is exact
		// here.
		assertThat(forecast.p90Hours()).isCloseTo(mean + Normal.P90_Z * deviation, withinPercentage(1.0));
	}

	/**
	 * <strong>Percentiles do not add, and this is the number that says so.</strong> Ten
	 * tasks each ranging 3 to 12 days: summing their P90s gives 120, and the plan's
	 * actual P90 is nowhere near it, because everything going wrong at once is not what
	 * usually happens.
	 */
	@Test
	void thePlansBandIsNarrowerThanTheSumOfItsItemsBands() {
		List<LogNormalFit> fits = new ArrayList<>();
		for (int at = 0; at < 10; at++) {
			fits.add(LogNormalFit.from(3.0, 12.0));
		}

		Forecast forecast = Engine.run(chainOf(fits), chainLinks(fits.size()), 1, TeamFactor.NONE, ScopeGrowth.NONE,
				MEASURED, SEED);

		assertThat(forecast.p90Hours()).isLessThan(120.0).isGreaterThan(forecast.p50Hours());
	}

	@Test
	void itsPercentilesAscend() {
		Forecast forecast = Engine.run(chainOf(threeOrdinaryFits()), chainLinks(3), 1, TeamFactor.NONE,
				ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(forecast.p10Hours()).isLessThan(forecast.p50Hours());
		assertThat(forecast.p50Hours()).isLessThan(forecast.p80Hours());
		assertThat(forecast.p80Hours()).isLessThan(forecast.p90Hours());
		assertThat(forecast.p90Hours()).isLessThan(forecast.p95Hours());
	}

	/**
	 * Decision 9's promise, which everything a stored run claims about itself rests on.
	 */
	@Test
	void theSameSeedForecastsTheSamePlanIdentically() {
		List<ItemModel> plan = chainOf(threeOrdinaryFits());

		Forecast once = Engine.run(plan, chainLinks(3), 2, TeamFactor.NONE, ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED);
		Forecast again = Engine.run(plan, chainLinks(3), 2, TeamFactor.NONE, ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(again).isEqualTo(once);
	}

	@Test
	void aDifferentSeedForecastsItDifferently() {
		List<ItemModel> plan = chainOf(threeOrdinaryFits());

		Forecast once = Engine.run(plan, chainLinks(3), 2, TeamFactor.NONE, ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED);
		Forecast again = Engine.run(plan, chainLinks(3), 2, TeamFactor.NONE, ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED + 1);

		assertThat(again).isNotEqualTo(once);
	}

	/**
	 * <strong>The test that fails if the sampler is subtly biased.</strong> Ten seeds,
	 * ten thousand runs each, and the P90 they report has to sit inside the ±0.77%
	 * sampling error `roadmap.md` measured for that many runs. A sampler that is merely
	 * plausible passes every other test in this file and fails this one.
	 */
	@Test
	void tenThousandRunsAgreeWithEachOtherToTheMeasuredSamplingError() {
		List<LogNormalFit> fits = new ArrayList<>();
		for (int at = 0; at < 10; at++) {
			fits.add(LogNormalFit.from(2.0, 30.0));
		}
		List<ItemModel> plan = chainOf(fits);
		List<Double> ninetieths = new ArrayList<>();

		for (int attempt = 0; attempt < 10; attempt++) {
			ninetieths.add(
					Engine
						.run(plan, chainLinks(fits.size()), 1, TeamFactor.NONE, ScopeGrowth.NONE,
								Engine.DEFAULT_SAMPLE_COUNT, SEED + attempt)
						.p90Hours());
		}
		double middle = ninetieths.stream().mapToDouble(Double::doubleValue).average().orElseThrow();

		assertThat(ninetieths)
			.allSatisfy((ninetieth) -> assertThat(ninetieth).isCloseTo(middle, withinPercentage(1.5)));
	}

	/**
	 * <strong>The most valuable test in M3b, and it belongs here in its first step rather
	 * than in its last.</strong> These are the numbers this engine produced before there
	 * was a team factor in it, recorded from the M3a build. A factor of none must not
	 * change any of them — not approximately, exactly — because a version 1 run is
	 * nothing more than a version 2 run with its two parameters zeroed, and every stored
	 * forecast's claim to be replayable rests on that being true.
	 *
	 * <p>
	 * It costs one assertion and turns the whole of {@code m3a}'s suite into a regression
	 * suite for {@code m3b}. What it actually guards is the order draws are taken in: a
	 * factor that consumed the generator even while returning 1 would shift every
	 * subsequent number, and nothing else here would report it.
	 *
	 * <p>
	 * The plan below is deliberately awkward — work under way with hours against it, work
	 * nobody estimated, a lag, a fork — so that the golden numbers pass through every
	 * branch of the sampler on their way out.
	 */
	@Test
	void aFactorOfNoneForecastsExactlyWhatThisEngineForecastBeforeItHadOne() {
		Forecast forecast = Engine.run(awkwardPlan(), awkwardLinks(), 2, TeamFactor.NONE, ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(forecast.meanHours()).isEqualTo(35.493596797692184);
		assertThat(forecast.standardDeviationHours()).isEqualTo(23.933403116517443);
		assertThat(forecast.p10Hours()).isEqualTo(15.327219537786771);
		assertThat(forecast.p50Hours()).isEqualTo(29.10202027006082);
		assertThat(forecast.p80Hours()).isEqualTo(47.59775270769211);
		assertThat(forecast.p90Hours()).isEqualTo(62.84338970329079);
		assertThat(forecast.p95Hours()).isEqualTo(77.78726833172917);
		assertThat(forecast.histogram().fromHours()).isEqualTo(9.126893761435971);
		assertThat(forecast.histogram().toHours()).isEqualTo(441.691958192979);
	}

	/**
	 * The mirror, so the golden numbers above are evidence that a factor of none is free
	 * rather than evidence that the parameter is ignored.
	 */
	@Test
	void aFactorThatStretchesForecastsSomethingElseEntirely() {
		Forecast stretched = Engine.run(awkwardPlan(), awkwardLinks(), 2, TeamFactor.from(30.0), ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(stretched.p90Hours()).isNotEqualTo(62.84338970329079);
	}

	/**
	 * <strong>The exactness oracle M3b has in place of M3a's.</strong> A whole plan under
	 * a shared factor has no closed form — that is why these two features are in a
	 * milestone of their own — but <em>one item</em> does, exactly: if {@code X} is
	 * log-normal({@code mu}, {@code sigma}) and {@code F} is log-normal(0, {@code s}) and
	 * they are independent, then {@code F·X} is log-normal({@code mu},
	 * {@code √(sigma² + s²)}). So the factor's implementation is pinned against
	 * arithmetic rather than against a direction of travel.
	 */
	@Test
	void oneItemUnderAFactorIsExactlyTheDistributionTheTwoImply() {
		LogNormalFit fit = LogNormalFit.from(18.0, 22.0);
		TeamFactor factor = TeamFactor.from(30.0);
		LogNormalFit together = new LogNormalFit(fit.mu(), Math.hypot(fit.sigma(), factor.multiplier().sigma()));

		Forecast forecast = Engine.run(List.of(item(List.of(fit))), List.of(), 1, factor, ScopeGrowth.NONE, MEASURED,
				SEED);

		assertThat(forecast.meanHours()).isCloseTo(together.mean(), withinPercentage(1.0));
		assertThat(forecast.standardDeviationHours()).isCloseTo(Math.sqrt(together.variance()), withinPercentage(2.0));
	}

	/**
	 * <strong>The measurement this milestone exists to reproduce.</strong> `roadmap.md`
	 * put ten wide tasks at a true P90 of 209.4 sampled independently and 222.2 under a
	 * shared team factor, and noted that the closed form answers 214.0 to both — a common
	 * cause moved the real answer by thirteen days and the formula could not see it at
	 * all.
	 *
	 * <p>
	 * Both halves of that table, from one plan and one seed, which is what makes the
	 * difference between them attributable to the factor and to nothing else. The 30%
	 * that produces it is the stretch decision 2 uses as its own worked example.
	 */
	@Test
	void tenWideTasksUnderABadStretchLandWhereTheyWereMeasuredToLand() {
		List<LogNormalFit> fits = new ArrayList<>();
		for (int at = 0; at < 10; at++) {
			fits.add(LogNormalFit.from(2.0, 30.0));
		}
		List<ItemModel> plan = chainOf(fits);
		List<Precedence> links = chainLinks(fits.size());

		Forecast alone = Engine.run(plan, links, 1, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED, SEED);
		Forecast together = Engine.run(plan, links, 1, TeamFactor.from(30.0), ScopeGrowth.NONE, MEASURED, SEED);

		assertThat(alone.p90Hours()).isCloseTo(209.4, withinPercentage(1.5));
		assertThat(together.p90Hours()).isCloseTo(222.2, withinPercentage(1.5));
	}

	/**
	 * <strong>The thing that will be reported as a bug.</strong> The band gets much wider
	 * and the middle barely moves — which is the entire point, since the P50 was never
	 * the problem. Common-cause risk is spread, and a factor that shifted the centre
	 * would be making a claim the estimates already made.
	 */
	@Test
	void aWorseStretchWidensTheBandAndLeavesTheMiddleWhereItWas() {
		List<LogNormalFit> fits = new ArrayList<>();
		for (int at = 0; at < 10; at++) {
			fits.add(LogNormalFit.from(2.0, 30.0));
		}
		List<ItemModel> plan = chainOf(fits);
		List<Precedence> links = chainLinks(fits.size());

		Forecast none = Engine.run(plan, links, 1, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED, SEED);
		Forecast some = Engine.run(plan, links, 1, TeamFactor.from(30.0), ScopeGrowth.NONE, MEASURED, SEED);
		Forecast lots = Engine.run(plan, links, 1, TeamFactor.from(60.0), ScopeGrowth.NONE, MEASURED, SEED);

		assertThat(some.p90Hours()).isGreaterThan(none.p90Hours());
		assertThat(lots.p90Hours()).isGreaterThan(some.p90Hours());
		assertThat(some.p10Hours()).isLessThan(none.p10Hours());
		assertThat(lots.p10Hours()).isLessThan(some.p10Hours());
		assertThat(some.p50Hours()).isCloseTo(none.p50Hours(), withinPercentage(2.0));
		assertThat(lots.p50Hours()).isCloseTo(none.p50Hours(), withinPercentage(2.0));
	}

	/**
	 * <strong>Decision 10: the factor multiplies what is left, and nothing else.</strong>
	 * Hours already spent are measured rather than modelled, and a bad quarter that has
	 * not happened yet cannot reach back and make them longer. An estimate that has been
	 * comprehensively outrun has a remainder of exactly zero — the model reporting that
	 * it has been falsified — and no stretch, however bad, may find something in it to
	 * multiply.
	 */
	@Test
	void aStretchFindsNothingToMultiplyInWorkWithNothingLeft() {
		List<ItemModel> plan = List.of(new ItemModel(UUID.randomUUID(), List.of(LogNormalFit.from(19.0, 21.0)),
				WorkItemStatus.IN_PROGRESS, 100.0),
				new ItemModel(UUID.randomUUID(), threeOrdinaryFits(), WorkItemStatus.DONE, 0.0));

		Forecast forecast = Engine.run(plan, List.of(), 1, TeamFactor.from(200.0), ScopeGrowth.NONE, 500, SEED);

		assertThat(forecast.meanHours()).isZero();
		assertThat(forecast.p95Hours()).isZero();
	}

	/**
	 * <strong>The oracle for decision 4, and it is exact.</strong> One item, and a plan
	 * certain to double in size: the run finishes at two draws from one distribution, so
	 * its mean is twice that fit's mean and its variance twice that fit's variance — the
	 * same closed form the whole of M3a was checked against, now checking that work
	 * nobody had thought of costs what this plan's work costs.
	 *
	 * <p>
	 * <strong>Measured at 39.918 against 39.922 exactly</strong>, and a standard
	 * deviation of 2.219 against 2.214. A generator drawing from anything else — the mean
	 * of the plan, a fixed size, the item it was attached to — misses both.
	 *
	 * <p>
	 * It is also the plan's "a plan of one item still grows": there is nothing else in
	 * this plan for the new work to hang off, and it grows regardless.
	 */
	@Test
	void newWorkCostsWhatThisPlansWorkCosts() {
		LogNormalFit fit = LogNormalFit.from(18.0, 22.0);

		Forecast forecast = Engine.run(List.of(item(List.of(fit))), List.of(), 1, TeamFactor.NONE,
				ScopeGrowth.from(100.0, 100.0), MEASURED, SEED);

		assertThat(forecast.meanHours()).isCloseTo(2.0 * fit.mean(), withinPercentage(1.0));
		assertThat(forecast.standardDeviationHours()).isCloseTo(Math.sqrt(2.0 * fit.variance()), withinPercentage(2.0));
	}

	/**
	 * <strong>Decision 6, measured rather than asserted — and it does not say what the
	 * plan said it would.</strong> Twenty items growing by a fifth, against the same
	 * twenty items each a fifth longer: at capacity 1 the two are the same plan, to
	 * within a tenth of a percent, exactly as the decision predicted. Above capacity 1
	 * they part company, which is the decision's real claim and the reason the two
	 * effects are not one effect wearing two hats.
	 *
	 * <p>
	 * <strong>Which of them is worse depends on what the plan is short of, and that is
	 * the finding.</strong> The decision expected scope to be the heavier of the two once
	 * there was a capacity constraint. It is heavier only where capacity is plentiful and
	 * the answer is decided by the longest path — where new work adds a step nothing was
	 * waiting for. Where capacity binds, a multiplier is heavier, because more smaller
	 * pieces pack into the same slots better than fewer larger ones do. Two effects with
	 * different bottlenecks, which is a stronger argument for keeping them apart than
	 * "one is bigger" ever was.
	 */
	@Test
	void scopeAndAMultiplierMeetAtCapacityOneAndPartCompanyAboveIt() {
		List<ItemModel> plan = plainPlan(20, 8.0, 40.0);
		List<ItemModel> stretched = plainPlan(20, 9.6, 48.0);
		ScopeGrowth aFifthMore = ScopeGrowth.from(20.0, 20.0);

		Forecast grownAlone = Engine.run(plan, List.of(), 1, TeamFactor.NONE, aFifthMore, MEASURED, SEED);
		Forecast stretchedAlone = Engine.run(stretched, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED,
				SEED);
		Forecast grownInChains = Engine.run(plan, chainsOfFive(20), 4, TeamFactor.NONE, aFifthMore, MEASURED, SEED);
		Forecast stretchedInChains = Engine.run(stretched, chainsOfFive(20), 4, TeamFactor.NONE, ScopeGrowth.NONE,
				MEASURED, SEED);
		Forecast grownWideOpen = Engine.run(plan, List.of(), 20, TeamFactor.NONE, aFifthMore, MEASURED, SEED);
		Forecast stretchedWideOpen = Engine.run(stretched, List.of(), 20, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED,
				SEED);

		assertThat(grownAlone.meanHours()).isCloseTo(stretchedAlone.meanHours(), withinPercentage(0.5));
		// Four slots and five-long chains: the plan is short of capacity, and a fifth
		// more
		// work in smaller pieces fills it better than a fifth longer pieces do.
		assertThat(grownInChains.meanHours()).isLessThan(stretchedInChains.meanHours() * 0.98);
		// Room for everything at once: nothing is short of a slot, so what decides the
		// answer is the longest path — and new work is what adds a step to one.
		assertThat(grownWideOpen.meanHours()).isGreaterThan(stretchedWideOpen.meanHours() * 1.005);
	}

	/**
	 * Every percentile moves out and none of them moves in — the direction that has no
	 * closed form but is not in any doubt.
	 */
	@Test
	void growingScopePushesEveryPercentileOutAndNeverIn() {
		List<ItemModel> plan = plainPlan(20, 8.0, 40.0);
		List<Precedence> links = chainsOfFive(20);

		Forecast listed = Engine.run(plan, links, 4, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED, SEED);
		Forecast growing = Engine.run(plan, links, 4, TeamFactor.NONE, ScopeGrowth.from(20.0, 60.0), MEASURED, SEED);

		assertThat(growing.p10Hours()).isGreaterThan(listed.p10Hours());
		assertThat(growing.p50Hours()).isGreaterThan(listed.p50Hours());
		assertThat(growing.p90Hours()).isGreaterThan(listed.p90Hours());
		assertThat(growing.p95Hours()).isGreaterThan(listed.p95Hours());
	}

	/**
	 * <strong>M3a's {@code nothing_to_forecast} doing load-bearing work two milestones
	 * later.</strong> New work costs what this plan's work costs, so a plan holding no
	 * estimate has nothing to answer with — and the refusal that already stops such a
	 * plan reaching the engine is the reason this can never happen through the API. It is
	 * stated here because it is exactly the kind of guarantee somebody relaxes without
	 * knowing what else was resting on it.
	 */
	@Test
	void refusesToGrowAPlanWithNothingEstimatedInIt() {
		List<ItemModel> nothingCosted = List.of(item(List.of()), item(List.of()));

		assertThatIllegalArgumentException().isThrownBy(() -> Engine.run(nothingCosted, List.of(), 1, TeamFactor.NONE,
				ScopeGrowth.from(20.0, 60.0), Engine.DEFAULT_SAMPLE_COUNT, SEED));
		// And forecasts it perfectly happily when nothing is expected to appear.
		assertThat(Engine
			.run(nothingCosted, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED)
			.meanHours()).isZero();
	}

	@Test
	void theSameSeedFindsTheSameWorkTwice() {
		List<ItemModel> plan = plainPlan(10, 8.0, 40.0);
		ScopeGrowth growth = ScopeGrowth.from(20.0, 60.0);

		Forecast once = Engine.run(plan, chainsOfFive(10), 3, TeamFactor.from(30.0), growth, 2_000, SEED);
		Forecast again = Engine.run(plan, chainsOfFive(10), 3, TeamFactor.from(30.0), growth, 2_000, SEED);

		assertThat(again).isEqualTo(once);
	}

	@Test
	void capacityMovesTheAnswerFurtherThanTheEstimatesDo() {
		List<LogNormalFit> fits = new ArrayList<>();
		for (int at = 0; at < 10; at++) {
			fits.add(LogNormalFit.from(3.0, 12.0));
		}
		List<ItemModel> plan = chainOf(fits);

		Forecast alone = Engine.run(plan, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT,
				SEED);
		Forecast together = Engine.run(plan, List.of(), 10, TeamFactor.NONE, ScopeGrowth.NONE,
				Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(together.p90Hours()).isLessThan(alone.p90Hours() / 2.0);
	}

	@Test
	void everyRunLandsInExactlyOneBucket() {
		Forecast forecast = Engine.run(chainOf(threeOrdinaryFits()), chainLinks(3), 1, TeamFactor.NONE,
				ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED);
		Histogram shape = forecast.histogram();

		assertThat(shape.counts()).hasSize(100);
		assertThat(shape.counts().stream().mapToInt(Integer::intValue).sum()).isEqualTo(Engine.DEFAULT_SAMPLE_COUNT);
		assertThat(shape.fromHours()).isLessThanOrEqualTo(forecast.p10Hours());
		assertThat(shape.toHours()).isGreaterThanOrEqualTo(forecast.p95Hours());
	}

	/**
	 * A plan whose work is all finished, or all unestimated, finishes at once — and the
	 * histogram has no width to divide by, so everything falls in the first bucket rather
	 * than nowhere.
	 */
	@Test
	void aPlanWithNoWorkLeftInItFinishesImmediately() {
		List<ItemModel> plan = List.of(new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.NOT_STARTED, 0.0),
				new ItemModel(UUID.randomUUID(), threeOrdinaryFits(), WorkItemStatus.DONE, 0.0));

		Forecast forecast = Engine.run(plan, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, 500, SEED);

		assertThat(forecast.meanHours()).isZero();
		assertThat(forecast.standardDeviationHours()).isZero();
		assertThat(forecast.p90Hours()).isZero();
		assertThat(forecast.histogram().counts().get(0)).isEqualTo(500);
	}

	@Test
	void unestimatedWorkStillHoldsTheOrderOfWhatSurroundsIt() {
		List<LogNormalFit> ordinary = threeOrdinaryFits();
		List<ItemModel> plan = List.of(item(List.of(ordinary.get(0))), item(List.of()), item(List.of(ordinary.get(1))));

		Forecast apart = Engine.run(plan, List.of(), 3, TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT,
				SEED);
		Forecast inOrder = Engine.run(plan, List.of(new Precedence(0, 1, 0.0), new Precedence(1, 2, 0.0)), 3,
				TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(inOrder.p90Hours()).isGreaterThan(apart.p90Hours());
	}

	/**
	 * <strong>Decision 5, reaching the forecast rather than merely being
	 * implemented.</strong> The same two-item plan twice: in the first the second task
	 * has not begun and has to wait for the first, and in the second it is visibly under
	 * way, so it runs alongside — and the plan finishes at the longer of the two rather
	 * than at their sum.
	 *
	 * <p>
	 * The sampler and the scheduler each test their own half of this, and neither notices
	 * if the wire between them comes loose. This is that wire.
	 */
	@Test
	void workAlreadyUnderWayShortensThePlanItIsIn() {
		LogNormalFit fit = LogNormalFit.from(8.0, 40.0);
		List<Precedence> inOrder = List.of(new Precedence(0, 1, 0.0));

		Forecast waiting = Engine.run(List.of(item(List.of(fit)), item(List.of(fit))), inOrder, 2, TeamFactor.NONE,
				ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED);
		Forecast underWay = Engine.run(
				List.of(item(List.of(fit)),
						new ItemModel(UUID.randomUUID(), List.of(fit), WorkItemStatus.IN_PROGRESS, 6.0)),
				inOrder, 2, TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED);

		assertThat(underWay.meanHours()).isLessThan(waiting.meanHours());
		// Not merely shorter: the two now overlap, so the plan costs about the longer of
		// them rather than both of them one after the other.
		assertThat(underWay.meanHours()).isLessThan(waiting.meanHours() * 0.75);
	}

	@Test
	void aSingleRunIsAForecastOfSorts() {
		Forecast forecast = Engine.run(chainOf(threeOrdinaryFits()), chainLinks(3), 1, TeamFactor.NONE,
				ScopeGrowth.NONE, 1, SEED);

		assertThat(forecast.meanHours()).isPositive();
		assertThat(forecast.standardDeviationHours()).isZero();
		assertThat(forecast.p10Hours()).isEqualTo(forecast.p95Hours());
	}

	@Test
	void refusesANumberOfRunsItWillNotDo() {
		List<ItemModel> plan = chainOf(threeOrdinaryFits());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> Engine.run(plan, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, 0, SEED));
		assertThatIllegalArgumentException().isThrownBy(() -> Engine.run(plan, List.of(), 1, TeamFactor.NONE,
				ScopeGrowth.NONE, Engine.MAX_SAMPLE_COUNT + 1, SEED));
	}

	@Test
	void refusesAPlanThatCannotBeScheduled() {
		List<ItemModel> plan = chainOf(threeOrdinaryFits());

		assertThatIllegalArgumentException()
			.isThrownBy(() -> Engine.run(plan, List.of(new Precedence(0, 1, 0.0), new Precedence(1, 0, 0.0)), 1,
					TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED));
		assertThatIllegalArgumentException().isThrownBy(() -> Engine.run(plan, List.of(), 0, TeamFactor.NONE,
				ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT, SEED));
	}

	/**
	 * <strong>Decision 8's synchronous answer is only true while this holds.</strong>
	 * Five hundred items is the ceiling M2 fixed so that a forecast need not be queued,
	 * and ten thousand runs is what decision 8 chose; if the two together stop fitting in
	 * a request, the lever is parallelising runs across cores — {@link Schedule} is
	 * immutable for exactly that reason — and queuing only if that is not enough.
	 *
	 * <p>
	 * <strong>Measured at about 300ms</strong>, so the ceiling below is roughly six times
	 * what it costs today. It is left that loose on purpose: a tight assertion on a
	 * wall-clock number is a test that fails on a busy machine and teaches nobody
	 * anything. What this is guarding is the order of magnitude — the day it takes
	 * seconds rather than milliseconds, decision 8's synchronous answer has stopped being
	 * true and somebody needs to know before a user finds out.
	 *
	 * <p>
	 * Tagged so it can be dropped from a machine too loaded to mean anything, and present
	 * because nothing else in the suite would notice this stopping being true.
	 */
	@Test
	@Tag("budget")
	void aPlanAtTheCeilingForecastsInsideARequest() {
		List<ItemModel> plan = new ArrayList<>();
		List<Precedence> links = new ArrayList<>();
		for (int at = 0; at < 500; at++) {
			plan.add(item(List.of(LogNormalFit.from(4.0 + at % 7, 20.0 + at % 11))));
			// A hundred chains of five, which is the shape a real plan of this size has.
			if (at % 5 != 0) {
				links.add(new Precedence(at - 1, at, 0.0));
			}
		}
		// Warmed first, because the first call measures the JIT rather than the engine.
		Engine.run(plan, links, 10, TeamFactor.NONE, ScopeGrowth.NONE, 200, SEED);

		long before = System.nanoTime();
		Forecast forecast = Engine.run(plan, links, 10, TeamFactor.NONE, ScopeGrowth.NONE, Engine.DEFAULT_SAMPLE_COUNT,
				SEED);
		long took = System.nanoTime() - before;

		assertThat(forecast.p90Hours()).isPositive();
		assertThat(took).as("a five hundred item plan, ten thousand runs, in nanoseconds").isLessThan(2_000_000_000L);
	}

	/**
	 * A plan chosen to be awkward rather than representative: work under way with hours
	 * against it, work nobody estimated, a fork and a lag. It is what the golden numbers
	 * above were recorded from, so every branch of the sampler is on the path between the
	 * seed and them.
	 */
	private static List<ItemModel> awkwardPlan() {
		return List.of(
				new ItemModel(UUID.randomUUID(), List.of(LogNormalFit.from(8.0, 40.0)), WorkItemStatus.NOT_STARTED,
						0.0),
				new ItemModel(UUID.randomUUID(), List.of(LogNormalFit.from(2.0, 30.0)), WorkItemStatus.NOT_STARTED,
						0.0),
				new ItemModel(UUID.randomUUID(), List.of(LogNormalFit.from(18.0, 22.0)), WorkItemStatus.IN_PROGRESS,
						6.0),
				new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.NOT_STARTED, 0.0));
	}

	private static List<Precedence> awkwardLinks() {
		return List.of(new Precedence(0, 1, 0.0), new Precedence(1, 2, 4.0), new Precedence(0, 3, 0.0));
	}

	/** One estimate each, nothing started, nothing joined up. */
	private static List<ItemModel> plainPlan(int items, double p10, double p90) {
		List<LogNormalFit> fits = new ArrayList<>(items);
		for (int at = 0; at < items; at++) {
			fits.add(LogNormalFit.from(p10, p90));
		}
		return chainOf(fits);
	}

	/** The shape a real plan of any size has: several short chains, side by side. */
	private static List<Precedence> chainsOfFive(int items) {
		List<Precedence> links = new ArrayList<>();
		for (int at = 0; at < items; at++) {
			if (at % 5 != 0) {
				links.add(new Precedence(at - 1, at, 0.0));
			}
		}
		return links;
	}

	private static List<LogNormalFit> threeOrdinaryFits() {
		return List.of(LogNormalFit.from(8.0, 40.0), LogNormalFit.from(2.0, 30.0), LogNormalFit.from(18.0, 22.0));
	}

	/**
	 * One item per estimate, none of them started, in the order they were written down.
	 */
	private static List<ItemModel> chainOf(List<LogNormalFit> fits) {
		List<ItemModel> plan = new ArrayList<>(fits.size());
		for (LogNormalFit fit : fits) {
			plan.add(item(List.of(fit)));
		}
		return plan;
	}

	private static List<Precedence> chainLinks(int items) {
		List<Precedence> links = new ArrayList<>();
		for (int at = 1; at < items; at++) {
			links.add(new Precedence(at - 1, at, 0.0));
		}
		return links;
	}

	private static ItemModel item(List<LogNormalFit> estimates) {
		return new ItemModel(UUID.randomUUID(), estimates, WorkItemStatus.NOT_STARTED, 0.0);
	}

}
