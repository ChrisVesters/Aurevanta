import { describe, expect, it } from 'vitest';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useLocation } from 'react-router';
import { OrderView } from './OrderView';
import {
  ACCOUNT,
  ESTIMATES,
  PROJECTS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Dependency, Estimate, WorkItem, WorkItemStatus } from './types';

const PROJECT_ID = PROJECTS[0].id;
const ITEMS_URL = `/api/projects/${PROJECT_ID}/items`;
const ESTIMATES_URL = `/api/projects/${PROJECT_ID}/estimates`;
const DEPENDENCIES_URL = `/api/projects/${PROJECT_ID}/dependencies`;

function task(
  id: string,
  title: string,
  status: WorkItemStatus = 'NOT_STARTED'
): WorkItem {
  return {
    id,
    projectId: PROJECT_ID,
    title,
    description: null,
    createdAt: '2026-08-13T08:00:00Z',
    archivedAt: null,
    status,
    // Real dates on the work that has them, so a screen that printed one would fail.
    startedOn: status === 'NOT_STARTED' ? null : '2026-08-10',
    completedOn: status === 'DONE' ? '2026-08-12' : null,
    actualEffortHours: status === 'DONE' ? 9 : null
  };
}

function arrow(from: string, to: string, lagHours = 0): Dependency {
  return {
    id: `${from}-${to}`,
    predecessorItemId: from,
    successorItemId: to,
    lagHours,
    createdAt: '2026-08-13T08:00:00Z'
  };
}

/** Only the first task is estimated, and with figures a screen could be tempted to print. */
function estimate(itemId: string): Estimate {
  return { ...ESTIMATES[0], itemId };
}

/**
 * A chain, a pair apart from it, and one task nothing is joined to:
 * Schema -> API (waits 16 hours) -> Runbook, and Plan -> Launch, and Spare.
 */
const SCHEMA = task('a', 'Design the schema', 'DONE');
const API = task('b', 'Build the API');
const RUNBOOK = task('c', 'Write the runbook', 'IN_PROGRESS');
const PLAN = task('d', 'Plan the launch');
const LAUNCH = task('e', 'Run the launch');
const SPARE = task('f', 'Tidy the wiki');
const ITEMS = [SCHEMA, API, RUNBOOK, PLAN, LAUNCH, SPARE];
const ARROWS = [arrow('a', 'b', 16), arrow('b', 'c'), arrow('d', 'e')];

describe('OrderView', () => {
  const fetchMock = mockFetch();

  /** Answered by URL, so a read cannot be handed another read's answer. */
  function answer(
    items: WorkItem[] = ITEMS,
    dependencies: Dependency[] = ARROWS,
    estimates: Estimate[] = [estimate('a')],
    failing?: string
  ) {
    fetchMock.mockImplementation((url: string) => {
      if (url === failing) {
        return Promise.resolve(
          jsonResponse(404, { code: 'project_not_found' })
        );
      }
      return Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ITEMS_URL
            ? jsonResponse(200, items)
            : url === DEPENDENCIES_URL
              ? jsonResponse(200, dependencies)
              : url === ESTIMATES_URL
                ? jsonResponse(200, estimates)
                : jsonResponse(404, { code: 'not_found' })
      );
    });
  }

  function Where() {
    const location = useLocation();
    return (
      <span data-testid="where">{location.pathname + location.search}</span>
    );
  }

  async function open(
    route = `/app/projects/${PROJECT_ID}?tab=order`,
    ...rest: Parameters<typeof answer>
  ) {
    storeAccessToken();
    answer(...rest);
    const view = renderRouted(
      <>
        <Where />
        <Routes>
          <Route
            path="/app/projects/:id"
            element={<OrderView projectId={PROJECT_ID} />}
          />
          <Route path="*" element={null} />
        </Routes>
      </>,
      { route }
    );
    await screen.findByRole('status').catch(() => undefined);
    return view;
  }

  const steps = () =>
    screen.findByRole('list', { name: 'The order, step by step' });

  const where = () => screen.getByTestId('where').textContent;

  const entry = (list: HTMLElement, title: string) =>
    within(list)
      .getByRole('link', { name: title })
      .closest('li') as HTMLElement;

  // What each task says ---------------------------------------------------------

  it('lays the work out in steps, each task after what it waits on', async () => {
    await open();
    const list = await steps();

    expect(entry(list, 'Design the schema')).toHaveTextContent('Step 1');
    expect(entry(list, 'Plan the launch')).toHaveTextContent('Step 1');
    expect(entry(list, 'Build the API')).toHaveTextContent('Step 2');
    expect(entry(list, 'Run the launch')).toHaveTextContent('Step 2');
    expect(entry(list, 'Write the runbook')).toHaveTextContent('Step 3');
    expect(
      within(list)
        .getAllByRole('link')
        .map((link) => link.textContent)
    ).toEqual([
      'Design the schema',
      'Plan the launch',
      'Build the API',
      'Run the launch',
      'Write the runbook'
    ]);
  });

  /** AC2: the longest chain into a task decides its step, not the shortest. */
  it('puts a task after its longest chain', async () => {
    await open(
      undefined,
      [SCHEMA, API, RUNBOOK],
      [arrow('a', 'b'), arrow('a', 'c'), arrow('b', 'c')]
    );
    const list = await steps();

    expect(entry(list, 'Write the runbook')).toHaveTextContent('Step 3');
  });

  it('says both directions on every task, with a wait only where there is one', async () => {
    await open();
    const list = await steps();

    expect(entry(list, 'Build the API')).toHaveTextContent(
      'Waiting on Design the schema with a 16-hour wait'
    );
    expect(entry(list, 'Build the API')).toHaveTextContent(
      'Must finish before Write the runbook'
    );
    expect(entry(list, 'Build the API')).not.toHaveTextContent(
      'Write the runbook (then'
    );
    expect(entry(list, 'Design the schema')).toHaveTextContent(
      'Must finish before Build the API'
    );
  });

  it('links every task to its own page', async () => {
    await open();
    const list = await steps();

    const link = within(list).getByRole('link', { name: 'Build the API' });
    expect(link).toHaveAttribute('href', '/app/items/b');

    link.focus();
    await userEvent.keyboard('{Enter}');

    expect(where()).toBe('/app/items/b');
  });

  /** AC5: nothing here writes, and the only control is the one that chooses what to look at. */
  it('changes nothing and offers no way to', async () => {
    await open();
    await steps();

    expect(screen.queryAllByRole('button')).toEqual([]);
    expect(screen.getAllByRole('combobox')).toHaveLength(1);
    expect(screen.queryAllByRole('textbox')).toEqual([]);
    expect(
      fetchMock.mock.calls.filter(
        ([, init]) => init?.method !== undefined && init.method !== 'GET'
      )
    ).toEqual([]);
  });

  /** AC6: no time, and no effort, anywhere — though the work carries both. */
  it('shows no date, no month and no hours but a wait', async () => {
    const { container } = await open();
    await steps();

    const text = container.textContent ?? '';
    expect(text).not.toMatch(/\d{4}-\d{2}-\d{2}/);
    expect(text).not.toMatch(
      /January|February|March|April|May|June|July|August|September|October|November|December/
    );
    // The one wait, said from each end of its arrow and nowhere else.
    expect(text.match(/\d+-hour wait/g)).toEqual([
      '16-hour wait',
      '16-hour wait'
    ]);
  });

  it('says where each task is, in words', async () => {
    await open();
    const list = await steps();

    expect(entry(list, 'Design the schema')).toHaveTextContent('Done');
    expect(entry(list, 'Write the runbook')).toHaveTextContent('In progress');
    expect(entry(list, 'Build the API')).toHaveTextContent('Not started');
  });

  it('marks work nobody has estimated', async () => {
    await open();
    const list = await steps();

    expect(entry(list, 'Design the schema')).not.toHaveTextContent(
      'Not estimated'
    );
    expect(entry(list, 'Build the API')).toHaveTextContent('Not estimated');
  });

  /** Waiting on finished work counts as free; work under way or done has already started. */
  it('marks what can start now', async () => {
    await open();
    const list = await steps();

    expect(entry(list, 'Build the API')).toHaveTextContent('Can start now');
    expect(entry(list, 'Plan the launch')).toHaveTextContent('Can start now');
    expect(entry(list, 'Run the launch')).not.toHaveTextContent(
      'Can start now'
    );
    expect(entry(list, 'Write the runbook')).not.toHaveTextContent(
      'Can start now'
    );
    expect(entry(list, 'Design the schema')).not.toHaveTextContent(
      'Can start now'
    );
  });

  it('collects work with no arrow, with a count', async () => {
    await open();
    await steps();

    expect(
      screen.getByRole('heading', {
        name: 'Not ordered against anything · 1'
      })
    ).toBeInTheDocument();
    const section = screen
      .getByRole('heading', { name: /Not ordered against anything/ })
      .closest('section') as HTMLElement;
    expect(
      within(section).getByRole('link', { name: 'Tidy the wiki' })
    ).toHaveAttribute('href', '/app/items/f');
    expect(section).toHaveTextContent('Not started');
    expect(section).toHaveTextContent('Can start now');
  });

  /** The same box as the drawing's, flowing rather than placed, and saying no more than needed. */
  it('draws work with no arrow as the same boxes, apart from the order', async () => {
    const { container } = await open();
    await steps();

    const loose = container.querySelector('.order-loose') as HTMLElement;
    expect(loose.closest('.order-drawing')).toBeNull();
    const boxes = loose.querySelectorAll('.order-box');
    expect(boxes).toHaveLength(1);
    expect(boxes[0]).toHaveAttribute('data-status', 'NOT_STARTED');
    expect(boxes[0].querySelector('.meta')).toHaveTextContent(
      'Not started · Can start now · Not estimated'
    );
    expect(boxes[0].querySelector('.meta')).not.toHaveTextContent('put-away');
    // Nothing joins it to anything, so there is nothing to read out besides the box.
    expect(boxes[0].querySelector('.visually-hidden')).toBeNull();
  });

  // Put-away work --------------------------------------------------------------

  it('does not draw or order by an arrow from work that has been put away', async () => {
    const { container } = await open(
      undefined,
      [API, RUNBOOK],
      [arrow('gone', 'b'), arrow('b', 'c')]
    );
    const list = await steps();

    expect(entry(list, 'Build the API')).toHaveTextContent(
      'Waiting on a task that has been put away'
    );
    expect(container.querySelectorAll('.curve')).toHaveLength(1);
    // And nothing waits on it, so it is free to start.
    expect(entry(list, 'Build the API')).toHaveTextContent('Can start now');
  });

  it('says so, in words, of a task whose only arrows touch work put away', async () => {
    const { container } = await open(
      undefined,
      [API],
      [arrow('gone', 'b'), arrow('b', 'away')]
    );

    expect(await screen.findByText(/No order has been set yet/)).toBeVisible();
    expect(container.querySelectorAll('.curve')).toHaveLength(0);
    const section = screen
      .getByRole('heading', { name: 'Not ordered against anything · 1' })
      .closest('section') as HTMLElement;
    const box = entry(section, 'Build the API');
    // Shown on the box, beside its status.
    expect(box.querySelector('.meta')).toHaveTextContent(
      'Not started · Can start now · Not estimated · Only arrows to put-away work'
    );
    // And read out, each way round.
    expect(box).toHaveTextContent('Waiting on a task that has been put away');
    expect(box).toHaveTextContent(
      'Must finish before a task that has been put away'
    );
  });

  // Empty and failing ---------------------------------------------------------

  it('says where to set an order when there is none, and still lists the work', async () => {
    await open(undefined, [API, SPARE], []);

    expect(await screen.findByText(/No order has been set yet/)).toBeVisible();
    expect(screen.getByRole('link', { name: 'Build the API' })).toBeVisible();
    expect(screen.getByRole('link', { name: 'Tidy the wiki' })).toBeVisible();
    expect(screen.queryByRole('combobox')).toBeNull();
  });

  it('says there is nothing to show for a plan with no work', async () => {
    await open(undefined, [], []);

    expect(
      await screen.findByText('This plan has no work to show yet.')
    ).toBeVisible();
  });

  it.each([ITEMS_URL, DEPENDENCIES_URL, ESTIMATES_URL])(
    'shows one translated failure and nothing partial when %s will not load',
    async (failing) => {
      await open(undefined, ITEMS, ARROWS, [estimate('a')], failing);

      expect(await screen.findByRole('alert')).toHaveTextContent(
        'That project is no longer in this organisation.'
      );
      expect(screen.queryByRole('list')).toBeNull();
      expect(screen.queryByRole('link')).toBeNull();
    }
  );

  it('refuses a loop rather than drawing half of it', async () => {
    const { container } = await open(
      undefined,
      [API, RUNBOOK],
      [arrow('b', 'c'), arrow('c', 'b')]
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(/form a loop/);
    expect(container.querySelector('.order-drawing')).toBeNull();
    expect(screen.queryByRole('link')).toBeNull();
  });

  it('says it is loading until all three reads have answered', async () => {
    storeAccessToken();
    fetchMock.mockImplementation(() => new Promise(() => undefined));
    renderRouted(<OrderView projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}?tab=order`
    });

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Loading the order…'
    );
  });

  // Focus --------------------------------------------------------------------

  it('shows only the chain through one task, names it, and offers the way back', async () => {
    await open();
    await steps();

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Show the chain through' }),
      'Build the API'
    );

    const list = await steps();
    expect(
      within(list)
        .getAllByRole('link')
        .map((link) => link.textContent)
    ).toEqual(['Design the schema', 'Build the API', 'Write the runbook']);
    expect(screen.getByText(/Showing only Build the API/)).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: /Not ordered/ })).toBeNull();
    expect(where()).toBe(`/app/projects/${PROJECT_ID}?tab=order&focus=b`);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show the whole plan' })
    );

    expect(
      within(await steps()).getByRole('link', { name: 'Plan the launch' })
    ).toBeVisible();
    expect(where()).toBe(`/app/projects/${PROJECT_ID}?tab=order`);
  });

  it('chooses the whole plan again from the same control', async () => {
    await open(`/app/projects/${PROJECT_ID}?tab=order&focus=b`);
    await steps();

    await userEvent.selectOptions(
      screen.getByRole('combobox', { name: 'Show the chain through' }),
      'Whole plan'
    );

    expect(where()).toBe(`/app/projects/${PROJECT_ID}?tab=order`);
    expect(
      within(await steps()).getByRole('link', { name: 'Plan the launch' })
    ).toBeVisible();
  });

  it('reopens the same chain from the address', async () => {
    await open(`/app/projects/${PROJECT_ID}?tab=order&focus=e`);
    const list = await steps();

    expect(
      within(list)
        .getAllByRole('link')
        .map((link) => link.textContent)
    ).toEqual(['Plan the launch', 'Run the launch']);
    expect(screen.getByRole('combobox')).toHaveValue('e');
  });

  /** A chain of one: live work with no arrow is a valid thing to look at. */
  it('focuses on work with no arrows', async () => {
    await open(`/app/projects/${PROJECT_ID}?tab=order&focus=f`);
    const list = await steps();

    expect(
      within(list)
        .getAllByRole('link')
        .map((link) => link.textContent)
    ).toEqual(['Tidy the wiki']);
  });

  it.each(['nothing-like-it', 'gone'])(
    'says so and shows the whole plan when the address names %s, leaving the address alone',
    async (id) => {
      await open(`/app/projects/${PROJECT_ID}?tab=order&focus=${id}`);
      const list = await steps();

      expect(
        screen.getByText(/not in this plan, or has been put away/)
      ).toBeInTheDocument();
      expect(
        within(list).getByRole('link', { name: 'Plan the launch' })
      ).toBeVisible();
      expect(where()).toBe(`/app/projects/${PROJECT_ID}?tab=order&focus=${id}`);
    }
  );

  /** Whether something can start is a fact about the plan, not about what is in view. */
  it('says the same about what can start whether or not the plan is focused', async () => {
    await open(`/app/projects/${PROJECT_ID}?tab=order&focus=b`);
    const list = await steps();

    expect(entry(list, 'Build the API')).toHaveTextContent('Can start now');
    expect(entry(list, 'Write the runbook')).not.toHaveTextContent(
      'Can start now'
    );
  });

  it('still names every neighbour of a task, in the chain or not', async () => {
    await open(
      `/app/projects/${PROJECT_ID}?tab=order&focus=b`,
      [SCHEMA, API, RUNBOOK, PLAN],
      [arrow('a', 'b'), arrow('b', 'c'), arrow('d', 'c')]
    );
    const list = await steps();

    // Plan the launch is not in the chain, but the runbook is waiting on it.
    expect(
      within(list).queryByRole('link', { name: 'Plan the launch' })
    ).toBeNull();
    expect(entry(list, 'Write the runbook')).toHaveTextContent(
      'Waiting on Build the API, Plan the launch'
    );
  });

  /** AC13: Tab reaches every task link in reading order, and the drawing adds no stops. */
  it('is reached by Tab in reading order, one stop per task', async () => {
    await open();
    await steps();

    const stops: (string | null)[] = [];
    for (let n = 0; n < 7; n++) {
      await userEvent.tab();
      stops.push(document.activeElement?.textContent ?? null);
    }

    // The control that chooses what to look at, then the drawing's boxes in step order and
    // the work with no arrow last.
    expect(stops.slice(1)).toEqual([
      'Design the schema',
      'Plan the launch',
      'Build the API',
      'Run the launch',
      'Write the runbook',
      'Tidy the wiki'
    ]);
  });

  /** AC9: a task that is ordered and also touches put-away work says both. */
  it('says a put-away arrow in words on a task that is also ordered', async () => {
    const { container } = await open(
      undefined,
      [API, RUNBOOK],
      [arrow('b', 'c'), arrow('b', 'away')]
    );
    const list = await steps();

    expect(entry(list, 'Build the API')).toHaveTextContent(
      'Must finish before Write the runbook, a task that has been put away'
    );
    expect(container.querySelectorAll('.curve')).toHaveLength(1);
  });

  // The drawing -----------------------------------------------------------------

  /** The list under the drawing is gone, so the drawing is what a screen reader reads. */
  it('hides only the curves and the waits, which the boxes say in words', async () => {
    const { container } = await open();
    await steps();

    const drawing = container.querySelector('.order-drawing') as HTMLElement;
    expect(drawing).not.toHaveAttribute('aria-hidden');
    expect(drawing.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(drawing.querySelector('.order-lag')).toHaveAttribute(
      'aria-hidden',
      'true'
    );
    for (const link of drawing.querySelectorAll('a')) {
      expect(link).not.toHaveAttribute('tabindex');
    }
    // One link per task, and no second copy of any of them.
    expect(screen.getAllByRole('link')).toHaveLength(6);
  });

  it('draws one curve per live arrow, a box per task, and a wait on its own arrow', async () => {
    const { container } = await open();
    await steps();

    const drawing = container.querySelector('.order-drawing') as HTMLElement;
    expect(drawing.querySelectorAll('.curve')).toHaveLength(3);
    expect(drawing.querySelectorAll('.order-box')).toHaveLength(5);
    const waits = drawing.querySelectorAll('.order-lag');
    expect(waits).toHaveLength(1);
    expect(waits[0]).toHaveTextContent('16 h');
    expect(drawing.querySelector('[data-status="DONE"]')).toHaveTextContent(
      'Done'
    );
    expect(
      drawing.querySelector('[data-status="IN_PROGRESS"]')
    ).toHaveTextContent('In progress');
  });

  it('draws only the focused chain', async () => {
    const { container } = await open(
      `/app/projects/${PROJECT_ID}?tab=order&focus=e`
    );
    await steps();

    const drawing = container.querySelector('.order-drawing') as HTMLElement;
    expect(drawing.querySelectorAll('.order-box')).toHaveLength(2);
    expect(drawing.querySelectorAll('.curve')).toHaveLength(1);
  });

  /** AC15: the ceiling, rendered whole. */
  it('renders five hundred tasks and their arrows', async () => {
    const items = Array.from({ length: 500 }, (_, n) =>
      task(`t${n}`, `Task ${n}`)
    );
    const arrows = Array.from({ length: 250 }, (_, n) =>
      arrow(`t${n * 2}`, `t${n * 2 + 1}`)
    );
    const { container } = await open(undefined, items, arrows, []);

    // Role queries walk the accessibility tree and are what is slow at this size, so the
    // boxes are counted from the DOM; a loaded machine must not turn a count into a timeout.
    await screen.findByRole(
      'list',
      { name: 'The order, step by step' },
      { timeout: 20000 }
    );
    expect(container.querySelectorAll('.order-box a')).toHaveLength(500);
    expect(container.querySelectorAll('.order-box')).toHaveLength(500);
    expect(container.querySelectorAll('.curve')).toHaveLength(250);
  }, 30000);
});
