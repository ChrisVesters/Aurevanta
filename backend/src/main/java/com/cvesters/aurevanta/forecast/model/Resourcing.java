package com.cvesters.aurevanta.forecast.model;

/**
 * What a plan has to work with, and what each piece of work needs of it.
 *
 * <p>
 * <strong>This is what capacity used to be, and the difference is worth a measurement
 * rather than an assertion.</strong> Six interchangeable slots and two pools of three are
 * not two ways of saying one thing: the same plan, the same durations and the same
 * priority order finish 14% to 59% later once the work cannot cross from one pool to the
 * other, and further the more specialised the team is. Pooling is a relaxation, so any
 * schedule that is feasible when the work is typed is feasible when it is not — <strong>a
 * capacity number is therefore a lower bound on when a plan finishes rather than an
 * approximation of it</strong>, and it errs in the one direction this product exists to
 * correct. {@code m11-plan.md} carries the table.
 *
 * <p>
 * <strong>One pool of <em>n</em> units with nothing named is capacity <em>n</em>
 * exactly.</strong> Not approximately, and not "the same decisions in practice": with
 * every item taking one unit, a pool with a free unit is a slot below the capacity, so
 * the scheduler starts the same work in the same order at the same moment. That is what
 * lets {@code Engine.VERSION} 3 contain version 2, which is what lets every forecast this
 * product has already stored keep being explained, weighed and compared.
 *
 * <p>
 * <strong>An item that names nothing is not unconstrained.</strong> It takes one unit of
 * whichever pool has one free, in declaration order — which is what an unannotated piece
 * of work <em>is</em>, generic work anybody can pick up. The alternatives were both
 * wrong: work that ignores the constraint entirely makes the first forecast after
 * declaring a team wildly optimistic, and demanding a requirement on every item is five
 * hundred rows of data entry before anybody can ask a question.
 *
 * <p>
 * <strong>Units are occupancy and never speed.</strong> Two units means the work ties up
 * two, not that it goes twice as fast. {@code m11-plan.md} decision 5 is the argument and
 * its last line is the one that decides: effort divided by headcount is checkable against
 * nothing, where every other modelling decision in M3 is checkable against arithmetic
 * that exists outside this codebase.
 */
public final class Resourcing {

	private final int[] units;

	/** What each item needs of each pool, indexed by item and then by pool. */
	private final int[][] needed;

	/**
	 * Whether each item names nothing, worked out once rather than in the scheduler's
	 * loop.
	 */
	private final boolean[] generic;

	private Resourcing(int[] units, int[][] needed, boolean[] generic) {
		this.units = units;
		this.needed = needed;
		this.generic = generic;
	}

	/**
	 * A plan with one undifferentiated pool and nothing named, which is what every
	 * forecast made before M11 assumed.
	 * @param capacity how many pieces of work may be under way at once
	 * @param items how many there are
	 * @throws IllegalArgumentException if the capacity is not a positive number of things
	 */
	public static Resourcing pooled(int capacity, int items) {
		return of(new int[] { capacity }, new int[items][1]);
	}

	/**
	 * A declared team, and what the work needs of it.
	 * @param unitsPerPool how many of each pool there are, in the order they were
	 * declared — which is the order a piece of work naming nothing takes a unit in, so it
	 * is part of the model rather than a presentation choice
	 * @param needed one row per item, one column per pool. A row of nothing means the
	 * item names nothing, which is a claim rather than an omission.
	 * @throws IllegalArgumentException if there are no pools, if a pool holds no units,
	 * if a row is the wrong length, if anything is asked for a negative number of units,
	 * or if anything asks for more of a pool than that pool holds
	 */
	public static Resourcing of(int[] unitsPerPool, int[][] needed) {
		if (unitsPerPool.length == 0) {
			throw new IllegalArgumentException("A plan with no resources at all has nothing to schedule against");
		}
		int[] units = unitsPerPool.clone();
		for (int pool = 0; pool < units.length; pool++) {
			if (units[pool] < 1) {
				throw new IllegalArgumentException(
						"A pool holds at least one unit, but pool " + pool + " holds " + units[pool]);
			}
		}
		int[][] rows = new int[needed.length][];
		boolean[] generic = new boolean[needed.length];
		for (int item = 0; item < needed.length; item++) {
			if (needed[item].length != units.length) {
				throw new IllegalArgumentException("Item " + item + " names " + needed[item].length + " pools, and the "
						+ "plan has " + units.length);
			}
			rows[item] = needed[item].clone();
			boolean namesNothing = true;
			for (int pool = 0; pool < units.length; pool++) {
				int wanted = rows[item][pool];
				if (wanted < 0) {
					throw new IllegalArgumentException("Item " + item + " needs " + wanted + " units of pool " + pool);
				}
				// **Refused here rather than never starting.** The loop that schedules
				// has
				// no guard against work that cannot fit, and its termination argument
				// depends on there being none: with nothing running every unit is free,
				// so
				// anything that can ever start can start then. Work asking for more of a
				// pool than exists would break that quietly, by waiting for ever.
				if (wanted > units[pool]) {
					throw new IllegalArgumentException("Item " + item + " needs " + wanted + " units of pool " + pool
							+ ", which holds " + units[pool]);
				}
				namesNothing &= wanted == 0;
			}
			generic[item] = namesNothing;
		}
		return new Resourcing(units, rows, generic);
	}

	/** How many items this was declared for, which is what a plan has to agree with. */
	public int items() {
		return this.needed.length;
	}

	public int pools() {
		return this.units.length;
	}

	/** The units of every pool, as a scheduler may spend them. */
	int[] freeUnits() {
		return this.units.clone();
	}

	int needed(int item, int pool) {
		return this.needed[item][pool];
	}

	/**
	 * Whether this item named nothing, and so takes one unit of whatever is free rather
	 * than units of anything in particular.
	 */
	boolean namesNothing(int item) {
		return this.generic[item];
	}

}
