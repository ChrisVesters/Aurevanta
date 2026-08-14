package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * Nothing in the plan carries an estimate, so there is nothing to forecast.
 *
 * <p>
 * A plan of unestimated items schedules a set of zero-effort nodes and finishes
 * instantly. That is arithmetically correct and useless, and returning it would be this
 * application stating a completion time it has no evidence for whatsoever.
 *
 * <p>
 * <strong>A partly estimated plan is not refused, at any coverage above nothing.</strong>
 * A plan half filled in is every real plan on its first day, which is why coverage is
 * reported prominently rather than demanded — {@code m2-plan.md} decision 5. Inventing a
 * threshold here would be re-litigating that with a number nobody chose.
 */
public class NothingToForecastException extends ApiProblemException {

	public NothingToForecastException() {
		super(HttpStatus.UNPROCESSABLE_ENTITY, "Nothing to forecast", "nothing_to_forecast",
				"No work in this plan carries an estimate, so there is nothing to forecast");
	}

}
