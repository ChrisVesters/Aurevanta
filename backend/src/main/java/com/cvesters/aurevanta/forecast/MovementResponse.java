package com.cvesters.aurevanta.forecast;

import java.util.List;
import java.util.UUID;

/**
 * Why the date moved, between two forecasts of one plan.
 *
 * <p>
 * <strong>The icebox calls this "the feature I would most want as a user".</strong> Two
 * dates a fortnight apart start an argument; an account of which fortnight was new scope,
 * which was a second opinion, and which was somebody halving the capacity ends one.
 *
 * <p>
 * <strong>An assumption that changed is a term and not a refusal</strong>, which is the
 * opposite of what M6 does and is not a contradiction. M6 refuses when the *model* cannot
 * reproduce a run — there is nothing to compare with. This reports when the *question*
 * changed, which is exactly the thing worth reporting: refusing a pair because somebody
 * adjusted the capacity would leave them staring at two dates with no account of either.
 * An engine version difference is still the first kind and still refuses.
 *
 * @param rule which order the terms were attributed in — a name, because two defensible
 * orders split the same eight days differently.
 * @param simulations how many times the plan was run to answer this, published for M7's
 * reason: a number that costs six simulations should say so rather than surprise
 * somebody.
 */
public record MovementResponse(UUID fromRunId, UUID toRunId, String rule, int simulations,
		List<MovementAtResponse> at) {

}
