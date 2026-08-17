import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TargetDate } from './TargetDate';
import {
  ACCOUNT,
  CUT_OPTIONS,
  FORECAST,
  WORK_ITEMS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { CutOptions, Forecast, WorkItem } from './types';

const ITEMS_URL = `/api/projects/${FORECAST.projectId}/items`;
const CUTS_URL = `/api/forecasts/${FORECAST.id}/cuts`;

describe('TargetDate', () => {
  const fetchMock = mockFetch();

  /**
   * The session, the plan's work, and whatever the cuts endpoint answers — three URLs and
   * three answers. A double that answered them alike would hand the tick list an account
   * and every assertion below would be about the wrong screen.
   */
  function answer(
    items: WorkItem[] = WORK_ITEMS,
    cuts: unknown = CUT_OPTIONS,
    status = 200
  ) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === ITEMS_URL
            ? jsonResponse(200, items)
            : url === CUTS_URL
              ? jsonResponse(status, cuts)
              : jsonResponse(404)
      )
    );
  }

  async function open(run: Forecast = FORECAST) {
    storeAccessToken();
    renderRouted(<TargetDate run={run} />);
    await screen.findByRole('heading', { name: 'Can we hit a date?' });
  }

  function asking() {
    return screen.getByRole('button', { name: 'What would it take?' });
  }

  /** Naming a day, ticking one piece of work, and asking. */
  async function ask(day = '2026-09-30', confidence = '85') {
    await userEvent.type(screen.getByLabelText('We want it done by'), day);
    await userEvent.type(
      screen.getByLabelText('How sure do you need to be?'),
      confidence
    );
    await userEvent.click(await screen.findByLabelText(WORK_ITEMS[0].title));
    await userEvent.click(asking());
  }

  /** Every request that carried a body, which is every request that asked anything. */
  function posted() {
    return fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST');
  }

  /**
   * <strong>Nothing goes out until there is a question.</strong> A target with no date is
   * not a question, and a target with nothing droppable asks only whether the plan already
   * gets there — which the band above has already answered. Both halves are the question
   * rather than options on it, so the button stays shut rather than producing a refusal
   * about a box somebody has not reached yet.
   */
  it('asks nothing until there is a date and something to drop', async () => {
    answer();
    await open();

    expect(asking()).toBeDisabled();

    await userEvent.click(await screen.findByLabelText(WORK_ITEMS[0].title));
    expect(asking()).toBeDisabled();

    await userEvent.type(
      screen.getByLabelText('We want it done by'),
      '2026-09-30'
    );
    expect(asking()).toBeEnabled();
    expect(posted()).toHaveLength(0);
  });

  /**
   * <strong>Decision 7 on screen, which is the whole reason this panel has two lists.</strong>
   * The singles are what each buys <em>alone</em> — 41.4 and 23.8 points here — and the set
   * that reaches the bar was measured at 88.1, not at their total. They are in separate
   * sections under separate headings, the singles say what they are, and the caveat arrives
   * before the numbers rather than after them, because a reader who has already added two
   * together has already been given the wrong answer.
   */
  it('keeps what a set was measured at apart from what each buys alone', async () => {
    answer();
    await open();

    await ask();

    const together = await screen.findByRole('heading', {
      name: 'What it would take'
    });
    const singles = screen.getByRole('heading', {
      name: 'What each would buy on its own'
    });
    expect(together).toBeInTheDocument();
    expect(singles).toBeInTheDocument();
    expect(
      screen.getByText('Drop Migrate the auth service — 71.8%.')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Drop Write the runbook — 88.1%.')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Migrate the auth service — 71.8%, 41.4 points better.')
    ).toBeInTheDocument();
    expect(screen.getByText(/These do not add up\./)).toBeInTheDocument();
    // Never one column: the set that was measured is an ordered list of steps, the
    // singles are a ranking, and nothing anywhere puts them side by side where the two
    // could be added.
    const steps = screen.getByRole('list', { name: 'What it would take' });
    expect(within(steps).getAllByRole('listitem')).toHaveLength(2);
    expect(within(steps).queryByText(/points better/)).toBeNull();
  });

  /**
   * <strong>Whether the bar is already met comes first, and nothing is proposed when it
   * is.</strong> A screen leading with a list of work to drop would have suggested a
   * sacrifice before mentioning it was unnecessary.
   */
  it('says the bar is already met and proposes nothing', async () => {
    const met: CutOptions = {
      ...CUT_OPTIONS,
      meets: true,
      baselineConfidence: 96.2,
      cuts: [],
      together: { steps: [], ending: 'met' }
    };
    answer(WORK_ITEMS, met);
    await open();

    await ask();

    expect(
      await screen.findByText(
        'This plan already gets there: 96.2% of its runs came in by that date.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText('What it would take')).toBeNull();
    expect(screen.queryByText('What each would buy on its own')).toBeNull();
  });

  /**
   * <strong>The ending is as much of the answer as the list is.</strong> Everything on offer
   * dropped and the date still out of reach is a different answer from a set that gets
   * there, and what to do next differs: put something else on the table rather than accept
   * the list.
   */
  it('says when even dropping everything offered does not get there', async () => {
    answer(WORK_ITEMS, {
      ...CUT_OPTIONS,
      together: { ...CUT_OPTIONS.together, ending: 'nothing_left' }
    });
    await open();

    await ask();

    expect(
      await screen.findByText(/Even with all of that dropped/)
    ).toBeInTheDocument();
  });

  /**
   * The third ending, and the one that is easiest to read as the second. A search that ran
   * out of the runs it is allowed has looked no further, which is not the same as there
   * being nothing further to find — so it says so, and says what to do about it.
   */
  it('says when the search ran out of the simulations it is allowed', async () => {
    answer(WORK_ITEMS, {
      ...CUT_OPTIONS,
      simulations: 34,
      together: { ...CUT_OPTIONS.together, ending: 'budget_spent' }
    });
    await open();

    await ask();

    expect(
      await screen.findByText(/That is as far as this looked/)
    ).toBeInTheDocument();
  });

  /**
   * <strong>The date became hours through this run's own calendar, and the answer says
   * which.</strong> M4's rule about a stated assumption arriving beside the number it
   * produced, in the one place on this screen where the number is a recommendation.
   */
  it('states what the date came to and how much simulating it took', async () => {
    answer();
    await open();

    await ask();

    expect(
      await screen.findByText(
        "That date is 18 hours of work under this run's own calendar, measured over 4 runs of the plan."
      )
    ).toBeInTheDocument();
  });

  /**
   * Two different questions get two different answers, and both halves of the question are
   * sent. Without this the control could be ignored entirely and every assertion above would
   * still pass against a fixture that never moved.
   */
  it('sends the date and the confidence that were chosen', async () => {
    answer();
    await open();

    await ask('2026-09-30', '85');
    await screen.findByRole('heading', { name: 'What it would take' });

    expect(JSON.parse(posted()[0][1].body)).toEqual({
      by: '2026-09-30',
      confidence: 85,
      candidates: [WORK_ITEMS[0].id]
    });

    await userEvent.clear(screen.getByLabelText('How sure do you need to be?'));
    await userEvent.type(
      screen.getByLabelText('How sure do you need to be?'),
      '50'
    );
    await userEvent.click(asking());

    await screen.findByRole('heading', { name: 'What it would take' });
    expect(JSON.parse(posted()[1][1].body)).toEqual({
      by: '2026-09-30',
      confidence: 50,
      candidates: [WORK_ITEMS[0].id]
    });
  });

  /**
   * <strong>Only work the run was actually about may be ticked.</strong> The server refuses
   * to weigh anything else, and a tick box that is refused after it is ticked is a trap
   * rather than a check — the same rule the progress form keeps when it offers only the
   * boxes a status has room for.
   */
  it('does not offer work written down since the forecast', async () => {
    const since: WorkItem = {
      ...WORK_ITEMS[0],
      id: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
      title: 'Thought of afterwards',
      createdAt: '2026-08-15T08:00:00Z'
    };
    answer([...WORK_ITEMS, since]);
    await open();

    expect(
      await screen.findByLabelText(WORK_ITEMS[0].title)
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Thought of afterwards')).toBeNull();
  });

  /** A forecast older than everything now in the plan has nothing it could weigh. */
  it('says so when there is nothing the forecast could weigh', async () => {
    answer([]);
    await open();

    expect(
      await screen.findByText(
        'None of the work now in this plan was in this forecast, so there is nothing here it could weigh. Ask for a new forecast and there will be.'
      )
    ).toBeInTheDocument();
    expect(asking()).toBeDisabled();
  });

  /**
   * <strong>A tick list that failed to load must not read as an empty one.</strong> The two
   * look identical on screen, and the second of them is an answer — "there is nothing you
   * could drop" — which is the one thing this panel must never say by accident.
   */
  it('says so when the work it would offer could not be loaded', async () => {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(404, { code: 'project_not_found' })
      )
    );
    await open();

    expect(
      await screen.findByText('That project is no longer in this organisation.')
    ).toBeInTheDocument();
    expect(
      screen.queryByText(
        'None of the work now in this plan was in this forecast, so there is nothing here it could weigh. Ask for a new forecast and there will be.'
      )
    ).toBeNull();
  });

  /**
   * <strong>A complaint about a box goes against that box, and the banner stays quiet.</strong>
   * Both fields this form renders are on screen while it is being answered, so a banner
   * repeating what an input already says would be the same refusal read twice — which is the
   * rule `useFormFailure` exists to keep and the one a form that handled only the banner
   * quietly breaks.
   */
  it('puts a complaint about a box against that box', async () => {
    answer(
      WORK_ITEMS,
      {
        code: 'validation_failed',
        detail: 'Invalid request',
        errors: {
          by: { code: 'not_null' },
          confidence: { code: 'max', value: 100 }
        }
      },
      400
    );
    await open();

    await ask('2026-09-30', '120');

    expect(
      await screen.findByText('Use no more than 100.')
    ).toBeInTheDocument();
    expect(screen.getByText('This is required.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /**
   * <strong>Nothing arriving after the panel has gone may touch it.</strong> The tick list
   * is fetched the moment this appears, and somebody who opens a plan and scrolls straight
   * past it is exactly the case that outlives the request — the same guard the breakdown
   * above already keeps, on the request that goes out without being asked for.
   */
  it('ignores work that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
    );

    const listed = renderRouted(<TargetDate run={FORECAST} />);
    await screen.findByRole('heading', { name: 'Can we hit a date?' });
    listed.unmount();
    settle(jsonResponse(200, WORK_ITEMS));

    const refused = renderRouted(<TargetDate run={FORECAST} />);
    await screen.findByRole('heading', { name: 'Can we hit a date?' });
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByLabelText(WORK_ITEMS[0].title)).toBeNull()
    );
  });

  /** Ticking is a choice and so is changing your mind about one. */
  it('gives a candidate back when it is unticked', async () => {
    answer();
    await open();

    await userEvent.type(
      screen.getByLabelText('We want it done by'),
      '2026-09-30'
    );
    const box = await screen.findByLabelText(WORK_ITEMS[0].title);
    await userEvent.click(box);
    expect(asking()).toBeEnabled();

    await userEvent.click(box);
    expect(box).not.toBeChecked();
    expect(asking()).toBeDisabled();
  });

  /**
   * <strong>Stopped at the limit rather than refused after the fact.</strong> Each candidate
   * is a whole simulation, so the server weighs twelve — and being told to untick three
   * would be being asked to guess which three mattered.
   */
  it('stops at as many candidates as can be weighed at once', async () => {
    const many = Array.from({ length: 14 }, (_unused, at) => ({
      ...WORK_ITEMS[0],
      id: `00000000-0000-0000-0000-0000000000${String(at).padStart(2, '0')}`,
      title: `Task ${at}`
    }));
    answer(many);
    await open();

    for (let at = 0; at < 12; at++) {
      await userEvent.click(await screen.findByLabelText(`Task ${at}`));
    }

    expect(
      screen.getByText(/is as many as can be weighed at once/)
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Task 12')).toBeDisabled();
    // Untickable, not unclickable: what is already chosen can still be given back.
    expect(screen.getByLabelText('Task 11')).toBeEnabled();
  });

  /**
   * <strong>A run with no calendar cannot be asked about a date at all</strong>, so it says
   * which of the two absences it is rather than showing a form that could only be refused.
   * A run made before M4 assumed no working day; a run made under a calendar this version
   * cannot read assumed one nobody here can resolve.
   */
  it('shows a reason rather than a form for a run made before there was a calendar', async () => {
    answer();
    await open({
      ...FORECAST,
      startsOn: null,
      workingHoursPerDay: null,
      calendarRule: null,
      p10Date: null,
      p50Date: null,
      p80Date: null,
      p90Date: null,
      p95Date: null
    });

    expect(
      screen.getByText(
        'This forecast was made before anybody stated a working day, so it cannot be asked about a date.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('We want it done by')).toBeNull();
  });

  it('shows a reason rather than a form for a calendar it cannot read', async () => {
    answer();
    await open({
      ...FORECAST,
      calendarRule: 'four_day_week',
      p10Date: null,
      p50Date: null,
      p80Date: null,
      p90Date: null,
      p95Date: null
    });

    expect(
      screen.getByText(
        'This forecast was made under a calendar this version of the app cannot read, so it cannot be asked about a date.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('We want it done by')).toBeNull();
  });

  /**
   * Work put away since the run is named and marked rather than hidden, and work the plan no
   * longer holds at all says so — the same three-way rule the contributions ranking keeps,
   * stated once and reached from both.
   */
  it('names work that has been put away, and work the plan no longer holds', async () => {
    answer(WORK_ITEMS, {
      ...CUT_OPTIONS,
      cuts: [
        { ...CUT_OPTIONS.cuts[0], archived: true },
        { ...CUT_OPTIONS.cuts[1], title: null }
      ],
      together: { steps: [], ending: 'nothing_left' }
    });
    await open();

    await ask();

    expect(
      await screen.findByText(
        /Migrate the auth service \(put away since\) — 71\.8%/
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Work no longer in this plan — 54\.2%/)
    ).toBeInTheDocument();
  });

  /**
   * A refusal arrives as a code and is worded by the catalogue, like every other failure in
   * this application — and the last answer goes with it, since a stale list of work to drop
   * beside a fresh refusal is the worst of both.
   */
  it('shows a refusal in our own words and drops the answer it replaces', async () => {
    answer();
    await open();
    await ask();
    await screen.findByRole('heading', { name: 'What it would take' });

    answer(
      WORK_ITEMS,
      { code: 'forecast_replay_mismatch', detail: 'moved' },
      409
    );
    await userEvent.click(asking());

    expect(
      await screen.findByText(
        'This forecast cannot be broken down: it was made by an earlier version of the model, which no longer reproduces it exactly.'
      )
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'What it would take' })
    ).toBeNull();
  });
});
