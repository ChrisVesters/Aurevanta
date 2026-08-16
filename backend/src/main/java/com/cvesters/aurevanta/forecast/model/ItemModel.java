package com.cvesters.aurevanta.forecast.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.random.RandomGenerator;

import com.cvesters.aurevanta.item.WorkItemStatus;

/**
 * One piece of work, as the engine sees it: everything known about how long it still has
 * to run, and nothing else.
 *
 * <p>
 * <strong>Two of this product's three honest decisions live in {@link #sample}</strong> —
 * what to do when several people disagree, and what to do about work already under way —
 * and both of them look like bugs from the outside. Neither is. They are the difference
 * between a forecast and a number.
 *
 * <p>
 * Carries a {@link WorkItemStatus} and otherwise nothing from the rest of the
 * application: no entity, no repository, no service. That enum is three constants with no
 * imports of its own, and its own documentation already says that what a forecast needs
 * is whether an item is still ahead of it — so it is reused rather than mirrored here, on
 * the same grounds every other rule in this codebase is stated once.
 *
 * @param id which item this is. Nothing here reads it; the scheduler does, and the run
 * that gets stored has to be able to say what it was forecasting.
 * @param estimates one fit per estimator, all of them current. Empty is ordinary and
 * means nobody has estimated this — see {@link #sample}.
 * @param spentHours effort already recorded against it, or zero where nobody measured
 * any, which will be most of the time.
 */
public record ItemModel(UUID id, List<LogNormalFit> estimates, WorkItemStatus status, double spentHours) {

	/**
	 * The most probability the conditional draw below will admit is still ahead.
	 *
	 * <p>
	 * Work barely begun leaves essentially all of its distribution in front of it, and
	 * {@link Normal#cdf} rounds that to exactly one — which {@link Normal#quantile} has
	 * no answer for. Clamped a single representable step below, so the arithmetic stays
	 * defined without changing any answer a person could notice.
	 */
	private static final double ALMOST_CERTAIN = Math.nextDown(1.0);

	public ItemModel {
		Objects.requireNonNull(id, "An item must say which item it is");
		Objects.requireNonNull(status, "An item must say how far along it is");
		// Copied rather than trusted: a run has to be reproducible, and a list somebody
		// else can still add to is not an input, it is a moving target.
		estimates = List.copyOf(estimates);
		if (!(spentHours >= 0.0)) {
			throw new IllegalArgumentException("Effort already spent cannot be negative, but was " + spentHours);
		}
	}

	/**
	 * One draw of how much work this item still has left in it.
	 *
	 * <p>
	 * <strong>Finished work draws nothing, however it was estimated.</strong> A forecast
	 * that re-predicted the past would be answering a question nobody asked, and the
	 * whole reason M2 records progress is to be able to leave it out.
	 *
	 * <p>
	 * <strong>Unestimated work also draws nothing, and it is still here.</strong> That is
	 * the difference between having no estimate and having no place in the plan: an item
	 * between two others carries their precedence whether or not anybody costed it, and
	 * dropping it would let the two run in parallel and shorten the forecast for a reason
	 * nothing in the plan supports. Its effort is missing, so its effort is zero, and
	 * coverage is what reports that honestly.
	 *
	 * <p>
	 * <strong>Where several people have estimated it, one of them is picked at
	 * random.</strong> Over ten thousand runs that is a mixture whose spread holds both
	 * the uncertainty each of them stated and the distance between them — which is the
	 * truth, because nobody knows which of them is right. Averaging their parameters
	 * instead would produce a tight band around the middle of two people who do not
	 * agree, converting disagreement into confidence, and that is the failure this
	 * product exists to prevent. It will look wrong the first time two colleagues differ
	 * and the band comes out wide. The band is wide because they differ.
	 */
	public double sample(RandomGenerator random) {
		if (weighsNothing()) {
			return 0.0;
		}
		LogNormalFit fit = this.estimates.get(random.nextInt(this.estimates.size()));
		return (this.spentHours > 0.0) ? remainderAfterWhatIsSpent(fit, random) : fit.at(random.nextGaussian());
	}

	/**
	 * One draw of what a piece of work like this one would cost, if nobody had started
	 * it.
	 *
	 * <p>
	 * <strong>The plan is its own reference class.</strong> Scope growth invents items
	 * that nobody has estimated, and the honest place to get a duration for one is the
	 * work that has been estimated: new work looks like existing work, stated as an
	 * assumption rather than assumed silently. One of the plan's estimated items is
	 * picked, and this is what it is asked.
	 *
	 * <p>
	 * <strong>Not {@link #sample}, and the difference is progress.</strong> That method
	 * answers what <em>this</em> item has left, so it returns nothing for finished work
	 * and conditions on hours already spent. Work nobody has thought of has not been
	 * started and cannot have been finished, so what is wanted here is the estimate
	 * itself. Which is also why an item may serve as a reference for work it has long
	 * since stopped being an estimate of.
	 *
	 * <p>
	 * The estimator is still picked at random where several disagree, for the reason
	 * {@link #sample} does it: disagreement about what this plan's work costs is
	 * disagreement about what its unlisted work will cost too.
	 * @throws IllegalArgumentException if nobody estimated this item, which is why the
	 * reference class is drawn from the items that carry an estimate
	 */
	public double sampleAsNewWork(RandomGenerator random) {
		if (this.estimates.isEmpty()) {
			throw new IllegalArgumentException("Work nobody estimated says nothing about what new work costs");
		}
		return this.estimates.get(random.nextInt(this.estimates.size())).at(random.nextGaussian());
	}

	/**
	 * Roughly what this item holds, for ranking it against the others and for nothing
	 * else.
	 *
	 * <p>
	 * <strong>Never used to forecast anything.</strong> It answers "how much work is
	 * waiting behind this" when the scheduler has more ready than it has room for, and
	 * that question has to be settled the same way in every run — otherwise two forecasts
	 * of one plan would be ordered differently and could not be compared. So it is the
	 * middle of what people said, taken from the plan rather than from a draw.
	 *
	 * <p>
	 * Where several people estimated it, their medians are averaged, which is exactly the
	 * flattening {@link #sample} refuses to do — and is right here, because a ranking
	 * needs one number and a forecast needs a distribution.
	 *
	 * <p>
	 * Effort already spent is ignored, so work halfway through still ranks by what it was
	 * thought to cost in total. That slightly overstates it, and it does not matter: this
	 * decides which of two ready tasks is picked up first, not how long either takes.
	 */
	public double typicalEffortHours() {
		if (weighsNothing()) {
			return 0.0;
		}
		double total = 0.0;
		for (LogNormalFit estimate : this.estimates) {
			total += estimate.median();
		}
		return total / this.estimates.size();
	}

	/**
	 * Finished, or never costed. Stated once because {@link #sample} and
	 * {@link #typicalEffortHours} have to agree about it: an item the forecast counts as
	 * free must also be an item nothing queues up behind.
	 */
	private boolean weighsNothing() {
		return this.status == WorkItemStatus.DONE || this.estimates.isEmpty();
	}

	/**
	 * What is left, given that the work has demonstrably already cost what it has cost.
	 *
	 * <p>
	 * Not the estimate, and not the estimate minus the hours: it is the estimate
	 * <em>conditioned</em> on the fact that the total has already exceeded them. Drawing
	 * afresh would ignore work that has visibly happened, and subtracting with a floor at
	 * zero would invent that floor and bias everything that crossed it.
	 *
	 * <p>
	 * <strong>It has a property that will be reported as a bug: the longer a task has
	 * already run, the more it has left.</strong> That is what a right-skewed
	 * distribution means, it is what happens on real projects, and a model without it
	 * flatters every late piece of work in the plan.
	 *
	 * <p>
	 * Sampled through the surviving tail rather than by inverting a probability near one,
	 * because that is the half of the distribution a double holds accurately — the same
	 * reason {@link Normal#cdf} computes the smaller tail and subtracts only when it
	 * must.
	 */
	private double remainderAfterWhatIsSpent(LogNormalFit fit, RandomGenerator random) {
		if (fit.sigma() == 0.0) {
			// Somebody who said they were certain. There is no distribution to condition,
			// so what is left is the arithmetic and nothing is drawn from it.
			return Math.max(fit.median() - this.spentHours, 0.0);
		}
		double stillAhead = Math.min(Normal.cdf((fit.mu() - Math.log(this.spentHours)) / fit.sigma()), ALMOST_CERTAIN);
		if (stillAhead <= 0.0) {
			// The estimate has been comprehensively outrun — an item put at 19 to 21
			// hours
			// with a hundred spent — and there is no mass left out there to draw from.
			// The
			// answer is nearly nothing, which is the model reporting that it has been
			// falsified rather than a forecast worth acting on. What fixes it is a new
			// estimate, which M2 makes a new row.
			return 0.0;
		}
		// Uniform over the surviving tail, taken from the far end so that the value can
		// never be zero: `1 - nextDouble()` lands in (0, 1] where `nextDouble()` would
		// reach 0, and a tail mass of zero names a point infinitely far out.
		double survives = stillAhead * (1.0 - random.nextDouble());
		return Math.max(fit.at(-Normal.quantile(survives)) - this.spentHours, 0.0);
	}

}
