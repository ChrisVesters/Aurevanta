import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { WorkItemPage } from './WorkItemPage';
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
  CombinedEstimate,
  Dependency,
  Estimate,
  EstimateQuality,
  ProgressReport,
  WorkItem
} from '../projects/types';
import type { Requirement, Resource } from '../resource/types';

const PROJECT = PROJECTS[0];
const ITEM = WORK_ITEMS[0];

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
 * too, deliberately: these strings are the work.
 */
const BAD_CASE =
  'Think of a version of this that goes badly — not a disaster, just a bad week. What number would make you genuinely surprised to have gone over?';
const GOOD_CASE =
  'Now the version where everything goes right. What is the least this could take?';
const TYPICAL_CASE = 'And what do you actually expect it to take?';

/** A pool this organisation has declared, which is what a requirement can name. */
const BACKEND: Resource = {
  id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1',
  name: 'Backend engineers',
  units: 3,
  personId: null,
  personName: null,
  createdAt: '2026-08-01T09:00:00Z',
  archivedAt: null
};

describe('WorkItemPage', () => {
  const fetchMock = mockFetch();

  type Plan = {
    item?: WorkItem;
    siblings?: WorkItem[];
    estimates?: Estimate[];
    dependencies?: Dependency[];
    quality?: EstimateQuality;
    pools?: Resource[];
    needs?: Requirement[];
    history?: ProgressReport[];
    combined?: CombinedEstimate | 'fails';
  };

  /**
   * Answers each URL this page reads separately.
   *
   * **A double that answered them alike would be a lying double**, and here the two that
   * matter most are the pair sharing a path: the item itself and the plan it names come
   * back from different resources, and the progress endpoint returns a work item to a
   * `PATCH` and a list of reports to a `GET`. One answering for the other would hand the
   * page a report to draw as a task.
   */
  function answer(plan: Plan = {}) {
    const item = plan.item ?? ITEM;
    const siblings = plan.siblings ?? WORK_ITEMS;
    fetchMock.mockImplementation((url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';
      if (url === '/api/auth/me') {
        return Promise.resolve(jsonResponse(200, ACCOUNT));
      }
      if (url === '/api/estimates/quality') {
        return Promise.resolve(jsonResponse(200, plan.quality ?? FINE));
      }
      if (url === `/api/projects/${PROJECT.id}`) {
        return Promise.resolve(jsonResponse(200, PROJECT));
      }
      if (url === `/api/projects/${PROJECT.id}/items`) {
        return Promise.resolve(jsonResponse(200, siblings));
      }
      if (url === `/api/projects/${PROJECT.id}/estimates`) {
        return Promise.resolve(jsonResponse(200, plan.estimates ?? []));
      }
      if (url === `/api/projects/${PROJECT.id}/dependencies`) {
        return Promise.resolve(jsonResponse(200, plan.dependencies ?? []));
      }
      if (url.endsWith('/estimates/combined')) {
        return Promise.resolve(
          plan.combined === 'fails'
            ? jsonResponse(500, null)
            : jsonResponse(200, plan.combined ?? { estimators: 0, range: null })
        );
      }
      if (url === '/api/resources') {
        return Promise.resolve(jsonResponse(200, plan.pools ?? []));
      }
      if (url.endsWith('/requirements')) {
        return Promise.resolve(
          jsonResponse(200, method === 'PUT' ? [] : (plan.needs ?? []))
        );
      }
      if (url.endsWith('/progress')) {
        return Promise.resolve(
          jsonResponse(200, method === 'GET' ? (plan.history ?? []) : item)
        );
      }
      return Promise.resolve(jsonResponse(200, item));
    });
  }

  /**
   * The page, on one task.
   *
   * Rendered inside the route it lives at rather than with the identifier handed in, so
   * that `useParams` answers the way it does live — this page's whole address is one item
   * and nothing else.
   */
  async function open(plan: Plan = {}) {
    const item = plan.item ?? ITEM;
    storeAccessToken();
    answer(plan);
    renderRouted(
      <Routes>
        <Route path="/app/items/:itemId" element={<WorkItemPage />} />
      </Routes>,
      { route: `/app/items/${item.id}` }
    );
    await screen.findByRole('heading', { level: 1, name: item.title });
  }

  const openEdit = () =>
    userEvent.click(screen.getByRole('button', { name: 'Edit' }));
  const openEstimate = () =>
    userEvent.click(screen.getByRole('button', { name: 'Estimate' }));
  const openProgress = () =>
    userEvent.click(screen.getByRole('button', { name: 'Progress' }));
  const openNeeds = () =>
    userEvent.click(screen.getByRole('button', { name: 'Needs' }));
  const openOrder = () =>
    userEvent.click(screen.getByRole('button', { name: 'Order' }));

  /**
   * Walking the three questions in the order they are asked, as far as the review. An
   * empty string leaves that question unanswered, which is a different thing from zero and
   * is what the server has to be told apart.
   */
  async function answerTheThree(bad: string, good: string, typical: string) {
    for (const [question, given] of [
      [BAD_CASE, bad],
      [GOOD_CASE, good],
      [TYPICAL_CASE, typical]
    ] as const) {
      if (given !== '') {
        await userEvent.type(screen.getByLabelText(question), given);
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

  // The page itself ---------------------------------------------------------

  it('shows what the task is and what is said about it', async () => {
    await open();

    expect(screen.getByText('Blocked on the vendor')).toBeInTheDocument();
    expect(screen.getByText('Not estimated')).toBeInTheDocument();
    expect(screen.getByText('Not started')).toBeInTheDocument();
  });

  /**
   * **This page's address carries one identifier and the plan comes off the row.** That is
   * `WorkItemController`'s own rule reaching the browser, and it is what this link is built
   * from — so a task read straight from a bookmark still knows where it belongs.
   */
  it('names the plan it belongs to, and links back to it', async () => {
    await open();

    const back = screen.getByRole('link', { name: '← Q3 platform work' });
    expect(back).toHaveAttribute('href', `/app/projects/${PROJECT.id}`);
  });

  it('says so when the task cannot be read', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(404, { code: 'work_item_not_found' })
      )
    );
    renderRouted(
      <Routes>
        <Route path="/app/items/:itemId" element={<WorkItemPage />} />
      </Routes>,
      { route: `/app/items/${ITEM.id}` }
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That task is no longer in this organisation.'
    );
  });

  /**
   * <strong>The read happens in two waves, so there are two places to drop an answer</strong>
   * — and a guard that only covered the second would let the first go on to ask a plan
   * about a task nobody is looking at. This is the first: the task itself lands after the
   * page has gone.
   */
  it('drops a task that arrives after the page has been left', async () => {
    const { pending, unmount } = leftMidRead();
    await screen.findByRole('status');

    unmount();
    pending.get(`/api/items/${ITEM.id}`)?.(jsonResponse(200, ITEM));

    await waitFor(() =>
      expect(screen.queryByText('Migrate the auth service')).toBeNull()
    );
  });

  /** And the second: the task arrived, and its plan lands after the page has gone. */
  it('drops a plan that arrives after the page has been left', async () => {
    const { pending, unmount } = leftMidRead();
    await screen.findByRole('status');
    pending.get(`/api/items/${ITEM.id}`)?.(jsonResponse(200, ITEM));
    await waitFor(() =>
      expect(pending.has(`/api/projects/${PROJECT.id}`)).toBe(true)
    );

    unmount();
    pending.get(`/api/projects/${PROJECT.id}`)?.(jsonResponse(200, PROJECT));
    pending.get(`/api/projects/${PROJECT.id}/items`)?.(
      jsonResponse(200, WORK_ITEMS)
    );
    pending.get(`/api/projects/${PROJECT.id}/estimates`)?.(
      jsonResponse(200, ESTIMATES)
    );
    pending.get(`/api/projects/${PROJECT.id}/dependencies`)?.(
      jsonResponse(200, DEPENDENCIES)
    );
    pending.get(`/api/items/${ITEM.id}/requirements`)?.(jsonResponse(200, []));
    pending.get('/api/resources')?.(jsonResponse(200, []));

    await waitFor(() =>
      expect(screen.queryByText('Migrate the auth service')).toBeNull()
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
    const { unmount } = renderRouted(
      <Routes>
        <Route path="/app/items/:itemId" element={<WorkItemPage />} />
      </Routes>,
      { route: `/app/items/${ITEM.id}` }
    );
    await screen.findByRole('status');

    unmount();
    refuse(jsonResponse(500, null));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  // Rewording it ------------------------------------------------------------

  it('rewords the task', async () => {
    await open();
    await openEdit();

    const box = screen.getByLabelText('Task');
    await userEvent.clear(box);
    await userEvent.type(box, 'Migrate auth');
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ITEM.id}`,
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({
            title: 'Migrate auth',
            description: 'Blocked on the vendor'
          })
        })
      )
    );
    // Closed again, so the page does not fill with open forms.
    await waitFor(() =>
      expect(screen.queryByRole('button', { name: 'Save' })).toBeNull()
    );
  });

  it('leaves the task alone when the edit is cancelled', async () => {
    await open();
    await openEdit();
    // The session, the task, its plan, the plan's work, its arrows, its estimates, what
    // this task needs, and what the organisation has to give it.
    const before = fetchMock.mock.calls.length;
    expect(before).toBe(8);

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText('Task')).toBeNull();
    expect(fetchMock.mock.calls).toHaveLength(before);
  });

  it('says so against the field when a reword is refused', async () => {
    await open();
    await openEdit();
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

  /**
   * Five forms share one page, so a complaint left over from the last one would be the
   * first thing on the next — about numbers or dates that are not what it is talking about.
   */
  it('does not carry one form’s complaint into the next one opened', async () => {
    await open();
    await openEdit();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { title: { code: 'not_blank' } }
      })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    await screen.findByText('This cannot be empty.');

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await openEdit();

    expect(screen.queryByText('This cannot be empty.')).toBeNull();
  });

  // Putting it away ---------------------------------------------------------

  it('puts the task away', async () => {
    await open();

    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ITEM.id}/archive`,
        expect.objectContaining({ method: 'POST' })
      )
    );
  });

  it('brings a put-away task back', async () => {
    await open({ item: ARCHIVED_WORK_ITEM });

    await userEvent.click(screen.getByRole('button', { name: 'Bring back' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ARCHIVED_WORK_ITEM.id}/unarchive`,
        expect.objectContaining({ method: 'POST' })
      )
    );
  });

  /**
   * Put-away work is out of the plan and out of every forecast, so nothing that feeds one
   * is offered against it — which is the same rule the plan's list follows, said once here
   * instead of four times there. What stays is rewording it and bringing it back.
   */
  it('offers nothing a forecast reads on work that has been put away', async () => {
    await open({ item: ARCHIVED_WORK_ITEM });

    expect(screen.getByText(/This task has been put away/)).toBeInTheDocument();
    for (const control of ['Estimate', 'Progress', 'Needs', 'Order']) {
      expect(screen.queryByRole('button', { name: control })).toBeNull();
    }
    expect(screen.getByRole('button', { name: 'Edit' })).toBeInTheDocument();
  });

  it('says so when archiving is refused', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(404, { code: 'work_item_not_found' })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Archive' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That task is no longer in this organisation.'
    );
  });

  // Estimates ---------------------------------------------------------------

  /**
   * **What a page has room for that a row did not.** The plan's list says who else gave a
   * range; this says what they gave, because two people who disagree about a task are
   * saying something that is invisible while only their names are on screen.
   */
  it('shows every current range on the task, the reader’s own first', async () => {
    await open({ estimates: [...ESTIMATES, COLLEAGUES_ESTIMATE] });

    const ranges = within(screen.getByRole('list')).getAllByRole('listitem');
    expect(ranges[0]).toHaveTextContent('Your estimate: 3 / 5 / 12 hours');
    expect(ranges[1]).toHaveTextContent('Bob: 10 / 20 / 40 hours');
    // And nothing about the range somebody gave a different task in the same plan: the
    // endpoint answers for the whole plan, and this page is about one item.
    expect(screen.queryByText(/1 \/ 2 \/ 4 hours/)).toBeNull();
  });

  /**
   * The server orders these by item and estimator, which says nothing about who is
   * reading. Somebody comes here to revise their own range, so theirs is the one that must
   * not have to be found among a column of colleagues.
   */
  it('puts the reader’s own range first however the server listed them', async () => {
    await open({ estimates: [ESTIMATES[1], ESTIMATES[0]] });

    const ranges = within(screen.getByRole('list')).getAllByRole('listitem');
    expect(ranges[0]).toHaveTextContent('Your estimate: 3 / 5 / 12 hours');
    expect(ranges[1]).toHaveTextContent('Bob: 10 / 20 / 40 hours');
  });

  /** And where the reader has given none, the colleagues keep the order they arrived in. */
  it('shows colleagues’ ranges where the reader has given none', async () => {
    const carol: Estimate = {
      ...ESTIMATES[1],
      id: '30303030-3030-3030-3030-303030303030',
      estimatorId: '40404040-4040-4040-4040-404040404040',
      estimatorName: 'Carol',
      p10Hours: 4,
      p50Hours: 6,
      p90Hours: 9
    };
    await open({ estimates: [ESTIMATES[1], carol] });

    const ranges = within(screen.getByRole('list')).getAllByRole('listitem');
    expect(ranges[0]).toHaveTextContent('Bob: 10 / 20 / 40 hours');
    expect(ranges[1]).toHaveTextContent('Carol: 4 / 6 / 9 hours');
  });

  // Read together -----------------------------------------------------------

  const COMBINED_URL = `/api/items/${ITEM.id}/estimates/combined`;

  /** Ada says 3 to 12, Bob 10 to 40 — what the forecast reads from the two of them. */
  const TOGETHER: CombinedEstimate = {
    estimators: 2,
    range: { p10Hours: 4.1, p50Hours: 11.62, p90Hours: 33.8 }
  };

  const combinedReads = () =>
    fetchMock.mock.calls.filter(([url]) => url === COMBINED_URL);

  /**
   * Beside the ranges and never instead of them, with a line saying it is not an average.
   * The numbers are the server's: the browser holds no part of the model.
   */
  it('says what several ranges come to together', async () => {
    await open({ estimates: ESTIMATES, combined: TOGETHER });

    expect(
      await screen.findByText('Taken together: 4.1 / 11.62 / 33.8 hours')
    ).toBeInTheDocument();
    expect(screen.getByText(/It is not an average/)).toBeInTheDocument();
    expect(screen.getByText('Bob: 10 / 20 / 40 hours')).toBeInTheDocument();
  });

  /** One person's range is already everything a combination of it would say. */
  it('asks for no combination where only one person has estimated', async () => {
    await open({ estimates: [ESTIMATES[0]], combined: TOGETHER });

    expect(
      screen.getByText('Your estimate: 3 / 5 / 12 hours')
    ).toBeInTheDocument();
    expect(combinedReads()).toHaveLength(0);
    expect(screen.queryByText(/Taken together/)).toBeNull();
  });

  /** Made of everybody else's numbers, so it leaves with them while somebody answers. */
  it('takes the combination off the screen while somebody is answering', async () => {
    await open({ estimates: ESTIMATES, combined: TOGETHER });
    await screen.findByText(/Taken together/);

    await openEstimate();

    expect(screen.getByLabelText(BAD_CASE)).toBeInTheDocument();
    expect(screen.queryByText(/Taken together/)).toBeNull();
  });

  /** Put-away work shows no estimates at all, so there is nothing to combine. */
  it('asks for no combination on work that has been put away', async () => {
    await open({
      item: ARCHIVED_WORK_ITEM,
      estimates: ESTIMATES.map((range) => ({
        ...range,
        itemId: ARCHIVED_WORK_ITEM.id
      })),
      combined: TOGETHER
    });

    expect(combinedReads()).toHaveLength(0);
    expect(screen.queryByText(/Taken together/)).toBeNull();
  });

  /** The combination is context: losing it leaves the ranges, and says nothing more. */
  it('leaves the ranges alone when the combination cannot be read', async () => {
    await open({ estimates: ESTIMATES, combined: 'fails' });

    await waitFor(() => expect(combinedReads()).toHaveLength(1));
    expect(screen.getByText('Bob: 10 / 20 / 40 hours')).toBeInTheDocument();
    expect(screen.queryByText(/Taken together/)).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /** A revision changes what the ranges come to, so saving one asks again. */
  it('asks for the combination again once an estimate is saved', async () => {
    await open({ estimates: ESTIMATES, combined: TOGETHER });
    await waitFor(() => expect(combinedReads()).toHaveLength(1));

    await openEstimate();
    await estimateThrough('20', '6', '9');

    await waitFor(() => expect(combinedReads()).toHaveLength(2));
  });

  /** Ranges that changed under it between two reads leave nothing to say. */
  it('says nothing where the server found nothing to combine', async () => {
    await open({ estimates: ESTIMATES });

    await waitFor(() => expect(combinedReads()).toHaveLength(1));
    expect(screen.queryByText(/Taken together/)).toBeNull();
  });

  it('says so when nobody has estimated it', async () => {
    await open();

    expect(screen.getByText('Not estimated')).toBeInTheDocument();
  });

  /**
   * <strong>The order is the work, and this is what holds it.</strong> The bad case
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
   * **The one thing a task's own page could easily have got wrong.** A row had space to
   * name a colleague and nothing else; a page has space to print their numbers — and two
   * people who anchored on each other are not two estimates, so the moment somebody starts
   * answering, what everybody else said leaves the screen. This is the assertion that fails
   * if the ranges are ever left beside the form.
   */
  it('never shows a colleague’s numbers while somebody is answering', async () => {
    await open({ estimates: ESTIMATES });
    expect(screen.getByText('Bob: 10 / 20 / 40 hours')).toBeInTheDocument();

    await openEstimate();

    for (const question of [BAD_CASE, GOOD_CASE, TYPICAL_CASE]) {
      expect(screen.getByLabelText(question)).toBeInTheDocument();
      expect(screen.queryByText('Bob: 10 / 20 / 40 hours')).toBeNull();
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
   * no change on this side.
   */
  it('shows the warnings the server sent, whatever the numbers were', async () => {
    await open({
      quality: { consistency: 1.09, inconsistent: false, overconfident: true }
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
    await open({
      quality: { consistency: 0.707, inconsistent: true, overconfident: false }
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
   * <strong>Advice, never a refusal.</strong> A tight band is sometimes exactly right, and
   * a rule that blocked one would become a specification people learn to type, which is
   * 3/5/8 with an extra step.
   */
  it('saves a warned estimate exactly as it was given', async () => {
    await open({
      quality: { consistency: 1.09, inconsistent: false, overconfident: true }
    });
    await openEstimate();

    await estimateThrough('14', '6', '10');

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ITEM.id}/estimates`,
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
   * and nothing arriving afterwards may touch it. The practical cost is a warning painted
   * onto a form somebody has already walked away from — or onto the next one they open,
   * about numbers that are not theirs.
   */
  it('ignores an answer about a range that arrives after the form has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    answer();
    const plain = fetchMock.getMockImplementation();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      url === '/api/estimates/quality'
        ? new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
        : (plain as (u: string, i?: RequestInit) => Promise<Response>)(
            url,
            init
          )
    );
    renderRouted(
      <Routes>
        <Route path="/app/items/:itemId" element={<WorkItemPage />} />
      </Routes>,
      { route: `/app/items/${ITEM.id}` }
    );
    await screen.findByRole('heading', { level: 1, name: ITEM.title });

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

  it('records a three-point estimate against the task', async () => {
    await open();
    await openEstimate();

    await estimateThrough('12', '3', '5');

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ITEM.id}/estimates`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            p10Hours: 3,
            p50Hours: 5,
            p90Hours: 12,
            // The form that asks says how it asked, so the row can be partitioned by it
            // later — which is the only way calibration can ever say whether this work
            // changed anything.
            method: 'surprise_framed'
          })
        })
      )
    );
  });

  /** Revising starts from what that person last said, not from three empty questions. */
  it('opens with the reader’s current estimate already in it', async () => {
    await open({ estimates: ESTIMATES });
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
        `/api/items/${ITEM.id}/estimates`,
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

  /**
   * **An estimate is what somebody said before they found out.** The server refuses one on
   * work that has begun, and a screen that still offered the form would walk somebody
   * through four questions to turn them away at the end — which is `progress_not_applicable`
   * seen from the same side.
   */
  it('offers no estimate on work that has been started', async () => {
    await open({ item: WORK_ITEMS[1] });

    expect(screen.queryByRole('button', { name: 'Estimate' })).toBeNull();
    expect(
      screen.getByText(/Started work is no longer estimated/)
    ).toBeInTheDocument();
  });

  it('offers no estimate on work that is finished either', async () => {
    await open({
      item: { ...ITEM, status: 'DONE', completedOn: '2026-08-14' }
    });

    expect(screen.queryByRole('button', { name: 'Estimate' })).toBeNull();
  });

  /**
   * What was said before the work began still stands, and is still worth reading: the rule
   * closes estimating, it does not hide the estimate. Nothing rewrites one of these rows,
   * which is what a track record is built from.
   */
  it('still shows what was estimated before the work began', async () => {
    await open({ item: WORK_ITEMS[1], estimates: [COLLEAGUES_ESTIMATE] });

    expect(screen.getByText('Bob: 1 / 2 / 4 hours')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Estimate' })).toBeNull();
  });

  /**
   * Reachable even though the form is not offered on started work: a colleague can mark it
   * started while somebody is part-way through the three questions. The wording says what
   * changed rather than what the visitor did wrong, because they did nothing wrong.
   */
  it('says so when a colleague starts the work mid-answer', async () => {
    await open();
    await openEstimate();

    await answerTheThree('12', '3', '5');
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, { code: 'work_already_started' })
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save estimate' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /This work has been started, so it can no longer be estimated/
    );
  });

  it('closes the estimate form when it is cancelled', async () => {
    await open();
    await openEstimate();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText(BAD_CASE)).toBeNull();
  });

  // Progress ----------------------------------------------------------------

  it('says how far along the task is', async () => {
    await open({ item: WORK_ITEMS[1] });

    // A status with no date beside it is a claim nobody has to stand behind.
    expect(
      screen.getByText('In progress since Aug 10, 2026')
    ).toBeInTheDocument();
  });

  it('says when finished work finished, and what it took where anybody measured it', async () => {
    await open({
      item: {
        ...ITEM,
        status: 'DONE',
        startedOn: '2026-08-10',
        completedOn: '2026-08-14',
        actualEffortHours: 6.5
      }
    });

    expect(
      screen.getByText('Done Aug 14, 2026 · took 6.5 hours')
    ).toBeInTheDocument();
  });

  it('records what has happened to the task', async () => {
    await open();
    await openProgress();

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');
    await userEvent.type(screen.getByLabelText('Finished on'), '2026-08-14');
    await userEvent.type(screen.getByLabelText('Actual effort (hours)'), '6.5');
    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ITEM.id}/progress`,
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

  /** Revising starts from what the task already says, not from an empty form. */
  it('opens with what the task already records', async () => {
    await open({ item: WORK_ITEMS[1] });
    await openProgress();

    expect(screen.getByLabelText('Status')).toHaveValue('IN_PROGRESS');
    expect(screen.getByLabelText('Started on')).toHaveValue('2026-08-10');
  });

  /**
   * The refusal that belongs to no single box: which date is missing depends on the status
   * in another one, so it arrives in the banner rather than against a field.
   */
  it('says so when a state is claimed without the date that supports it', async () => {
    await open();
    await openProgress();
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
    await openProgress();

    expect(screen.getByLabelText('Status')).toHaveValue('NOT_STARTED');
    expect(screen.queryByLabelText('Actual effort (hours)')).toBeNull();
    expect(screen.queryByLabelText('Started on')).toBeNull();
    expect(screen.queryByLabelText('Finished on')).toBeNull();
  });

  /** Work under way has taken hours already; it simply has no date it finished on. */
  it('offers effort and a start, but no completion, on work in progress', async () => {
    await open();
    await openProgress();

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
    await open({
      item: {
        ...ITEM,
        status: 'DONE',
        startedOn: '2026-08-10',
        completedOn: '2026-08-14',
        actualEffortHours: 6.5
      }
    });
    await openProgress();

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'NOT_STARTED'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save progress' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/items/${ITEM.id}/progress`,
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
    await open({
      item: {
        ...ITEM,
        status: 'DONE',
        startedOn: '2026-08-10',
        completedOn: '2026-08-14',
        actualEffortHours: 6.5
      }
    });
    await openProgress();
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
    await open({
      item: { ...ITEM, status: 'DONE', completedOn: '2026-08-14' }
    });
    await openProgress();

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'NOT_STARTED'
    );

    expect(screen.getByText(/Saving this discards/)).toBeInTheDocument();
  });

  /** Nothing recorded, nothing to lose: the warning would be about no data at all. */
  it('says nothing about discarding when there is nothing recorded', async () => {
    await open();
    await openProgress();

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');

    expect(screen.queryByText(/Saving this discards/)).toBeNull();
  });

  /**
   * **Said before saving rather than discovered afterwards**, which is the rule the discard
   * warning follows too. Starting the work is what closes estimating, and the person about
   * to make that true is standing in front of this form.
   */
  it('warns that starting the work closes estimating', async () => {
    await open();
    await openProgress();
    expect(screen.queryByText(/no longer be estimated/)).toBeNull();

    await userEvent.selectOptions(
      screen.getByLabelText('Status'),
      'IN_PROGRESS'
    );

    expect(
      screen.getByText(/Once this is started it can no longer be estimated/)
    ).toBeInTheDocument();
  });

  /** And the sharper half, where there is nothing to close over. */
  it('says what starting unestimated work costs a forecast', async () => {
    await open();
    await openProgress();

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');

    expect(
      screen.getByText(/every forecast will carry it at no effort and say so/)
    ).toBeInTheDocument();
  });

  it('says nothing about a forecast where somebody has already estimated it', async () => {
    await open({ estimates: ESTIMATES });
    await openProgress();

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');

    expect(
      screen.getByText(/Once this is started it can no longer be estimated/)
    ).toBeInTheDocument();
    expect(screen.queryByText(/at no effort and say so/)).toBeNull();
  });

  /**
   * Only on the way *out* of not-started. Work already under way has already closed, and
   * repeating it on every save would be noise about a decision nobody is taking.
   */
  it('says nothing about closing estimating on work already under way', async () => {
    await open({ item: WORK_ITEMS[1] });
    await openProgress();

    await userEvent.selectOptions(screen.getByLabelText('Status'), 'DONE');

    expect(screen.queryByText(/no longer be estimated/)).toBeNull();
  });

  it('says so when work would finish before it began', async () => {
    await open();
    await openProgress();
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
    await openProgress();
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
    await openProgress();
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
    await open({
      history: [
        {
          id: '90909090-9090-9090-9090-909090909090',
          itemId: ITEM.id,
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
      ]
    });
    await openProgress();

    expect(
      await screen.findByText('Last reported by Linus on Aug 14, 2026.')
    ).toBeInTheDocument();
  });

  it('says nothing about who reported when nobody has', async () => {
    await open();
    await openProgress();

    expect(screen.queryByText(/Last reported by/)).toBeNull();
  });

  /**
   * The history is context and recording progress does not depend on it, so a form that
   * could not fetch it is a form with one fewer line — not a form that refuses to open.
   */
  it('still records progress when the history cannot be read', async () => {
    await open();
    const plain = fetchMock.getMockImplementation();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      url.endsWith('/progress') && (init?.method ?? 'GET') === 'GET'
        ? Promise.resolve(jsonResponse(500, null))
        : (plain as (u: string, i?: RequestInit) => Promise<Response>)(
            url,
            init
          )
    );

    await openProgress();

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
    await openProgress();
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
    const plain = fetchMock.getMockImplementation();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      url.endsWith('/progress') && (init?.method ?? 'GET') === 'GET'
        ? new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
        : (plain as (u: string, i?: RequestInit) => Promise<Response>)(
            url,
            init
          )
    );

    await openProgress();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    settle(
      jsonResponse(200, [
        {
          id: '90909090-9090-9090-9090-909090909090',
          itemId: ITEM.id,
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
    await openProgress();
    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/Last reported by/)).toBeNull()
    );
  });

  it('closes the progress form when it is cancelled', async () => {
    await open();
    await openProgress();

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText('Status')).toBeNull();
  });

  // What it needs -----------------------------------------------------------

  it('says what the task already needs', async () => {
    await open({
      pools: [BACKEND],
      needs: [
        {
          workItemId: ITEM.id,
          resourceId: BACKEND.id,
          resourceName: 'Backend engineers',
          resourceArchived: false,
          units: 2
        }
      ]
    });

    expect(screen.getByText('Backend engineers × 2')).toBeInTheDocument();
  });

  /**
   * An empty set is a claim rather than an omission, so the page says which claim it is
   * rather than leaving a blank where a list would be.
   */
  it('says so when the task needs nothing in particular', async () => {
    await open({ pools: [BACKEND] });

    expect(screen.getByText(/Anybody can pick this up/)).toBeInTheDocument();
  });

  /** A pool the team has put away is named as such, since that is a thing to act on. */
  it('marks a need on a pool the team has put away', async () => {
    await open({
      needs: [
        {
          workItemId: ITEM.id,
          resourceId: 'a9a9a9a9-a9a9-a9a9-a9a9-a9a9a9a9a9a9',
          resourceName: 'Staging environment',
          resourceArchived: true,
          units: 1
        }
      ]
    });

    expect(
      screen.getByText('Staging environment × 1 — put away')
    ).toBeInTheDocument();
  });

  it('asks how many of each pool a task ties up', async () => {
    await open({ pools: [BACKEND] });

    await openNeeds();

    expect(screen.getByLabelText('Backend engineers')).toBeInTheDocument();
    expect(screen.getByText('3 available')).toBeInTheDocument();
  });

  /** And it starts from what the work already needs, rather than from nothing. */
  it('starts from what the work already needs', async () => {
    await open({
      pools: [BACKEND],
      needs: [
        {
          workItemId: ITEM.id,
          resourceId: BACKEND.id,
          resourceName: 'Backend engineers',
          resourceArchived: false,
          units: 2
        }
      ]
    });

    await openNeeds();

    expect(screen.getByLabelText('Backend engineers')).toHaveValue(2);
  });

  /**
   * <strong>The endpoint replaces the whole set, so a row this form does not render is a
   * row pressing save deletes.</strong> A pool the team has put away is left out of the
   * listing the form is built from — so before this, opening the panel on work that named
   * one and saving destroyed that requirement without a word on screen about it.
   */
  it('keeps a requirement on a pool the team has put away, and says it is put away', async () => {
    await open({
      pools: [BACKEND],
      needs: [
        {
          workItemId: ITEM.id,
          resourceId: 'a9a9a9a9-a9a9-a9a9-a9a9-a9a9a9a9a9a9',
          resourceName: 'Staging environment',
          resourceArchived: true,
          units: 1
        }
      ]
    });
    await openNeeds();

    expect(screen.getByLabelText('Staging environment')).toHaveValue(1);
    expect(
      screen.getByText('Put away — this still needs it')
    ).toBeInTheDocument();

    await userEvent.click(
      screen.getByRole('button', { name: 'Save what it needs' })
    );

    expect(sentBody('PUT')).toEqual({
      needs: [{ resourceId: 'a9a9a9a9-a9a9-a9a9-a9a9-a9a9a9a9a9a9', units: 1 }]
    });
  });

  /** And it can be cleared, which is the one place anybody can act on it. */
  it('lets a requirement on a put-away pool be cleared', async () => {
    await open({
      needs: [
        {
          workItemId: ITEM.id,
          resourceId: 'a9a9a9a9-a9a9-a9a9-a9a9-a9a9a9a9a9a9',
          resourceName: 'Staging environment',
          resourceArchived: true,
          units: 1
        }
      ]
    });
    await openNeeds();

    expect(
      screen.getByText(
        /A resource that has been put away is left out of forecasts/
      )
    ).toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Staging environment'));
    await userEvent.click(
      screen.getByRole('button', { name: 'Save what it needs' })
    );

    expect(sentBody('PUT')).toEqual({ needs: [] });
  });

  it('sends the whole set in place of whatever was there', async () => {
    await open({ pools: [BACKEND] });
    await openNeeds();
    await userEvent.type(screen.getByLabelText('Backend engineers'), '2');

    await userEvent.click(
      screen.getByRole('button', { name: 'Save what it needs' })
    );

    const sent = fetchMock.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === 'PUT'
    );
    expect(String(sent?.[0])).toBe(`/api/items/${ITEM.id}/requirements`);
    expect(sentBody('PUT')).toEqual({
      needs: [{ resourceId: BACKEND.id, units: 2 }]
    });
  });

  /**
   * <strong>An empty set is a claim rather than an omission</strong>, and the form says so
   * where somebody is deciding: work that names nothing is scheduled against one unit of
   * whatever is free, which is a different thing from work outside the competition for a
   * team altogether.
   */
  it('sends an empty set when every box is left empty, and says what that means', async () => {
    await open({ pools: [BACKEND] });
    await openNeeds();

    expect(
      screen.getByText(/Leave them all empty and anybody can pick this up/)
    ).toBeInTheDocument();
    await userEvent.click(
      screen.getByRole('button', { name: 'Save what it needs' })
    );

    expect(sentBody('PUT')).toEqual({ needs: [] });
  });

  /**
   * Nothing to need yet says where to go and declare something, rather than showing an
   * empty form somebody cannot fill in.
   */
  it('says where to declare a team when there is none', async () => {
    await open();

    await openNeeds();

    expect(
      screen.getByText(/Nothing has been declared to need yet/)
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Save what it needs' })
    ).toBeNull();
  });

  it('closes the needs form without asking the server anything when it is cancelled', async () => {
    await open({ pools: [BACKEND] });
    await openNeeds();
    const before = fetchMock.mock.calls.length;

    await userEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(screen.queryByLabelText('Backend engineers')).toBeNull();
    expect(fetchMock.mock.calls).toHaveLength(before);
  });

  /**
   * The two refusals a set can meet — the same pool twice, and one that is not there —
   * belong to the set rather than to a box, so they arrive in the banner.
   */
  it('says why a set was refused', async () => {
    await open({ pools: [BACKEND] });
    await openNeeds();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'duplicate_requirement' })
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Save what it needs' })
    );

    expect(
      await screen.findByText(/That work already needs that resource/)
    ).toBeInTheDocument();
  });

  // Ordering ----------------------------------------------------------------

  /**
   * Both directions, because neither is redundant: what a delay here would hold up, and
   * why this has not started.
   */
  it('says what the task comes before and what it is waiting on', async () => {
    await open({ item: WORK_ITEMS[1], dependencies: DEPENDENCIES });

    expect(
      screen.getByText('Waiting on Migrate the auth service')
    ).toBeInTheDocument();
  });

  it('says so when the task is ordered against nothing', async () => {
    await open();

    expect(
      screen.getByText('Nothing waits on this, and it waits on nothing.')
    ).toBeInTheDocument();
  });

  it('draws an arrow from the task the page is about', async () => {
    await open();
    await openOrder();

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
            predecessorItemId: ITEM.id,
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
    await open({ siblings: THREE_ITEMS });
    await openOrder();
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    answer({ siblings: THREE_ITEMS, dependencies: DEPENDENCIES });

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    expect(
      await screen.findByRole('button', {
        name: 'Stop requiring this to finish before Write the runbook'
      })
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Must finish before')).toBeInTheDocument();
  });

  /** So the next arrow starts from empty boxes rather than the last one's wait. */
  it('empties the boxes once an arrow has landed', async () => {
    await open({ siblings: THREE_ITEMS });
    await openOrder();
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    await userEvent.type(screen.getByLabelText('Wait afterwards (hours)'), '8');
    answer({ siblings: THREE_ITEMS, dependencies: DEPENDENCIES });

    await userEvent.click(screen.getByRole('button', { name: 'Add' }));

    await waitFor(() =>
      expect(screen.getByLabelText('Wait afterwards (hours)')).toHaveValue(null)
    );
    expect(screen.getByLabelText('Must finish before')).toHaveValue('');
  });

  /** A refusal does not reload, so it leaves what was typed exactly where it is. */
  it('leaves the boxes alone when an arrow is refused', async () => {
    await open({ siblings: THREE_ITEMS });
    await openOrder();
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
    await open();
    await openOrder();

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
    await open({ item: WORK_ITEMS[1], dependencies: DEPENDENCIES });
    await openOrder();

    const choices = within(
      screen.getByLabelText('Must finish before')
    ).getAllByRole('option');
    expect(choices.map((choice) => choice.textContent)).toEqual([
      'Choose a task…',
      'Migrate the auth service'
    ]);
  });

  it('says so when there is nothing left to order a task against', async () => {
    await open({ dependencies: DEPENDENCIES });

    await openOrder();

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
    await open();
    await openOrder();
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        code: 'dependency_cycle',
        path: [ITEM.id, WORK_ITEMS[1].id]
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
    await open();
    await openOrder();
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
    await open({ dependencies: DEPENDENCIES });
    await openOrder();

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
    await open({ dependencies: [{ ...DEPENDENCIES[0], lagHours: 8 }] });

    await openOrder();

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
    await open({
      dependencies: [
        { ...DEPENDENCIES[0], successorItemId: ARCHIVED_WORK_ITEM.id }
      ]
    });

    expect(
      screen.getByText('Must finish before a task that has been put away')
    ).toBeInTheDocument();

    await openOrder();

    expect(
      screen.getByRole('button', {
        name: 'Stop requiring this to finish before a task that has been put away'
      })
    ).toBeInTheDocument();
  });

  /** And the same where the loop a refusal names runs through work that is put away. */
  it('names a loop that runs through work put away since', async () => {
    await open();
    await openOrder();
    await userEvent.selectOptions(
      screen.getByLabelText('Must finish before'),
      WORK_ITEMS[1].id
    );
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, {
        code: 'dependency_cycle',
        path: [ITEM.id, ARCHIVED_WORK_ITEM.id]
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
    await open();
    await openOrder();
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
    await open({ dependencies: DEPENDENCIES });
    await openOrder();
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
    await open();
    await openOrder();

    await userEvent.click(screen.getByRole('button', { name: 'Done' }));

    expect(screen.queryByLabelText('Must finish before')).toBeNull();
  });

  /**
   * The page part-way through its read, with every answer held back by URL so a case can
   * decide which of the two waves it is interrupting.
   */
  function leftMidRead() {
    storeAccessToken();
    const pending = new Map<string, (value: Response) => void>();
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => pending.set(url, resolve))
    );
    const { unmount } = renderRouted(
      <Routes>
        <Route path="/app/items/:itemId" element={<WorkItemPage />} />
      </Routes>,
      { route: `/app/items/${ITEM.id}` }
    );
    return { pending, unmount };
  }

  /** What the last request of one method carried, which is what a whole-set save is. */
  function sentBody(method: string) {
    const sent = fetchMock.mock.calls.findLast(
      (call) => (call[1] as RequestInit | undefined)?.method === method
    );
    return JSON.parse(String((sent?.[1] as RequestInit).body)) as unknown;
  }
});
