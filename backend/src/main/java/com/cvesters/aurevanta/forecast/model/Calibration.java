package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What a set of ranges turned out to be worth, accumulated one completed task at a time.
 *
 * <p>
 * <strong>Two numbers come out of here and neither may be shown without the
 * other.</strong> A hit rate on its own is gamed in one move — estimate everything one to
 * a thousand hours and score 100% forever — and that is not a hypothetical failure of a
 * metric but the obvious response to being shown a number about yourself. What closes it
 * is that {@link #bandWidthMultiplier()} moves the other way: bands three times wider
 * than the errors report {@code 0.33}, which reads as <em>narrow these</em>. Bias and
 * spread are two failures, neither statistic sees the other, and collapsing them into one
 * "estimation score" would delete the only thing that makes either safe to publish.
 *
 * <p>
 * <strong>The two corrections are scale-free, and there is deliberately no single
 * "multiply your estimates by" number.</strong> A correction to a fitted distribution is
 * {@code mu + b·sigma}, so the multiplier it implies depends on the width of the range
 * being corrected — one scalar would be right for an estimator's typical band and wrong
 * for every other. {@link #medianPercentile()} and {@link #bandWidthMultiplier()} are
 * both exact and both mean the same thing whatever the range: where the truth usually
 * lands on somebody's own scale, and how much wider that scale should have been.
 *
 * <p>
 * <strong>The individual values are kept, which the contribution ranking's accumulator
 * could not afford to.</strong> A median needs all of them, and the population here is
 * completed work — hundreds of rows over years — rather than ten thousand runs times five
 * hundred items. <strong>The two-pass mean and variance are used deliberately, and that
 * is not an inconsistency with {@code Contributions}.</strong> Its Welford update exists
 * because sums of squares over a plan of a million hours lose the answer in the third
 * decimal and go negative on a plan of a billion; this series is standardised, centred
 * near zero and a few units wide by construction, which is the one case that argument
 * does not reach.
 */
public final class Calibration {

	/**
	 * How many outcomes it takes before the corrections say anything.
	 *
	 * <p>
	 * A spread needs two, since the sample form divides by {@code n − 1}. The bias needs
	 * only one and is withheld at the same point anyway: the two are published together
	 * because neither is safe alone, so withholding one and not the other would hand a
	 * reader exactly the half that can be misread.
	 */
	private static final int ENOUGH_TO_CORRECT = 2;

	/**
	 * How far out each outcome landed on its own estimator's scale, in order of arrival.
	 * Only the ranges that had a width are here — see {@link BandScore#modelled()}.
	 */
	private final List<Double> howFarOut = new ArrayList<>();

	private int scored;

	private int hits;

	private int belowP10;

	private int aboveP90;

	/** Takes one completed piece of work into the record. */
	public void scored(BandScore score) {
		this.scored++;
		if (score.inside()) {
			this.hits++;
		}
		if (score.belowP10()) {
			this.belowP10++;
		}
		if (score.aboveP90()) {
			this.aboveP90++;
		}
		if (score.modelled()) {
			this.howFarOut.add(score.z());
		}
	}

	/** How many estimates have been measured against an outcome. */
	public int estimates() {
		return this.scored;
	}

	/** How many of them contained it. A well-calibrated set contains 80%. */
	public int hits() {
		return this.hits;
	}

	/** Misses that came in under the low end — work that was easier than anybody said. */
	public int belowP10() {
		return this.belowP10;
	}

	/** Misses that ran past the high end, which is the direction teams miss in. */
	public int aboveP90() {
		return this.aboveP90;
	}

	/**
	 * How many of them were a claim of certainty — three identical numbers, or two ends
	 * the same.
	 *
	 * <p>
	 * They count in the hit rate, because whether the outcome fell between the two ends
	 * is a perfectly good question about them and the answer is nearly always no. They
	 * cannot count in the corrections, because a distribution with no width puts nothing
	 * anywhere on a scale. Published rather than quietly dropped, so the two denominators
	 * can be seen to differ for a reason.
	 */
	public int pointEstimates() {
		return this.scored - this.howFarOut.size();
	}

	/**
	 * How often the range contained the outcome, with the interval that says how firmly.
	 */
	public Proportion hitRate() {
		return new Proportion(this.hits, this.scored);
	}

	/**
	 * Whether there is enough here to say anything about bias or spread.
	 *
	 * <p>
	 * Both accessors below refuse rather than answering when this is false, following
	 * {@link Proportion#measured()}. One outcome has a median and no spread, and a bias
	 * published without a spread beside it is the half of this record that can be read as
	 * a target.
	 */
	public boolean corrected() {
		return this.howFarOut.size() >= ENOUGH_TO_CORRECT;
	}

	/**
	 * Where the truth typically landed on the estimator's own scale — 0.5 if they are
	 * unbiased, and above it if their work usually runs longer than their own middle.
	 *
	 * <p>
	 * <strong>An even count splits its two middle observations in standardised units and
	 * not in percentiles</strong>, which is the same argument {@link LogNormalFit} makes
	 * for working in logarithms. The percentile scale is not linear — the distance
	 * between the 45th and the 55th is a fraction of the distance between the 85th and
	 * the 95th in anything anybody cares about — so an average taken on it means
	 * different things at different points, while the standardised scale is symmetric and
	 * evenly spaced by construction. Sorting happens there too, which costs nothing:
	 * {@link Normal#cdf} is monotone, so the order is the same either way. For an odd
	 * count the two agree.
	 * @throws IllegalStateException if fewer than two outcomes had a scale to land on
	 */
	public double medianPercentile() {
		requireCorrectable();
		List<Double> sorted = new ArrayList<>(this.howFarOut);
		Collections.sort(sorted);
		int middle = sorted.size() / 2;
		if (sorted.size() % 2 == 1) {
			return Normal.cdf(sorted.get(middle));
		}
		return Normal.cdf((sorted.get(middle - 1) + sorted.get(middle)) / 2.0);
	}

	/**
	 * How many times wider the ranges should have been — 1 if they were the right width,
	 * above it if they were too tight, and below it if they were padded.
	 *
	 * <p>
	 * The standard deviation of the standardised outcomes <strong>about their own
	 * mean</strong>, not about zero. About zero it would absorb the bias
	 * {@link #medianPercentile()} already reports, so an estimator who is reliably 40%
	 * low with perfectly judged bands would be told to widen them — which is the wrong
	 * instruction, and the one that produces a band nobody believes.
	 *
	 * <p>
	 * The sample form, dividing by {@code n − 1}: this is a spread estimated from a
	 * sample of somebody's work and not the spread of a population that happens to be all
	 * of it. The two differ by more than a rounding error at the counts calibration will
	 * actually see — over ten outcomes they read 0.989 and 0.938 on the same numbers.
	 * @throws IllegalStateException if fewer than two outcomes had a scale to land on
	 */
	public double bandWidthMultiplier() {
		requireCorrectable();
		double mean = 0.0;
		for (double out : this.howFarOut) {
			mean += out;
		}
		mean /= this.howFarOut.size();
		double squared = 0.0;
		for (double out : this.howFarOut) {
			squared += (out - mean) * (out - mean);
		}
		return Math.sqrt(squared / (this.howFarOut.size() - 1));
	}

	private void requireCorrectable() {
		if (!corrected()) {
			throw new IllegalStateException("A correction needs at least " + ENOUGH_TO_CORRECT
					+ " outcomes on a scale, and there are " + this.howFarOut.size());
		}
	}

}
