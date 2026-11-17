import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes, useParams } from 'react-router';
import { ForecastList } from './ForecastList';
import {
  ACCOUNT,
  FORECAST,
  PROJECTS,
  jsonResponse,
  mockFetch,
  NO_HISTORY,
  NOTHING_SCORED,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Forecast } from './forecastTypes';

const PROJECT_ID = PROJECTS[0].id;
const FORECASTS_URL = `/api/projects/${PROJECT_ID}/forecasts`;
const CALIBRATION_URL = '/api/calibration';
const THROUGHPUT_URL = `/api/projects/${PROJECT_ID}/throughput`;

const OLDER: Forecast = {
  ...FORECAST,
  id: '60606060-6060-6060-6060-606060606060',
  createdAt: '2026-08-07T10:00:00Z',
  p50Hours: 44.0,
  p90Hours: 71.5,
  p80Date: '2026-08-17',
  requestedByName: 'Bob'
};

/** Where creating a forecast lands: its own page, which says which one it was given. */
function Opened() {
  return <p>{`Opened forecast ${useParams().runId}`}</p>;
}

describe('ForecastList', () => {
  const fetchMock = mockFetch();

  function listing(runs: Forecast[]) {
    return {
      runs,
      drift:
        runs.length === 0
          ? null
          : {
              sinceRunId: runs[runs.length - 1].id,
              runs: runs.length,
              at: [50, 80, 95].map((confidence) => ({
                confidence,
                fromDate: runs[runs.length - 1].p80Date,
                toDate: runs[0].p80Date,
                days: 0,
                bandDays: 21,
                movingOut: false
              }))
            }
    };
  }

  function otherReads(url: string, runs: Forecast[] = [FORECAST]) {
    return url.endsWith('/forecasts')
      ? jsonResponse(200, listing(runs))
      : jsonResponse(200, []);
  }

  /** The list, at the plan's address, beside the page a new forecast opens on. */
  function renderList() {
    return renderRouted(
      <Routes>
        <Route
          path="/app/projects/:projectId"
          element={<ForecastList projectId={PROJECT_ID} />}
        />
        <Route path="/app/forecasts/:runId" element={<Opened />} />
      </Routes>,
      { route: `/app/projects/${PROJECT_ID}?tab=forecasts` }
    );
  }

  async function settled() {
    await screen.findByRole('button', { name: 'New forecast' });
  }

  function answer(runs: Forecast[]) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === FORECASTS_URL
            ? jsonResponse(200, listing(runs))
            : jsonResponse(200, [])
      )
    );
  }

  /** The list of this plan's forecasts — none, unless a case says otherwise. */
  async function open(runs: Forecast[] = []) {
    storeAccessToken();
    answer(runs);
    renderList();
    await settled();
    // Every forecast-reading case waits for the list to have arrived, not only its heading.
    await waitFor(() =>
      expect(screen.queryByText('Loading forecasts…')).toBeNull()
    );
  }

  async function openWithResources(pools: unknown[]) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === '/api/resources'
            ? jsonResponse(200, pools)
            : otherReads(url, [])
      )
    );
    renderList();
    await settled();
  }

  /**
   * Open the form that asks for a forecast, which is asked for from a button above the list
   * now rather than always on screen. Every case about the form goes through here, because
   * the button is what a person presses.
   */
  async function startAsking() {
    await userEvent.click(
      await screen.findByRole('button', { name: 'New forecast' })
    );
    return within(await screen.findByRole('dialog'));
  }

  function refuse(problem: Record<string, unknown>) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : init?.method === 'POST'
            ? jsonResponse(400, problem)
            : jsonResponse(200, listing([]))
      )
    );
  }

  async function answerTheAssumptions(
    capacity: string,
    worseBy: string,
    growthLow: string,
    growthHigh: string,
    workingDay = '6'
  ) {
    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      capacity
    );
    // The two common-cause questions sit under Advanced, so they are answered the way a
    // person answers them: by opening it first.
    await openAdvanced();
    await userEvent.type(
      screen.getByLabelText(
        'In a bad stretch, how much longer does everything take?'
      ),
      worseBy
    );
    await userEvent.type(screen.getByLabelText('Usually at least'), growthLow);
    await userEvent.type(screen.getByLabelText('And as much as'), growthHigh);
    await userEvent.type(
      screen.getByLabelText('Hours in a working day'),
      workingDay
    );
  }

  /** Opens the disclosure the optional assumptions sit in, if it is not open already. */
  async function openAdvanced() {
    const disclosure = screen
      .getByText('Advanced')
      .closest('details') as HTMLDetailsElement;
    if (!disclosure.open) {
      await userEvent.click(screen.getByText('Advanced'));
    }
  }

  /** Replacing the date the browser filled in, so a body assertion can be exact. */
  async function startWorkOn(day: string) {
    const box = screen.getByLabelText('Work starts on');
    await userEvent.clear(box);
    await userEvent.type(box, day);
  }

  it('says there is nothing yet before anybody has asked', async () => {
    await open();

    expect(
      screen.getByText(
        'No forecasts yet. Press “New forecast” to make the first one.'
      )
    ).toBeInTheDocument();
    // And the pop-up has nothing to have been filled in from, so it says nothing about it.
    const form = await startAsking();
    expect(form.queryByText(/Filled in with what/)).toBeNull();
  });

  /**
   * <strong>Asked for in a pop-up, and read on its own page.</strong> Somebody who asked
   * for a forecast wants to read it, so creating one goes straight to it.
   */
  it('asks for a forecast and opens the one that comes back', async () => {
    await open();
    await startAsking();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : init?.method === 'POST'
            ? jsonResponse(201, FORECAST)
            : otherReads(url)
      )
    );

    await answerTheAssumptions('2', '30', '20', '60');
    await startWorkOn('2026-08-17');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(
      await screen.findByText(`Opened forecast ${FORECAST.id}`)
    ).toBeInTheDocument();
    const posted = fetchMock.mock.calls.filter(
      ([, init]) => init?.method === 'POST'
    );
    expect(JSON.parse(posted[0][1].body)).toEqual({
      capacity: 2,
      teamFactorWorseByPercent: 30,
      scopeGrowthP10Percent: 20,
      scopeGrowthP90Percent: 60,
      workingHoursPerDay: 6,
      startsOn: '2026-08-17',
      sampleCount: null
    });
  });

  /** Cancel shuts the pop-up and asks for nothing. */
  it('asks for nothing when the pop-up is cancelled', async () => {
    await open();
    const form = await startAsking();

    await userEvent.click(form.getByRole('button', { name: 'Cancel' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull());
    expect(
      fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
    ).toHaveLength(0);
  });

  /**
   * <strong>Asking again starts from what was said last time.</strong> The capacity, the
   * working day and both common-cause answers come from the newest forecast — the team's own
   * claims, made before — and Advanced opens because it is about to repeat something that is
   * not zero. The start date does not: what day it is is today's fact.
   */
  it('fills the pop-up with what the newest forecast assumed', async () => {
    await open([FORECAST, OLDER]);
    const form = await startAsking();

    expect(
      form.getByText(/Filled in with what the newest forecast assumed/)
    ).toBeInTheDocument();
    expect(
      form.getByLabelText('Things that can be under way at once')
    ).toHaveValue(2);
    expect(form.getByLabelText('Hours in a working day')).toHaveValue(6);
    const badStretch = form.getByLabelText(
      'In a bad stretch, how much longer does everything take?'
    );
    expect(badStretch).toHaveValue(30);
    expect(badStretch).toBeVisible();
    expect(form.getByLabelText('Usually at least')).toHaveValue(20);
    expect(form.getByLabelText('And as much as')).toHaveValue(60);
  });

  /** A forecast that assumed zero for both repeats it with Advanced still folded away. */
  it('keeps Advanced shut when the newest forecast assumed nothing there', async () => {
    await open([
      {
        ...FORECAST,
        teamFactorWorseByPercent: 0,
        scopeGrowthP10Percent: 0,
        scopeGrowthP90Percent: 0
      }
    ]);
    const form = await startAsking();

    expect(
      form.getByLabelText(
        'In a bad stretch, how much longer does everything take?'
      )
    ).not.toBeVisible();
  });

  /**
   * A run scheduled against a team stored the team's units as its capacity, and offering
   * that as a capacity would be a claim nobody made — so it is not carried over.
   */
  it('carries no capacity over from a forecast made against a team', async () => {
    await open([
      {
        ...FORECAST,
        capacity: 5,
        resources: [
          {
            resourceId: 'r1',
            name: 'Backend engineers',
            archived: false,
            units: 5
          }
        ]
      }
    ]);
    const form = await startAsking();

    expect(
      form.getByLabelText('Things that can be under way at once')
    ).toHaveValue(null);
  });

  /**
   * <strong>The one box that arrives answered, and the one that must not.</strong> What day
   * it is is a fact this browser holds and the server cannot reach; a working day is a claim
   * about a team, and a box already answered is a box nobody reads.
   *
   * The instant is ten at night in New York on the last day of August, which is already
   * September in UTC — so a pre-fill through `toISOString()` would offer the wrong month,
   * silently, to everybody west of the meridian.
   */
  it('starts the working day empty and the start date on today, here', async () => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date('2026-09-01T02:00:00Z'));
    try {
      await open();
      await startAsking();

      expect(screen.getByLabelText('Work starts on')).toHaveValue('2026-08-31');
      expect(screen.getByLabelText('Hours in a working day')).toHaveValue(null);
    } finally {
      vi.useRealTimers();
    }
  });

  /**
   * Both in the open, like every other required question here: a required box inside the
   * collapsed section is a refusal about a field the visitor cannot see. The sample count
   * is asserted alongside to show the check has teeth.
   */
  it('asks for the calendar outside the disclosure', async () => {
    await open();
    await startAsking();

    expect(screen.getByLabelText('Work starts on')).toBeVisible();
    expect(screen.getByLabelText('Hours in a working day')).toBeVisible();
    expect(screen.getByLabelText('Simulated runs')).not.toBeVisible();
  });

  /**
   * Against its own box, and the hint above it is most of what stops the mistake this
   * work is really about: answering with the team's daily total makes every date too
   * early by exactly the number of people on the plan.
   */
  it('shows a refused working day against the working day box', async () => {
    await open();
    await startAsking();
    refuse({
      code: 'validation_failed',
      detail: 'invalid',
      errors: { workingHoursPerDay: { code: 'max', value: 24 } }
    });

    await answerTheAssumptions('2', '30', '20', '60', '25');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(await screen.findByText('Use no more than 24.')).toBeInTheDocument();
    expect(screen.getByLabelText('Hours in a working day')).toHaveAttribute(
      'aria-invalid',
      'true'
    );
    expect(screen.queryByText('Some fields need attention.')).toBeNull();
  });

  /** The start date is required too, and its complaint belongs to its own box. */
  it('shows a refused start date against the start date box', async () => {
    await open();
    await startAsking();
    refuse({
      code: 'validation_failed',
      detail: 'invalid',
      errors: { startsOn: { code: 'not_null' } }
    });

    await userEvent.clear(screen.getByLabelText('Work starts on'));
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(await screen.findByText('This is required.')).toBeInTheDocument();
    expect(screen.getByLabelText('Work starts on')).toHaveAttribute(
      'aria-invalid',
      'true'
    );
  });

  /** An empty box is null, never today — the server has no reading for a missing one. */
  it('sends no start date at all when the box has been emptied', async () => {
    await open();
    await startAsking();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : init?.method === 'POST'
                ? jsonResponse(201, FORECAST)
                : otherReads(url)
      )
    );

    await answerTheAssumptions('1', '0', '0', '0');
    await userEvent.clear(screen.getByLabelText('Work starts on'));
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    await waitFor(() => {
      const posted = fetchMock.mock.calls.filter(
        ([, init]) => init?.method === 'POST'
      );
      expect(JSON.parse(posted[0][1].body).startsOn).toBeNull();
    });
  });

  /**
   * <strong>Not pre-filled, any of them.</strong> Zero is a claim — that nothing goes
   * wrong for everybody at once, and that nobody will discover anything nobody listed —
   * and a box arriving already answered is a claim inherited rather than made. It is the
   * argument capacity already had, and `useProposedSlug` before it.
   */
  it('starts with every assumption unanswered', async () => {
    await open();
    await startAsking();

    expect(
      screen.getByLabelText('Things that can be under way at once')
    ).toHaveValue(null);
    expect(
      screen.getByLabelText(
        'In a bad stretch, how much longer does everything take?'
      )
    ).toHaveValue(null);
    expect(screen.getByLabelText('Usually at least')).toHaveValue(null);
    expect(screen.getByLabelText('And as much as')).toHaveValue(null);
  });

  /**
   * The two common-cause questions are optional and sit under Advanced, so a plan can be
   * forecast on its listed work alone. The capacity and the working day stay in the open.
   */
  it('keeps the two common-cause questions under Advanced', async () => {
    await open();
    await startAsking();
    const badStretch = screen.getByLabelText(
      'In a bad stretch, how much longer does everything take?'
    );
    expect(badStretch).not.toBeVisible();
    expect(screen.getByLabelText('Usually at least')).not.toBeVisible();
    expect(screen.getByLabelText('Hours in a working day')).toBeVisible();

    await userEvent.click(screen.getByText('Advanced'));

    expect(badStretch).toBeVisible();
    expect(
      within(
        screen.getByRole('group', {
          name: 'How much does a plan like this usually grow?'
        })
      ).getByLabelText('Usually at least')
    ).toBeVisible();
  });

  /**
   * What each Advanced question means is behind its own "?" rather than printed under it,
   * and the growth question's group is still named by the question alone.
   */
  it('explains each Advanced question behind a question mark', async () => {
    await open();
    await startAsking();
    await openAdvanced();

    for (const [help, text] of [
      [
        'What does the bad-stretch question mean?',
        /only one quarter in ten is worse/
      ],
      ['What does the growth question mean?', /a usual amount and a bad one/],
      ['What are simulated runs?', /accurate to well under a percent/]
    ] as const) {
      const explanation = screen.getByText(text);
      expect(explanation).not.toBeVisible();
      await userEvent.click(screen.getByRole('button', { name: help }));
      expect(explanation).toBeVisible();
      await userEvent.keyboard('{Escape}');
    }
    expect(
      screen.getByRole('group', {
        name: 'How much does a plan like this usually grow?'
      })
    ).toBeInTheDocument();
  });

  /**
   * <strong>Empty is zero, and the forecast says so.</strong> The browser sends zero for the
   * two common-cause questions when they are left empty — which the server then reports as
   * `no_team_factor` and `no_scope_uncertainty` beside the date. The sample count keeps its
   * own rule: empty goes as null, and the server reads that as the ordinary ten thousand.
   */
  it('forecasts the listed work alone when the common-cause questions are left empty', async () => {
    await open();
    await startAsking();
    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      '2'
    );
    await userEvent.type(screen.getByLabelText('Hours in a working day'), '6');
    await startWorkOn('2026-08-17');

    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.filter(([, init]) => init?.method === 'POST')
      ).toHaveLength(1)
    );
    const [posted] = fetchMock.mock.calls.filter(
      ([, init]) => init?.method === 'POST'
    );
    expect(JSON.parse(posted[1].body)).toEqual({
      capacity: 2,
      teamFactorWorseByPercent: 0,
      scopeGrowthP10Percent: 0,
      scopeGrowthP90Percent: 0,
      workingHoursPerDay: 6,
      startsOn: '2026-08-17',
      sampleCount: null
    });
  });

  it('shows the refusal when nobody has estimated anything', async () => {
    await open();
    await startAsking();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : init?.method === 'POST'
                ? jsonResponse(422, {
                    code: 'nothing_to_forecast',
                    detail: 'nothing'
                  })
                : jsonResponse(200, listing([]))
      )
    );

    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      '1'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    // Reads as a thing to go and do, on the screen where it can be done.
    expect(
      await screen.findByText(
        'Nothing in this plan has been estimated yet, so there is nothing to forecast. Add a range to some of the work above.'
      )
    ).toBeInTheDocument();
  });

  it('shows a refused capacity against the capacity box', async () => {
    await open();
    await startAsking();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : init?.method === 'POST'
                ? jsonResponse(400, {
                    code: 'validation_failed',
                    detail: 'invalid',
                    errors: { capacity: { code: 'not_null' } }
                  })
                : jsonResponse(200, listing([]))
      )
    );

    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(await screen.findByText('This is required.')).toBeInTheDocument();
  });

  /**
   * Each box answers for itself. Two complaints arrive together and neither goes to the
   * banner, because the visitor can see both of the fields they belong to — which is the
   * rule `useFormFailure` exists to keep, and the reason none of these questions may hide
   * behind the disclosure the sample count sits in.
   */
  it('shows each refused assumption against its own box', async () => {
    await open();
    await startAsking();
    refuse({
      code: 'validation_failed',
      detail: 'invalid',
      errors: {
        teamFactorWorseByPercent: { code: 'not_null' },
        scopeGrowthP10Percent: { code: 'positive_or_zero' }
      }
    });

    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      '2'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(await screen.findByText('This is required.')).toBeInTheDocument();
    expect(
      screen.getByText('Use zero or a number greater than it.')
    ).toBeInTheDocument();
    // Under Advanced, which was shut: a complaint about a box nobody can see is one shown
    // nowhere, so the disclosure opens itself to show it.
    expect(
      screen.getByLabelText(
        'In a bad stretch, how much longer does everything take?'
      )
    ).toBeVisible();
    expect(
      screen.getByLabelText(
        'In a bad stretch, how much longer does everything take?'
      )
    ).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Usually at least')).toHaveAttribute(
      'aria-invalid',
      'true'
    );
    expect(screen.queryByText('Some fields need attention.')).toBeNull();
  });

  it('shows a refused growth number against the box it belongs to', async () => {
    await open();
    await startAsking();
    refuse({
      code: 'validation_failed',
      detail: 'invalid',
      errors: { scopeGrowthP90Percent: { code: 'max', value: 200 } }
    });

    await answerTheAssumptions('2', '30', '20', '900');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    const high = screen.getByLabelText('And as much as');
    expect(
      await screen.findByText('Use no more than 200.')
    ).toBeInTheDocument();
    expect(high).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Usually at least')).not.toHaveAttribute(
      'aria-invalid'
    );
  });

  /**
   * <strong>A question about the two numbers, not a complaint about one of them.</strong>
   * Each is a perfectly good percentage and what is wrong is which way round they are, so
   * the server names no field and this reads as a sentence about the pair — the same shape
   * `estimate_out_of_order` takes on the estimate form.
   */
  it('reads a growth range the wrong way round as a question about both numbers', async () => {
    await open();
    await startAsking();
    refuse({ code: 'scope_growth_out_of_order', detail: 'backwards' });

    await answerTheAssumptions('2', '30', '60', '20');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(
      await screen.findByText(
        'The two growth numbers are the wrong way round: the most a plan grows cannot be less than the usual amount.'
      )
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Usually at least')).not.toHaveAttribute(
      'aria-invalid'
    );
    expect(screen.getByLabelText('And as much as')).not.toHaveAttribute(
      'aria-invalid'
    );
  });

  /** The one bound the server puts on how much work a caller may ask it to do. */
  it('shows a refused sample count against the sample count box', async () => {
    await open();
    await startAsking();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : init?.method === 'POST'
                ? jsonResponse(400, {
                    code: 'validation_failed',
                    detail: 'invalid',
                    errors: { sampleCount: { code: 'max', value: 100000 } }
                  })
                : jsonResponse(200, listing([]))
      )
    );

    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      '2'
    );
    await userEvent.type(screen.getByLabelText('Simulated runs'), '100001');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(
      await screen.findByText('Use no more than 100000.')
    ).toBeInTheDocument();
  });

  /**
   * A forecast can take a moment, and a visitor can leave in the middle of one. Nothing
   * arriving after the panel has gone may touch it — React warns about it, and the more
   * practical cost is a stale answer painted over a page somebody has already moved on
   * from.
   */
  it('ignores an answer that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url.startsWith(THROUGHPUT_URL)
        ? Promise.resolve(jsonResponse(200, NO_HISTORY))
        : url === CALIBRATION_URL
          ? Promise.resolve(jsonResponse(200, NOTHING_SCORED))
          : url === '/api/auth/me'
            ? Promise.resolve(jsonResponse(200, ACCOUNT))
            : new Promise<Response>((resolve, reject) => {
                settle = resolve;
                fail = reject;
              })
    );

    const answered = renderList();
    await settled();
    answered.unmount();
    settle(jsonResponse(200, listing([FORECAST])));

    const refused = renderList();
    await settled();
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/An 80% chance/)).not.toBeInTheDocument()
    );
  });

  /**
   * <strong>Each row says when, what it said, who asked and what it assumed.</strong> Bob
   * asked the same plan a different question — fewer people, no common cause, no unlisted
   * work — so his shorter answer is not this plan having got better, and a list that showed
   * only the dates would read exactly as though it were.
   */
  it('lists every forecast, newest first, with what each assumed', async () => {
    await open([
      FORECAST,
      {
        ...OLDER,
        capacity: 1,
        teamFactorWorseByPercent: 0,
        scopeGrowthP10Percent: 0,
        scopeGrowthP90Percent: 0,
        // What a run that answered zero carries, and what the row names instead of
        // printing "up to 0%" as though it were a claim about the team.
        limitations: ['no_team_factor', 'no_scope_uncertainty']
      }
    ]);

    const rows = screen.getAllByRole('listitem');
    expect(rows).toHaveLength(2);
    expect(
      within(rows[0]).getByRole('link', {
        name: 'Forecast of Aug 14, 2026, 6:00 AM'
      })
    ).toHaveAttribute('href', `/app/forecasts/${FORECAST.id}`);
    expect(within(rows[0]).getByText('Newest')).toBeInTheDocument();
    expect(
      within(rows[0]).getByText('80% likely by Aug 25, 2026')
    ).toBeInTheDocument();
    expect(
      within(rows[0]).getByText(
        'Asked for by Ada · 2 at a time, up to 30% longer in a bad stretch, 20–60% more work.'
      )
    ).toBeInTheDocument();

    expect(
      within(rows[1]).getByRole('link', {
        name: 'Forecast of Aug 7, 2026, 6:00 AM'
      })
    ).toHaveAttribute('href', `/app/forecasts/${OLDER.id}`);
    expect(within(rows[1]).queryByText('Newest')).toBeNull();
    expect(
      within(rows[1]).getByText(
        'Asked for by Bob · 1 at a time, no bad stretches, no unlisted work.'
      )
    ).toBeInTheDocument();
  });

  /** A run made before there was a calendar has no date, so its row says its hours. */
  it('reads a forecast with no date by its hours', async () => {
    await open([
      {
        ...FORECAST,
        startsOn: null,
        workingHoursPerDay: null,
        calendarRule: null,
        p10Date: null,
        p50Date: null,
        p80Date: null,
        p90Date: null,
        p95Date: null
      }
    ]);

    expect(screen.getByText('41.8 hours of effort at 80%')).toBeInTheDocument();
  });

  /**
   * Whether the date keeps moving out is a property of the sequence, so it is said above
   * the list — read at the usual confidence, since a list has no control to choose another.
   */
  it('warns above the list when the date keeps moving out', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === FORECASTS_URL
            ? jsonResponse(200, {
                runs: [FORECAST, OLDER],
                drift: {
                  sinceRunId: OLDER.id,
                  runs: 2,
                  at: [50, 80, 95].map((confidence) => ({
                    confidence,
                    fromDate: '2026-08-17',
                    toDate: '2026-08-25',
                    days: 8,
                    bandDays: 6,
                    movingOut: confidence === 80
                  }))
                }
              })
            : jsonResponse(200, [])
      )
    );
    renderList();

    expect(
      await screen.findByText(
        'This plan has been drifting. At 80% it said Aug 17, 2026 and now says Aug 25, 2026 — 8 days out, against a band 6 days wide.'
      )
    ).toBeInTheDocument();
  });

  // The team a forecast was scheduled against -------------------------------

  /**
   * <strong>Absent rather than disabled</strong>, which is the rule the confidence control
   * already follows on a run with no dates: a box that visibly does nothing reads as a
   * broken screen, where a sentence saying what answers the question says what is true.
   */
  it('takes the capacity box away once a team has been described', async () => {
    await openWithResources([
      {
        id: 'r1',
        name: 'Backend engineers',
        units: 3,
        personId: null,
        personName: null,
        createdAt: '2026-08-01',
        archivedAt: null
      }
    ]);
    await startAsking();

    expect(
      screen.queryByLabelText('Things that can be under way at once')
    ).toBeNull();
    // The question is still named, so the sentence under it answers something visible.
    expect(
      screen.getByText('Things that can be under way at once')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /the forecast schedules the work against the resource on the/
      )
    ).toHaveTextContent(
      'Answered by your team: the forecast schedules the work against the resource on the Resources page, so you do not need to fill this in. Change it there if it is not right.'
    );
    expect(
      screen.getByRole('link', { name: 'Resources page' })
    ).toHaveAttribute('href', '/app/resources');
  });

  it('names how many resources answer the question when there are several', async () => {
    const pool = {
      id: 'r1',
      name: 'Backend engineers',
      units: 3,
      personId: null,
      personName: null,
      createdAt: '2026-08-01',
      archivedAt: null
    };
    await openWithResources([pool, { ...pool, id: 'r2', name: 'Designers' }]);
    await startAsking();

    expect(
      screen.getByText(
        /the forecast schedules the work against the 2 resources/
      )
    ).toHaveTextContent(/Change them there if they are not right\./);
  });

  /** And it is there for an organisation that has described none, which is most of them. */
  it('asks for a capacity when no team has been described', async () => {
    await openWithResources([]);
    await startAsking();

    expect(
      screen.getByLabelText('Things that can be under way at once')
    ).toBeInTheDocument();
  });

  /**
   * The team is its own read, and one that must not touch a panel that has gone — the guard
   * every request here keeps.
   */
  it('drops a team that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/resources'
        ? new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
        : url.startsWith(THROUGHPUT_URL)
          ? Promise.resolve(jsonResponse(200, NO_HISTORY))
          : url === CALIBRATION_URL
            ? Promise.resolve(jsonResponse(200, NOTHING_SCORED))
            : url === '/api/auth/me'
              ? Promise.resolve(jsonResponse(200, ACCOUNT))
              : Promise.resolve(otherReads(url))
    );

    const answered = renderList();
    await settled();
    answered.unmount();
    settle(jsonResponse(200, []));

    const refused = renderList();
    await settled();
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Forecast' })).toBeNull()
    );
  });

  it('reports a plan whose forecasts could not be loaded', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : jsonResponse(404, {
                  code: 'project_not_found',
                  detail: 'gone'
                })
      )
    );
    renderList();

    expect(
      await screen.findByText('That project is no longer in this organisation.')
    ).toBeInTheDocument();
  });

  it('sends a sample count when somebody opens the disclosure and gives one', async () => {
    await open();
    await startAsking();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : init?.method === 'POST'
                ? jsonResponse(201, FORECAST)
                : otherReads(url)
      )
    );

    await answerTheAssumptions('3', '0', '0', '0', '7.5');
    await startWorkOn('2026-08-17');
    await userEvent.type(screen.getByLabelText('Simulated runs'), '2000');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    await waitFor(() => {
      const posted = fetchMock.mock.calls.filter(
        ([, init]) => init?.method === 'POST'
      );
      expect(JSON.parse(posted[0][1].body)).toEqual({
        capacity: 3,
        teamFactorWorseByPercent: 0,
        scopeGrowthP10Percent: 0,
        scopeGrowthP90Percent: 0,
        workingHoursPerDay: 7.5,
        startsOn: '2026-08-17',
        sampleCount: 2000
      });
    });
  });
});
