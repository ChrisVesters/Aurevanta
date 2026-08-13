package com.cvesters.aurevanta.item;

/**
 * How far along one piece of work is.
 *
 * <p>
 * Three states and no more. This is not a workflow — {@code roadmap.md} is explicit that
 * Aurevanta is not a task tracker, and day-to-day movement belongs in whatever the team
 * already uses. What a forecast needs is only whether an item is still ahead of it, and
 * these three answer that.
 */
public enum WorkItemStatus {

	/** Nothing has happened yet, which is where every item starts. */
	NOT_STARTED,

	/**
	 * Under way, and so still partly ahead of a forecast. Carries a start date, because a
	 * claim that work began is evidence and a date is what makes it one.
	 */
	IN_PROGRESS,

	/**
	 * Finished, and therefore not something to predict again. Carries a completion date;
	 * how long it took is optional, because most teams do not measure it.
	 */
	DONE

}
