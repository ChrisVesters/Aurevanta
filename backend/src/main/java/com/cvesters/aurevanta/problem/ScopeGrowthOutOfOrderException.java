package com.cvesters.aurevanta.problem;

import org.springframework.http.HttpStatus;

/**
 * The two ends of a scope growth range are the wrong way round: the P90 must be no less
 * than the P10.
 *
 * <p>
 * A refusal of its own rather than a per-field validation error, for the reason
 * {@link EstimateOutOfOrderException} is one: each number is a perfectly good percentage,
 * and what is wrong is the relationship between them. {@code FieldProblem} exists to say
 * "this box is wrong", and pointing at one of the two here would be picking a culprit
 * arbitrarily — the caller may have meant either.
 *
 * <p>
 * Refused rather than quietly swapped, which is the tempting fix and the wrong one. A
 * range of 60 to 20 is a typo, and a server that read it as 20 to 60 would be forecasting
 * against an assumption nobody made while reporting it back as though they had.
 */
public class ScopeGrowthOutOfOrderException extends ApiProblemException {

	public ScopeGrowthOutOfOrderException() {
		super(HttpStatus.BAD_REQUEST, "Scope growth out of order", "scope_growth_out_of_order",
				"The high end of a growth range must be no less than the low end");
	}

}
