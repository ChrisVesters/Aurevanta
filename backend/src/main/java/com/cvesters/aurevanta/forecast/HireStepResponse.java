package com.cvesters.aurevanta.forecast;

import java.time.LocalDate;

/**
 * One more unit of a pool, and the date it buys.
 *
 * <p>
 * <strong>These are cumulative and not a column to add up.</strong> Each row is the plan
 * with that many added, measured — so the second row is what two people buy, not what the
 * second person buys on top of the first. The difference between two rows is the honest
 * way to read the diminishing return, and it is why the rows are measured rather than the
 * first one being multiplied.
 *
 * @param daysEarlier how much sooner the date comes, in whole days. <strong>Zero is an
 * answer and often the right one</strong>: a pool that is not what the plan is waiting
 * for buys nothing, and a plan whose finish is decided by a chain of dependencies buys
 * nothing from anybody.
 *
 * <p>
 * <strong>And it can be negative, which is a result rather than a fault.</strong> This is
 * a fixed-priority list scheduler over a precedence graph, which is exactly where
 * Graham's anomaly lives: another unit can let a low-priority item start early, tie up
 * what a critical-path successor was about to need, and push the finish out. Each end is
 * also rounded up to a whole day on its own, so a hair's difference in hours is a whole
 * day either way. Clamping it to zero would turn "more people can be slower here" — which
 * is worth knowing and hard to believe without being shown — into a shrug, so the sign is
 * published and the screen has a third sentence for it.
 */
public record HireStepResponse(int units, LocalDate by, int daysEarlier) {

}
