package com.cvesters.aurevanta.calibration;

import java.time.Instant;
import java.util.List;

import com.cvesters.aurevanta.forecast.model.Calibration;

/**
 * What one organisation's ranges have turned out to be worth, in three parts that must
 * not be added together.
 *
 * <p>
 * <strong>The split is the work.</strong> An estimate written after the work began is a
 * report by somebody who could already see how the task was going, and folding it into
 * the headline flatters the one number in this product whose entire value is that it is
 * unflattering. So the buckets are decided by <em>when</em> each range was written
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
 * @param byEstimator and {@code byMethod} split the forecasts and only the forecasts, in
 * name order and in method order — a total order in both cases, because two people may
 * share a display name and a list that rearranges itself between requests is one nobody
 * can read twice.
 * @param firstScored and {@code lastScored} are when the earliest and latest scored
 * <em>estimates</em> were written, not when the work finished. A calibration record is a
 * statement about how an organisation estimates, so what makes it stale is the age of the
 * estimating in it: a last-scored date eight months old says that nothing predicted since
 * has finished yet, which is exactly what a reader needs to know before acting on the
 * number. Null when nothing has been scored.
 */
public record OrganisationCalibration(Calibration forecasts, Calibration reports, Calibration unbounded,
		List<EstimatorCalibration> byEstimator, List<MethodCalibration> byMethod, CalibrationCoverage coverage,
		Instant firstScored, Instant lastScored) {
}
