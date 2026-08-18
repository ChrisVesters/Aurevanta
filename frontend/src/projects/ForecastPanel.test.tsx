import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForecastPanel } from './ForecastPanel';
import {
  ACCOUNT,
  CONTRIBUTIONS,
  CUT_OPTIONS,
  FORECAST,
  PROJECTS,
  WORK_ITEMS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Forecast } from './types';

const PROJECT_ID = PROJECTS[0].id;
const FORECASTS_URL = `/api/projects/${PROJECT_ID}/forecasts`;
const CONTRIBUTIONS_URL = `/api/forecasts/${FORECAST.id}/contributions`;
const CALIBRATION_URL = '/api/calibration';

const EMPTY_RECORD = {
  scored: 0,
  hits: 0,
  belowP10: 0,
  aboveP90: 0,
  pointEstimates: 0,
  rate: null,
  corrections: null
};

/**
 * The organisation's track record, answered separately from everything else here.
 *
 * **A double that let this fall through to the forecast list would hand the panel an array
 * where it expects a record**, which is the "answers every URL alike" failure in the shape
 * this file is most exposed to: the panel reads it on mount, so every case in this suite
 * would have been affected by one that got it wrong.
 *
 * Nothing scored, which is the state that keeps the line off the screen — the cases that
 * want it on say so for themselves.
 */
const NOTHING_SCORED = {
  forecasts: EMPTY_RECORD,
  reports: EMPTY_RECORD,
  unbounded: EMPTY_RECORD,
  byEstimator: [],
  byMethod: [],
  coverage: {
    completedItems: 0,
    withActual: 0,
    withEstimate: 0,
    scoredItems: 0,
    movedByTheStartDay: 0
  },
  firstScored: null,
  lastScored: null
};
const ITEMS_URL = `/api/projects/${PROJECT_ID}/items`;
const CUTS_URL = `/api/forecasts/${FORECAST.id}/cuts`;

describe('ForecastPanel', () => {
  const fetchMock = mockFetch();

  /**
   * The session and the plan's forecasts, answered separately. A double that answered both
   * alike would hand the panel an account where it expected a list, and every assertion
   * about what is on screen would be testing the wrong thing.
   */
  function answer(runs: Forecast[], spread: unknown = CONTRIBUTIONS) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : url === FORECASTS_URL
              ? jsonResponse(200, runs)
              : url === CONTRIBUTIONS_URL
                ? jsonResponse(200, spread)
                : // The work a target date could be asked to drop, which the panel below
                  // loads for itself. Answered by URL like everything else here: a double
                  // that answered in *order* would hand this list to whichever request
                  // happened to go out first.
                  url === ITEMS_URL
                  ? jsonResponse(200, WORK_ITEMS)
                  : url === CUTS_URL
                    ? jsonResponse(200, CUT_OPTIONS)
                    : jsonResponse(404)
      )
    );
  }

  async function open(runs: Forecast[] = [], spread: unknown = CONTRIBUTIONS) {
    storeAccessToken();
    answer(runs, spread);
    renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
  }

  /** Whatever the next request for a forecast is refused with. */
  function refuse(problem: Record<string, unknown>) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(400, problem)
              : jsonResponse(200, [])
      )
    );
  }

  /**
   * Filling in the four boxes a forecast cannot be asked for without. Written out here
   * because it is what somebody has to do every time, which is the point of them being
   * required rather than filled in for them.
   */
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

  /** Replacing the date the browser filled in, so a body assertion can be exact. */
  async function startWorkOn(day: string) {
    const box = screen.getByLabelText('Work starts on');
    await userEvent.clear(box);
    await userEvent.type(box, day);
  }

  it('says there is nothing yet before anybody has asked', async () => {
    await open();

    expect(
      await screen.findByText(
        'No forecast yet. Answer the questions below and ask for one — almost none of them has an answer this application can give for you.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>The whole milestone, in one test.</strong> Three confidences, three dates, one
   * forecast and **no second request** — which is not an optimisation but the feature: "can
   * we go faster?" is answered by a control moving from 95 to 80 and a date moving with it
   * while everybody watches, rather than by a capitulation.
   *
   * It is also the timezone regression. `p95Date` is `2026-08-31`, and the suite runs in
   * New York: `new Date('2026-08-31')` is UTC midnight there, which is the 30th. A date
   * built through `formatDay` says the 31st, and one built the obvious way says August 30
   * for most of the planet with nothing else on screen looking wrong.
   */
  it('leads with a date and moves it between confidences without asking again', async () => {
    await open([FORECAST]);
    await screen.findByText(/80% likely/);
    const asked = fetchMock.mock.calls.length;

    expect(
      screen.getByText('80% likely to be finished by Aug 25, 2026.')
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '50%' }));
    expect(
      screen.getByText('50% likely to be finished by Aug 21, 2026.')
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '95%' }));
    expect(
      screen.getByText('95% likely to be finished by Aug 31, 2026.')
    ).toBeInTheDocument();

    expect(fetchMock.mock.calls).toHaveLength(asked);
  });

  /**
   * The band is what the engine produced; the date is that with a working day on top. Take
   * the hours away and nothing on this screen came out of the model, which is how an
   * assumption stops being visible and starts being mistaken for a result.
   */
  it('keeps the hours on screen at every confidence', async () => {
    await open([FORECAST]);
    await screen.findByText(/80% likely/);

    for (const level of ['50%', '95%']) {
      await userEvent.click(screen.getByRole('radio', { name: level }));
      expect(
        screen.getByText(
          'An 80% chance of taking between 14.2 and 52.6 hours of effort.'
        )
      ).toBeInTheDocument();
      expect(screen.getByText('61.9 hours')).toBeInTheDocument();
    }
  });

  /**
   * <strong>Beside the date and never behind a disclosure.</strong> A date is the first
   * thing this product emits that looks like a fact — an hours band advertises that it came
   * out of a model and "Aug 25" does not — so the working day it rests on is printed in the
   * same breath as the five assumptions M3b already prints.
   */
  it('states the calendar the dates were read under', async () => {
    await open([FORECAST]);

    expect(
      await screen.findByText(
        "Dates assume work starts Aug 17, 2026, one person's working day holds 6 hours, and nobody works weekends."
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>Decision 6, on screen.</strong> A run made before M4 assumed no calendar,
   * because it produced no date — so nothing was backfilled onto it, and this says so in a
   * line rather than showing a blank or a date nobody's assumptions produced.
   */
  it('shows a run made before there was a calendar as hours and a reason', async () => {
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

    expect(
      await screen.findByText(
        'No date for this one: it was made before anybody stated a working day, so hours are all it can say.'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        'An 80% chance of taking between 14.2 and 52.6 hours of effort.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText(/Dates assume/)).toBeNull();
    // And no control, because there is nothing for it to choose between. Three buttons
    // that visibly change nothing read as a broken screen.
    expect(screen.queryByRole('radio', { name: '95%' })).toBeNull();
  });

  /**
   * The other absence, and the one that arrives later: the server versions ahead of the
   * browser, so a run read under a calendar this app has never heard of reports its hours
   * rather than a date worked out under the wrong rule. Same direction as an unrecognised
   * limitation, same answer — say what is true instead of showing nothing.
   */
  it('says so rather than guessing when a calendar is one it cannot read', async () => {
    await open([
      {
        ...FORECAST,
        calendarRule: 'four_day_week',
        p10Date: null,
        p50Date: null,
        p80Date: null,
        p90Date: null,
        p95Date: null
      }
    ]);

    expect(
      await screen.findByText(
        'No date for this one: it was made under a calendar this version of the app cannot read.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByRole('radio', { name: '95%' })).toBeNull();
    // Its calendar is still stated, unlike the case above: the run *has* one, and what is
    // missing is this version's ability to resolve it rather than the assumption itself.
    expect(
      screen.getByText(/Dates assume work starts Aug 17, 2026/)
    ).toBeInTheDocument();
  });

  it('draws the band once a forecast has been made', async () => {
    await open([FORECAST]);

    expect(
      await screen.findByText(
        'An 80% chance of taking between 14.2 and 52.6 hours of effort.'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('29.5 hours')).toBeInTheDocument();
    expect(screen.getByText('61.9 hours')).toBeInTheDocument();
  });

  /**
   * <strong>All five, beside the band and not behind anything.</strong> A forecast whose
   * assumptions are one click away is a forecast that gets screenshotted without them —
   * and the two M3b added are the ones a reader is least able to guess. The coverage is
   * here too, which is the disclosure this whole product turns on.
   */
  it('states what it assumed and how much of the plan it covered', async () => {
    await open([FORECAST]);

    expect(
      await screen.findByText(
        'Assuming 2 things under way at once, up to 30% longer in a bad stretch, and 20–60% more work than has been listed — over 10000 simulated runs.'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('2 of 2 items estimated')).toBeInTheDocument();
  });

  /** Zero is a claim somebody made, so it is reported like any other answer. */
  it('states assumptions that are doing nothing just as plainly', async () => {
    await open([
      {
        ...FORECAST,
        teamFactorWorseByPercent: 0,
        scopeGrowthP10Percent: 0,
        scopeGrowthP90Percent: 0
      }
    ]);

    expect(
      await screen.findByText(
        'Assuming 2 things under way at once, up to 0% longer in a bad stretch, and 0–0% more work than has been listed — over 10000 simulated runs.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>Decision 12, on screen rather than merely in the payload.</strong> A band
   * reported without these is narrower than the truth by an amount nobody looking at it
   * could guess, so this asserts they are rendered — not that they arrived.
   */
  it('prints what the model did not do, beside the number', async () => {
    await open([FORECAST]);

    const caveats = within(
      screen.getByRole('heading', { name: 'What this forecast does not do' })
        .parentElement as HTMLElement
    );
    expect(
      caveats.getByText(/middle number sits a long way from their own two ends/)
    ).toBeInTheDocument();
  });

  /**
   * <strong>The two codes M3b retired are still describable, and this is why they were
   * retired rather than deleted.</strong> Nothing writes them now, and every forecast a
   * plan made before this milestone still carries them — so a screen that had forgotten
   * their wording would tell somebody their own history was something it could not
   * understand.
   */
  // What the ranges feeding this band have historically been worth ------------

  /**
   * <strong>A caveat about the inputs, never a correction to the number.</strong> Folding a
   * calibration factor into the forecast would close a loop on its own evidence — the record
   * would converge on 80% while nothing about the estimating changed — so this says what the
   * estimates have been worth and leaves the band exactly as the engine produced it.
   */
  it('says what this organisation’s estimates have historically been worth', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, {
              ...NOTHING_SCORED,
              forecasts: {
                ...EMPTY_RECORD,
                scored: 40,
                hits: 18,
                rate: { value: 0.45, low: 0.353, high: 0.551 }
              }
            })
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : jsonResponse(200, [FORECAST])
      )
    );
    renderRouted(<ForecastPanel projectId={PROJECT_ID} />);

    expect(
      // A regex because the sentence shares its paragraph with the link out to the page
      // that explains it, so the element's text is not the sentence alone.
      await screen.findByText(
        /Estimates in this organisation have contained the outcome 45% of the time, over 40 scored so far\./
      )
    ).toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'See the track record' })
    ).toHaveAttribute('href', '/app/calibration');
  });

  /** Nothing arriving after somebody has navigated away may touch a panel that has gone. */
  it('ignores a track record that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === CALIBRATION_URL
        ? new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
        : url === '/api/auth/me'
          ? Promise.resolve(jsonResponse(200, ACCOUNT))
          : Promise.resolve(jsonResponse(200, [FORECAST]))
    );

    const answered = renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(
      jsonResponse(200, {
        ...NOTHING_SCORED,
        forecasts: {
          ...EMPTY_RECORD,
          scored: 40,
          hits: 18,
          rate: { value: 0.45, low: 0.353, high: 0.551 }
        }
      })
    );

    // And the same on the way out through the failure path, which is the one that would
    // otherwise clear a track record belonging to a panel that is now on screen.
    const refused = renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/have contained the outcome/)).toBeNull()
    );
  });

  /**
   * "No estimate here has ever been checked" on every forecast anybody runs is noise rather
   * than a caveat. The track record page is where that belongs, and it says it properly.
   */
  it('says nothing about a track record that does not exist yet', async () => {
    await open([FORECAST]);

    expect(screen.queryByText(/have contained the outcome/)).toBeNull();
  });

  /**
   * A forecast is not less true because the record beside it could not be loaded, so a
   * failure there leaves the line off and touches nothing else.
   */
  it('still shows the band when the track record cannot be read', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(500, null)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : jsonResponse(200, [FORECAST])
      )
    );
    renderRouted(<ForecastPanel projectId={PROJECT_ID} />);

    await screen.findByRole('heading', { name: 'Forecast' });
    expect(screen.queryByText(/have contained the outcome/)).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('still explains the limitations of a forecast made before M3b', async () => {
    await open([
      { ...FORECAST, limitations: ['no_team_factor', 'no_scope_uncertainty'] }
    ]);

    const heading = await screen.findByRole('heading', {
      name: 'What this forecast does not do'
    });
    const caveats = within(heading.parentElement as HTMLElement);
    expect(
      caveats.getByText(/treated every task as independent/)
    ).toBeInTheDocument();
    expect(
      caveats.getByText(/only the work already written down/)
    ).toBeInTheDocument();
  });

  it('describes a limitation this version has never heard of rather than dropping it', async () => {
    await open([
      {
        ...FORECAST,
        limitations: ['something_from_a_later_engine' as never]
      }
    ]);

    expect(
      await screen.findByText(
        'This forecast reported something this version of the app cannot describe yet.'
      )
    ).toBeInTheDocument();
  });

  it('asks for a forecast and shows the one that comes back', async () => {
    await open();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(201, FORECAST)
              : jsonResponse(200, [FORECAST])
      )
    );

    await answerTheAssumptions('2', '30', '20', '60');
    await startWorkOn('2026-08-17');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    expect(
      await screen.findByText(
        'An 80% chance of taking between 14.2 and 52.6 hours of effort.'
      )
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
      await open([FORECAST]);

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
    await open([FORECAST]);

    expect(screen.getByLabelText('Work starts on')).toBeVisible();
    expect(screen.getByLabelText('Hours in a working day')).toBeVisible();
    expect(screen.getByLabelText('Simulated runs')).not.toBeVisible();
  });

  /**
   * Against its own box, and the hint above it is most of what stops the mistake this
   * milestone is really about: answering with the team's daily total makes every date too
   * early by exactly the number of people on the plan.
   */
  it('shows a refused working day against the working day box', async () => {
    await open();
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
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(201, FORECAST)
              : jsonResponse(200, [FORECAST])
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
    await open([FORECAST]);

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
   * The two questions this milestone exists to ask, on screen and not behind the
   * disclosure the sample count sits in — a required box inside a collapsed section is a
   * refusal about a field the visitor cannot see.
   */
  it('asks both of the hard questions in the open', async () => {
    await open([FORECAST]);

    const growth = screen.getByRole('group', {
      name: 'How much does a plan like this usually grow?'
    });
    expect(growth).toBeInTheDocument();
    expect(within(growth).getByLabelText('Usually at least')).toBeVisible();
    expect(
      screen.getByLabelText(
        'In a bad stretch, how much longer does everything take?'
      )
    ).toBeVisible();
  });

  it('shows the refusal when nobody has estimated anything', async () => {
    await open();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(422, {
                  code: 'nothing_to_forecast',
                  detail: 'nothing'
                })
              : jsonResponse(200, [])
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
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(400, {
                  code: 'validation_failed',
                  detail: 'invalid',
                  errors: { capacity: { code: 'not_null' } }
                })
              : jsonResponse(200, [])
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
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(400, {
                  code: 'validation_failed',
                  detail: 'invalid',
                  errors: { sampleCount: { code: 'max', value: 100000 } }
                })
              : jsonResponse(200, [])
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
      url === CALIBRATION_URL
        ? Promise.resolve(jsonResponse(200, NOTHING_SCORED))
        : url === '/api/auth/me'
          ? Promise.resolve(jsonResponse(200, ACCOUNT))
          : new Promise<Response>((resolve, reject) => {
              settle = resolve;
              fail = reject;
            })
    );

    const answered = renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(jsonResponse(200, [FORECAST]));

    const refused = renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/An 80% chance/)).not.toBeInTheDocument()
    );
  });

  /**
   * <strong>Each entry carries its own assumptions, and that is M10's problem arriving
   * early enough to design around.</strong> Bob asked the same plan a different question —
   * fewer people, no common cause, no unlisted work — so his shorter answer is not this
   * plan having got better. A history that showed only the numbers would read exactly as
   * though it were.
   */
  it('lists the forecasts made before this one, with what each of them assumed', async () => {
    const earlier: Forecast = {
      ...FORECAST,
      id: '60606060-6060-6060-6060-606060606060',
      capacity: 1,
      teamFactorWorseByPercent: 0,
      scopeGrowthP10Percent: 0,
      scopeGrowthP90Percent: 0,
      p50Hours: 44.0,
      p90Hours: 71.5,
      requestedByName: 'Bob',
      startsOn: '2026-08-03',
      workingHoursPerDay: 8
    };
    await open([FORECAST, earlier]);

    const history = within(
      screen.getByRole('heading', { name: 'Earlier forecasts' })
        .parentElement as HTMLElement
    );
    expect(
      history.getByText(
        /44 h as likely as not, 71.5 h at the cautious end — 1 at a time, up to 0% longer in a bad stretch, 0–0% more work, asked for by Bob\./
      )
    ).toBeInTheDocument();
    // And its own calendar, because a run read under eight-hour days from a different
    // Monday is a different reading rather than this plan having moved.
    expect(
      history.getByText(/8-hour days from Aug 3, 2026\./)
    ).toBeInTheDocument();
  });

  /** A run with none says nothing rather than inventing one from the newest. */
  it('leaves the calendar off an earlier forecast that had none', async () => {
    await open([
      FORECAST,
      {
        ...FORECAST,
        id: '70707070-7070-7070-7070-707070707070',
        startsOn: null,
        workingHoursPerDay: null,
        calendarRule: null
      }
    ]);

    const history = within(
      screen.getByRole('heading', { name: 'Earlier forecasts' })
        .parentElement as HTMLElement
    );
    expect(history.queryByText(/-hour days from/)).toBeNull();
  });

  // What it would take to hit a date ---------------------------------------

  /**
   * <strong>A list of work to drop belongs to the run it was measured against.</strong>
   * Asking for a new forecast leaves the previous answer describing a run that is no longer
   * on screen — the same staleness the breakdown clears itself for, arriving through the
   * whole panel rather than one section of it. It is keyed on the run, so a new one starts
   * the question again.
   */
  it('starts the target question again when a new forecast lands', async () => {
    await open([FORECAST]);
    await userEvent.type(
      screen.getByLabelText('We want it done by'),
      '2026-09-30'
    );
    await userEvent.type(
      screen.getByLabelText('How sure do you need to be?'),
      '85'
    );
    await userEvent.click(await screen.findByLabelText(WORK_ITEMS[0].title));
    await userEvent.click(
      screen.getByRole('button', { name: 'What would it take?' })
    );
    expect(
      await screen.findByRole('heading', { name: 'What it would take' })
    ).toBeInTheDocument();

    answer([{ ...FORECAST, id: '60606060-6060-6060-6060-606060606060' }]);
    await answerTheAssumptions('2', '30', '20', '60');
    await userEvent.click(
      screen.getByRole('button', { name: 'Forecast this plan' })
    );

    await waitFor(() =>
      expect(
        screen.queryByRole('heading', { name: 'What it would take' })
      ).toBeNull()
    );
    expect(screen.getByLabelText('We want it done by')).toHaveValue('');
  });

  // What the spread is made of ---------------------------------------------

  /**
   * <strong>Loaded when somebody asks, and not before.</strong> Working it out replays the
   * whole run — about half a second at the five hundred items a plan may hold — which is
   * cheap for a reader who wants it and rude to charge everybody who opened the page.
   */
  it('does not ask what the spread is made of until somebody does', async () => {
    await open([FORECAST]);
    await screen.findByText(/80% likely/);

    expect(
      fetchMock.mock.calls.filter(([url]) => url === CONTRIBUTIONS_URL)
    ).toHaveLength(0);
    expect(screen.queryByText('What the spread is made of')).toBeNull();

    await userEvent.click(
      screen.getByRole('button', { name: 'What is widening this?' })
    );

    expect(
      await screen.findByText('What the spread is made of')
    ).toBeInTheDocument();
  });

  /**
   * <strong>In contribution order, which is the feature.</strong> "What should I spike
   * next" is a question about position, so the list is read top down — and the fixture is
   * deliberately not in plan order, so a panel that rendered what it was given in the order
   * the plan holds would pass nothing here.
   */
  it('ranks the sources by how much the finish moves with them', async () => {
    await open([FORECAST]);
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );

    const ranking = within(
      (
        await screen.findByRole('heading', {
          name: 'What the spread is made of'
        })
      ).parentElement as HTMLElement
    );

    expect(
      ranking.getAllByRole('listitem').map((row) => row.textContent)
    ).toEqual([
      'A bad stretch, affecting everything at once',
      'Migrate the auth service',
      'Work nobody has listed yet',
      'Write the runbook'
    ]);
  });

  /**
   * <strong>The two rows that are not tasks.</strong> When either tops the list — and in
   * this fixture the shared factor does — the honest reading is that no estimate below it
   * is the problem, which a ranking of tasks alone would have hidden.
   */
  it('names the two sources that are not work', async () => {
    await open([FORECAST]);
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );

    expect(
      await screen.findByText('A bad stretch, affecting everything at once')
    ).toBeInTheDocument();
    expect(screen.getByText('Work nobody has listed yet')).toBeInTheDocument();
  });

  /**
   * <strong>Never a percentage, and this is the assertion that says so.</strong> These
   * shares overlap — a bad quarter is bad for everything at once — so they add to more than
   * a whole. A screen that printed them as percentages would show a plan accounting for
   * three hundred percent of its own uncertainty, which is precisely the precise-looking
   * wrong number this product exists to replace.
   */
  it('states that the shares overlap, and shows no percentage at all', async () => {
    await open([FORECAST]);
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );

    expect(
      await screen.findByText(
        'These overlap and do not add up to a whole: when a quarter goes badly it goes badly for everything at once, so more than one of these moves at the same time.'
      )
    ).toBeInTheDocument();
    const ranking = document.querySelector('.ranking');
    expect(ranking?.textContent).not.toMatch(/%/);
  });

  /**
   * The server versions ahead of the browser, so a kind this build has never heard of says
   * so rather than being labelled as one of the kinds it does know. Falling through to the
   * item branch would have called it "work no longer in this plan", which is not merely
   * unhelpful but wrong — the same back door the limitations list is closed against.
   */
  it('describes a source kind this version has never heard of', async () => {
    await open(
      [FORECAST],
      [{ ...CONTRIBUTIONS[0], kind: 'something_later' as never }]
    );

    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );

    expect(
      await screen.findByText(
        'Something this version of the app cannot describe yet'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText('Work no longer in this plan')).toBeNull();
  });

  /** Named and marked rather than hidden, the way an arrow into archived work is. */
  it('names work that has been put away since the run', async () => {
    await open(
      [FORECAST],
      [
        { ...CONTRIBUTIONS[1], archived: true },
        { ...CONTRIBUTIONS[3], title: null }
      ]
    );
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );

    expect(
      await screen.findByText('Migrate the auth service (put away since)')
    ).toBeInTheDocument();
    expect(screen.getByText('Work no longer in this plan')).toBeInTheDocument();
  });

  /**
   * <strong>A run the engine no longer reproduces is not explained at all.</strong> A
   * ranking from a different model is not a rougher ranking of this plan, it is an exact
   * ranking of a plan nobody forecast — and it would look entirely reasonable, which is
   * why the server refuses and this renders the refusal instead of a list.
   */
  it('says why a run that cannot be replayed has no breakdown', async () => {
    await open([FORECAST]);
    // Refused by URL rather than by turn: the panel below asks for the plan's work as
    // well, so a double answering "the next request, whatever it is" would refuse
    // whichever of the two happened to go out first.
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === CONTRIBUTIONS_URL
          ? jsonResponse(409, {
              code: 'forecast_replay_mismatch',
              detail: 'moved'
            })
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : url === FORECASTS_URL
                ? jsonResponse(200, [FORECAST])
                : jsonResponse(200, WORK_ITEMS)
      )
    );

    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );

    expect(
      await screen.findByText(
        'This forecast cannot be broken down: it was made by an earlier version of the model, which no longer reproduces it exactly.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText('What the spread is made of')).toBeNull();
  });

  /**
   * <strong>The slowest request this panel makes, so the likeliest to outlive it.</strong>
   * Working out a breakdown replays the whole run — half a second at the largest plan — and
   * nothing arriving after somebody has navigated away may touch a panel that has gone.
   * This is the same guard the plan's forecast list already keeps; the breakdown was the
   * one request in the panel that did not, and it is the one that needed it most.
   */
  it('ignores a breakdown that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === CALIBRATION_URL
        ? Promise.resolve(jsonResponse(200, NOTHING_SCORED))
        : url === '/api/auth/me'
          ? Promise.resolve(jsonResponse(200, ACCOUNT))
          : url === CONTRIBUTIONS_URL
            ? new Promise<Response>((resolve, reject) => {
                settle = resolve;
                fail = reject;
              })
            : Promise.resolve(jsonResponse(200, [FORECAST]))
    );

    const answered = renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );
    answered.unmount();
    settle(jsonResponse(200, CONTRIBUTIONS));

    const refused = renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText('What the spread is made of')).toBeNull()
    );
  });

  it('says nothing about earlier forecasts when this is the only one', async () => {
    await open([FORECAST]);

    await screen.findByText(/An 80% chance/);
    expect(
      screen.queryByRole('heading', { name: 'Earlier forecasts' })
    ).not.toBeInTheDocument();
  });

  it('reports a plan whose forecasts could not be loaded', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : jsonResponse(404, {
                code: 'project_not_found',
                detail: 'gone'
              })
      )
    );
    renderRouted(<ForecastPanel projectId={PROJECT_ID} />);

    expect(
      await screen.findByText('That project is no longer in this organisation.')
    ).toBeInTheDocument();
  });

  it('sends a sample count when somebody opens the disclosure and gives one', async () => {
    await open();
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === CALIBRATION_URL
          ? jsonResponse(200, NOTHING_SCORED)
          : url === '/api/auth/me'
            ? jsonResponse(200, ACCOUNT)
            : init?.method === 'POST'
              ? jsonResponse(201, FORECAST)
              : jsonResponse(200, [FORECAST])
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
