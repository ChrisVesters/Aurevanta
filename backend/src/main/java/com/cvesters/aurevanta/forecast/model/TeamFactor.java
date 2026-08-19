package com.cvesters.aurevanta.forecast.model;

import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * The one thing that happens to every piece of work at once.
 *
 * <p>
 * <strong>Independence is a lie, and this is the correction.</strong> Sampling every item
 * on its own lets good and bad luck cancel, so a plan of ten tasks comes out far tighter
 * than any plan of ten tasks has ever been. Real quarters are short-staffed or they are
 * not; the codebase fights back or it does not; there are three incidents or there are
 * none. One multiplier is drawn per run and applied to everything still ahead, and the
 * band widens to something a person recognises — `roadmap.md` measured it moving a true
 * P90 from 209.4 to 222.2 on ten wide tasks, which is a difference the closed form this
 * product rejected cannot see at all.
 *
 * <p>
 * <strong>Once per run, never once per item.</strong> A factor drawn inside the item loop
 * is exactly the bug this exists to fix, written by accident and invisible from the
 * outside: the draws would average out again and the band would come back to where it
 * started. That is why {@link #sample} is called by the engine outside its inner loop and
 * this class has no idea how many items there are.
 *
 * <p>
 * <strong>The median is pinned to 1, and that is the decision inside the
 * decision.</strong> A multiplier with a <em>mean</em> of 1 has a median below it, so it
 * would drag the centre of every forecast down while claiming only to widen it — a change
 * nobody would see and nothing would fail on. The estimates already carry the central
 * case, since that is what a P50 is, so a factor whose job is common-cause spread has to
 * leave the middle alone and pull the tails apart. Half the runs are a good stretch and
 * half are a bad one, and neither is the default. The compact constructor refuses
 * anything else rather than trusting the caller to remember.
 *
 * @param multiplier the distribution a draw comes from: log-normal, with a {@code mu} of
 * zero so that {@code at(0)} is exactly 1.
 */
public record TeamFactor(LogNormalFit multiplier) {

	/**
	 * No common cause at all — every run multiplies by exactly 1, and nothing is drawn.
	 *
	 * <p>
	 * This is what the simulation engine did, which is what makes it the compatibility
	 * layer rather than a branch beside one: a version 1 run is a version 2 run with
	 * this. It is not a default anybody gets by omission, because zero is a claim — that
	 * nothing in this team's world has a common cause — and a claim has to be made by
	 * somebody.
	 */
	public static final TeamFactor NONE = new TeamFactor(new LogNormalFit(0.0, 0.0));

	public TeamFactor {
		Objects.requireNonNull(multiplier, "A team factor must say what it draws from");
		if (multiplier.mu() != 0.0) {
			throw new IllegalArgumentException(
					"A team factor's median is pinned to 1, so its mu must be zero, but was " + multiplier.mu());
		}
	}

	/**
	 * Fits the factor from the only form of the question anybody can answer: <em>in a bad
	 * stretch, how much longer does everything take?</em>
	 *
	 * <p>
	 * {@code s} is a log-standard-deviation and nobody has an opinion about one of those,
	 * so it is never asked for. An answer of 30 means a stretch bad enough that only one
	 * run in ten is worse takes 30% longer — the factor's P90 — and that, with the median
	 * pinned at 1, determines the distribution completely.
	 *
	 * <p>
	 * <strong>Fitted by {@link LogNormalFit}, not beside it.</strong> A stretch as good
	 * as this one is bad is its reciprocal, so {@code 1/worst} and {@code worst} are the
	 * two ends of a range whose middle is 1 — and the fit that turns an estimate into a
	 * distribution turns this into a multiplier, through the same twenty lines and the
	 * same {@link Normal#P90_Z}. Getting that constant wrong now breaks estimation as
	 * well, loudly, which is worth more than a second formula that agrees with it today.
	 *
	 * <p>
	 * The middle is then written as zero rather than read back from that fit: a
	 * reciprocal pair only <em>rounds</em> to a mu of zero, and a median of 1 is not a
	 * thing that may drift in the last digit.
	 * @param worseByPercent how much longer everything takes in a bad stretch, as a
	 * percentage. Zero is {@link #NONE}.
	 * @throws IllegalArgumentException if a bad stretch is claimed to be shorter than an
	 * ordinary one
	 */
	public static TeamFactor from(double worseByPercent) {
		if (!(worseByPercent >= 0.0)) {
			throw new IllegalArgumentException(
					"A bad stretch cannot be quicker than an ordinary one, but was " + worseByPercent + "%");
		}
		if (worseByPercent == 0.0) {
			return NONE;
		}
		double worst = 1.0 + worseByPercent / 100.0;
		return new TeamFactor(new LogNormalFit(0.0, LogNormalFit.from(1.0 / worst, worst).sigma()));
	}

	/**
	 * One stretch, good or bad, for one whole run.
	 *
	 * <p>
	 * <strong>{@link #NONE} draws nothing, and that is the subtlest thing in this
	 * work.</strong> Multiplying by an always-1 factor would be harmless; taking the draw
	 * that produces it would not, because it advances the generator and every subsequent
	 * number in the run shifts. Every stored forecast is replayable only while a seed
	 * means what it meant, so a factor of none has to be free — otherwise version 1 stops
	 * being version 2 with its parameters zeroed, and the promise that makes old runs
	 * readable quietly stops holding with nothing failing to say so.
	 */
	public double sample(RandomGenerator random) {
		if (this.multiplier.sigma() == 0.0) {
			return 1.0;
		}
		return this.multiplier.at(random.nextGaussian());
	}

}
