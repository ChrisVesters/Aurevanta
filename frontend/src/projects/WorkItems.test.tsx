import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WorkItems } from './WorkItems';
import {
  ACCOUNT,
  ARCHIVED_WORK_ITEM,
  COLLEAGUES_ESTIMATE,
  DEPENDENCIES,
  ESTIMATES,
  PROJECTS,
  WORK_ITEMS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Dependency, Estimate, WorkItem } from './types';

const PROJECT_ID = PROJECTS[0].id;
const ITEMS_URL = `/api/projects/${PROJECT_ID}/items`;
const ESTIMATES_URL = `/api/projects/${PROJECT_ID}/estimates`;
const DEPENDENCIES_URL = `/api/projects/${PROJECT_ID}/dependencies`;

describe('WorkItems', () => {
  const fetchMock = mockFetch();

  /**
   * Answers five URLs separately: the session, the work in the plan, what everybody
   * currently thinks it will take, how the plan is joined up, and — once a card has been
   * dropped — who last reported progress on it. A double that answered them alike would
   * hand the estimate list a page of work items, and every row would quietly read as
   * unestimated.
   */
  function answer(
    items: WorkItem[],
    estimates: Estimate[] = [],
    dependencies: Dependency[] = []
  ) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ESTIMATES_URL
            ? jsonResponse(200, estimates)
            : url === DEPENDENCIES_URL
              ? jsonResponse(200, dependencies)
              : url.endsWith('/progress')
                ? jsonResponse(200, [])
                : jsonResponse(200, items)
      )
    );
  }

  async function open(
    items = WORK_ITEMS,
    estimates: Estimate[] = [],
    dependencies: Dependency[] = []
  ) {
    storeAccessToken();
    answer(items, estimates, dependencies);
    renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });
    await screen.findByRole('heading', { name: 'Work' });
  }

  /**
   * Open the form that writes a task down, which is asked for now rather than always on
   * screen. Every case that adds something goes through here, because the button is what a
   * person presses and a case that reached into the dialog without it would be testing a
   * form nobody can get to.
   */
  async function startAdding() {
    await userEvent.click(screen.getByRole('button', { name: 'Add a task' }));
    return within(await screen.findByRole('dialog'));
  }

  // The list ----------------------------------------------------------------

  it('lists the work in the plan', async () => {
    await open();

    expect(
      await screen.findByText('Migrate the auth service')
    ).toBeInTheDocument();
    expect(screen.getByText('Blocked on the vendor')).toBeInTheDocument();
    expect(screen.getByText('Write the runbook')).toBeInTheDocument();
  });

  /**
   * **Every way of changing a task is on the task's own page**, and the row's title is the
   * way there. A link rather than a button beside the name: a row is about one task and its
   * name is what somebody is looking for, so a column of identical "Open" labels would say
   * nothing about which task each one opened.
   */
  it('links each task to its own page', async () => {
    await open();

    expect(
      await screen.findByRole('link', { name: 'Migrate the auth service' })
    ).toHaveAttribute('href', `/app/items/${WORK_ITEMS[0].id}`);
  });

  /**
   * And the list itself carries only what is about the list. Five buttons on every one of
   * up to five hundred rows is what this replaced, and a row that still offered one of them
   * would be a second place to change a task.
   */
  it('offers nothing on a row but putting it away', async () => {
    await open();

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[0])
        .getAllByRole('button')
        .map((control) => control.textContent)
    ).toEqual(['Archive']);
  });

  it('says so when nothing has been written down yet', async () => {
    await open([]);

    expect(
      await screen.findByText(/Nothing written down yet/)
    ).toBeInTheDocument();
  });

  /** An item with notes shows them; one without shows nothing rather than an empty line. */
  it('shows notes only where there are any', async () => {
    await open();

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[0]).getByText('Blocked on the vendor')
    ).toBeInTheDocument();
    expect(within(rows[1]).queryByText('Blocked on the vendor')).toBeNull();
  });

  // Writing one down --------------------------------------------------------

  it('writes work down and asks the server what the list looks like now', async () => {
    await open();
    await screen.findByText('Write the runbook');

    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        ITEMS_URL,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ title: 'Cut the release', description: null })
        })
      )
    );
  });

  /**
   * <strong>It shuts once the server has it, and opens empty next time.</strong> The form
   * used to sit under the list and be remounted to clear it; now it exists only while it is
   * open, which is the same rule reached by not being there rather than by being replaced.
   */
  it('shuts once something has been written down, and reopens empty', async () => {
    await open();

    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect((await startAdding()).getByLabelText('Task')).toHaveValue('');
  });

  /**
   * <strong>And stays up holding what was typed when the server refuses.</strong> Shutting
   * on submit would ask somebody to type the whole thing again in order to read what was
   * wrong with it — which is why whether it is open is this component's state and not the
   * dialog's own.
   */
  it('stays open with what was typed when the write is refused', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(jsonResponse(500, null));

    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(screen.getByLabelText('Task')).toHaveValue('Cut the release');
  });

  /** Two ways out, and both are ways somebody meant: the form's own, and the platform's. */
  it('takes a cancelled form off the screen', async () => {
    await open();
    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');

    await userEvent.click(form.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    // And nothing was written: cancelling is not a quiet way to save.
    expect(
      fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    ).toHaveLength(0);
  });

  it('puts a complaint about the task against the field that holds it', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { description: { code: 'max_size', max: 2000 } }
      })
    );

    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));

    expect(
      await screen.findByText('Use no more than 2000 characters.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('puts a failure that belongs to no field in the banner', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(jsonResponse(500, null));

    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Something went wrong/
    );
  });

  // Putting one away --------------------------------------------------------

  it('puts an item away and asks the list again', async () => {
    await open();

    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Archive Write the runbook'
      })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[1].id}/archive`,
        expect.objectContaining({ method: 'POST' })
      )
    );
  });

  it('asks for the archived work separately, and brings one back', async () => {
    await open();
    answer([ARCHIVED_WORK_ITEM]);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );
    await screen.findByText('Something we dropped');

    await userEvent.click(
      screen.getByRole('button', { name: 'Bring Something we dropped back' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ARCHIVED_WORK_ITEM.id}/unarchive`,
        expect.objectContaining({ method: 'POST' })
      )
    );
  });

  it('says so when nothing here has been put away', async () => {
    await open();
    answer([]);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );

    expect(
      await screen.findByText('Nothing here has been put away.')
    ).toBeInTheDocument();
  });

  /**
   * Archived work is out of the plan and the estimates endpoint says nothing about it, so
   * the archived listing shows what a task is called and nothing it no longer takes part
   * in. Its own page is where somebody goes to bring it back.
   */
  it('says nothing about ranges or arrows on the archived listing', async () => {
    await open();
    answer([ARCHIVED_WORK_ITEM]);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );
    await screen.findByText('Something we dropped');

    expect(screen.queryByText('Not estimated')).toBeNull();
    expect(screen.queryByText('Not started')).toBeNull();
  });

  /**
   * Writing something down while looking at what was put away is a plausible thing to do,
   * and being shown nothing for it would read as the task having failed to save.
   */
  it('returns to the current work after adding while showing archived', async () => {
    await open();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ITEMS_URL
            ? jsonResponse(200, WORK_ITEMS)
            : url === ESTIMATES_URL
              ? jsonResponse(200, [])
              : jsonResponse(200, [ARCHIVED_WORK_ITEM])
      )
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );
    await screen.findByText('Something we dropped');

    const form = await startAdding();
    await userEvent.type(form.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(form.getByRole('button', { name: 'Add task' }));

    expect(await screen.findByText('Write the runbook')).toBeInTheDocument();
  });

  it('says so when archiving is refused', async () => {
    await open();

    fetchMock.mockResolvedValueOnce(
      jsonResponse(404, { code: 'work_item_not_found' })
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'Archive Write the runbook' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That task is no longer in this organisation.'
    );
  });

  // Reading it --------------------------------------------------------------

  it('says so when the work cannot be loaded', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(500, null)
      )
    );
    renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Something went wrong/
    );
  });

  /**
   * The work, its estimates and its arrows are asked for together, so all three answers
   * have to be dropped when the page has gone — which is what this delivers, after
   * leaving.
   */
  it('drops work that arrives after the page has been left', async () => {
    storeAccessToken();
    const pending: ((value: Response) => void)[] = [];
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => pending.push(resolve))
    );
    const { unmount } = renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });
    await screen.findByRole('status');
    await waitFor(() => expect(pending).toHaveLength(3));

    unmount();
    pending[0](jsonResponse(200, WORK_ITEMS));
    pending[1](jsonResponse(200, ESTIMATES));
    pending[2](jsonResponse(200, DEPENDENCIES));

    await waitFor(() =>
      expect(screen.queryByText('Write the runbook')).toBeNull()
    );
  });

  it('drops a failure that arrives after the page has been left', async () => {
    storeAccessToken();
    let refuse: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => {
            refuse = resolve;
          })
    );
    const { unmount } = renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });
    await screen.findByRole('status');

    unmount();
    refuse(jsonResponse(500, null));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  // What a row says ---------------------------------------------------------

  it('shows the reader their own range, and says who else gave one', async () => {
    await open(WORK_ITEMS, [...ESTIMATES, COLLEAGUES_ESTIMATE]);

    const rows = await screen.findAllByRole('listitem');
    // Ada's own numbers, not Bob's, even though both are current on that item.
    expect(
      within(rows[0]).getByText(/Your estimate: 3 \/ 5 \/ 12/)
    ).toBeInTheDocument();
    // And on the item only a colleague has estimated, their range is not invisible.
    expect(within(rows[1]).getByText('Estimated by Bob')).toBeInTheDocument();
  });

  /**
   * Several people holding a current estimate on one item is the schema working as
   * intended, so the row names all of them rather than whichever arrived first. What they
   * each said is on the task's own page, which has the room for it.
   */
  it('names every colleague who estimated an item the reader has not', async () => {
    const carol: Estimate = {
      ...COLLEAGUES_ESTIMATE,
      id: '30303030-3030-3030-3030-303030303030',
      estimatorId: '40404040-4040-4040-4040-404040404040',
      estimatorName: 'Carol'
    };
    await open(WORK_ITEMS, [COLLEAGUES_ESTIMATE, carol]);

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[1]).getByText('Estimated by Bob, Carol')
    ).toBeInTheDocument();
  });

  it('says which work carries no estimate at all', async () => {
    await open();

    const rows = await screen.findAllByRole('listitem');
    expect(within(rows[0]).getByText('Not estimated')).toBeInTheDocument();
  });

  /**
   * A partly estimated plan is the ordinary case, and what a forecast would leave out has
   * to be on screen rather than worked out by counting rows.
   */
  it('says how much of the plan is estimated', async () => {
    await open(WORK_ITEMS, [...ESTIMATES, COLLEAGUES_ESTIMATE]);

    expect(
      await screen.findByText('2 of 2 items estimated')
    ).toBeInTheDocument();
  });

  it('counts an item once however many people estimated it', async () => {
    await open(WORK_ITEMS, ESTIMATES);

    expect(
      await screen.findByText('1 of 2 items estimated')
    ).toBeInTheDocument();
  });

  it('says how far along each piece of work is', async () => {
    await open();

    const rows = await screen.findAllByRole('listitem');
    expect(within(rows[0]).getByText('Not started')).toBeInTheDocument();
    // A status with no date beside it is a claim nobody has to stand behind.
    expect(
      within(rows[1]).getByText('In progress since Aug 10, 2026')
    ).toBeInTheDocument();
  });

  it('says when finished work finished, and what it took where anybody measured it', async () => {
    await open([
      {
        ...WORK_ITEMS[0],
        status: 'DONE',
        startedOn: '2026-08-10',
        completedOn: '2026-08-14',
        actualEffortHours: 6.5
      },
      { ...WORK_ITEMS[1], status: 'DONE', completedOn: '2026-08-12' }
    ]);

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[0]).getByText('Done Aug 14, 2026 · took 6.5 hours')
    ).toBeInTheDocument();
    // Nobody measured this one, which will be the common case.
    expect(within(rows[1]).getByText('Done Aug 12, 2026')).toBeInTheDocument();
  });

  /**
   * Both directions on the row, because neither is redundant: what a delay here would
   * hold up, and why this has not started. Only one of them is visible from any one row
   * if the page shows one direction.
   */
  it('says what each task comes before and what it is waiting on', async () => {
    await open(WORK_ITEMS, [], DEPENDENCIES);

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[0]).getByText('Must finish before Write the runbook')
    ).toBeInTheDocument();
    expect(
      within(rows[1]).getByText('Waiting on Migrate the auth service')
    ).toBeInTheDocument();
  });

  /**
   * Nothing removes an arrow when its far end is archived, and the archived listing is a
   * different screen — so the title is not on this one to look up. Saying so beats a row
   * with a blank where a task should be.
   */
  it('says so where an arrow points at work that has been put away', async () => {
    await open(
      WORK_ITEMS,
      [],
      [{ ...DEPENDENCIES[0], successorItemId: ARCHIVED_WORK_ITEM.id }]
    );

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[0]).getByText(
        'Must finish before a task that has been put away'
      )
    ).toBeInTheDocument();
  });

  // The board ---------------------------------------------------------------

  /** The column a piece of work sits in, found by its heading. */
  function column(heading: RegExp) {
    return screen
      .getByRole('heading', { name: heading })
      .closest('section') as HTMLElement;
  }

  /** The card a task is drawn on. */
  function cardOf(title: string) {
    return screen
      .getByRole('link', { name: title })
      .closest('li') as HTMLElement;
  }

  /**
   * A drag from one card to one column, as the browser raises it. The `dataTransfer` is a
   * stand-in because jsdom has none; the component writes to it for Firefox's sake and
   * reads the card from its own state.
   */
  function drag(title: string, to: HTMLElement) {
    const card = cardOf(title);
    const dataTransfer = { setData: vi.fn(), effectAllowed: 'none' };
    fireEvent.dragStart(card, { dataTransfer });
    const accepted = fireEvent.dragOver(to);
    fireEvent.drop(to);
    fireEvent.dragEnd(card);
    return { dataTransfer, accepted };
  }

  it('puts each piece of work in the column for its status', async () => {
    await open();
    await screen.findByText('Migrate the auth service');

    expect(
      within(column(/^Not started · 1$/)).getByText('Migrate the auth service')
    ).toBeInTheDocument();
    expect(
      within(column(/^In progress · 1$/)).getByText('Write the runbook')
    ).toBeInTheDocument();
    // An empty column is said, not left as a blank box somebody might think is loading.
    expect(
      within(column(/^Done · 0$/)).getByText('Nothing here.')
    ).toBeInTheDocument();
  });

  /**
   * **A drop is not a save.** The server needs the day work started or finished and will
   * not stamp its own clock, so a drop opens the progress form with the column's status
   * already chosen, and nothing is written until somebody presses save.
   */
  it('asks when it happened before moving a card to another column', async () => {
    await open();
    await screen.findByText('Migrate the auth service');

    const { dataTransfer, accepted } = drag(
      'Migrate the auth service',
      column(/^In progress/)
    );

    // The browser is told a drop is possible, and the card travels as its identifier.
    expect(accepted).toBe(false);
    expect(dataTransfer.setData).toHaveBeenCalledWith(
      'text/plain',
      WORK_ITEMS[0].id
    );
    expect(dataTransfer.effectAllowed).toBe('move');

    const form = within(
      await screen.findByRole('dialog', {
        name: 'Report progress on Migrate the auth service'
      })
    );
    expect(form.getByLabelText('Status')).toHaveValue('IN_PROGRESS');
    expect(
      fetchMock.mock.calls.filter(([, init]) => init?.method === 'PATCH')
    ).toHaveLength(0);

    await userEvent.type(form.getByLabelText('Started on'), '2026-11-16');
    await userEvent.click(form.getByRole('button', { name: 'Save progress' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/progress`,
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({
            status: 'IN_PROGRESS',
            startedOn: '2026-11-16',
            completedOn: null,
            actualEffortHours: null
          })
        })
      )
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
  });

  /**
   * Moving out of not-started closes estimating, and the form says so — more sharply where
   * nobody has estimated the work, which a drop is just as able to do as the task's page.
   */
  it('warns that a moved card can no longer be estimated', async () => {
    await open();
    await screen.findByText('Migrate the auth service');

    drag('Migrate the auth service', column(/^Done/));

    const form = within(await screen.findByRole('dialog'));
    expect(form.getByLabelText('Status')).toHaveValue('DONE');
    expect(
      form.getByText(/can no longer be estimated or re-estimated/)
    ).toHaveTextContent(/every forecast will carry it at no effort/);
  });

  it('does not warn about unestimated work when somebody has estimated it', async () => {
    await open(WORK_ITEMS, ESTIMATES);
    await screen.findByText('Migrate the auth service');

    drag('Migrate the auth service', column(/^Done/));

    const form = within(await screen.findByRole('dialog'));
    expect(
      form.getByText(/can no longer be estimated or re-estimated/)
    ).not.toHaveTextContent(/at no effort/);
  });

  it('does nothing when a card is let go over its own column', async () => {
    await open();
    await screen.findByText('Migrate the auth service');

    const { accepted } = drag(
      'Migrate the auth service',
      column(/^Not started/)
    );

    // Not cancelled, so the browser shows no drop is possible there.
    expect(accepted).toBe(true);
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  /** A drop arriving with nothing dragged — a file from the desktop, say — is ignored. */
  it('ignores a drop that carries no card', async () => {
    await open();
    await screen.findByText('Migrate the auth service');

    const target = column(/^Done/);
    expect(fireEvent.dragOver(target)).toBe(true);
    fireEvent.drop(target);

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('marks the column a card can land in, and the card being dragged', async () => {
    await open();
    await screen.findByText('Migrate the auth service');
    const card = cardOf('Migrate the auth service');
    const target = column(/^Done/);

    fireEvent.dragStart(card, {
      dataTransfer: { setData: vi.fn(), effectAllowed: 'none' }
    });
    fireEvent.dragOver(target);

    expect(card).toHaveClass('dragging');
    expect(target).toHaveClass('over');

    fireEvent.dragLeave(target);
    expect(target).not.toHaveClass('over');

    fireEvent.dragOver(target);
    fireEvent.dragEnd(card);
    expect(card).not.toHaveClass('dragging');
    expect(target).not.toHaveClass('over');
  });

  it('keeps the dialog open beside the refusal when the move is refused', async () => {
    await open();
    await screen.findByText('Migrate the auth service');
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        init?.method === 'PATCH'
          ? jsonResponse(400, { code: 'progress_date_required' })
          : url.endsWith('/progress')
            ? jsonResponse(200, [])
            : jsonResponse(200, ACCOUNT)
      )
    );

    drag('Migrate the auth service', column(/^In progress/));
    const form = within(await screen.findByRole('dialog'));
    await userEvent.click(form.getByRole('button', { name: 'Save progress' }));

    expect(await form.findByRole('alert')).toHaveTextContent(
      /Work in progress needs a start date/
    );
    expect(screen.getByRole('dialog')).toBeInTheDocument();
  });

  it('moves nothing when the dialog is cancelled', async () => {
    await open();
    await screen.findByText('Migrate the auth service');

    drag('Migrate the auth service', column(/^Done/));
    const form = within(await screen.findByRole('dialog'));
    await userEvent.click(form.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(
      fetchMock.mock.calls.filter(([, init]) => init?.method === 'PATCH')
    ).toHaveLength(0);
  });

  /** Put-away work is in no forecast and reports no progress, so it has no column. */
  it('lists archived work without columns, and lets none of it be dragged', async () => {
    await open();
    answer([ARCHIVED_WORK_ITEM]);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );
    await screen.findByText('Something we dropped');

    expect(screen.queryByRole('heading', { name: /^Not started/ })).toBeNull();
    expect(cardOf('Something we dropped')).toHaveAttribute(
      'draggable',
      'false'
    );
  });
});
