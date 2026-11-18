import type { Dependency, WorkItem } from './types';

/**
 * The order a plan's arrows impose on its work, as pure functions over what the plan screen
 * already loads.
 *
 * **Nothing here is a time.** A task's step is how many arrows long the longest chain into it
 * is, and that is all it means: step 3 is "after everything in steps 1 and 2 that it is
 * joined to", never a week. There is no effort in these functions at all — an estimate is
 * read for one fact, whether it exists.
 *
 * **It is a separate module for the engine's reason, in the browser.** The failure here is a
 * plausible wrong picture: a layering bug puts a task to the left of something it waits on
 * and nothing on screen looks broken. So the arithmetic has no React and no `t` in it, and
 * its tests assert the invariant over a large generated graph rather than only over examples.
 */

/** One piece of live work, with what the whole plan says about it. */
export type OrderedTask = {
  item: WorkItem;
  /**
   * Where the server listed it. Order within a step starts from this and breaks ties by it,
   * and is otherwise chosen to uncross the arrows — never by the title.
   */
  index: number;
  /** Whether anybody has estimated it. The figures are never read. */
  estimated: boolean;
  /**
   * Not started, and every live task it waits on is done. **Worked out on the whole plan**
   * and never on a focused chain: hiding a predecessor must not make something startable.
   */
  canStartNow: boolean;
  /** An arrow *into* this from work that has been put away — one the forecast drops. */
  waitsOnPutAway: boolean;
  /** An arrow from this *into* work that has been put away. */
  holdsUpPutAway: boolean;
  /** Live arrows only, both directions. */
  waitingOn: Dependency[];
  holdingUp: Dependency[];
};

export type Arrangement = {
  kind: 'ok';
  /** Every live task by identifier, ordered or not. */
  tasks: Map<string, OrderedTask>;
  /** Ordered work: step by step, each step ordered by `untangle`. */
  steps: OrderedTask[][];
  /** Live work with no live arrow at all. */
  notOrdered: OrderedTask[];
  /** Arrows between two live tasks, which is the only kind that orders anything. */
  edges: Dependency[];
};

/** The data contained a loop, which the server's own checks make unreachable. */
export type Cyclic = { kind: 'cycle' };

/** A task's chain: everything it waits on, everything waiting on it, and the task. */
export type Chain = { steps: OrderedTask[][]; edges: Dependency[] };

/**
 * The step of every node, by longest path, for the nodes that can be placed.
 *
 * Kahn's algorithm, O(nodes + arrows). A node on a loop is never released, so it is absent
 * from the result — which is how the caller notices a loop without this function having a
 * branch for it.
 */
function layers(ids: string[], edges: Dependency[]): Map<string, number> {
  const waiting = new Map<string, number>(ids.map((id) => [id, 0]));
  const successors = new Map<string, string[]>();
  for (const edge of edges) {
    waiting.set(
      edge.successorItemId,
      (waiting.get(edge.successorItemId) as number) + 1
    );
    successors.set(edge.predecessorItemId, [
      ...(successors.get(edge.predecessorItemId) ?? []),
      edge.successorItemId
    ]);
  }
  const step = new Map<string, number>();
  const ready = ids.filter((id) => waiting.get(id) === 0);
  for (const id of ready) {
    step.set(id, 0);
  }
  // `ready` grows while it is walked, which is the queue.
  for (let at = 0; at < ready.length; at++) {
    const id = ready[at];
    for (const next of successors.get(id) ?? []) {
      const placed = step.get(id) as number;
      step.set(next, Math.max(step.get(next) ?? 0, placed + 1));
      const left = (waiting.get(next) as number) - 1;
      waiting.set(next, left);
      if (left === 0) {
        ready.push(next);
      }
    }
  }
  return step;
}

/** Tasks grouped by step, each step starting in the server's order, then untangled. */
function grouped(
  tasks: OrderedTask[],
  step: Map<string, number>,
  edges: Dependency[]
): OrderedTask[][] {
  const steps: OrderedTask[][] = [];
  for (const task of [...tasks].sort((a, b) => a.index - b.index)) {
    const at = step.get(task.item.id) as number;
    while (steps.length <= at) {
      steps.push([]);
    }
    steps[at].push(task);
  }
  return untangle(steps, edges, step);
}

/** Barycentre sweeps tried at most before settling for the best order seen. */
const SWEEPS = 8;
/**
 * A sweep that removes less than this share of the crossings ends the search. Measured on
 * five-hundred-task plans, the first sweep removes about 70% of them and each later one 1-2%
 * or nothing, so the last sweeps were most of the time and almost none of the effect.
 */
const WORTH_SWEEPING = 0.01;

/**
 * Reorder each step so that fewer arrows cross: the barycentre heuristic of a layered
 * (Sugiyama) drawing.
 *
 * **It reduces crossings and cannot promise none.** Some graphs cannot be drawn in steps
 * without a crossing, and the fewest possible is NP-hard to find even between two steps.
 * Each sweep sorts a step by the average position of its neighbours in the step it was just
 * compared with, down and then up; the best order seen is kept, and the server's order is the
 * first one counted, so the result is never worse than where it started. Sweeping stops once
 * one stops paying (`WORTH_SWEEPING`).
 *
 * **An arrow spanning several steps is walked through a waypoint in each step it passes**, so
 * it is weighed against the arrows it actually meets on the way. The waypoints decide the
 * order and are then dropped; the drawing does not route around them.
 *
 * Ties keep the current order (the sort is stable) and nothing is random, so the same plan
 * always draws the same way, and a plan with nothing to uncross keeps the server's order.
 */
function untangle(
  steps: OrderedTask[][],
  edges: Dependency[],
  step: Map<string, number>
): OrderedTask[][] {
  // Every node is a number: a task is its place in `tasks`, a waypoint one after the last.
  // At five hundred tasks there are tens of thousands of waypoints, and looking each one up
  // by a string key was most of the cost.
  const tasks = steps.flat();
  const node = new Map(tasks.map((task, n) => [task.item.id, n]));
  const rows = steps.map((row) =>
    row.map((task) => node.get(task.item.id) as number)
  );
  const above: number[][] = tasks.map(() => []);
  const below: number[][] = tasks.map(() => []);
  for (const edge of edges) {
    let from = node.get(edge.predecessorItemId) as number;
    const end = step.get(edge.successorItemId) as number;
    for (
      let at = (step.get(edge.predecessorItemId) as number) + 1;
      at < end;
      at++
    ) {
      const via = above.length;
      above.push([from]);
      below.push([]);
      below[from].push(via);
      rows[at].push(via);
      from = via;
    }
    const to = node.get(edge.successorItemId) as number;
    below[from].push(to);
    above[to].push(from);
  }

  const place: number[] = new Array<number>(above.length);
  const settle = (row: number[]) => row.forEach((n, at) => (place[n] = at));
  rows.forEach(settle);

  /**
   * Arrows crossing between each step and the next. Two cross when their ends swap sides, so
   * with the arrows sorted by where they leave, the crossings are the inversions in where they
   * arrive — counted by merge sort rather than pair by pair, which at five hundred tasks is
   * the difference between milliseconds and a frozen screen.
   */
  const crossings = () => {
    let count = 0;
    for (const row of rows) {
      const arrivals: number[] = [];
      // The row is already in order of place, so sorting each node's own arrivals is
      // enough to put every arrow in order of where it leaves and then where it arrives.
      for (const n of row) {
        const ends = below[n].map((to) => place[to]).sort((a, b) => a - b);
        for (const end of ends) {
          arrivals.push(end);
        }
      }
      count += inversions(arrivals);
    }
    return count;
  };

  /** A step sorted by where its neighbours sit; one with none keeps its own place. */
  const reorder = (at: number, neighbours: number[][]) => {
    const keyed = rows[at].map((n, own) => {
      const near = neighbours[n];
      let sum = 0;
      for (const other of near) {
        sum += place[other];
      }
      return { n, key: near.length === 0 ? own : sum / near.length };
    });
    keyed.sort((a, b) => a.key - b.key);
    rows[at] = keyed.map((entry) => entry.n);
    settle(rows[at]);
  };

  let best = rows.map((row) => [...row]);
  let fewest = crossings();
  for (let sweep = 0; sweep < SWEEPS && fewest > 0; sweep++) {
    for (let at = 1; at < rows.length; at++) {
      reorder(at, above);
    }
    for (let at = rows.length - 2; at >= 0; at--) {
      reorder(at, below);
    }
    const count = crossings();
    const enough = count < fewest * (1 - WORTH_SWEEPING);
    if (count < fewest) {
      fewest = count;
      best = rows.map((row) => [...row]);
    }
    if (!enough) {
      break;
    }
  }

  return best.map((row) =>
    row.filter((n) => n < tasks.length).map((n) => tasks[n])
  );
}

/** Pairs `i < j` with `values[i] > values[j]`, by merge sort. Sorts `values` as it goes. */
function inversions(values: number[]): number {
  if (values.length < 2) {
    return 0;
  }
  const half = values.length >> 1;
  const left = values.slice(0, half);
  const right = values.slice(half);
  let count = inversions(left) + inversions(right);
  let i = 0;
  let j = 0;
  let k = 0;
  while (i < left.length && j < right.length) {
    if (left[i] <= right[j]) {
      values[k++] = left[i++];
    } else {
      values[k++] = right[j++];
      count += left.length - i;
    }
  }
  while (i < left.length) {
    values[k++] = left[i++];
  }
  while (j < right.length) {
    values[k++] = right[j++];
  }
  return count;
}

/**
 * Lay a plan's live work out in steps.
 *
 * @param items live work only, in the order the server listed it
 * @param dependencies every arrow in the plan, including those at work since put away —
 * the server never deletes one, so an end that is not among the items is put-away work
 * @param estimatedIds the work somebody has estimated
 */
export function arrange(
  items: WorkItem[],
  dependencies: Dependency[],
  estimatedIds: Set<string>
): Arrangement | Cyclic {
  const live = new Set(items.map((item) => item.id));
  const edges = dependencies.filter(
    (edge) => live.has(edge.predecessorItemId) && live.has(edge.successorItemId)
  );

  const tasks = new Map<string, OrderedTask>(
    items.map((item, index) => [
      item.id,
      {
        item,
        index,
        estimated: estimatedIds.has(item.id),
        canStartNow: false,
        waitsOnPutAway: false,
        holdsUpPutAway: false,
        waitingOn: [],
        holdingUp: []
      }
    ])
  );
  for (const edge of dependencies) {
    const before = tasks.get(edge.predecessorItemId);
    const after = tasks.get(edge.successorItemId);
    if (before && after) {
      before.holdingUp.push(edge);
      after.waitingOn.push(edge);
    } else if (after) {
      after.waitsOnPutAway = true;
    } else if (before) {
      before.holdsUpPutAway = true;
    }
  }
  for (const task of tasks.values()) {
    task.canStartNow =
      task.item.status === 'NOT_STARTED' &&
      task.waitingOn.every(
        (edge) => tasks.get(edge.predecessorItemId)?.item.status === 'DONE'
      );
  }

  const all = [...tasks.values()];
  const ordered = all.filter(
    (task) => task.waitingOn.length > 0 || task.holdingUp.length > 0
  );
  const step = layers(
    ordered.map((task) => task.item.id),
    edges
  );
  if (step.size < ordered.length) {
    return { kind: 'cycle' };
  }
  return {
    kind: 'ok',
    tasks,
    steps: grouped(ordered, step, edges),
    notOrdered: all.filter(
      (task) => task.waitingOn.length === 0 && task.holdingUp.length === 0
    ),
    edges
  };
}

/**
 * One task's chain, laid out on its own so that it reads compactly.
 *
 * Upstream and downstream are walked separately: something that merely shares a successor
 * with the focus is not part of what the focus waits on or holds up. Null for a task the
 * plan does not hold live, which the caller reports instead of drawing.
 */
export function chainThrough(
  arrangement: Arrangement,
  focusId: string
): Chain | null {
  const focus = arrangement.tasks.get(focusId);
  if (!focus) {
    return null;
  }
  const inChain = new Set<string>([focusId]);
  for (const [side, far] of [
    ['waitingOn', 'predecessorItemId'],
    ['holdingUp', 'successorItemId']
  ] as const) {
    const pending = [focus];
    while (pending.length > 0) {
      const task = pending.pop() as OrderedTask;
      for (const edge of task[side]) {
        const next = arrangement.tasks.get(edge[far]) as OrderedTask;
        if (!inChain.has(next.item.id)) {
          inChain.add(next.item.id);
          pending.push(next);
        }
      }
    }
  }
  const edges = arrangement.edges.filter(
    (edge) =>
      inChain.has(edge.predecessorItemId) && inChain.has(edge.successorItemId)
  );
  const members = [...inChain].map(
    (id) => arrangement.tasks.get(id) as OrderedTask
  );
  return {
    steps: grouped(members, layers([...inChain], edges), edges),
    edges
  };
}

// The drawing ---------------------------------------------------------------

/** Pixel sizes, fixed so that nothing needs measuring and jsdom needs no layout. */
export const BOX_WIDTH = 190;
export const BOX_HEIGHT = 56;
/** Between two steps: tall enough for an arrow to bend and a lag to be written on it. */
export const GAP_STEP = 64;
/** Between two tasks in one step. */
export const GAP_ACROSS = 16;
export const PAD = 16;

export type Point = { x: number; y: number };

export type Curve = {
  id: string;
  from: Point;
  to: Point;
  lagHours: number;
  /** Where the lag is written: just above the arrowhead, to its right. */
  label: Point;
};

export type Drawn = {
  boxes: Map<string, Point & { step: number }>;
  curves: Curve[];
  width: number;
  height: number;
};

/**
 * Where everything in a drawing goes.
 *
 * A box sits at its step's row and its place in that step, so y grows with the step and
 * every arrow runs top to bottom — leaving a box's bottom edge and entering the next one's
 * top. **Several arrows on one task do not meet at one point**: each leaves and enters at its
 * own port, spread across the box's width and ordered by where the other end is, so they fan
 * rather than cross at the edge.
 */
export function layout(steps: OrderedTask[][], edges: Dependency[]): Drawn {
  const boxes = new Map<string, Point & { step: number }>();
  let widest = 0;
  steps.forEach((tasks, step) => {
    widest = Math.max(widest, tasks.length);
    tasks.forEach((task, place) => {
      boxes.set(task.item.id, {
        step,
        x: PAD + place * (BOX_WIDTH + GAP_ACROSS),
        y: PAD + step * (BOX_HEIGHT + GAP_STEP)
      });
    });
  });

  const at = (id: string) => boxes.get(id) as Point;
  const portsOf = (end: 'predecessorItemId' | 'successorItemId') => {
    const far =
      end === 'predecessorItemId' ? 'successorItemId' : 'predecessorItemId';
    const own = new Map<string, Dependency[]>();
    for (const edge of edges) {
      own.set(edge[end], [...(own.get(edge[end]) ?? []), edge]);
    }
    const port = new Map<string, number>();
    for (const [id, group] of own) {
      [...group]
        .sort((a, b) => at(a[far]).x - at(b[far]).x || a.id.localeCompare(b.id))
        .forEach((edge, k) =>
          port.set(
            edge.id,
            at(id).x + (BOX_WIDTH * (k + 1)) / (group.length + 1)
          )
        );
    }
    return port;
  };
  const leaving = portsOf('predecessorItemId');
  const entering = portsOf('successorItemId');

  const curves = edges.map((edge) => {
    const from = {
      x: leaving.get(edge.id) as number,
      y: at(edge.predecessorItemId).y + BOX_HEIGHT
    };
    const to = {
      x: entering.get(edge.id) as number,
      y: at(edge.successorItemId).y
    };
    return {
      id: edge.id,
      from,
      to,
      lagHours: edge.lagHours,
      label: { x: to.x + 4, y: to.y - 8 }
    };
  });

  return {
    boxes,
    curves,
    width: PAD * 2 + widest * BOX_WIDTH + Math.max(widest - 1, 0) * GAP_ACROSS,
    height:
      PAD * 2 +
      steps.length * BOX_HEIGHT +
      Math.max(steps.length - 1, 0) * GAP_STEP
  };
}
