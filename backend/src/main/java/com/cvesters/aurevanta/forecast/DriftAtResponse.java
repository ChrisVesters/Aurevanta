package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;

/**
 * How far one confidence's date has drifted, and whether that is worth saying.
 *
 * <p>
 * <strong>Three of these rather than one, because the control moves.</strong> All of them
 * are arithmetic over dates this endpoint is already carrying, so publishing the three
 * M4's control offers costs nothing — and a reader who moves the control must not be
 * shown a verdict about a percentile they are no longer looking at.
 *
 * @param fromDate where the oldest run in the window put this percentile and
 * {@code toDate} where the newest one does, published so the flag can be checked rather
 * than believed.
 * @param days the distance between those two, positive when the plan has moved out. Null
 * whenever either run has no calendar to read its hours through, which is the same
 * absence {@link ForecastResponse} reports for the same reason.
 * @param bandDays what the current forecast itself says the distance between the good and
 * the bad case is — the yardstick rather than a reading. A drift is worth saying only
 * against it: the same three days are nothing on one plan and the whole of another.
 * @param movingOut the flag, and the number behind it stays on the server the way
 * {@code EstimateQuality}'s thresholds do.
 */
public record DriftAtResponse(int confidence, LocalDate fromDate, LocalDate toDate, Integer days, Integer bandDays,
		boolean movingOut) {

}
