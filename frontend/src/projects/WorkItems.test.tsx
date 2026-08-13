import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WorkItems } from './WorkItems';
import {
  ACCOUNT,
  ARCHIVED_WORK_ITEM,
  COLLEAGUES_ESTIMATE,
  ESTIMATES,
  PROJECTS,
  WORK_ITEMS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Estimate, WorkItem } from './types';

const PROJECT_ID = PROJECTS[0].id;
const ITEMS_URL = `/api/projects/${PROJECT_ID}/items`;
const ESTIMATES_URL = `/api/projects/${PROJECT_ID}/estimates`;

describe('WorkItems', () => {
  const fetchMock = mockFetch();

  /**
   * Answers three URLs separately: the session, the work in the plan, and what everybody
   * currently thinks it will take. A double that answered them alike would hand the
   * estimate list a page of work items, and every row would quietly read as unestimated.
   */
  function answer(items: WorkItem[], estimates: Estimate[]) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ESTIMATES_URL
            ? jsonResponse(200, estimates)
            : jsonResponse(200, items)
      )
    );
  }

  async function open(items = WORK_ITEMS, estimates: Estimate[] = []) {
    storeAccessToken();
    answer(items, estimates);
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
    // Three calls: the session, the work, and what it is estimated at. Cancelling asks
    // the server nothing.
    expect(fetchMock).toHaveBeenCalledTimes(3);
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
    answer([ARCHIVED_WORK_ITEM], []);

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
    answer([], []);

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

  /** The guard that keeps a list arriving late from touching a page that has gone. */
  /**
   * The work and its estimates are asked for together, so both answers have to be dropped
   * when the page has gone — which is what this delivers, after leaving.
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
    await waitFor(() => expect(pending).toHaveLength(2));

    unmount();
    pending[0](jsonResponse(200, WORK_ITEMS));
    pending[1](jsonResponse(200, ESTIMATES));

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
    answer([ARCHIVED_WORK_ITEM], []);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show archived work' })
    );
    await screen.findByText('Something we dropped');

    expect(
      screen.queryByRole('button', { name: 'Estimate Something we dropped' })
    ).toBeNull();
    expect(screen.queryByText('Not estimated')).toBeNull();
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
