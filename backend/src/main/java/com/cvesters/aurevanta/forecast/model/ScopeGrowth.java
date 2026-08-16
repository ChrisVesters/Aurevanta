package com.cvesters.aurevanta.forecast.model;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * How much of this plan nobody has written down yet.
 *
 * <p>
 * <strong>The larger of the two uncertainty sources, and the one every tool
 * omits.</strong> `product-concept.md` is blunt about it: projects do not mostly run late
 * because the tasks took longer than their estimates, they run late because the ticket
 * list itself grows. An engine that forecasts exactly the work somebody listed is
 * answering a question about a plan that has already stopped being true.
 *
 * <p>
 * <strong>Items, not a multiplier on the total, and capacity is why.</strong> Inflating
 * every duration by 30% and adding 30% more work both make a plan about 30% longer when
 * one person does everything in order — but they are different effects the moment there
 * is a capacity constraint, because new items <em>compete for slots</em>. They make the
 * plan longer and they make everything else wait, which no multiplier can express. That
 * is the whole reason this is modelled as work rather than as a number, and it is only
 * available because the aggregator is a scheduler. It is also what stops this and
 * {@link TeamFactor} from being one effect counted twice under two names.
 *
 * @param multiplier what the plan's item count gets multiplied by — 1.4 for a plan that
 * grew by 40%. Log-normal, and exactly 1 for a plan nobody expects to grow.
 */
public record ScopeGrowth(LogNormalFit multiplier) {

	/**
	 * A plan somebody is claiming holds every piece of work it will ever hold.
	 *
	 * <p>
	 * What M3a did, and so the other half of what makes a version 1 run replayable by
	 * this engine. It is a strong claim rather than an absence of one, which is why
	 * nothing hands it out by default.
	 */
	public static final ScopeGrowth NONE = new ScopeGrowth(new LogNormalFit(0.0, 0.0));

	public ScopeGrowth {
		Objects.requireNonNull(multiplier, "Scope growth must say what it draws from");
	}

	/**
	 * Fits the range somebody can answer from their own history: <em>how much does a plan
	 * like this usually grow?</em>
	 *
	 * <p>
	 * `product-concept.md` frames the question and the answer together — "if the last
	 * five projects grew 40–90% in ticket count, that is a distribution to sample from
	 * and multiply through" — so the two ends are percentages and the fit is
	 * {@link LogNormalFit} for the third time in this package.
	 *
	 * <p>
	 * <strong>What is fitted is the multiplier, not the percentage, and that is a
	 * departure.</strong> Fitting the percentage directly would mean a low end of zero
	 * has no fit at all — a log-normal cannot reach zero — and "usually it does not grow,
	 * but sometimes by 40%" is one of the more honest answers a person can give. Fitting
	 * {@code 1 + p/100} takes that answer, gives {@link #NONE} for a range of 0–0 without
	 * a special case anywhere, and is what "multiply through" describes. The cost is that
	 * a range starting at or near zero puts some of its low tail below a multiplier of 1,
	 * which is a plan that shrank; {@link #sample} reads that as no growth, because
	 * nothing in this model removes work.
	 * @param p10Percent how much a plan like this grows in the tenth-percentile case
	 * @param p90Percent and in the ninetieth
	 * @throws IllegalArgumentException if either end is negative, or the two are the
	 * wrong way round
	 */
	public static ScopeGrowth from(double p10Percent, double p90Percent) {
		if (!(p10Percent >= 0.0)) {
			throw new IllegalArgumentException(
					"A plan cannot grow by less than nothing, but the low end was " + p10Percent + "%");
		}
		if (!(p90Percent >= p10Percent)) {
			throw new IllegalArgumentException(
					"A growth range must ascend, but " + p90Percent + "% is below " + p10Percent + "%");
		}
		return new ScopeGrowth(LogNormalFit.from(1.0 + p10Percent / 100.0, 1.0 + p90Percent / 100.0));
	}

	/**
	 * How many pieces of work this run discovers that nobody had thought of.
	 *
	 * <p>
	 * <strong>Rounding this is a decision with a bias in it.</strong> A plan of ten items
	 * growing 4% wants 0.4 new items, and rounding to nearest makes that zero — not
	 * usually, but <em>every single run</em>, so a small and entirely real growth becomes
	 * exactly no growth and the forecast quietly returns to assuming somebody thought of
	 * everything. So the whole part is taken and one more is added with probability equal
	 * to the fraction: over ten thousand runs the mean count is the number that was
	 * sampled, which is the only thing that makes sampling a distribution mean anything.
	 * The cost is that a small plan sometimes grows and sometimes does not, which is the
	 * design and not a fault.
	 *
	 * <p>
	 * <strong>{@link #NONE} draws nothing at all</strong>, for the reason
	 * {@link TeamFactor#sample} does: a parameter that changes no number must also cost
	 * no randomness, or every draw after it shifts and a version 1 run stops replaying.
	 *
	 * <p>
	 * <strong>Growth already realised is not deducted, and cannot be.</strong> The count
	 * is a share of the plan as it stands, which is right for a plan forecast near its
	 * start and generous for one that has already grown — part of what it is being told
	 * to expect has arrived and been written down, and nothing here knows what the plan
	 * looked like when it began. Finished work counts towards the base for the same
	 * reason: the question is how much a plan like this one turns out to hold, not how
	 * much of it is left. Both stop being guesses once M8 can read a plan's own history,
	 * which is the answer decision 3 gives to weighting where new work lands as well.
	 * @param items how many pieces of work the plan holds today, finished ones included
	 */
	public int sample(int items, RandomGenerator random) {
		if (NONE.equals(this)) {
			return 0;
		}
		double discovered = items * (this.multiplier.at(random.nextGaussian()) - 1.0);
		if (!(discovered > 0.0)) {
			// A draw below a multiplier of 1 is a plan that shrank, which this model has
			// no
			// way to express: nothing here removes work somebody already wrote down.
			return 0;
		}
		int whole = (int) discovered;
		return (random.nextDouble() < discovered - whole) ? whole + 1 : whole;
	}

}
