import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
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

/**
 * A third task, so that drawing one arrow still leaves something to pick. With two, the
 * panel would correctly report there is nothing left to order against — which is a
 * different thing to assert than that it stayed open.
 */
const THREE_ITEMS: WorkItem[] = [
  ...WORK_ITEMS,
  {
    ...WORK_ITEMS[1],
    id: '40404040-4040-4040-4040-404040404040',
    title: 'Cut the release'
  }
];

describe('WorkItems', () => {
  const fetchMock = mockFetch();

  /**
   * Answers four URLs separately: the session, the work in the plan, what everybody
   * currently thinks it will take, and how the plan is joined up. A double that answered
   * them alike would hand the estimate list a page of work items, and every row would
   * quietly read as unestimated.
   */
  function answer(
    items: WorkItem[],
    estimates: Estimate[],
    dependencies: Dependency[]
  ) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ESTIMATES_URL
            ? jsonResponse(200, estimates)
            : url === DEPENDENCIES_URL
              ? jsonResponse(200, dependencies)
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

  it('lists the work in the plan', async () => {
    await open();

    expect(
      await screen.findByText('Migrate the auth service')
    ).toBeInTheDocument();
    expect(screen.getByText('Blocked on the vendor')).toBeInTheDocument();
    expect(screen.getByText('Write the runbook')).toBeInTheDocument();
  });

  it('says so when nothing has been written down yet', async () => {
    await open([]);

    expect(
      await screen.findByText(/Nothing written down yet/)
    ).toBeInTheDocument();
  });

  it('writes work down and asks the server what the list looks like now', async () => {
    await open();
    await screen.findByText('Write the runbook');

    await userEvent.type(screen.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

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

  /** So the next task starts from an empty box rather than the last one's title. */
  it('empties the add form once something has been written down', async () => {
    await open();

    await userEvent.type(screen.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

    await waitFor(() => expect(screen.getByLabelText('Task')).toHaveValue(''));
  });

  it('rewords an item in place', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit Write the runbook' })
    );

    const form = screen.getByLabelText('Task', {
      selector: '#item-' + WORK_ITEMS[1].id + '-title'
    });
    await userEvent.clear(form);
    await userEvent.type(form, 'Write the runbook and the rollback');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[1].id}`,
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({
            title: 'Write the runbook and the rollback',
            description: null
          })
        })
      )
    );
    // Closed again, so the page does not fill with open forms.
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Save' })).toBeNull()
    );
  });

  it('leaves a row alone when the edit is cancelled', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit Write the runbook' })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.getByText('Write the runbook')).toBeInTheDocument();
    // Four calls: the session, the work, what it is estimated at, and how it is joined
    // up. Cancelling asks the server nothing.
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('says so against the field when a reword is refused', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit Write the runbook' })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { title: { code: 'not_blank' } }
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    expect(
      await screen.findByText('This cannot be empty.')
    ).toBeInTheDocument();
    // Still open, so the visitor can fix what they were told about.
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
  });

  it('does not carry one row’s complaint into the next row opened', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', { name: 'Edit Write the runbook' })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { title: { code: 'not_blank' } }
      })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await screen.findByText('This cannot be empty.');

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await userEvent.click(
      screen.getByRole('button', { name: 'Edit Migrate the auth service' })
    );

    expect(screen.queryByText('This cannot be empty.')).toBeNull();
  });

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
    answer([ARCHIVED_WORK_ITEM], [], []);

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
    answer([], [], []);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );

    expect(
      await screen.findByText('Nothing here has been put away.')
    ).toBeInTheDocument();
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

    await userEvent.type(screen.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

    expect(await screen.findByText('Write the runbook')).toBeInTheDocument();
  });

  it('puts a complaint about the task against the field that holds it', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { description: { code: 'max_size', max: 2000 } }
      })
    );

    await userEvent.type(screen.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

    expect(
      await screen.findByText('Use no more than 2000 characters.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('puts a failure that belongs to no field in the banner', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(jsonResponse(500, null));

    await userEvent.type(screen.getByLabelText('Task'), 'Cut the release');
    await userEvent.click(screen.getByRole('button', { name: 'Add task' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /Something went wrong/
    );
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

  // Estimates ---------------------------------------------------------------

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
   * intended, so the row names all of them rather than whichever arrived first.
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
   * Decision 5 made visible: a partly estimated plan is the ordinary case, and what a
   * forecast would leave out has to be on screen rather than worked out by counting rows.
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

  it('records a three-point estimate against the item', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );

    await userEvent.type(screen.getByLabelText('P10'), '3');
    await userEvent.type(screen.getByLabelText('P50'), '5');
    await userEvent.type(screen.getByLabelText('P90'), '12');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/estimates`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ p10Hours: 3, p50Hours: 5, p90Hours: 12 })
        })
      )
    );
  });

  /** Revising starts from what that person last said, not from three empty boxes. */
  it('opens with the reader’s current estimate already in it', async () => {
    await open(WORK_ITEMS, ESTIMATES);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );

    expect(screen.getByLabelText('P10')).toHaveValue(3);
    expect(screen.getByLabelText('P90')).toHaveValue(12);
  });

  /**
   * A box nobody filled in is missing rather than zero — `Number('')` is 0, and sending
   * that would have the visitor told their estimate must be more than zero about a field
   * they never touched.
   */
  it('sends an untouched box as nothing rather than as no hours', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );

    await userEvent.type(screen.getByLabelText('P50'), '5');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/estimates`,
        expect.objectContaining({
          body: JSON.stringify({
            p10Hours: null,
            p50Hours: 5,
            p90Hours: null
          })
        })
      )
    );
  });

  /**
   * The refusal that belongs to no single box: each number is fine and the order is not,
   * so it arrives in the banner rather than against a field chosen arbitrarily.
   */
  it('says so when the three numbers do not go up', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'estimate_out_of_order' })
    );

    await userEvent.type(screen.getByLabelText('P10'), '5');
    await userEvent.type(screen.getByLabelText('P50'), '3');
    await userEvent.type(screen.getByLabelText('P90'), '12');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /The three numbers must go up/
    );
  });

  it('puts a complaint about one number against that box', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { p10Hours: { code: 'positive' } }
      })
    );

    await userEvent.type(screen.getByLabelText('P10'), '0');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    expect(
      await screen.findByText('Use a number greater than zero.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('closes the estimate form when it is cancelled', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText('P10')).toBeNull();
  });

  /**
   * Archived work is out of the plan, and the estimates endpoint says nothing about it —
   * so a row there must not offer to estimate something the forecast will never see.
   */
  it('offers no estimate on work that has been put away', async () => {
    await open();
    answer([ARCHIVED_WORK_ITEM], [], []);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );
    await screen.findByText('Something we dropped');

    expect(
      screen.queryByRole('button', { name: 'Estimate Something we dropped' })
    ).toBeNull();
    expect(screen.queryByText('Not estimated')).toBeNull();
  });

  // Progress ----------------------------------------------------------------

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

  it('records what has happened to a piece of work', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');
    await userEvent.type(screen.getByLabelText('Finished on'), '2026-08-14');
    await userEvent.type(screen.getByLabelText('Actual effort (hours)'), '6.5');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/progress`,
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({
            status: 'DONE',
            startedOn: null,
            completedOn: '2026-08-14',
            actualEffortHours: 6.5
          })
        })
      )
    );
  });

  /** Revising starts from what the row already says, not from an empty form. */
  it('opens with what the item already records', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Write the runbook'
      })
    );

    expect(screen.getByLabelText('Status')).toHaveValue('IN_PROGRESS');
    expect(screen.getByLabelText('Started on')).toHaveValue('2026-08-10');
  });

  /**
   * The refusal that belongs to no single box: which date is missing depends on the status
   * in another one, so it arrives in the banner rather than against a field.
   */
  it('says so when a state is claimed without the date that supports it', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'progress_date_required' })
    );

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'IN_PROGRESS'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /needs a start date/
    );
  });

  /**
   * The defect this rule exists for: hours typed against work marked not started were
   * accepted, dropped by the server, and never mentioned again.
   */
  it('offers no effort or dates on work that has not started', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    expect(screen.getByLabelText('Status')).toHaveValue('NOT_STARTED');
    expect(screen.queryByLabelText('Actual effort (hours)')).toBeNull();
    expect(screen.queryByLabelText('Started on')).toBeNull();
    expect(screen.queryByLabelText('Finished on')).toBeNull();
  });

  /** Work under way has taken hours already; it simply has no date it finished on. */
  it('offers effort and a start, but no completion, on work in progress', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'IN_PROGRESS'
    );

    expect(screen.getByLabelText('Started on')).toBeInTheDocument();
    expect(screen.getByLabelText('Actual effort (hours)')).toBeInTheDocument();
    expect(screen.queryByLabelText('Finished on')).toBeNull();
  });

  /** And what is not on screen is sent as nothing, rather than quietly kept back. */
  it('sends nothing for what the chosen status has no room for', async () => {
    await open([
      {
        ...WORK_ITEMS[0],
        status: 'DONE',
        startedOn: '2026-08-10',
        completedOn: '2026-08-14',
        actualEffortHours: 6.5
      }
    ]);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'NOT_STARTED'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/progress`,
        expect.objectContaining({
          body: JSON.stringify({
            status: 'NOT_STARTED',
            startedOn: null,
            completedOn: null,
            actualEffortHours: null
          })
        })
      )
    );
  });

  /**
   * The boxes holding those values have just vanished, so somebody who is not told would
   * reasonably assume they survive the save.
   */
  it('warns before a status change throws away what was recorded', async () => {
    await open([
      {
        ...WORK_ITEMS[0],
        status: 'DONE',
        startedOn: '2026-08-10',
        completedOn: '2026-08-14',
        actualEffortHours: 6.5
      }
    ]);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    expect(screen.queryByText(/Saving this discards/)).toBeNull();

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'NOT_STARTED'
    );

    expect(screen.getByText(/Saving this discards/)).toBeInTheDocument();
  });

  /**
   * Work ticked off by somebody who never marked it as begun records only the day it
   * finished, so the warning has to notice that one date without going looking for a start
   * that was never there.
   */
  it('warns when the only thing recorded is the day it finished', async () => {
    await open([
      { ...WORK_ITEMS[0], status: 'DONE', completedOn: '2026-08-14' }
    ]);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'NOT_STARTED'
    );

    expect(screen.getByText(/Saving this discards/)).toBeInTheDocument();
  });

  /** Nothing recorded, nothing to lose: the warning would be about no data at all. */
  it('says nothing about discarding when there is nothing recorded', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');

    expect(screen.queryByText(/Saving this discards/)).toBeNull();
  });

  it('says so when work would finish before it began', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'progress_out_of_order' })
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Work cannot be finished before it was started.'
    );
  });

  it('puts a complaint about the effort against that box', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { actualEffortHours: { code: 'positive' } }
      })
    );

    // The box exists only once the status has room for it, which is the whole of the rule
    // this form now follows.
    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'IN_PROGRESS'
    );
    await userEvent.type(screen.getByLabelText('Actual effort (hours)'), '0');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    expect(
      await screen.findByText('Use a number greater than zero.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /** A status the server will not take is a complaint about the box that holds it. */
  it('puts a complaint about the status against the status', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { status: { code: 'not_null' } }
      })
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    expect(
      await screen.findByText('Choose one of the options.')
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('closes the progress form when it is cancelled', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText('Status')).toBeNull();
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

  it('draws an arrow from the task the form was opened on', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );

    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dependencies',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            predecessorItemId: WORK_ITEMS[0].id,
            successorItemId: WORK_ITEMS[1].id,
            // An empty wait box is answered here rather than sent unanswered to a server
            // that refuses to guess at it.
            lagHours: 0
          })
        })
      )
    );
  });

  /**
   * Unlike every other form here, which closes on a successful write. Ordering is plural
   * where the others are not: a task that must finish before one thing usually must
   * finish before two, and closing after each would make drawing three arrows three trips
   * through the same button.
   */
  it('stays open once an arrow lands, and shows it in the list', async () => {
    await open(THREE_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    answer(THREE_ITEMS, [], DEPENDENCIES);

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    // Asserted through the button that rubs it out, which only the panel has — the row
    // above says the same sentence, and matching either would prove nothing about which.
    expect(
      await screen.findByRole('button', {
        name: 'Stop requiring this to finish before Write the runbook'
      })
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Must finish before')).toBeInTheDocument();
  });

  /** So the next arrow starts from empty boxes rather than the last one's wait. */
  it('empties the boxes once an arrow has landed', async () => {
    await open(THREE_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    await userEvent.type(screen.getByLabelText('Wait afterwards (hours)'), '8');
    answer(THREE_ITEMS, [], DEPENDENCIES);

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(screen.getByLabelText('Wait afterwards (hours)')).toHaveValue(null)
    );
    expect(screen.getByLabelText('Must finish before')).toHaveValue('');
  });

  /** A refusal does not reload, so it leaves what was typed exactly where it is. */
  it('leaves the boxes alone when an arrow is refused', async () => {
    await open(THREE_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    await userEvent.type(screen.getByLabelText('Wait afterwards (hours)'), '8');
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, { code: 'dependency_already_exists' })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    await screen.findByRole('alert');
    expect(screen.getByLabelText('Wait afterwards (hours)')).toHaveValue(8);
    expect(screen.getByLabelText('Must finish before')).toHaveValue(
      WORK_ITEMS[1].id
    );
  });

  it('sends the wait somebody typed', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );

    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    await userEvent.type(screen.getByLabelText('Wait afterwards (hours)'), '8');
    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/dependencies',
        expect.objectContaining({
          body: expect.stringContaining('"lagHours":8')
        })
      )
    );
  });

  /**
   * Both are refusals the server would give, so offering them would waste somebody's
   * time to tell them something the form already knew.
   */
  it('offers neither the task itself nor one it already comes before', async () => {
    await open(WORK_ITEMS, [], DEPENDENCIES);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Write the runbook'
      })
    );

    const choices = within(
      screen.getByLabelText('Must finish before')
    ).getAllByRole('option');
    expect(choices.map((choice) => choice.textContent)).toEqual([
      'Choose a task…',
      'Migrate the auth service'
    ]);
  });

  it('says so when there is nothing left to order a task against', async () => {
    await open(WORK_ITEMS, [], DEPENDENCIES);

    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );

    expect(
      await screen.findByText(/Nothing left to order this against/)
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Must finish before')).toBeNull();
  });

  /**
   * The refusal carries the loop it would have closed, which is the whole reason the
   * server walks the graph rather than answering yes or no — a plan holds up to five
   * hundred tasks, and finding it by hand is not something to ask of anybody.
   */
  it('names the loop a refused arrow would have closed', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        code: 'dependency_cycle',
        path: [WORK_ITEMS[0].id, WORK_ITEMS[1].id]
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(
      await screen.findByText(
        'That would make a loop: Migrate the auth service → Write the runbook → Migrate the auth service.'
      )
    ).toBeInTheDocument();
  });

  it('shows a per-field refusal against the field it belongs to', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { successorItemId: { code: 'not_null' } }
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(
      await screen.findByText('Choose one of the options.')
    ).toBeInTheDocument();
    // Said once: the banner would only repeat what the field already says.
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('rubs an arrow out and asks the plan again', async () => {
    await open(WORK_ITEMS, [], DEPENDENCIES);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );

    await userEvent.click(
      screen.getByRole('button', {
        name: 'Stop requiring this to finish before Write the runbook'
      })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/dependencies/${DEPENDENCIES[0].id}`,
        expect.objectContaining({ method: 'DELETE' })
      )
    );
  });

  /** A wait is the only thing an arrow carries beyond itself, so it has to be visible. */
  it('says how long a wait is where an arrow has one', async () => {
    await open(WORK_ITEMS, [], [{ ...DEPENDENCIES[0], lagHours: 8 }]);

    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );

    expect(
      screen.getByText('Must finish before Write the runbook, plus 8 hours')
    ).toBeInTheDocument();
  });

  /**
   * Nothing removes an arrow when its far end is archived, and the archived listing is a
   * different screen — so the title is not on this one to look up. Saying so beats a row
   * with a blank where a task should be.
   */
  it('says so where an arrow points at work that has been put away', async () => {
    const gone = {
      ...DEPENDENCIES[0],
      successorItemId: ARCHIVED_WORK_ITEM.id
    };
    await open(WORK_ITEMS, [], [gone]);

    const rows = await screen.findAllByRole('listitem');
    expect(
      within(rows[0]).getByText(
        'Must finish before a task that has been put away'
      )
    ).toBeInTheDocument();

    await userEvent.click(
      within(rows[0]).getByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    expect(
      screen.getByRole('button', {
        name: 'Stop requiring this to finish before a task that has been put away'
      })
    ).toBeInTheDocument();
  });

  /** And the same where the loop a refusal names runs through work that is put away. */
  it('names a loop that runs through work put away since', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        code: 'dependency_cycle',
        path: [WORK_ITEMS[0].id, ARCHIVED_WORK_ITEM.id]
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(
      await screen.findByText(
        'That would make a loop: Migrate the auth service → a task that has been put away → Migrate the auth service.'
      )
    ).toBeInTheDocument();
  });

  it('shows a refused wait against the box it was typed in', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { lagHours: { code: 'positive_or_zero' } }
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(
      await screen.findByText('Use zero or a number greater than it.')
    ).toBeInTheDocument();
  });

  it('says so when rubbing an arrow out is refused', async () => {
    await open(WORK_ITEMS, [], DEPENDENCIES);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(404, { code: 'dependency_not_found' })
    );

    await userEvent.click(
      screen.getByRole('button', {
        name: 'Stop requiring this to finish before Write the runbook'
      })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That dependency is no longer in this project.'
    );
  });

  it('closes the order form when it is cancelled', async () => {
    await open(WORK_ITEMS, [], []);
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Order work around Migrate the auth service'
      })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Done' }));

    expect(screen.queryByLabelText('Must finish before')).toBeNull();
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
});
