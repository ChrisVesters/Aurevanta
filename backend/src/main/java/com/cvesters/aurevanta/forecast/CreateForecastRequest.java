package com.cvesters.aurevanta.forecast;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

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
 * <p>
 * <strong>The three percentages are required for a sharper version of the same
 * reason.</strong> Unlike capacity they have a neutral value — zero — and zero is exactly
 * what the engine did before M3b. That is not an absence of a claim but a strong one:
 * that nothing in this team's world has a common cause, and that no unlisted work will
 * ever appear. Defaulting to it would ship M3a's behaviour under M3b's name with the
 * notices that admitted to it deleted and nothing put in their place. Somebody who does
 * want that may say so by typing zero, and the run will report that they did.
 *
 * @param sampleCount optional, because ten thousand is a statement about sampling error
 * rather than about this plan — a tenth the cost of a hundred thousand, for an error
 * still an order of magnitude below the closed form this product rejected. Bounded above,
 * because a forecast is the first endpoint here whose cost is processor rather than
 * query.
 * @param teamFactorWorseByPercent how much longer everything takes in a bad stretch, read
 * as the P90 of the multiplier every item in a run shares.
 * @param scopeGrowthP10Percent and {@code scopeGrowthP90Percent} are the two ends of how
 * much a plan like this usually grows. They may be equal, and the low one may be zero —
 * which says a tenth of runs discover nothing at all. That the high one is not below the
 * low one is refused as a fact about the pair rather than about either box, the way
 * {@code estimate_out_of_order} is.
 */
public record CreateForecastRequest(@NotNull @Positive Integer capacity,

		@Positive @Max(100_000) Integer sampleCount,

		@NotNull @PositiveOrZero @Max(MAX_PERCENT) @Digits(integer = 4,
				fraction = 2) BigDecimal teamFactorWorseByPercent,

		@NotNull @PositiveOrZero @Max(MAX_PERCENT) @Digits(integer = 4, fraction = 2) BigDecimal scopeGrowthP10Percent,

		@NotNull @PositiveOrZero @Max(MAX_PERCENT) @Digits(integer = 4,
				fraction = 2) BigDecimal scopeGrowthP90Percent) {

	/**
	 * As far as any of these three is worth asking, and the one of the two reasons that
	 * bites is the processor.
	 *
	 * <p>
	 * Everything taking three times as long in a bad stretch, or a plan turning out to
	 * hold three times the work somebody listed, is already past the edge of a number
	 * anybody can act on. The team factor could go further at no cost — it is one
	 * multiplication — and shares this bound for want of a reason to differ.
	 *
	 * <p>
	 * <strong>Scope growth cannot, and the ceiling was measured rather than
	 * picked.</strong> Every percent of it is items the scheduler has to run: at the five
	 * hundred a plan may hold, ten thousand runs cost 413ms with no growth, 915ms at this
	 * bound, 1.9s at 500% and 3.4s at 1000%. This is the last value that leaves the
	 * two-second budget {@code m3a-plan.md} decision 8 measured with room in it, and that
	 * budget is the whole of why a forecast is answered inside the request rather than
	 * queued.
	 */
	static final int MAX_PERCENT = 200;

}
