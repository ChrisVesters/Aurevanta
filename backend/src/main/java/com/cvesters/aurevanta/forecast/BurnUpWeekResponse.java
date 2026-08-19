package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;

/**
 * One week of what a plan has actually delivered.
 *
 * @param week the Monday that week began on, which is {@code Throughput.RULE}'s bucket
 * and not a week number — a number needs a year beside it and the pair disagree with the
 * calendar for a few days every January.
 * @param delivered the running total by the end of it, rather than that week's own count.
 * A burn-up is a cumulative picture, and publishing the weekly counts would leave every
 * reader adding them up — differently, at the ends.
 */
public record BurnUpWeekResponse(LocalDate week, int delivered) {

}
