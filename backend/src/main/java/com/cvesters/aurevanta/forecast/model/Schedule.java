package com.cvesters.aurevanta.forecast.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;

/**
 * When a plan finishes, given how long each piece of work turned out to take and how many
 * of them can be under way at once.
 *
 * <p>
 * <strong>This is where the aggregator stops being a sum.</strong> Ten identical items
 * forecast at wildly different dates depending only on how they are joined up and how
 * many people are available — 51 against 86 in the measurement {@code roadmap.md} carries
 * — so adding durations is not a neutral simplification. It is the special case of one
 * worker doing everything end to end, and a strong claim about capacity made by accident.
 *
 * <p>
 * <strong>Prepared once, run many times.</strong> The graph, the topological order and
 * the priority key are all properties of the plan rather than of any draw, so
 * {@link #of(List, double[], boolean[], int)} works them out once and {@link #finish}
 * consumes a fresh set of durations each time. The plan's own bullets describe one static
 * function taking the edges alongside the durations; that would redo a graph walk ten
 * thousand times, and — worse — would let the priority rule vary run to run, which
 * decision 7 exists to prevent.
 *
 * <p>
 * <strong>Scope growth is the one thing that varies per run, and it is why it enters as
 * leaves.</strong> Work nobody had thought of is discovered afresh in every run, so it
 * cannot be part of what is prepared once — but each piece hangs off a single existing
 * item and holds nothing up behind it, which means no edge it adds can close a loop and
 * no priority it takes can displace anything. So {@link #finish(double[], int[], int)}
 * takes it as an argument, and the graph walk, the topological order and the ranking all
 * stay exactly what the plan made them.
 *
 * <p>
 * <strong>Immutable, and therefore safe to run from several threads.</strong> Every scrap
 * of per-run state is local to {@link #finish}. That is what makes parallelising runs an
 * available lever if step 4's wall-clock budget is ever missed.
 *
 * <p>
 * <strong>Three modelling assumptions are named here rather than buried.</strong> Work is
 * <em>non-preemptive</em> — once something starts it runs to completion, nobody is pulled
 * off a task halfway. When more work is ready than there is room for, the one with the
 * most work waiting behind it goes first. And when the next piece of work in that order
 * cannot have what it needs, <em>the one behind it starts instead</em> rather than the
 * room being held: a team that will not do available work because one person is busy is
 * not a team anybody has, and a model that assumed it would report dates later than the
 * plan for a reason nobody could act on.
 *
 * <p>
 * Two defensible priority rules produce two different forecasts from identical data —
 * worth 0 to 4% where every slot is interchangeable and up to 9% once the resources are
 * typed, measured in {@code m11-plan.md} — so the rule is stable, explicable in a
 * sentence, and stored alongside every run it produced. It is also the smaller of the two
 * effects there by some way, which is worth knowing before spending a milestone on it.
 */
public final class Schedule {

	/**
	 * What the rule below is called, stored on every run that was scheduled by it.
	 *
	 * <p>
	 * The priority rule is a modelling assumption rather than an implementation detail —
	 * two defensible rules give two different forecasts from identical data — so a run
	 * made under one must never be silently compared with a run made under another. A
	 * second rule would be a second constant, and the runs would say which they had.
	 */
	public static final String PRIORITY_RULE = "most_work_waiting";

	/** No work was discovered behind this item, or none at all. */
	private static final int NOTHING = -1;

	private static final int[] NOTHING_FOUND = new int[0];

	private final int itemCount;

	private final Resourcing resourcing;

	/** For each item, the successors it unblocks and the wait before each of them. */
	private final int[][] successors;

	private final double[][] lags;

	private final int[] predecessorCount;

	/** Items in priority order: most work waiting behind them first, then write order. */
	private final int[] order;

	/** Where each item sits in {@link #order}, so "highest priority" is "lowest bit". */
	private final int[] positionOf;

	private final boolean[] underWay;

	private Schedule(int itemCount, Resourcing resourcing, int[][] successors, double[][] lags, int[] predecessorCount,
			int[] order, int[] positionOf, boolean[] underWay) {
		this.itemCount = itemCount;
		this.resourcing = resourcing;
		this.successors = successors;
		this.lags = lags;
		this.predecessorCount = predecessorCount;
		this.order = order;
		this.positionOf = positionOf;
		this.underWay = underWay;
	}

	/**
	 * Works out everything about a plan that does not depend on how long anything turns
	 * out to take.
	 * @param edges what has to finish before what
	 * @param typicalEffortHours roughly what each item holds, used only to rank them
	 * against each other — never to forecast anything. Taken from the plan rather than
	 * from a draw so that the ordering is the same in every run, which is what lets two
	 * forecasts of one plan be compared at all.
	 * @param underWay which items have visibly started, and so are ready at once whatever
	 * the plan says about them
	 * @param capacity how many items may be in flight at the same time
	 * @throws IllegalArgumentException if the capacity is not a positive number of
	 * things, if the two arrays disagree about how many items there are, if an edge names
	 * an item that is not there, or if the graph waits for itself
	 */
	public static Schedule of(List<Precedence> edges, double[] typicalEffortHours, boolean[] underWay, int capacity) {
		if (capacity < 1) {
			throw new IllegalArgumentException(
					"At least one thing must be able to be under way, but capacity was " + capacity);
		}
		return of(edges, typicalEffortHours, underWay, Resourcing.pooled(capacity, typicalEffortHours.length));
	}

	/**
	 * The same, against a team that has been described rather than counted.
	 *
	 * <p>
	 * <strong>This is the method, and the one above is it with one pool.</strong> Not a
	 * shorthand for it and not an approximation of it: with every item taking one unit, a
	 * pool with a free unit is a slot below the capacity, so the two take the same
	 * decisions in the same order at the same moments. That equivalence is what lets a
	 * version bump contain the version before it, and it is asserted on rather than
	 * argued.
	 *
	 * <p>
	 * Nothing about the graph, the topological order or the priority key depends on any
	 * of this — resources decide what may start, never what is worth starting — so
	 * everything prepared here is prepared exactly as it was.
	 * @param resourcing the pools and what each item needs of them, which must have been
	 * declared for this many items
	 * @throws IllegalArgumentException as above, and if the declaration is for a
	 * different plan than this one
	 */
	public static Schedule of(List<Precedence> edges, double[] typicalEffortHours, boolean[] underWay,
			Resourcing resourcing) {
		if (resourcing.items() != typicalEffortHours.length) {
			throw new IllegalArgumentException("The plan has " + typicalEffortHours.length
					+ " items and the resources were declared for " + resourcing.items());
		}
		if (typicalEffortHours.length != underWay.length) {
			throw new IllegalArgumentException("The plan has " + typicalEffortHours.length + " efforts and "
					+ underWay.length + " states, which cannot both be right");
		}
		int items = typicalEffortHours.length;
		int[] predecessorCount = new int[items];
		List<List<Precedence>> outgoing = new ArrayList<>(items);
		for (int item = 0; item < items; item++) {
			outgoing.add(new ArrayList<>());
		}
		for (Precedence edge : edges) {
			require(edge.predecessor(), items);
			require(edge.successor(), items);
			outgoing.get(edge.predecessor()).add(edge);
			predecessorCount[edge.successor()]++;
		}
		int[][] successors = new int[items][];
		double[][] lags = new double[items][];
		for (int item = 0; item < items; item++) {
			List<Precedence> from = outgoing.get(item);
			successors[item] = new int[from.size()];
			lags[item] = new double[from.size()];
			for (int at = 0; at < from.size(); at++) {
				successors[item][at] = from.get(at).successor();
				lags[item][at] = from.get(at).lagHours();
			}
		}
		int[] order = rank(topological(items, successors, predecessorCount), successors, typicalEffortHours);
		int[] positionOf = new int[items];
		for (int position = 0; position < items; position++) {
			positionOf[order[position]] = position;
		}
		return new Schedule(items, resourcing, successors, lags, predecessorCount, order, positionOf, underWay.clone());
	}

	/**
	 * When the plan finishes, given one draw of how long each piece of work takes.
	 *
	 * <p>
	 * An item becomes available when its last predecessor finishes; it may start once
	 * that predecessor's lag has also elapsed and a slot is free; and it then runs
	 * without interruption. Work already under way starts at once — whatever the plan
	 * says has to come before it, and whatever the capacity is — because it has visibly
	 * begun, and a plan that says otherwise is wrong about the world rather than the
	 * other way round.
	 *
	 * <p>
	 * <strong>Why this terminates, since nothing here checks that it does.</strong> Each
	 * turn of the loop either starts something, finishes something, or moves the clock
	 * forward to a lag that has not yet elapsed; the first two can happen at most once
	 * per item and the third at most once per edge. The loop can only stall if nothing is
	 * running, nothing is waiting on a lag, and nothing can start — which needs every
	 * unfinished item to be waiting on another unfinished item, and that is a cycle.
	 * {@link #of} has already refused those, so a guard here would be a branch no input
	 * could reach.
	 * @throws IllegalArgumentException if there is not a duration for every item
	 */
	public double finish(double[] durations) {
		return finish(durations, NOTHING_FOUND, 0);
	}

	/**
	 * When the plan finishes, given one draw of how long each piece of work takes and one
	 * draw of the work nobody had thought of.
	 *
	 * <p>
	 * <strong>Discovered work is ordinary work in every respect but three.</strong> It
	 * occupies a slot, it delays whatever was waiting for one, and it is scheduled by the
	 * same rule as everything else. What it does not do is come before anything somebody
	 * planned: each piece hangs off exactly one existing item, with no lag and nothing
	 * behind it. That is why it needs no cycle check and no second topological pass — an
	 * edge into a brand new leaf cannot close a loop — and it is the reason this can be a
	 * per-run argument to a schedule that was prepared once.
	 *
	 * <p>
	 * <strong>It is picked up last among equals, and that follows from the rule rather
	 * than being an exception to it.</strong> Priority is the work waiting behind an
	 * item, and nothing waits behind work discovered a moment ago, so it sorts with the
	 * other leaves; among those the plan works on what it wrote down first, and this was
	 * written down last. What it deliberately does <em>not</em> do is lift its parent up
	 * the order for holding it: the priority key is a property of the plan, settled
	 * identically in every run, and a key that moved with a draw would leave two
	 * forecasts of one plan ordered differently and unable to be compared.
	 * @param durations one per item and then one per discovered piece of work, in that
	 * order. It may be longer than that, since a caller reuses the room a previous run
	 * needed.
	 * @param parentOf which existing item each discovered piece hangs off
	 * @param found how many of {@code parentOf} to read
	 * @throws IllegalArgumentException if there are not enough durations, or a parent is
	 * not an item in this plan
	 */
	public double finish(double[] durations, int[] parentOf, int found) {
		int total = this.itemCount + found;
		if (durations.length < total) {
			throw new IllegalArgumentException(
					"The plan has " + total + " pieces of work and " + durations.length + " durations");
		}
		// A run that discovered nothing does none of this, which is what keeps the
		// degenerate case as cheap as it was before there was anything to discover — the
		// path every forecast takes whose owner answered zero. The three arrays below are
		// each the length of the plan or of what it found, so building them
		// unconditionally
		// would add two passes over the whole plan to every one of ten thousand runs.
		int[] order = this.order;
		int[] firstFound = NOTHING_FOUND;
		int[] nextFound = NOTHING_FOUND;
		if (found > 0) {
			// Only the priority order has to grow: discovered work is ranked below every
			// planned item, so its position in the order is its own index and the head of
			// this array is untouched. Nothing else here is asked about it by index — it
			// is
			// let go by its parent rather than by counting predecessors down.
			order = Arrays.copyOf(this.order, total);
			firstFound = new int[this.itemCount];
			nextFound = new int[found];
			Arrays.fill(firstFound, NOTHING);
			for (int at = found - 1; at >= 0; at--) {
				int parent = parentOf[at];
				require(parent, this.itemCount);
				order[this.itemCount + at] = this.itemCount + at;
				// Built backwards so that two pieces landing on one parent are let go in
				// the order they were discovered.
				nextFound[at] = firstFound[parent];
				firstFound[parent] = at;
			}
		}
		int[] awaiting = this.predecessorCount.clone();
		double[] readyAt = new double[this.itemCount];
		BitSet startable = new BitSet(total);
		Timeline running = new Timeline(total);
		Timeline lagging = new Timeline(total);
		int[] free = this.resourcing.freeUnits();
		// How many pieces of work that could start right now could use each pool. The
		// scan below stops when nothing startable can use anything still free, which is
		// not the same question as whether anything is free at all — see anyUseful.
		int[] demand = new int[free.length];
		// Which pool each piece of work that named nothing took its one unit from, so
		// that
		// it gives that unit back rather than one of somebody else's.
		int[] tookFrom = new int[total];
		Arrays.fill(tookFrom, NOTHING);
		int finished = 0;
		double now = 0.0;
		double completion = 0.0;

		for (int item = 0; item < this.itemCount; item++) {
			if (this.underWay[item]) {
				// Started already, so it is running now — holding what it needs whether
				// or
				// not the pools have it. A team that is oversubscribed is a fact about
				// the
				// world, and a plan that said otherwise would be wrong about it rather
				// than
				// the other way round: the units go negative, and nothing new starts
				// until
				// enough of them come back.
				take(item, free, tookFrom, item);
				running.add(durations[item], item);
			}
			else if (awaiting[item] == 0) {
				startable.set(this.positionOf[item]);
				wanting(item, demand, 1);
			}
		}

		while (finished < total) {
			// Whatever fits, highest priority first — which is the lowest set bit,
			// because the items were sorted into priority order once, when the plan was
			// prepared. Anything that does not fit is stepped over rather than waited
			// for,
			// and the class note says why: holding a slot for one piece of work while
			// another could have it models a team nobody has.
			for (int position = startable.nextSetBit(0); position >= 0
					&& anyUseful(free, demand); position = startable.nextSetBit(position + 1)) {
				int item = order[position];
				if (fits(rowOf(item, parentOf), free)) {
					take(rowOf(item, parentOf), free, tookFrom, item);
					startable.clear(position);
					wanting(rowOf(item, parentOf), demand, -1);
					running.add(now + durations[item], item);
				}
			}
			now = (!running.isEmpty() && (lagging.isEmpty() || running.next() <= lagging.next())) ? running.next()
					: lagging.next();
			while (!lagging.isEmpty() && lagging.next() <= now) {
				int let = lagging.take();
				startable.set(this.positionOf[let]);
				wanting(let, demand, 1);
			}
			while (!running.isEmpty() && running.next() <= now) {
				int item = running.take();
				giveBack(rowOf(item, parentOf), free, tookFrom, item);
				finished++;
				completion = now;
				if (item < this.itemCount) {
					release(item, now, awaiting, readyAt, startable, lagging, demand);
					// Whatever this run discovered behind this item can begin. No lag,
					// and
					// nothing waits on it in turn, so there is nothing else to hand on.
					// The
					// guard is what lets a run that found nothing carry no list at all.
					if (found > 0) {
						for (int at = firstFound[item]; at >= 0; at = nextFound[at]) {
							startable.set(this.itemCount + at);
							wanting(parentOf[at], demand, 1);
						}
					}
				}
			}
		}
		return completion;
	}

	/**
	 * Whether anything that could start now could use anything still free.
	 *
	 * <p>
	 * <strong>The scan above is bounded by this and needs to be.</strong> Stepping over
	 * work that does not fit means the loop cannot stop at the first thing it cannot
	 * start — but with no reason to stop at all it walks every ready item on every event,
	 * which on a five-hundred-item plan is the difference between three hundred
	 * milliseconds and two seconds. That is measured rather than reasoned: the budget
	 * case in {@code EngineTests} failed on the first version of this, which had no such
	 * guard.
	 *
	 * <p>
	 * <strong>"Is anything free" is the wrong question, and it is wrong in exactly the
	 * shape this milestone exists to model.</strong> A team of ten backend and one
	 * designer running work that is nearly all backend leaves the designer's unit free
	 * for most of the plan — so a guard asking only whether some pool has a unit is true
	 * throughout, and every event walks every ready item again. Measured on the same
	 * five-hundred-item plan: 449 ms with one pool, 2,276 ms with those two, against a
	 * budget of two seconds. The unit being free is not the point; a unit being free
	 * <em>that something waiting could take</em> is.
	 *
	 * <p>
	 * With one pool the two questions coincide — everything startable wants the only pool
	 * there is — so the containment holds in cost as well as in answers.
	 * @param demand how many startable items could use each pool, which is what makes
	 * this tighter than counting free units
	 */
	private static boolean anyUseful(int[] free, int[] demand) {
		for (int pool = 0; pool < free.length; pool++) {
			if (free[pool] > 0 && demand[pool] > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Counts one piece of work in or out of the demand for every pool it could use.
	 *
	 * <p>
	 * Work that names nothing could use any of them, which is what makes it the case that
	 * decides the guard above: a plan of generic work wants every pool, so the guard
	 * relaxes to "is anything free" exactly where that is the right question.
	 * @param row whose requirement it is scheduled by — its own, or its parent's for work
	 * a run discovered
	 * @param by 1 as it becomes startable and -1 as it starts
	 */
	private void wanting(int row, int[] demand, int by) {
		if (this.resourcing.namesNothing(row)) {
			for (int pool = 0; pool < demand.length; pool++) {
				demand[pool] += by;
			}
			return;
		}
		for (int pool = 0; pool < demand.length; pool++) {
			if (this.resourcing.needed(row, pool) > 0) {
				demand[pool] += by;
			}
		}
	}

	/**
	 * Which item's requirement one piece of work is scheduled by.
	 *
	 * <p>
	 * Its own for anything the plan holds, and its parent's for anything a run
	 * discovered: work found behind a backend task is backend work. The alternative would
	 * need a rule about what an unlisted item is made of, and nothing measured says what.
	 */
	private int rowOf(int item, int[] parentOf) {
		return (item < this.itemCount) ? item : parentOf[item - this.itemCount];
	}

	/**
	 * Whether the pools can spare what this piece of work needs.
	 *
	 * <p>
	 * Asked before anything is taken rather than taken and given back, because a piece of
	 * work needing two pools needs both at once — reserving one and then finding the
	 * other short would leave a unit held by nobody.
	 *
	 * <p>
	 * Asked only while {@link #anyUseful} holds, which is what makes the answer for work
	 * that names nothing a constant rather than a search: work that names nothing wants
	 * every pool, so the guard cannot hold without a unit it can take.
	 */
	private boolean fits(int row, int[] free) {
		if (this.resourcing.namesNothing(row)) {
			// One unit of anything, and this is only asked while there is one — so work
			// that names nothing is never what stops a scan.
			return true;
		}
		for (int pool = 0; pool < free.length; pool++) {
			if (free[pool] < this.resourcing.needed(row, pool)) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Takes what it needs, and remembers where a piece of work that named nothing took it
	 * from.
	 *
	 * <p>
	 * <strong>Declaration order is the whole of the rule for work that names
	 * nothing</strong>, and it is why a listing of pools is ordered by when they were
	 * declared rather than by name: renaming a pool would otherwise change what a
	 * forecast scheduled.
	 *
	 * <p>
	 * Called without asking {@link #fits} for work that is already under way, which is
	 * the one caller that may push a pool below nothing — see the note where it starts.
	 */
	private void take(int row, int[] free, int[] tookFrom, int item) {
		if (this.resourcing.namesNothing(row)) {
			for (int pool = 0; pool < free.length; pool++) {
				if (free[pool] > 0) {
					free[pool]--;
					tookFrom[item] = pool;
					return;
				}
			}
			// Nothing free anywhere, which only the already-under-way caller reaches: it
			// holds a unit of the first pool and the count goes negative.
			free[0]--;
			tookFrom[item] = 0;
			return;
		}
		for (int pool = 0; pool < free.length; pool++) {
			free[pool] -= this.resourcing.needed(row, pool);
		}
	}

	/** And gives back exactly what that piece of work took. */
	private void giveBack(int row, int[] free, int[] tookFrom, int item) {
		if (this.resourcing.namesNothing(row)) {
			free[tookFrom[item]]++;
			return;
		}
		for (int pool = 0; pool < free.length; pool++) {
			free[pool] += this.resourcing.needed(row, pool);
		}
	}

	/**
	 * Hands one finished item's successors whatever it was holding them up by: a
	 * predecessor fewer, and a moment before which they cannot begin.
	 */
	private void release(int item, double now, int[] awaiting, double[] readyAt, BitSet startable, Timeline lagging,
			int[] demand) {
		int[] next = this.successors[item];
		for (int at = 0; at < next.length; at++) {
			int successor = next[at];
			readyAt[successor] = Math.max(readyAt[successor], now + this.lags[item][at]);
			awaiting[successor]--;
			// Something already under way is not waiting to be let go: it started at zero
			// and its predecessor catching up later does not entitle it to start twice.
			if (awaiting[successor] > 0 || this.underWay[successor]) {
				continue;
			}
			if (readyAt[successor] <= now) {
				startable.set(this.positionOf[successor]);
				wanting(successor, demand, 1);
			}
			else {
				// Counting down a wait rather than occupying a slot, so nothing else is
				// held up by it.
				lagging.add(readyAt[successor], successor);
			}
		}
	}

	/**
	 * The order the work will be picked up in when more of it is ready than there is room
	 * for: most work waiting behind it first, and where two are level, the one written
	 * down first.
	 *
	 * <p>
	 * <strong>Reachable work rather than the longest chain behind it.</strong> The
	 * standard scheduling heuristic is the latter, and it would be the better scheduler;
	 * this one is the better sentence — "the plan works on whatever unblocks the most" —
	 * and a rule a person cannot repeat back is a rule they cannot judge a forecast by.
	 * It costs a transitive closure, which is affordable only because a plan is capped at
	 * 500 items; that ceiling and this are tied together.
	 */
	private static int[] rank(int[] topological, int[][] successors, double[] efforts) {
		int items = topological.length;
		BitSet[] downstream = new BitSet[items];
		for (int at = items - 1; at >= 0; at--) {
			int item = topological[at];
			BitSet reachable = new BitSet(items);
			for (int successor : successors[item]) {
				reachable.set(successor);
				reachable.or(downstream[successor]);
			}
			downstream[item] = reachable;
		}
		double[] waiting = new double[items];
		for (int item = 0; item < items; item++) {
			BitSet reachable = downstream[item];
			for (int at = reachable.nextSetBit(0); at >= 0; at = reachable.nextSetBit(at + 1)) {
				waiting[item] += efforts[at];
			}
		}
		Integer[] ranked = new Integer[items];
		Arrays.setAll(ranked, (item) -> item);
		Arrays.sort(ranked,
				Comparator.comparingDouble((Integer item) -> waiting[item])
					.reversed()
					.thenComparingInt((item) -> item));
		int[] order = new int[items];
		Arrays.setAll(order, (position) -> ranked[position]);
		return order;
	}

	/**
	 * An order in which no item comes before something it depends on, which exists
	 * exactly when the plan does not wait for itself.
	 *
	 * <p>
	 * The refusal is unreachable through the API — M2 checks every new edge against the
	 * whole graph under a lock on the plan, which is the sharpest thing in that milestone
	 * — but this method takes primitives, so a test hands it a cycle directly. That is
	 * the distinction {@code ProjectService.lockForGraphChange} draws from the other
	 * side, where a refusal was removed for being reachable by nothing at all.
	 */
	private static int[] topological(int items, int[][] successors, int[] predecessorCount) {
		int[] awaiting = predecessorCount.clone();
		int[] order = new int[items];
		int found = 0;
		for (int item = 0; item < items; item++) {
			if (awaiting[item] == 0) {
				order[found++] = item;
			}
		}
		for (int at = 0; at < found; at++) {
			for (int successor : successors[order[at]]) {
				if (--awaiting[successor] == 0) {
					order[found++] = successor;
				}
			}
		}
		if (found < items) {
			throw new IllegalArgumentException("The plan waits for itself, so there is no order to work in");
		}
		return order;
	}

	private static void require(int item, int items) {
		if (item < 0 || item >= items) {
			throw new IllegalArgumentException("An edge names item " + item + ", and the plan has " + items);
		}
	}

	/**
	 * A little heap of items, each keyed by the moment something is due to happen to it —
	 * either finishing, or reaching the end of a wait.
	 *
	 * <p>
	 * Written out rather than taken from {@code java.util} because this is the innermost
	 * loop of the product: five hundred items times ten thousand runs is several million
	 * of these operations per forecast, and a {@code PriorityQueue<Integer>} boxes every
	 * one of them. The measured budget in step 4 is what this is for.
	 */
	private static final class Timeline {

		private final double[] times;

		private final int[] items;

		private int size;

		Timeline(int room) {
			this.times = new double[room];
			this.items = new int[room];
		}

		boolean isEmpty() {
			return this.size == 0;
		}

		double next() {
			return this.times[0];
		}

		void add(double time, int item) {
			int at = this.size++;
			while (at > 0) {
				int parent = (at - 1) / 2;
				if (this.times[parent] <= time) {
					break;
				}
				this.times[at] = this.times[parent];
				this.items[at] = this.items[parent];
				at = parent;
			}
			this.times[at] = time;
			this.items[at] = item;
		}

		int take() {
			int taken = this.items[0];
			double time = this.times[--this.size];
			int item = this.items[this.size];
			int at = 0;
			while (true) {
				int child = 2 * at + 1;
				if (child >= this.size) {
					break;
				}
				if (child + 1 < this.size && this.times[child + 1] < this.times[child]) {
					child++;
				}
				if (this.times[child] >= time) {
					break;
				}
				this.times[at] = this.times[child];
				this.items[at] = this.items[child];
				at = child;
			}
			if (this.size > 0) {
				this.times[at] = time;
				this.items[at] = item;
			}
			return taken;
		}

	}

}
