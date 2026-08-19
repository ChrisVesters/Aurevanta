package com.cvesters.aurevanta.forecast.model;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.cvesters.aurevanta.item.WorkItemStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

/**
 * What a forecast's spread turns out to be made of, measured through the engine that made
 * it.
 *
 * <p>
 * <strong>{@code watchingAForecastChangesNoNumberInIt} is the one that decides whether
 * the contribution ranking needs a version bump</strong>, and the answer is no: an
 * observer is told after every draw and takes none of its own, so the same seed produces
 * byte-identical percentiles whether anybody is listening or not. It is the same
 * discipline the common-cause model's {@code TeamFactor.NONE} follows, asserted the same
 * way.
 *
 * <p>
 * <strong>{@code aWideItemOffTheDecidingPathContributesAlmostNothing} is the one worth
 * reading twice.</strong> It is `roadmap.md`'s claim made executable: the widest item in
 * the plan — by a factor of forty in variance — contributes least, because it sits beside
 * the chain that actually decides the finish. A summing model would have ranked it first,
 * which is exactly why the contribution ranking exists.
 */
class RunContributionTests {

	private static final long SEED = 20260817L;

	/** Enough runs to converge a correlation to a couple of hundredths. */
	private static final int MEASURED = 40_000;

	/**
	 * <strong>The property that keeps {@link Engine#VERSION} at 2.</strong> Watching is
	 * not a model change, and nothing but this says so: an observer that drew a single
	 * number, or that were called before one, would move every percentile and unreplay
	 * every forecast stored before it — silently, since a forecast is a forecast either
	 * way.
	 */
	@Test
	void watchingAForecastChangesNoNumberInIt() {
		List<ItemModel> plan = chain();
		Forecast alone = Engine.run(plan, links(), 1, TeamFactor.from(30.0), ScopeGrowth.from(20.0, 60.0), 5_000, SEED);

		Forecast watched = Engine.run(plan, links(), 1, TeamFactor.from(30.0), ScopeGrowth.from(20.0, 60.0), 5_000,
				SEED, Contributions.forRun(plan.size()));

		assertThat(watched).isEqualTo(alone);
	}

	/**
	 * <strong>Step 1's oracle, reaching the engine.</strong> A chain at capacity one with
	 * no common cause finishes when the sum of its draws says it does, and there one
	 * item's correlation with the finish is {@code sd_i / sqrt(sum of sd_j squared)} — a
	 * closed form that holds for any independent distributions, log-normal included,
	 * because the covariance of a draw with a sum containing it is just its own variance.
	 */
	@Test
	void aChainRanksItsItemsByTheClosedForm() {
		List<ItemModel> plan = chain();
		Contributions measured = Contributions.forRun(plan.size());

		Engine.run(plan, links(), 1, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED, SEED, measured);

		double total = 0.0;
		for (ItemModel item : plan) {
			total += item.estimates().getFirst().variance();
		}
		for (int at = 0; at < plan.size(); at++) {
			double expected = Math.sqrt(plan.get(at).estimates().getFirst().variance() / total);
			assertThat(measured.ofItem(at).correlation()).as("item %d", at).isCloseTo(expected, within(0.02));
		}
	}

	/**
	 * <strong>And the case the closed form gets wrong, which is the point.</strong> A
	 * lone item of 5 to 50 hours has <em>forty-five times</em> the variance of any link
	 * in the chain beside it — a summing model would rank it first and send somebody off
	 * to spike it. It runs in parallel with a chain five times as long, so it almost
	 * never decides anything: measured, it accounts for 1.2% of the spread while each of
	 * the five narrow links accounts for about 18%. The widest thing in the plan is the
	 * last thing worth touching, and no arithmetic over variances in isolation can say
	 * so.
	 */
	@Test
	void aWideItemOffTheDecidingPathContributesAlmostNothing() {
		LogNormalFit wide = LogNormalFit.from(5.0, 50.0);
		LogNormalFit link = LogNormalFit.from(45.0, 55.0);
		List<ItemModel> plan = List.of(item(wide), item(link), item(link), item(link), item(link), item(link));
		Contributions measured = Contributions.forRun(plan.size());

		// The five links in order, the wide one beside them, two things at a time.
		Engine.run(plan, chainFrom(1, plan.size()), 2, TeamFactor.NONE, ScopeGrowth.NONE, MEASURED, SEED, measured);

		// It really is the widest thing in the plan, by a long way.
		assertThat(wide.variance()).isGreaterThan(40.0 * link.variance());
		// And it accounts for almost none of the spread, while each of the five does.
		assertThat(measured.ofItem(0).shareOfSpread()).isLessThan(0.03);
		for (int at = 1; at < plan.size(); at++) {
			assertThat(measured.ofItem(at).shareOfSpread()).as("chain item %d", at).isGreaterThan(0.1);
		}
	}

	/**
	 * <strong>The row that says no estimate on the list is the problem.</strong> A shared
	 * multiplier drawn once per run and applied to everything moves the finish more than
	 * any single item does, and a report that ranked only tasks would hide it.
	 */
	@Test
	void theSharedTeamFactorIsASourceLikeAnyOther() {
		List<ItemModel> plan = chain();
		Contributions measured = Contributions.forRun(plan.size());

		Engine.run(plan, links(), 1, TeamFactor.from(60.0), ScopeGrowth.NONE, MEASURED, SEED, measured);

		assertThat(measured.ofTeamFactor().shareOfSpread()).isGreaterThan(measured.ofItem(0).shareOfSpread());
		assertThat(measured.ofDiscoveredWork()).isEqualTo(Contribution.NONE);
	}

	/**
	 * And the other one. `product-concept.md` is blunt that projects overrun because the
	 * ticket list grows more often than because a listed task ran long, so a plan
	 * expecting growth should find that its largest source is not on it.
	 */
	@Test
	void theWorkNobodyListedIsASourceLikeAnyOther() {
		List<ItemModel> plan = chain();
		Contributions measured = Contributions.forRun(plan.size());

		Engine.run(plan, links(), 1, TeamFactor.NONE, ScopeGrowth.from(40.0, 90.0), MEASURED, SEED, measured);

		assertThat(measured.ofDiscoveredWork().shareOfSpread()).isGreaterThan(0.2);
		assertThat(measured.ofTeamFactor()).isEqualTo(Contribution.NONE);
	}

	/**
	 * <strong>A source nobody modelled never varied, so it contributes exactly
	 * nothing.</strong> That is the mechanism; whether a report shows a row saying zero
	 * or no row at all is a different question, decided from what the run stored rather
	 * than from what it drew — the same shape as a forecast made before there was a
	 * calendar.
	 */
	@Test
	void aForecastThatAssumedNeitherReportsNeither() {
		List<ItemModel> plan = chain();
		Contributions measured = Contributions.forRun(plan.size());

		Engine.run(plan, links(), 1, TeamFactor.NONE, ScopeGrowth.NONE, 2_000, SEED, measured);

		assertThat(measured.ofTeamFactor()).isEqualTo(Contribution.NONE);
		assertThat(measured.ofDiscoveredWork()).isEqualTo(Contribution.NONE);
		assertThat(measured.runs()).isEqualTo(2_000);
	}

	/**
	 * An item nobody estimated holds its place in the graph and weighs nothing, so it
	 * moves no finish anywhere — which is the ordinary case decision 5 is about, arriving
	 * through the engine rather than through a hand-built accumulator.
	 */
	@Test
	void anItemNobodyEstimatedContributesNothing() {
		List<ItemModel> plan = List.of(item(LogNormalFit.from(8.0, 40.0)),
				new ItemModel(UUID.randomUUID(), List.of(), WorkItemStatus.NOT_STARTED, 0.0));
		Contributions measured = Contributions.forRun(plan.size());

		Engine.run(plan, List.of(), 1, TeamFactor.NONE, ScopeGrowth.NONE, 2_000, SEED, measured);

		assertThat(measured.ofItem(1)).isEqualTo(Contribution.NONE);
		assertThat(measured.ofItem(0).shareOfSpread()).isCloseTo(1.0, within(1e-9));
	}

	/**
	 * The accumulator is built for one plan and refuses another, since an off-by-one here
	 * would attribute a plan's spread to the wrong work and look entirely plausible doing
	 * it.
	 */
	@Test
	void refusesToWatchAPlanOfADifferentSize() {
		Contributions measured = Contributions.forRun(3);

		assertThatIllegalArgumentException()
			.isThrownBy(() -> measured.observed(new double[] { 1.0, 2.0 }, 2, 0.0, 1.0, 3.0));
	}

	/**
	 * Three items of widening range, in a line: the plan a sum can be checked against.
	 */
	private static List<ItemModel> chain() {
		return List.of(item(LogNormalFit.from(18.0, 22.0)), item(LogNormalFit.from(10.0, 40.0)),
				item(LogNormalFit.from(30.0, 90.0)));
	}

	private static List<Precedence> links() {
		return List.of(new Precedence(0, 1, 0.0), new Precedence(1, 2, 0.0));
	}

	/** Everything from {@code first} to the end of the plan, in a line. */
	private static List<Precedence> chainFrom(int first, int items) {
		List<Precedence> edges = new java.util.ArrayList<>();
		for (int at = first; at < items - 1; at++) {
			edges.add(new Precedence(at, at + 1, 0.0));
		}
		return edges;
	}

	private static ItemModel item(LogNormalFit estimate) {
		return new ItemModel(UUID.randomUUID(), List.of(estimate), WorkItemStatus.NOT_STARTED, 0.0);
	}

}
