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
 * <strong>Immutable, and therefore safe to run from several threads.</strong> Every scrap
 * of per-run state is local to {@link #finish}. That is what makes parallelising runs an
 * available lever if step 4's wall-clock budget is ever missed.
 *
 * <p>
 * <strong>Two modelling assumptions are named here rather than buried.</strong> Work is
 * <em>non-preemptive</em> — once something starts it runs to completion, nobody is pulled
 * off a task halfway — and when more work is ready than there are slots, the one with the
 * most work waiting behind it goes first. Two defensible priority rules produce two
 * different forecasts from identical data, so the rule is stable, explicable in a
 * sentence, and stored alongside every run it produced.
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

	private final int itemCount;

	private final int capacity;

	/** For each item, the successors it unblocks and the wait before each of them. */
	private final int[][] successors;

	private final double[][] lags;

	private final int[] predecessorCount;

	/** Items in priority order: most work waiting behind them first, then write order. */
	private final int[] order;

	/** Where each item sits in {@link #order}, so "highest priority" is "lowest bit". */
	private final int[] positionOf;

	private final boolean[] underWay;

	private Schedule(int itemCount, int capacity, int[][] successors, double[][] lags, int[] predecessorCount,
			int[] order, int[] positionOf, boolean[] underWay) {
		this.itemCount = itemCount;
		this.capacity = capacity;
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
		return new Schedule(items, capacity, successors, lags, predecessorCount, order, positionOf, underWay.clone());
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
	 * @throws IllegalArgumentException if there is not exactly one duration per item
	 */
	public double finish(double[] durations) {
		if (durations.length != this.itemCount) {
			throw new IllegalArgumentException(
					"The plan has " + this.itemCount + " items and " + durations.length + " durations");
		}
		int[] awaiting = this.predecessorCount.clone();
		double[] readyAt = new double[this.itemCount];
		BitSet startable = new BitSet(this.itemCount);
		Timeline running = new Timeline(this.itemCount);
		Timeline lagging = new Timeline(this.itemCount);
		int inFlight = 0;
		int finished = 0;
		double now = 0.0;
		double completion = 0.0;

		for (int item = 0; item < this.itemCount; item++) {
			if (this.underWay[item]) {
				// Started already, so it is running now — over capacity if need be.
				running.add(durations[item], item);
				inFlight++;
			}
			else if (awaiting[item] == 0) {
				startable.set(this.positionOf[item]);
			}
		}

		while (finished < this.itemCount) {
			// Whatever fits, highest priority first — which is the lowest set bit,
			// because the items were sorted into priority order once, when the plan was
			// prepared.
			while (inFlight < this.capacity) {
				int position = startable.nextSetBit(0);
				if (position < 0) {
					break;
				}
				startable.clear(position);
				int item = this.order[position];
				running.add(now + durations[item], item);
				inFlight++;
			}
			now = (!running.isEmpty() && (lagging.isEmpty() || running.next() <= lagging.next())) ? running.next()
					: lagging.next();
			while (!lagging.isEmpty() && lagging.next() <= now) {
				startable.set(this.positionOf[lagging.take()]);
			}
			while (!running.isEmpty() && running.next() <= now) {
				int item = running.take();
				inFlight--;
				finished++;
				completion = now;
				release(item, now, awaiting, readyAt, startable, lagging);
			}
		}
		return completion;
	}

	/**
	 * Hands one finished item's successors whatever it was holding them up by: a
	 * predecessor fewer, and a moment before which they cannot begin.
	 */
	private void release(int item, double now, int[] awaiting, double[] readyAt, BitSet startable, Timeline lagging) {
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
