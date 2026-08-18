package com.cvesters.aurevanta.calibration;

import com.cvesters.aurevanta.forecast.model.Calibration;

/**
 * What one organisation's ranges have turned out to be worth, in three parts that must
 * not be added together.
 *
 * <p>
 * <strong>The split is the milestone.</strong> An estimate written after the work began
 * is a report by somebody who could already see how the task was going, and folding it
 * into the headline flatters the one number in this product whose entire value is that it
 * is unflattering. So the buckets are decided by <em>when</em> each range was written
 * relative to the first day anybody claimed the work had started, and nothing is thrown
 * away — scoring the late ones separately says how large the hindsight effect is on a
 * team's own work, which is the strongest available argument that the rule is not
 * pedantry.
 *
 * @param forecasts written before the work began. The headline, and the only bucket worth
 * acting on.
 * @param reports written on or after the day it began. Expect these to be very good;
 * anything else is a finding.
 * @param unbounded every range on finished work nobody ever reported a start for. It
 * cannot be told from a report, so it is named rather than guessed at — and kept out of
 * the headline rather than laundered into it.
 */
public record OrganisationCalibration(Calibration forecasts, Calibration reports, Calibration unbounded,
		CalibrationCoverage coverage) {
}
