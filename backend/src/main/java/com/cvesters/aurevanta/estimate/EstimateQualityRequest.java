package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Three numbers somebody is about to commit to, asked about rather than recorded.
 *
 * <p>
 * <strong>Its own record rather than {@link RecordEstimateRequest} with a field made
 * optional.</strong> The two carry the same three numbers and differ in exactly one
 * thing: recording says how the question was put, and asking a question about a range
 * does not. Relaxing {@code method} to serve both would weaken the field whose entire
 * value is that it is always answered — a column nobody may leave blank is worth more
 * than one shared request type.
 *
 * <p>
 * The constraints are the same because the numbers are the same, and getting a different
 * answer here from the one the estimate itself will get would be worse than repeating
 * four annotations.
 */
public record EstimateQualityRequest(@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal p10Hours,

		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal p50Hours,

		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal p90Hours) {

}
