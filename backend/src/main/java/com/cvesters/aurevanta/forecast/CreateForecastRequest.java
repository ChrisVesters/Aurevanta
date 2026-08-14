package com.cvesters.aurevanta.forecast;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * What to assume while forecasting a plan.
 *
 * <p>
 * <strong>The capacity is required and has no default anywhere.</strong> A dependency
 * graph without one assumes unlimited parallelism and is optimistic by the same margin
 * that summing durations is pessimistic — the same ten items came out at 51 or 86 days at
 * the P90 in {@code roadmap.md}'s measurement, depending on nothing else. A number that
 * moves the answer by that much is not an implementation detail, and a server that filled
 * it in would leave the caller holding a claim about their team that they never made.
 * This is M1a's argument about the organisation handle reaching a second place: an
 * assumption is only honest when somebody made it.
 *
 * @param sampleCount optional, because ten thousand is a statement about sampling error
 * rather than about this plan — a tenth the cost of a hundred thousand, for an error
 * still an order of magnitude below the closed form this product rejected. Bounded above,
 * because a forecast is the first endpoint here whose cost is processor rather than
 * query.
 */
public record CreateForecastRequest(@NotNull @Positive Integer capacity,

		@Positive @Max(100_000) Integer sampleCount) {

}
