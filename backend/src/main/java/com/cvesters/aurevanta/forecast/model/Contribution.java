package com.cvesters.aurevanta.forecast.model;

/**
 * How much a plan's finish moves with one of the things that could move it.
 *
 * <p>
 * <strong>This is not a share of anything, and the number that reads most like one is the
 * one to be careful with.</strong> {@link #shareOfSpread()} is the squared correlation,
 * which under a linear reading is the fraction of the forecast's variance that moves with
 * this source — and in the degenerate case it really is a partition: one worker, a chain,
 * no common cause, and the shares sum to exactly 1, which is the "share of total
 * variance" a summing model computes directly. That case is the oracle this class is
 * tested against and it is not a case this product models.
 *
 * <p>
 * <strong>In a real forecast they sum to more than 1.</strong> M3b's team factor
 * multiplies every item in a run by the same draw, so everything moves with everything,
 * and the shares overlap. A screen that presented them as percentages would show a plan
 * whose parts account for three hundred percent of its own uncertainty — which is
 * precisely the kind of precise-looking, wrong number `product-concept.md` exists to
 * complain about. They are a <em>ranking</em>, and the wording on screen has to say so.
 *
 * <p>
 * Exactly zero for a source that never varies, which is the ordinary case rather than an
 * edge one — see {@link Contributions#of}.
 *
 * @param correlation between this source's draw and the moment the plan finished, across
 * every run. Positive almost always: a longer task does not finish a plan sooner. A small
 * negative one is a scheduling anomaly rather than a mistake — a shorter task can free a
 * slot early and change what gets picked up next — which is one more reason the square is
 * what gets ranked.
 */
public record Contribution(double correlation) {

	/** A source that never moved, and so is nothing the finish moved with. */
	public static final Contribution NONE = new Contribution(0.0);

	/**
	 * The fraction of the forecast's spread that moves with this source.
	 *
	 * <p>
	 * Derived rather than stored, so the two numbers cannot come to disagree — the whole
	 * reason this record has one component and an accessor rather than two components.
	 */
	public double shareOfSpread() {
		return this.correlation * this.correlation;
	}

}
