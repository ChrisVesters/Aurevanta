package com.cvesters.aurevanta.forecast.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The terms a forecast was made on — everything about a run except the plan it forecast.
 *
 * <p>
 * <strong>Two runs of one plan are only comparable on the same terms</strong>, and this
 * is the list of what that means. `roadmap.md` warns in as many words that "a comparison
 * across runs made under different calendars — or across an {@code Engine.VERSION} bump —
 * is the way this feature reports a slide that never happened", and M4 is what made it
 * checkable: every run since stores the calendar it was read under rather than being read
 * through today's.
 *
 * <p>
 * <strong>Held apart from the plan on purpose.</strong> What changed about the *work* is
 * a decomposition's business and is read from the stored inputs; what changed about the
 * *question* is this, and it is the part that can make a date move without anybody
 * touching a task. Somebody halving the capacity moves a plan a fortnight, and that is
 * not a slide.
 *
 * @param calendarRule and {@code workingHoursPerDay} are null together on a run made
 * before M4, which had no calendar rather than a default one — so two such runs agree
 * with each other and neither agrees with a run that has one.
 * @param startsOn is in here and is the odd one of the five assumptions: a run started a
 * month later finishes a month later with nothing about the plan having changed. That is
 * *time simply passing*, and it is why a decomposition applies it last.
 */
public record ForecastTerms(int engineVersion, String calendarRule, BigDecimal workingHoursPerDay, int capacity,
		BigDecimal teamFactorWorseByPercent, BigDecimal scopeGrowthP10Percent, BigDecimal scopeGrowthP90Percent,
		LocalDate startsOn) {

}
