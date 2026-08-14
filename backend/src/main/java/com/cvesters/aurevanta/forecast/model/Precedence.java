package com.cvesters.aurevanta.forecast.model;

/**
 * One piece of work has to finish before another begins, and how long afterwards.
 *
 * <p>
 * Finish-to-start with a lag, which is the only kind of edge this product models. The
 * other three multiply a scheduler's complexity for cases most teams never draw.
 *
 * <p>
 * <strong>Both ends are positions in an array, not identifiers.</strong> The engine works
 * over {@code int} indices and {@code double} arrays because it walks the same graph ten
 * thousand times, and mapping a pair of {@code UUID}s on every hop of every run would
 * cost more than the scheduling does. Turning identifiers into positions happens once,
 * outside this package.
 *
 * @param lagHours a wait rather than work, so it occupies no capacity — something else
 * can be running while it counts down. Zero is the ordinary answer.
 */
public record Precedence(int predecessor, int successor, double lagHours) {

	/**
	 * A negative lag would be a lead: a successor starting before its predecessor
	 * finishes. That is a different kind of edge from the one this models, and letting
	 * one through would quietly produce a schedule nobody asked for rather than a refusal
	 * somebody could act on. The API refuses it too, so this is unreachable through a
	 * request and reachable from a test — which is the whole reason a pure function may
	 * hold a check like this at all.
	 */
	public Precedence {
		if (!(lagHours >= 0.0)) {
			throw new IllegalArgumentException("A lag cannot run backwards, but was " + lagHours);
		}
	}

}
