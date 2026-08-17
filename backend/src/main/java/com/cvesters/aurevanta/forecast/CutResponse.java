package com.cvesters.aurevanta.forecast;

import java.util.UUID;

/**
 * One piece of work somebody could drop, and what dropping it would be worth.
 *
 * <p>
 * <strong>What this buys on its own, and never a term in a sum.</strong> Two cuts do not
 * buy the total of what each buys: on one chain they overlap and cutting both is barely
 * better than cutting one; on separate branches the finish is the later of the two and
 * shortening one leaves the other deciding. A column of these with plus signs in front
 * reads as arithmetic waiting to happen, and it is not — which is why the answer to "what
 * do I cut" is a separate list that was actually measured rather than the top few of
 * these added together.
 *
 * @param buys the difference this makes to the confidence, in percentage points. Usually
 * positive; a candidate that is never on the deciding path buys nothing measurable, which
 * is the whole reason cuts are simulated rather than ranked by size.
 * @param meets whether cutting this alone clears the bar that was asked for.
 * @param title what the work is called now, from the plan rather than from the run — the
 * snapshot never held a title, and somebody reading this is being told what to go and do.
 */
public record CutResponse(UUID itemId, String title, boolean archived, double confidence, double buys, boolean meets) {

}
