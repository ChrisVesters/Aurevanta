package com.cvesters.aurevanta.estimate;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A three-point range, in hours of effort.
 *
 * <p>
 * The unit is in every field name because it is the whole of decision 3: a "day" is a
 * calendar word, and what a day is worth is M11's setting rather than something buried in
 * old rows. A team that thinks in days multiplies by their own.
 *
 * <p>
 * {@code @Digits} matches the column exactly. Without it, 0.005 hours would pass
 * {@code @Positive}, be rounded to 0.00 on the way into {@code numeric(12, 2)}, and land
 * as an estimate of nothing — breaking the rule that let it in, silently, after the
 * check.
 *
 * <p>
 * What is <em>not</em> here is the order the three have to be in. That is not a fact
 * about any one field, so it is refused as {@code estimate_out_of_order} rather than
 * reported against a box chosen arbitrarily.
 */
public record RecordEstimateRequest(@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal p10Hours,

		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal p50Hours,

		@NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal p90Hours) {

}
