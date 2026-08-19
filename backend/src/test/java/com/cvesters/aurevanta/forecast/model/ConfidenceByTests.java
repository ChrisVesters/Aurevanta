package com.cvesters.aurevanta.forecast.model;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.item.WorkItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * <strong>{@code aPercentileIsTheBudgetThatShareOfRunsBeat} is the one that ties this to
 * the rest of the engine.</strong> A forecast already reports five percentiles, and
 * counting the runs under one of them has to agree with it — the P80 is by definition the
 * hours 80% of runs came in under. Two ways of asking one question, meeting on one
 * number, which is the footing every other piece of this engine stands on.
 */
class ConfidenceByTests {

	private static final long SEED = 20260818L;

	@Test
	void countsTheRunsThatCameInUnderTheBudget() {
		ConfidenceBy counted = new ConfidenceBy(10.0);

		for (double completion : new double[] { 4.0, 9.99, 10.0, 10.01, 40.0 }) {
			counted.observed(new double[0], 0, 0.0, 1.0, completion);
		}

		// Three of five, and the boundary counts as having made it: a plan that finished
		// at
		// exactly the moment it was due did not miss.
		assertThat(counted.share()).isCloseTo(0.6, within(1e-12));
		assertThat(counted.runs()).isEqualTo(5);
	}

	/**
	 * The two ends are the ones worth pinning, because a share is the one number whose
	 * meaning collapses if either is off by a run.
	 */
	@Test
	void everyRunOrNoRunAtAllAreOneAndZero() {
		ConfidenceBy all = new ConfidenceBy(100.0);
		ConfidenceBy none = new ConfidenceBy(1.0);

		for (double completion : new double[] { 4.0, 40.0, 99.0 }) {
			all.observed(new double[0], 0, 0.0, 1.0, completion);
			none.observed(new double[0], 0, 0.0, 1.0, completion);
		}

		assertThat(all.share()).isEqualTo(1.0);
		assertThat(none.share()).isEqualTo(0.0);
	}

	/**
	 * <strong>The engine's own percentiles are the oracle.</strong> The P80 is the hours
	 * eight runs in ten came in under, so counting them has to land on eight in ten — to
	 * within the one run of slack a nearest-rank percentile leaves, since no
	 * interpolation happens at either end.
	 */
	@Test
	void aPercentileIsTheBudgetThatShareOfRunsBeat() {
		List<ItemModel> plan = List.of(item(8.0, 40.0), item(10.0, 30.0), item(18.0, 22.0));
		List<Precedence> chain = List.of(new Precedence(0, 1, 0.0), new Precedence(1, 2, 0.0));
		Forecast forecast = Engine.run(plan, chain, 1, TeamFactor.NONE, ScopeGrowth.NONE, 10_000, SEED);

		for (double[] point : new double[][] { { forecast.p10Hours(), 0.10 }, { forecast.p50Hours(), 0.50 },
				{ forecast.p80Hours(), 0.80 }, { forecast.p95Hours(), 0.95 } }) {
			ConfidenceBy counted = new ConfidenceBy(point[0]);
			Engine.run(plan, chain, 1, TeamFactor.NONE, ScopeGrowth.NONE, 10_000, SEED, counted);

			assertThat(counted.share()).as("at %.2f hours", point[0]).isCloseTo(point[1], within(0.001));
		}
	}

	/**
	 * A budget nothing can beat and a budget everything beats, through the engine rather
	 * than by hand — the ends again, where an off-by-one is invisible in the middle.
	 */
	@Test
	void aPlanAlwaysBeatsForeverAndNeverBeatsNothing() {
		List<ItemModel> plan = List.of(item(8.0, 40.0));
		ConfidenceBy everything = new ConfidenceBy(1e9);
		ConfidenceBy nothing = new ConfidenceBy(0.0);

		Engine.run(plan, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, 2_000, SEED, everything);
		Engine.run(plan, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, 2_000, SEED, nothing);

		assertThat(everything.share()).isEqualTo(1.0);
		assertThat(nothing.share()).isEqualTo(0.0);
		assertThat(everything.runs()).isEqualTo(2_000);
	}

	/**
	 * Watching a forecast to count it changes no number in it, which is the contribution
	 * ranking's property arriving for a second observer — and the reason `Engine.VERSION`
	 * does not move for this work either.
	 */
	@Test
	void countingAForecastChangesNoNumberInIt() {
		List<ItemModel> plan = List.of(item(8.0, 40.0), item(10.0, 30.0));
		Forecast alone = Engine.run(plan, List.of(), 2, TeamFactor.from(30.0), ScopeGrowth.from(20.0, 60.0), 2_000,
				SEED);

		Forecast counted = Engine.run(plan, List.of(), 2, TeamFactor.from(30.0), ScopeGrowth.from(20.0, 60.0), 2_000,
				SEED, new ConfidenceBy(100.0));

		assertThat(counted).isEqualTo(alone);
	}

	@Test
	void anAccumulatorThatHasSeenNothingSaysSo() {
		ConfidenceBy counted = new ConfidenceBy(10.0);

		assertThat(counted.runs()).isZero();
		assertThat(counted.share()).isEqualTo(0.0);
	}

	/**
	 * A budget of nothing is a real question — can this plan be done before it starts? —
	 * and a budget of less than nothing is a bug upstream that would answer zero rather
	 * than fail.
	 */
	@Test
	void refusesLessTimeThanNone() {
		assertThatIllegalArgumentException().isThrownBy(() -> new ConfidenceBy(-0.01));
		assertThatIllegalArgumentException().isThrownBy(() -> new ConfidenceBy(Double.NaN));
	}

	private static ItemModel item(double low, double high) {
		return new ItemModel(UUID.randomUUID(), List.of(LogNormalFit.from(low, high)), WorkItemStatus.NOT_STARTED, 0.0);
	}

}
