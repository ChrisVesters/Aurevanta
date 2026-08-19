package com.cvesters.aurevanta.forecast;

import java.util.List;
import java.util.UUID;

/**
 * What adding to one pool would be worth, measured against one stored run.
 *
 * <p>
 * <strong>It weighs and never decides.</strong> The answer is a number of days, not a
 * recommendation — the same line the cut search hold, and for the same reason: what a
 * person costs, whether one can be found, and how long they take to be useful are facts
 * this server does not have. <strong>The last of those is the sharpest</strong>, because
 * the model genuinely does not have it: a new unit is at full rate from the first hour,
 * which no new joiner is. That is stated beside the answer rather than left for somebody
 * to remember.
 *
 * @param simulations how many times the plan was re-run to answer this — one per unit
 * asked about, plus the one that proves the run still reproduces. Published for the
 * inverse query's reason: a number that costs simulations should say so rather than
 * surprise somebody.
 */
public record HireOptionsResponse(UUID resourceId, int simulations, List<HireAtResponse> at) {

}
