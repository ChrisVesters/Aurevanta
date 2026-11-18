import { describe, expect, it } from 'vitest';
import {
  BOX_HEIGHT,
  BOX_WIDTH,
  PAD,
  arrange,
  chainThrough,
  layout
} from './order';
import type { Arrangement } from './order';
import type { Dependency, WorkItem, WorkItemStatus } from './types';

function item(
  id: string,
  status: WorkItemStatus = 'NOT_STARTED',
  title = id
): WorkItem {
  return {
    id,
    projectId: 'p',
    title,
    description: null,
    createdAt: '2026-08-13T08:00:00Z',
    archivedAt: null,
    status,
    startedOn: null,
    completedOn: null,
    actualEffortHours: null
  };
}

function arrow(from: string, to: string, lagHours = 0): Dependency {
  return {
    id: `${from}>${to}`,
    predecessorItemId: from,
    successorItemId: to,
    lagHours,
    createdAt: '2026-08-13T08:00:00Z'
  };
}

/** The ids of each step, which is what most of these cases are about. */
function ids(steps: { item: WorkItem }[][]) {
  return steps.map((step) => step.map((task) => task.item.id));
}

function ok(
  items: WorkItem[],
  dependencies: Dependency[],
  estimated: string[] = []
): Arrangement {
  const arranged = arrange(items, dependencies, new Set(estimated));
  if (arranged.kind !== 'ok') {
    throw new Error('expected an arrangement');
  }
  return arranged;
}

describe('arrange', () => {
  it('puts a chain one step at a time', () => {
    const plan = ok(
      [item('A'), item('B'), item('C')],
      [arrow('A', 'B'), arrow('B', 'C')]
    );

    expect(ids(plan.steps)).toEqual([['A'], ['B'], ['C']]);
    expect(plan.edges).toHaveLength(2);
  });

  /** The longest chain into C decides, not the shortest. */
  it('places a task after its longest chain', () => {
    const plan = ok(
      [item('A'), item('B'), item('C')],
      [arrow('A', 'B'), arrow('A', 'C'), arrow('B', 'C')]
    );

    expect(ids(plan.steps)).toEqual([['A'], ['B'], ['C']]);
  });

  it('keeps the server order inside a step, and never sorts by title', () => {
    const plan = ok(
      [
        item('Z', 'NOT_STARTED', 'Zebra'),
        item('Y', 'NOT_STARTED', 'Aardvark'),
        item('R'),
        item('S')
      ],
      [arrow('R', 'Z'), arrow('R', 'Y'), arrow('S', 'Y')]
    );

    expect(ids(plan.steps)).toEqual([
      ['R', 'S'],
      ['Z', 'Y']
    ]);
  });

  /** Two arrows that would cross in the server's order: the lower step swaps. */
  it('reorders a step so that arrows do not cross', () => {
    const plan = ok(
      [item('A'), item('B'), item('C'), item('D')],
      [arrow('A', 'D'), arrow('B', 'C')]
    );

    expect(ids(plan.steps)).toEqual([
      ['A', 'B'],
      ['D', 'C']
    ]);
  });

  /** A→C passes step 2 on its way, and is weighed against B→X where it does. */
  it('weighs an arrow spanning several steps where it passes', () => {
    const plan = ok(
      [item('A'), item('B'), item('X'), item('D'), item('C')],
      [arrow('B', 'X'), arrow('X', 'D'), arrow('X', 'C'), arrow('A', 'C')]
    );

    expect(ids(plan.steps)).toEqual([['A', 'B'], ['X'], ['C', 'D']]);
  });

  /** Every arrow between two pairs: one crossing whatever the order, so nothing moves. */
  it('keeps the server order where no order crosses less', () => {
    const plan = ok(
      [item('A'), item('B'), item('C'), item('D')],
      [arrow('A', 'C'), arrow('A', 'D'), arrow('B', 'C'), arrow('B', 'D')]
    );

    expect(ids(plan.steps)).toEqual([
      ['A', 'B'],
      ['C', 'D']
    ]);
  });

  it('uncrosses a focused chain as well', () => {
    const plan = ok(
      [item('F'), item('A'), item('B'), item('C'), item('D')],
      [arrow('F', 'A'), arrow('F', 'B'), arrow('A', 'D'), arrow('B', 'C')]
    );

    expect(ids(chainThrough(plan, 'F')?.steps ?? [])).toEqual([
      ['F'],
      ['A', 'B'],
      ['D', 'C']
    ]);
  });

  it('collects work with no arrow at all, in the server order', () => {
    const plan = ok(
      [item('C'), item('A'), item('B'), item('D')],
      [arrow('A', 'B')]
    );

    expect(ids(plan.steps)).toEqual([['A'], ['B']]);
    expect(plan.notOrdered.map((task) => task.item.id)).toEqual(['C', 'D']);
  });

  it('has nothing to show for an empty plan', () => {
    const plan = ok([], []);

    expect(plan.steps).toEqual([]);
    expect(plan.notOrdered).toEqual([]);
    expect(plan.edges).toEqual([]);
  });

  describe('work that has been put away', () => {
    it('is not an arrow, and the live end says so', () => {
      const plan = ok(
        [item('A'), item('B'), item('C')],
        [arrow('GONE', 'A'), arrow('A', 'B'), arrow('B', 'AWAY')]
      );

      expect(plan.edges.map((edge) => edge.id)).toEqual(['A>B']);
      expect(plan.tasks.get('A')?.waitsOnPutAway).toBe(true);
      expect(plan.tasks.get('A')?.holdsUpPutAway).toBe(false);
      expect(plan.tasks.get('B')?.holdsUpPutAway).toBe(true);
      expect(plan.tasks.get('C')?.holdsUpPutAway).toBe(false);
      expect(ids(plan.steps)).toEqual([['A'], ['B']]);
    });

    /** Both of its arrows touch work that is gone, so nothing orders it. */
    it('leaves a task whose only arrows touch it among the not ordered', () => {
      const plan = ok([item('B')], [arrow('GONE', 'B'), arrow('B', 'AWAY')]);

      expect(plan.steps).toEqual([]);
      expect(plan.notOrdered.map((task) => task.item.id)).toEqual(['B']);
      expect(plan.notOrdered[0].waitsOnPutAway).toBe(true);
      expect(plan.notOrdered[0].holdsUpPutAway).toBe(true);
    });

    it('ignores an arrow between two tasks that are both gone', () => {
      const plan = ok([item('A')], [arrow('X', 'Y')]);

      expect(plan.edges).toEqual([]);
      expect(plan.tasks.get('A')?.waitsOnPutAway).toBe(false);
    });
  });

  describe('can start now', () => {
    const start = (items: WorkItem[], deps: Dependency[], id: string) =>
      ok(items, deps).tasks.get(id)?.canStartNow;

    it('is true for work that waits on nothing and has not started', () => {
      expect(start([item('A')], [], 'A')).toBe(true);
    });

    it('is true when everything it waits on is done', () => {
      expect(
        start([item('A', 'DONE'), item('B')], [arrow('A', 'B')], 'B')
      ).toBe(true);
    });

    it('is false while something it waits on is in progress or untouched', () => {
      expect(
        start([item('A', 'IN_PROGRESS'), item('B')], [arrow('A', 'B')], 'B')
      ).toBe(false);
      expect(start([item('A'), item('B')], [arrow('A', 'B')], 'B')).toBe(false);
    });

    it('is false for work already under way or finished', () => {
      expect(start([item('A', 'IN_PROGRESS')], [], 'A')).toBe(false);
      expect(start([item('A', 'DONE')], [], 'A')).toBe(false);
    });

    it('is not held up by an arrow from work that has been put away', () => {
      expect(start([item('B')], [arrow('GONE', 'B')], 'B')).toBe(true);
    });

    it('needs every one of several predecessors', () => {
      const items = [item('A', 'DONE'), item('B', 'IN_PROGRESS'), item('C')];
      expect(start(items, [arrow('A', 'C'), arrow('B', 'C')], 'C')).toBe(false);
    });
  });

  it('records which work somebody has estimated', () => {
    const plan = ok([item('A'), item('B')], [], ['A']);

    expect(plan.tasks.get('A')?.estimated).toBe(true);
    expect(plan.tasks.get('B')?.estimated).toBe(false);
  });

  it('refuses a loop rather than laying out half of it', () => {
    expect(
      arrange(
        [item('A'), item('B'), item('C')],
        [arrow('A', 'B'), arrow('B', 'C'), arrow('C', 'B')],
        new Set()
      )
    ).toEqual({ kind: 'cycle' });
  });

  /**
   * <strong>The invariant that matters, over a graph nobody wrote by hand.</strong> A layering
   * bug puts a task to the left of something it waits on and the picture still looks like a
   * picture; only a check over every arrow notices.
   */
  it('puts every task after everything it waits on, at the plan ceiling', () => {
    let seed = 12345;
    const random = (below: number) => {
      seed = (seed * 1103515245 + 12345) % 2147483648;
      return Math.floor(seed / 65536) % below;
    };
    const items = Array.from({ length: 500 }, (_, n) => item(`t${n}`));
    const deps = new Map<string, Dependency>();
    while (deps.size < 1500) {
      const a = random(500);
      const b = random(500);
      if (a !== b) {
        const edge = arrow(`t${Math.min(a, b)}`, `t${Math.max(a, b)}`);
        deps.set(edge.id, edge);
      }
    }

    const started = performance.now();
    const plan = ok(items, [...deps.values()]);
    const chain = chainThrough(plan, 't250');
    const elapsed = performance.now() - started;

    const stepOf = new Map<string, number>();
    plan.steps.forEach((step, at) =>
      step.forEach((task) => stepOf.set(task.item.id, at))
    );
    for (const edge of plan.edges) {
      expect(stepOf.get(edge.predecessorItemId) as number).toBeLessThan(
        stepOf.get(edge.successorItemId) as number
      );
    }
    expect(plan.steps.flat().length + plan.notOrdered.length).toBe(500);
    // Fewer crossings between neighbouring steps than the server's order drew.
    const crossings = (place: Map<string, number>) => {
      const segments = plan.edges
        .filter(
          (edge) =>
            (stepOf.get(edge.successorItemId) as number) -
              (stepOf.get(edge.predecessorItemId) as number) ===
            1
        )
        .map((edge) => ({
          step: stepOf.get(edge.predecessorItemId) as number,
          from: place.get(edge.predecessorItemId) as number,
          to: place.get(edge.successorItemId) as number
        }));
      let count = 0;
      for (const a of segments) {
        for (const b of segments) {
          if (a.step === b.step && a.from < b.from && a.to > b.to) {
            count++;
          }
        }
      }
      return count;
    };
    const drawn = new Map<string, number>();
    plan.steps.forEach((step) =>
      step.forEach((task, at) => drawn.set(task.item.id, at))
    );
    const listed = new Map<string, number>();
    plan.steps.forEach((step) =>
      [...step]
        .sort((a, b) => a.index - b.index)
        .forEach((task, at) => listed.set(task.item.id, at))
    );
    expect(crossings(drawn)).toBeLessThan(crossings(listed));
    expect(chain?.steps.flat().length).toBeGreaterThan(1);
    // A guard against an accidental quadratic, not a budget.
    expect(elapsed).toBeLessThan(2000);
  });
});

describe('chainThrough', () => {
  const plan = ok(
    [item('A'), item('B'), item('C'), item('D'), item('E'), item('X')],
    [arrow('A', 'B'), arrow('B', 'C'), arrow('D', 'E'), arrow('X', 'C')]
  );

  it('is what the task waits on, what waits on it, and itself', () => {
    const chain = chainThrough(plan, 'B');

    expect(ids(chain?.steps ?? [])).toEqual([['A'], ['B'], ['C']]);
    expect(chain?.edges.map((edge) => edge.id)).toEqual(['A>B', 'B>C']);
  });

  it('does not take work that only shares a successor with it', () => {
    const everything = chainThrough(plan, 'B')
      ?.steps.flat()
      .map((task) => task.item.id);

    expect(everything).not.toContain('X');
  });

  it('follows a diamond in both directions without repeating anything', () => {
    const diamond = ok(
      [item('A'), item('B'), item('C'), item('D')],
      [arrow('A', 'B'), arrow('A', 'C'), arrow('B', 'D'), arrow('C', 'D')]
    );

    expect(ids(chainThrough(diamond, 'A')?.steps ?? [])).toEqual([
      ['A'],
      ['B', 'C'],
      ['D']
    ]);
    expect(ids(chainThrough(diamond, 'D')?.steps ?? [])).toEqual([
      ['A'],
      ['B', 'C'],
      ['D']
    ]);
  });

  it('is a chain of one for live work with no arrows', () => {
    const lonely = ok([item('A'), item('B')], []);

    const chain = chainThrough(lonely, 'B');

    expect(ids(chain?.steps ?? [])).toEqual([['B']]);
    expect(chain?.edges).toEqual([]);
  });

  it('keeps what the whole plan says about each task', () => {
    const chain = chainThrough(plan, 'C');

    expect(
      chain?.steps.flat().find((t) => t.item.id === 'C')?.waitingOn
    ).toHaveLength(2);
  });

  it('is nothing for a task the plan does not hold live', () => {
    expect(chainThrough(plan, 'GONE')).toBeNull();
  });
});

describe('layout', () => {
  const plan = ok(
    [item('A'), item('B'), item('C'), item('D')],
    [arrow('A', 'B', 16), arrow('A', 'C'), arrow('B', 'D'), arrow('C', 'D')]
  );
  const drawn = layout(plan.steps, plan.edges);

  it('puts a task below everything it waits on', () => {
    for (const curve of drawn.curves) {
      expect(curve.to.y).toBeGreaterThan(curve.from.y);
    }
    const at = (id: string) => drawn.boxes.get(id)?.y as number;
    expect(at('B')).toBeGreaterThan(at('A'));
    expect(at('D')).toBeGreaterThan(at('B'));
  });

  it('never lets two boxes overlap', () => {
    const boxes = [...drawn.boxes.values()];
    for (const a of boxes) {
      for (const b of boxes) {
        if (a !== b) {
          const apart =
            a.x + BOX_WIDTH <= b.x ||
            b.x + BOX_WIDTH <= a.x ||
            a.y + BOX_HEIGHT <= b.y ||
            b.y + BOX_HEIGHT <= a.y;
          expect(apart).toBe(true);
        }
      }
    }
  });

  it('leaves from the bottom edge and enters at the top', () => {
    const at = (id: string) => drawn.boxes.get(id) as { y: number };
    for (const curve of drawn.curves) {
      const [from, to] = curve.id.split('>');
      expect(curve.from.y).toBe(at(from).y + BOX_HEIGHT);
      expect(curve.to.y).toBe(at(to).y);
    }
  });

  it('spreads arrows leaving or entering one task across its width', () => {
    const a = drawn.boxes.get('A') as { x: number };
    const leaving = drawn.curves.filter((c) => c.id.startsWith('A>'));
    expect(new Set(leaving.map((c) => c.from.x)).size).toBe(2);
    for (const curve of leaving) {
      expect(curve.from.x).toBeGreaterThan(a.x);
      expect(curve.from.x).toBeLessThan(a.x + BOX_WIDTH);
    }
    const entering = drawn.curves.filter((c) => c.id.endsWith('>D'));
    expect(new Set(entering.map((c) => c.to.x)).size).toBe(2);
  });

  it('writes a lag beside the arrowhead of its own arrow', () => {
    const lagged = drawn.curves.find((c) => c.id === 'A>B');
    expect(lagged?.lagHours).toBe(16);
    expect(lagged?.label.y).toBeLessThan(lagged?.to.y as number);
    expect(lagged?.label.x).toBeGreaterThan(lagged?.to.x as number);
  });

  it('is big enough for every box', () => {
    for (const box of drawn.boxes.values()) {
      expect(box.x + BOX_WIDTH + PAD).toBeLessThanOrEqual(drawn.width);
      expect(box.y + BOX_HEIGHT + PAD).toBeLessThanOrEqual(drawn.height);
    }
  });

  it('answers the same for the same input', () => {
    expect(layout(plan.steps, plan.edges)).toEqual(drawn);
  });

  it('has no size to speak of for nothing', () => {
    const none = layout([], []);

    expect(none.width).toBe(PAD * 2);
    expect(none.height).toBe(PAD * 2);
    expect(none.curves).toEqual([]);
  });
});
