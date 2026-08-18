package com.cvesters.aurevanta.calibration;

import java.util.UUID;

import com.cvesters.aurevanta.forecast.model.Calibration;

/**
 * One person's own record, over the forecasts they wrote before the work began.
 *
 * <p>
 * <strong>Forecasts only, and never the other two buckets.</strong> A report is what
 * somebody wrote once they could see how the task was going, and attributing that to them
 * as an estimate would rank people partly by how late they file.
 *
 * <p>
 * <strong>Names the person and does not rank them.</strong> This product ranks work — M6
 * ranks what makes a plan uncertain, M7 ranks what to drop — and it does not rank people:
 * a hit rate leaderboard is won by writing one-to-a-thousand, which is the failure the
 * whole record exists to expose. Rows come out in name order for that reason, with the
 * count and the interval on each so that six outcomes are visibly not ninety.
 *
 * @param estimatorName taken off the estimate rather than off a membership, so somebody
 * who has left still appears — the same reason {@code estimates.estimator_user_id} does
 * not cascade.
 */
public record EstimatorCalibration(UUID estimatorId, String estimatorName, Calibration record) {
}
