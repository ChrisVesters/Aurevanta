package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;

import com.cvesters.aurevanta.forecast.Movement.Step;

/**
 * One thing that moved the date, and how far it moved it.
 *
 * <p>
 * Measured with every earlier step already applied, so the terms sum to the whole
 * distance between the two runs rather than to most of it — see {@link Movement}.
 *
 * @param movedDays what a reader is shown, and null when either run has no calendar to
 * read a date through. It is the difference between two <em>dates</em> and never the
 * hours converted: each end is rounded up to a whole day on its own, so a day count
 * derived from the hours would disagree with the dates on screen by one about half the
 * time.
 * @param movedHours always present, because the engine's answer is hours and a run with
 * no calendar still has them.
 */
public record MovementTermResponse(Step step, BigDecimal movedHours, Integer movedDays) {

}
