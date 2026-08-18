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
import type {
  Dependency,
  Estimate,
  EstimateQuality,
  ProgressReport,
  WorkItem
} from './types';

const PROJECT_ID = PROJECTS[0].id;
const ITEMS_URL = `/api/projects/${PROJECT_ID}/items`;
const ESTIMATES_URL = `/api/projects/${PROJECT_ID}/estimates`;
const DEPENDENCIES_URL = `/api/projects/${PROJECT_ID}/dependencies`;
const QUALITY_URL = '/api/estimates/quality';

/**
 * What the server says about a range with nothing odd about it — which is what 3/5/8 gets,
 * and is the default so that a warning appearing in a test is one the test asked for.
 */
const FINE: EstimateQuality = {
  consistency: 1,
  inconsistent: false,
  overconfident: false
};

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

/**
 * The three questions, as somebody reads them — and in the order they are asked, which is
 * the whole of what makes this form different from the three boxes it replaced. They are
 * written out rather than looked up so that a change to the wording has to be made here
 * too, deliberately: these strings are the milestone.
 */
const BAD_CASE =
  'Think of a version of this that goes badly — not a disaster, just a bad week. What number would make you genuinely surprised to have gone over?';
const GOOD_CASE =
  'Now the version where everything goes right. What is the least this could take?';
const TYPICAL_CASE = 'And what do you actually expect it to take?';

describe('WorkItems', () => {
  const fetchMock = mockFetch();

  /**
   * Answers five URLs separately: the session, the work in the plan, what everybody
   * currently thinks it will take, how the plan is joined up, and who has reported on a
   * task. A double that answered them alike would hand the estimate list a page of work
   * items, and every row would quietly read as unestimated.
   *
   * The last of them is answered on the method as well as the path, because reporting
   * progress and reading who reported it are the same URL — one returns the item and the
   * other its history, and a double that confused them would hand the form a work item to
   * render as somebody's name.
   */
  function answer(
    items: WorkItem[],
    estimates: Estimate[],
    dependencies: Dependency[],
    quality: EstimateQuality = FINE
  ) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === QUALITY_URL
            ? jsonResponse(200, quality)
            : url === ESTIMATES_URL
              ? jsonResponse(200, estimates)
              : url === DEPENDENCIES_URL
                ? jsonResponse(200, dependencies)
                : url.endsWith('/progress') && init?.method === 'GET'
                  ? jsonResponse(200, [])
                  : jsonResponse(200, items)
      )
    );
  }

  async function open(
    items = WORK_ITEMS,
    estimates: Estimate[] = [],
    dependencies: Dependency[] = [],
    quality: EstimateQuality = FINE
  ) {
    storeAccessToken();
    answer(items, estimates, dependencies, quality);
    renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });
    await screen.findByRole('heading', { name: 'Work' });
  }

  /**
   * The plan, then the progress form on the first task, with a history behind it. Its own
   * helper because the history is answered on the method as well as the path — the URL it
   * comes from is the one a report is written to.
   */
  async function openWithHistory(history: ProgressReport[]) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ESTIMATES_URL
            ? jsonResponse(200, [])
            : url === DEPENDENCIES_URL
              ? jsonResponse(200, [])
              : url.endsWith('/progress') && init?.method === 'GET'
                ? jsonResponse(200, history)
                : jsonResponse(200, WORK_ITEMS)
      )
    );
    renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
  }

  /** Opening the estimate form on the first task, which is what every case below does. */
  async function openEstimate() {
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Estimate Migrate the auth service'
      })
    );
  }

  /**
   * Walking the three questions in the order they are asked, as far as the review. An
   * empty string leaves that question unanswered, which is a different thing from zero and
   * is what the server has to be told apart.
   */
  async function answerTheThree(bad: string, good: string, typical: string) {
    for (const [question, answer] of [
      [BAD_CASE, bad],
      [GOOD_CASE, good],
      [TYPICAL_CASE, typical]
    ] as const) {
      if (answer !== '') {
        await userEvent.type(screen.getByLabelText(question), answer);
      }
      await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    }
    await screen.findByText('What you have said');
  }

  /** The three questions, then the review, then saving it. */
  async function estimateThrough(bad: string, good: string, typical: string) {
    await answerTheThree(bad, good, typical);
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );
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

  /**
   * <strong>The order is the milestone, and this is what holds it.</strong> The bad case
   * is asked first because it is the only one of the three with nothing above it; the
   * middle is asked last because the fit does not use it and it is the number people
   * answer fastest. A form that asked them in any other order would pass every other test
   * in this file.
   */
  it('asks the bad case first, the good case second and the typical one last', async () => {
    await open();
    await openEstimate();

    expect(screen.getByLabelText(BAD_CASE)).toBeInTheDocument();
    expect(screen.queryByLabelText(GOOD_CASE)).toBeNull();
    expect(screen.queryByLabelText(TYPICAL_CASE)).toBeNull();

    await userEvent.type(screen.getByLabelText(BAD_CASE), '12');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.getByLabelText(GOOD_CASE)).toBeInTheDocument();
    expect(screen.queryByLabelText(BAD_CASE)).toBeNull();
    // The whole point of one at a time: the answer just given is not on screen to
    // anchor the next one, and a hidden input holding it would be no better.
    expect(screen.queryByDisplayValue('12')).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.getByLabelText(TYPICAL_CASE)).toBeInTheDocument();
    expect(screen.queryByLabelText(GOOD_CASE)).toBeNull();
  });

  /**
   * The percentile names are what invite somebody to reason about tail probability, which
   * nobody can do. They appear nowhere on this form, and this is the assertion that fails
   * the day one comes back as a "clearer" label.
   */
  it('never names a percentile while a question is being answered', async () => {
    await open();
    await openEstimate();

    for (const question of [BAD_CASE, GOOD_CASE, TYPICAL_CASE]) {
      expect(screen.getByLabelText(question)).toBeInTheDocument();
      expect(screen.queryByText(/\bP(10|50|90)\b/)).toBeNull();
      await userEvent.click(
        screen.getByRole('button', {
          name: question === TYPICAL_CASE ? 'Cancel' : 'Next'
        })
      );
    }
  });

  /** Going back is revision rather than anchoring, so that step keeps its own answer. */
  it('keeps each answer when somebody steps back and forward again', async () => {
    await open();
    await openEstimate();

    await userEvent.type(screen.getByLabelText(BAD_CASE), '12');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.type(screen.getByLabelText(GOOD_CASE), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Back' }));

    expect(screen.getByLabelText(BAD_CASE)).toHaveValue(12);

    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(screen.getByLabelText(GOOD_CASE)).toHaveValue(3);
  });

  /**
   * <strong>Decision 10.</strong> Two people who anchored on each other are not two
   * estimates, and the band they produce is confidently narrow for a reason nothing
   * downstream can see. Bob's 10/20/40 is on the row's summary as a name and nowhere near
   * the boxes.
   */
  it('never shows a colleague’s numbers while somebody is answering', async () => {
    await open(WORK_ITEMS, ESTIMATES);
    await openEstimate();

    for (const question of [BAD_CASE, GOOD_CASE, TYPICAL_CASE]) {
      expect(screen.getByLabelText(question)).toBeInTheDocument();
      for (const his of ['10', '20', '40']) {
        expect(screen.queryByDisplayValue(his)).toBeNull();
      }
      if (question !== TYPICAL_CASE) {
        await userEvent.click(screen.getByRole('button', { name: 'Next' }));
      }
    }
  });

  /**
   * <strong>The first and only moment the three are seen together.</strong> Together while
   * one is still being answered is the anchoring the order exists to prevent; together once
   * all three exist is the only way to notice that a bad week is barely worse than an
   * ordinary one. In plain words, and still no percentile named.
   */
  it('shows all three answers together at the review and nowhere earlier', async () => {
    await open();
    await openEstimate();

    await answerTheThree('12', '3', '5');

    expect(screen.getByText('A bad week: 12 hours')).toBeInTheDocument();
    expect(
      screen.getByText('Everything goes right: 3 hours')
    ).toBeInTheDocument();
    expect(screen.getByText('What you expect: 5 hours')).toBeInTheDocument();
    expect(screen.queryByText(/\bP(10|50|90)\b/)).toBeNull();
  });

  /** A question nobody answered says so rather than showing a gap. */
  it('says which of the three was never answered', async () => {
    await open();
    await openEstimate();

    await answerTheThree('', '3', '5');

    expect(screen.getByText('A bad week: not answered')).toBeInTheDocument();
  });

  /**
   * <strong>The betting frame, which gates nothing.</strong> It makes a number typed
   * cheaply feel expensive, and the only control it carries is the way out — because a bet
   * somebody would not take is a P90 they have not really given. Saying yes is pressing
   * save.
   */
  it('asks whether somebody would take the bet their own high number implies', async () => {
    await open();
    await openEstimate();

    await answerTheThree('12', '3', '5');

    expect(
      screen.getByText(
        /You are saying that nine times in ten this comes in under 12 hours/
      )
    ).toBeInTheDocument();
  });

  /** Declining goes back to the question that number answers, with it still in the box. */
  it('sends somebody back to the bad case when they would not take the bet', async () => {
    await open();
    await openEstimate();
    await answerTheThree('12', '3', '5');

    await userEvent.click(
      screen.getByRole('button', { name: 'No — let me change that' })
    );

    expect(screen.getByLabelText(BAD_CASE)).toHaveValue(12);

    await userEvent.clear(screen.getByLabelText(BAD_CASE));
    await userEvent.type(screen.getByLabelText(BAD_CASE), '40');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByText('A bad week: 40 hours')).toBeInTheDocument();
    expect(
      screen.getByText(/nine times in ten this comes in under 40 hours/)
    ).toBeInTheDocument();
  });

  /**
   * <strong>Rendered from what the server said, not worked out here.</strong> 6/10/14 is a
   * tight band and 3/5/8 is not, and this test does not know which is which — it renders
   * whatever the response carried, which is what lets a threshold move on the server with
   * no change on this side. Decision 6, asserted rather than intended.
   */
  it('shows the warnings the server sent, whatever the numbers were', async () => {
    await open(WORK_ITEMS, [], [], {
      consistency: 1.09,
      inconsistent: false,
      overconfident: true
    });
    await openEstimate();

    await answerTheThree('8', '6', '10');

    expect(
      screen.getByText(/Your bad week is barely worse than what you expect/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/a long way from the middle/)).toBeNull();
  });

  /** And the other one, for a middle that argues with its own two ends. */
  it('shows the consistency warning when the server reports one', async () => {
    await open(WORK_ITEMS, [], [], {
      consistency: 0.707,
      inconsistent: true,
      overconfident: false
    });
    await openEstimate();

    await answerTheThree('40', '5', '10');

    expect(
      screen.getByText(/a long way from the middle your own two ends imply/)
    ).toBeInTheDocument();
  });

  /** A range with nothing odd about it says nothing, which is most of them. */
  it('says nothing about a range the server had no comment on', async () => {
    await open();
    await openEstimate();

    await answerTheThree('8', '3', '5');

    expect(screen.queryByText(/Your bad week is barely worse/)).toBeNull();
    expect(screen.queryByText(/a long way from the middle/)).toBeNull();
  });

  /**
   * <strong>Advice, never a refusal — decision 5.</strong> A tight band is sometimes
   * exactly right, and a rule that blocked one would become a specification people learn to
   * type, which is 3/5/8 with an extra step.
   */
  it('saves a warned estimate exactly as it was given', async () => {
    await open(WORK_ITEMS, [], [], {
      consistency: 1.09,
      inconsistent: false,
      overconfident: true
    });
    await openEstimate();

    await estimateThrough('14', '6', '10');

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/estimates`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            p10Hours: 6,
            p50Hours: 10,
            p90Hours: 14,
            method: 'surprise_framed'
          })
        })
      )
    );
  });

  /**
   * The review is advice, so it survives not getting any. A server that cannot be reached
   * about the range must not stop somebody saving what they came to say.
   */
  it('reviews without warnings when the server could not be asked', async () => {
    await open();
    await openEstimate();
    await userEvent.type(screen.getByLabelText(BAD_CASE), '12');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.type(screen.getByLabelText(GOOD_CASE), '3');
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    await userEvent.type(screen.getByLabelText(TYPICAL_CASE), '5');
    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'));
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));

    expect(await screen.findByText('A bad week: 12 hours')).toBeInTheDocument();
    expect(screen.queryByText(/Your bad week is barely worse/)).toBeNull();
    expect(
      screen.getByRole('button', { name: 'Save estimate' })
    ).toBeInTheDocument();
  });

  /**
   * Somebody can close the form while the server is still being asked about their range,
   * and nothing arriving afterwards may touch it. React warns about it; the practical cost
   * is a warning painted onto a form somebody has already walked away from — or onto the
   * next one they open, about numbers that are not theirs.
   */
  it('ignores an answer about a range that arrives after the form has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : url === QUALITY_URL
          ? new Promise<Response>((resolve, reject) => {
              settle = resolve;
              fail = reject;
            })
          : Promise.resolve(
              jsonResponse(
                200,
                url === ITEMS_URL || url.startsWith(ITEMS_URL) ? WORK_ITEMS : []
              )
            )
    );
    renderRouted(<WorkItems projectId={PROJECT_ID} />, {
      route: `/app/projects/${PROJECT_ID}`
    });
    await screen.findByRole('heading', { name: 'Work' });

    await openEstimate();
    await answerTheThree('12', '3', '5');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    settle(
      jsonResponse(200, {
        consistency: 1.09,
        inconsistent: false,
        overconfident: true
      })
    );

    await openEstimate();
    await answerTheThree('12', '3', '5');
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/Your bad week is barely worse/)).toBeNull()
    );
  });

  it('records a three-point estimate against the item', async () => {
    await open();
    await openEstimate();

    await estimateThrough('12', '3', '5');

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/estimates`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            p10Hours: 3,
            p50Hours: 5,
            p90Hours: 12,
            // The form that asks says how it asked, so the row can be partitioned by it
            // later — which is the only way M8 can ever say whether this milestone
            // changed anything.
            method: 'surprise_framed'
          })
        })
      )
    );
  });

  /** Revising starts from what that person last said, not from three empty questions. */
  it('opens with the reader’s current estimate already in it', async () => {
    await open(WORK_ITEMS, ESTIMATES);
    await openEstimate();

    expect(screen.getByLabelText(BAD_CASE)).toHaveValue(12);
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByLabelText(GOOD_CASE)).toHaveValue(3);
    await userEvent.click(screen.getByRole('button', { name: 'Next' }));
    expect(screen.getByLabelText(TYPICAL_CASE)).toHaveValue(5);
  });

  /**
   * A box nobody filled in is missing rather than zero — `Number('')` is 0, and sending
   * that would have the visitor told their estimate must be more than zero about a field
   * they never touched.
   */
  it('sends an untouched box as nothing rather than as no hours', async () => {
    await open();
    await openEstimate();

    await estimateThrough('', '', '5');

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${WORK_ITEMS[0].id}/estimates`,
        expect.objectContaining({
          body: JSON.stringify({
            p10Hours: null,
            p50Hours: 5,
            p90Hours: null,
            method: 'surprise_framed'
          })
        })
      )
    );
  });

  /**
   * <strong>The refusal that belongs to no single box, on a form where no box is in
   * view.</strong> Each number is fine and the order is not, so it arrives in the banner —
   * and a banner about three numbers the visitor cannot see is useless, so the form goes
   * back to the first question for them to read their own answers in order.
   */
  it('says so when the three numbers do not go up, and goes back to the first question', async () => {
    await open();
    await openEstimate();

    await answerTheThree('12', '5', '3');
    // Armed here rather than before the walk: the review asks the server about the range
    // first, and a `once` mock set earlier would be spent on that instead.
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'estimate_out_of_order' })
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /The three numbers must go up/
    );
    expect(screen.getByLabelText(BAD_CASE)).toHaveValue(12);
  });

  /**
   * And a complaint that <em>does</em> belong to one box brings that box's own question
   * back, rather than being rendered on a screen nobody is looking at. `useFormFailure`
   * suppresses the banner because the field is one this form renders — which is only true
   * once the form has navigated to it.
   */
  it('brings back the question a refused number was answered on', async () => {
    await open();
    await openEstimate();

    await answerTheThree('12', '0', '5');
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { p10Hours: { code: 'positive' } }
      })
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    expect(
      await screen.findByText('Use a number greater than zero.')
    ).toBeInTheDocument();
    expect(screen.getByLabelText(GOOD_CASE)).toHaveValue(0);
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('closes the estimate form when it is cancelled', async () => {
    await open();
    await openEstimate();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText(BAD_CASE)).toBeNull();
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

    expect(await screen.findByText('This is required.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /**
   * <strong>The log made visible, which is half of why it is read at all.</strong> Progress
   * is the one thing on a work item that is written over, and a record nothing ever shows
   * is a record that quietly stops being written correctly — so the form says who last
   * claimed something before inviting somebody to claim something else.
   */
  it('says who last reported on this task', async () => {
    await openWithHistory([
      {
        id: '90909090-9090-9090-9090-909090909090',
        itemId: WORK_ITEMS[0].id,
        reportedById: '11111111-1111-1111-1111-111111111111',
        reportedByName: 'Linus',
        // Two in the morning UTC, which is the previous evening where this suite runs —
        // and a moment is converted where a reported day is not, so this must read as the
        // fourteenth rather than the fifteenth.
        reportedAt: '2026-08-15T02:00:00Z',
        status: 'IN_PROGRESS',
        startedOn: '2026-08-10',
        completedOn: null,
        actualEffortHours: null
      }
    ]);

    expect(
      await screen.findByText('Last reported by Linus on Aug 14, 2026.')
    ).toBeInTheDocument();
  });

  it('says nothing about who reported when nobody has', async () => {
    await openWithHistory([]);

    expect(screen.queryByText(/Last reported by/)).toBeNull();
  });

  /**
   * The history is context and recording progress does not depend on it, so a form that
   * could not fetch it is a form with one fewer line — not a form that refuses to open.
   */
  it('still records progress when the history cannot be read', async () => {
    await open();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.endsWith('/progress') && init?.method === 'GET'
          ? jsonResponse(500, null)
          : jsonResponse(200, WORK_ITEMS)
      )
    );
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );

    expect(await screen.findByLabelText('Status')).toBeInTheDocument();
    expect(screen.queryByText(/Last reported by/)).toBeNull();
  });

  /**
   * <strong>Optional, and the nudge says why rather than pressing.</strong> It is the only
   * number a track record can compare a range against, and refusing to let somebody mark
   * work finished until they can supply it would refuse the common case — so the honest
   * form of the ask is to explain what leaving it empty costs.
   */
  it('says what the actual effort box is for without requiring it', async () => {
    await open();
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');

    expect(
      screen.getByText(
        'Optional, and the one number your track record is built from — without it there is nothing to compare the estimate against.'
      )
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Actual effort (hours)')).not.toBeRequired();
  });

  /**
   * The history is fetched when the form opens and the form can be closed before it lands.
   * Nothing arriving then may touch a form that has gone.
   */
  it('ignores a report history that arrives after the form has closed', async () => {
    await open();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      url.endsWith('/progress') && init?.method === 'GET'
        ? new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
        : Promise.resolve(jsonResponse(200, WORK_ITEMS))
    );
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    settle(
      jsonResponse(200, [
        {
          id: '90909090-9090-9090-9090-909090909090',
          itemId: WORK_ITEMS[0].id,
          reportedById: '11111111-1111-1111-1111-111111111111',
          reportedByName: 'Linus',
          reportedAt: '2026-08-15T02:00:00Z',
          status: 'IN_PROGRESS',
          startedOn: '2026-08-10',
          completedOn: null,
          actualEffortHours: null
        }
      ])
    );

    // And the same on the way out through the failure path, which is the one that would
    // otherwise clear a line belonging to a form somebody has since reopened.
    await userEvent.click(
      await screen.findByRole('button', {
        name: 'Record progress for Migrate the auth service'
      })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/Last reported by/)).toBeNull()
    );
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

    expect(await screen.findByText('This is required.')).toBeInTheDocument();
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
