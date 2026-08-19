package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;
import java.util.List;

import com.cvesters.aurevanta.forecast.model.Engine;
import com.cvesters.aurevanta.forecast.model.Forecast;
import com.cvesters.aurevanta.forecast.model.ForecastTerms;
import com.cvesters.aurevanta.forecast.model.ItemModel;
import com.cvesters.aurevanta.forecast.model.RunObserver;
import com.cvesters.aurevanta.forecast.model.ScopeGrowth;
import com.cvesters.aurevanta.forecast.model.TeamFactor;
import com.cvesters.aurevanta.problem.ForecastReplayMismatchException;

/**
 * Running a stored forecast again, and refusing to explain one that no longer reproduces.
 *
 * <p>
 * <strong>The one place a stored run is re-run</strong>, which is what makes every answer
 * derived from one comparable with the answer somebody was originally given. M6 ranks
 * what widened a band, M7 weighs what to cut, M10 accounts for why a date moved and M11
 * weighs a hire — four features, none of which stores anything, all of which exist
 * because a run kept its seed. Two ways of re-running one forecast would eventually be
 * one right way and one that had stopped being told about a parameter, and the wrong one
 * would explain a plan nobody forecast while looking entirely reasonable.
 *
 * <p>
 * Static because there is nothing to hold: a run carries its own terms, its own seed and
 * its own snapshot, so re-running it needs no collaborator and no state.
 * {@link ForecastSnapshots} beside it is the same shape for the same reason.
 */
final class ForecastReplays {

	private ForecastReplays() {
	}

	/**
	 * The run again, exactly as it was made, with something watching it go past — and
	 * with whatever the caller has imagined away.
	 */
	static Forecast replay(ForecastRun run, ForecastInputs inputs, List<ItemModel> plan, RunObserver watching) {
		return replayWith(plan, inputs, run.getTerms(), run.getSampleCount(), run.getSeed(), watching);
	}

	/**
	 * The engine run for a plan and a set of terms that need not be any one run's.
	 *
	 * <p>
	 * <strong>The one place a stored forecast is re-run.</strong> A decomposition varies
	 * the terms deliberately — the older plan under the newer capacity, and so on — so it
	 * cannot take them off a row the way the replay above does. Two ways of re-running
	 * one stored forecast would eventually be one right way and one that had drifted, and
	 * the drifted one would explain a plan nobody forecast while looking entirely
	 * reasonable.
	 */
	static Forecast replayWith(List<ItemModel> plan, ForecastInputs inputs, ForecastTerms terms, int sampleCount,
			long seed, RunObserver watching) {
		return Engine.run(plan, inputs.toPrecedences(), inputs.toResourcing(terms.capacity()),
				TeamFactor.from(terms.teamFactorWorseByPercent().doubleValue()), ScopeGrowth
					.from(terms.scopeGrowthP10Percent().doubleValue(), terms.scopeGrowthP90Percent().doubleValue()),
				sampleCount, seed, watching);
	}

	/**
	 * The two M3b assumptions a run stored, as the engine takes them.
	 *
	 * <p>
	 * Read off the row in one place because two readings would eventually be one reading
	 * and one stale one — and the stale one would replay a plan nobody forecast while
	 * looking entirely reasonable. A contribution ranking needs them a second time for
	 * something else: whether either was modelled at all decides whether it gets a row,
	 * and a source nobody modelled must get none rather than one reading zero.
	 */
	static TeamFactor teamFactorOf(ForecastRun run) {
		return TeamFactor.from(run.getTeamFactorWorseByPercent().doubleValue());
	}

	static ScopeGrowth scopeGrowthOf(ForecastRun run) {
		return ScopeGrowth.from(run.getScopeGrowthP10Percent().doubleValue(),
				run.getScopeGrowthP90Percent().doubleValue());
	}

	/**
	 * Refuses to explain a run the engine no longer reproduces.
	 *
	 * <p>
	 * Compared on the rounded figures rather than the raw ones, because that is what the
	 * columns hold — and a replay that took the same draws produces the same doubles bit
	 * for bit, so the roundings match exactly or something has genuinely moved. All six,
	 * because a change that moved only the mean or only one tail is exactly the change
	 * nobody would think to look for.
	 */
	static void requireReproduces(ForecastRun run, Forecast replayed) {
		BigDecimal[] stored = { run.getMeanHours(), run.getP10Hours(), run.getP50Hours(), run.getP80Hours(),
				run.getP90Hours(), run.getP95Hours() };
		double[] again = { replayed.meanHours(), replayed.p10Hours(), replayed.p50Hours(), replayed.p80Hours(),
				replayed.p90Hours(), replayed.p95Hours() };
		for (int at = 0; at < stored.length; at++) {
			if (stored[at].compareTo(ForecastRun.hours(again[at])) != 0) {
				throw new ForecastReplayMismatchException();
			}
		}
	}

}
