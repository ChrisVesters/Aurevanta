package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;

/**
 * How much this plan will have delivered by one future week, as a band.
 *
 * <p>
 * <strong>These percentiles read the other way round from the dates beside them, and it
 * is the easiest thing here to misread.</strong> Every figure this API publishes is the
 * percentile of the quantity it names, so {@code p90Date} is the <em>late</em> end of a
 * finish and {@link #p90} is the <em>good</em> end of a delivery count — nine weeks in
 * ten deliver less than that. The edge a reader should be planning against is
 * {@link #p10}.
 *
 * <p>
 * <strong>A band and not a line, at P10 to P90</strong>, which is the interval every
 * other screen in this product shows. Two conventions on one page is one too many, and a
 * cone drawn at one interval beside a date read at another is two answers about one plan.
 *
 * @param week the day this many weeks after the day being asked about — counted from
 * <em>then</em> rather than snapped onto a Monday, because that is what the model drew.
 * The bootstrap takes whole weeks from now, so relabelling them would claim a backlog
 * runs out on a Monday when nothing measured says which day it runs out on.
 */
public record BurnUpConeWeekResponse(LocalDate week, int p10, int p50, int p90) {

}
