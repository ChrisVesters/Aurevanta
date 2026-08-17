package com.cvesters.aurevanta.forecast;

import java.util.UUID;

import com.cvesters.aurevanta.forecast.model.Contribution;

/**
 * One thing that could have moved a plan's finish, and how much it did.
 *
 * <p>
 * <strong>The share is not a share of anything, and the wording that renders it has to
 * say so.</strong> These sum to exactly 1 only for a chain at capacity one with no common
 * cause — the summing model this product deliberately stopped using. In a real forecast
 * M3b's team factor multiplies every item in a run by the same draw, so everything moves
 * with everything and the shares overlap: presenting them as percentages would show a
 * plan whose parts account for three hundred percent of its own uncertainty.
 *
 * <p>
 * <strong>Three kinds of row, and the two that are not items are why the ranking is
 * honest.</strong> A list of tasks alone answers "which of these should I spike" while
 * hiding whether spiking any of them is worth doing — and when the shared factor or the
 * unlisted work tops the list, the true answer is that no estimate on it is the problem.
 * They carry no {@code itemId}, because there is no item: discovered work is different
 * work in every run, and a multiplier is not a piece of work at all.
 *
 * @param itemId which piece of work this is, or null for the two sources that are not
 * pieces of work.
 * @param title what that work is called <em>now</em>, read off the plan rather than out
 * of the run. The snapshot never held a title — deliberately, since M10 diffs those
 * snapshots and a rename is not a thing that moved — and somebody reading this is being
 * told what to go and do, so the current name is the useful one. Null for the two sources
 * that are not work.
 * @param archived whether that work has been put away since the run. Named rather than
 * hidden, the same choice an arrow pointing at archived work already makes: a top
 * contributor missing from the live plan is exactly the thing a reader would otherwise
 * spend a minute looking for.
 * @param correlation how the plan's finish moved with this, across every run of it.
 * @param shareOfSpread that squared, which is what the ranking is by.
 */
public record ContributionResponse(ContributionKind kind, UUID itemId, String title, boolean archived,
		double correlation, double shareOfSpread) {

	static ContributionResponse of(UUID itemId, String title, boolean archived, Contribution measured) {
		return new ContributionResponse(ContributionKind.ITEM, itemId, title, archived, measured.correlation(),
				measured.shareOfSpread());
	}

	static ContributionResponse of(ContributionKind kind, Contribution measured) {
		return new ContributionResponse(kind, null, null, false, measured.correlation(), measured.shareOfSpread());
	}

}
