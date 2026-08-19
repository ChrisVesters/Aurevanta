package com.cvesters.aurevanta.calibration;

import com.cvesters.aurevanta.forecast.model.Calibration;

/**
 * How the ranges collected one way turned out, against the ranges collected another.
 *
 * <p>
 * <strong>This is what {@code V15} was added for, and it is the only instrument that can
 * ever say whether elicitation worked.</strong> That work's claim is that changing the
 * *question* produces honester ranges, and its failure mode is a form that feels better
 * and changes nothing — which nothing in the test suite can settle. Split the calibration
 * record by how each range was asked for and the question answers itself.
 *
 * <p>
 * Forecasts only, like the per-person record beside it, and grouped by the raw stored
 * name: a method this code has never heard of comes back under its own name rather than
 * making a row unreadable, which is why that column is a {@code varchar} and not an enum.
 */
public record MethodCalibration(String method, Calibration record) {
}
