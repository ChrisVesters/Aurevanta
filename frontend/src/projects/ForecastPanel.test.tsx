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
  EMPTY_CALIBRATION_RECORD,
  NO_HISTORY,
  NOTHING_SCORED,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Forecast } from './types';

const PROJECT_ID = PROJECTS[0].id;
const PROJECT_NAME = PROJECTS[0].name;
const FORECASTS_URL = `/api/projects/${PROJECT_ID}/forecasts`;
const CONTRIBUTIONS_URL = `/api/forecasts/${FORECAST.id}/contributions`;
const CALIBRATION_URL = '/api/calibration';
const THROUGHPUT_URL = `/api/projects/${PROJECT_ID}/throughput`;

/**
 * What the plan's own history says, answered separately from everything else here.
 *
 * **A double that let this fall through to the forecast list would hand the panel an array
 * where it expects a record**, and the panel reads it on mount — so one that got it wrong
 * would take every case in this suite with it, which is exactly what happened when the
 * calibration read was added.
 *
 * Too little history, which keeps the second date off the screen. The cases that want it on
 * say so for themselves.
 */
/** Half a year of delivery, so both dates are on screen and the control moves both. */
const DELIVERING = {
  projectId: PROJECT_ID,
  asOf: '2026-08-18',
  rule: 'monday_week',
  remaining: 40,
  window: {
    weeks: 26,
    from: '2026-02-16',
    to: '2026-08-17',
    completed: 104,
    perWeek: 4,
    best: 7,
    worst: 0
  },
  projection: {
    meanWeeks: 9.4,
    p10Weeks: 7,
    p50Weeks: 8,
    p80Weeks: 11,
    p90Weeks: 12,
    p95Weeks: 13,
    p10Date: '2026-10-05',
    p50Date: '2026-10-12',
    p80Date: '2026-11-02',
    p90Date: '2026-11-09',
    p95Date: '2026-11-16',
    seed: '834729',
    sampleCount: 10000
  },
  burnUp: burnUp(),
  limitations: ['throughput_excludes_unlisted_work']
};

/**
 * The two halves of `DELIVERING` drawn out: twenty-six weeks climbing to 104, and a cone
 * continuing from 104 and closing on 144.
 *
 * **Built rather than written out, and built to agree with the projection above it.** Each
 * edge tops out on the week that projection says it should — the good edge at seven weeks,
 * the middle at eight, the low edge at twelve — because a fixture whose picture disagrees
 * with its own dates would let a component that mixed the two up pass.
 */
function burnUp() {
  return {
    delivered: 104,
    total: 144,
    past: Array.from({ length: 26 }, (_, week) => ({
      week: weekAfter('2026-02-16', week),
      delivered: (week + 1) * 4
    })),
    cone: Array.from({ length: 14 }, (_, ahead) => ({
      week: weekAfter('2026-08-18', ahead),
      p10: climb(ahead, 12),
      p50: climb(ahead, 8),
      p90: climb(ahead, 7)
    }))
  };
}

/** One edge of the cone, arriving at the backlog in that many weeks and staying there. */
function climb(ahead: number, weeks: number) {
  return Math.min(144, 104 + Math.round((ahead * 40) / weeks));
}

/**
 * A day that many weeks on, in UTC rather than through the local clock: this suite runs in
 * New York on purpose, and a fixture built through the browser's own timezone would be a
 * fixture that moved with it.
 */
function weekAfter(from: string, weeks: number) {
  const day = new Date(`${from}T00:00:00Z`);
  day.setUTCDate(day.getUTCDate() + weeks * 7);
  return day.toISOString().slice(0, 10);
}

/** An earlier forecast of the same plan, which is what a movement is measured against. */
const OLDER: Forecast = {
  ...FORECAST,
  id: '60606060-6060-6060-6060-606060606060',
  p50Hours: 44.0,
  p90Hours: 71.5,
  p80Date: '2026-08-17',
  requestedByName: 'Bob'
};

/**
 * Why the date moved, as the server accounts for it.
 *
 * **The terms sum to the distance between the two dates**, which is the whole claim the
 * feature makes — so the fixture honours it too: out eight days at 80%, made of five of new
 * scope, four of revised estimates and one of progress the other way.
 */
const MOVEMENT = {
  fromRunId: OLDER.id,
  toRunId: FORECAST.id,
  rule: 'progress_first',
  simulations: 6,
  at: [50, 80, 95].map((confidence) => ({
    confidence,
    fromDate: '2026-08-17',
    toDate: confidence === 80 ? '2026-08-25' : '2026-08-21',
    fromHours: 44.0,
    toHours: 52.6,
    terms: [
      { step: 'SAMPLING', movedHours: 0, movedDays: 0 },
      { step: 'PROGRESS', movedHours: -6, movedDays: -1 },
      { step: 'ESTIMATES', movedHours: 24, movedDays: 4 },
      { step: 'SCOPE', movedHours: 30, movedDays: confidence === 80 ? 5 : 1 },
      { step: 'ASSUMPTIONS', movedHours: 0, movedDays: 0 },
      { step: 'CALENDAR', movedHours: 0, movedDays: 0 },
      { step: 'STARTS_ON', movedHours: 0, movedDays: 0 }
    ]
  }))
};

const MOVEMENT_URL = `/api/forecasts/${FORECAST.id}/movement`;

const ITEMS_URL = `/api/projects/${PROJECT_ID}/items`;
const CUTS_URL = `/api/forecasts/${FORECAST.id}/cuts`;

describe('ForecastPanel', () => {
  const fetchMock = mockFetch();

  /**
   * The listing's own shape: the runs, and what the sequence of them says. Written out
   * here rather than left to each case, because a double that answered a bare array would
   * be answering a shape this endpoint stopped having — and the panel would read `runs`
   * off it as undefined with nothing saying why.
   */
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

  /**
   * Everything else one of these panels asks for, answered by URL rather than in bulk.
   * The plan's forecasts arrive wrapped in an account of the history and its work arrives
   * as a list, so a double answering both alike hands one of them the other's shape — the
   * rule this file already keeps for the session and the switcher.
   */
  function otherReads(url: string, runs: Forecast[] = [FORECAST]) {
    return url.endsWith('/forecasts')
      ? jsonResponse(200, listing(runs))
      : jsonResponse(200, []);
  }

  /** The panel over an organisation that has described its team. */
  async function openWithResources(pools: unknown[]) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : url === '/api/resources'
                ? jsonResponse(200, pools)
                : otherReads(url)
      )
    );
    const rendered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    return rendered;
  }

  /**
   * The session and the plan's forecasts, answered separately. A double that answered both
   * alike would hand the panel an account where it expected a list, and every assertion
   * about what is on screen would be testing the wrong thing.
   */
  function answer(runs: Forecast[], spread: unknown = CONTRIBUTIONS) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : url === FORECASTS_URL
                ? jsonResponse(200, listing(runs))
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

  /** The plan, with a history behind it — answered by URL like everything else here. */
  async function openWithHistory(history: unknown, run: Forecast = FORECAST) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, history)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : otherReads(url, [run])
      )
    );
    const rendered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    return rendered;
  }

  /**
   * The panel over a history the server has already made up its mind about — the drift
   * verdict rides on the listing, so a case about it is a case about that payload.
   */
  async function openHistory(history: unknown, account: unknown = MOVEMENT) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : url === FORECASTS_URL
                ? jsonResponse(200, history)
                : url.startsWith(MOVEMENT_URL)
                  ? jsonResponse(200, account)
                  : jsonResponse(200, [])
      )
    );
    const rendered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    return rendered;
  }

  /** A history of two runs, with whatever the server has concluded about the drift. */
  function drifting(at: Record<string, unknown>) {
    return {
      runs: [FORECAST, OLDER],
      drift: {
        sinceRunId: OLDER.id,
        runs: 2,
        at: [50, 80, 95].map((confidence) => ({
          confidence,
          fromDate: '2026-08-17',
          toDate: '2026-08-25',
          days: 8,
          bandDays: 10,
          movingOut: false,
          ...(confidence === 80 ? at : {})
        }))
      }
    };
  }

  /**
   * The burn-up's table, found the way a reader finds it — by its caption. The panel holds
   * another table and this is what tells the two apart, which is also the assertion that the
   * caption is doing its job as a label.
   */
  function burnUpTable() {
    return screen.getByRole('table', { name: /weeks delivered/ });
  }

  function burnUpTableWhenReady() {
    return screen.findByRole('table', { name: /weeks delivered/ });
  }

  /** Where one shape in the drawing actually sits, read back off what was rendered. */
  function pointsOf(container: HTMLElement, selector: string) {
    return (container.querySelector(selector)?.getAttribute('points') ?? '')
      .split(' ')
      .filter((point) => point !== '');
  }

  async function open(runs: Forecast[] = [], spread: unknown = CONTRIBUTIONS) {
    storeAccessToken();
    answer(runs, spread);
    renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
  }

  /** Whatever the next request for a forecast is refused with. */
  function refuse(problem: Record<string, unknown>) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : init?.method === 'POST'
                ? jsonResponse(400, problem)
                : jsonResponse(200, listing([]))
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
    await screen.findByText(/80% chance that Q3 platform work/);
    const asked = fetchMock.mock.calls.length;

    expect(
      screen.getByText(
        'There is a 80% chance that Q3 platform work will be finished by Aug 25, 2026.'
      )
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '50%' }));
    expect(
      screen.getByText(
        'There is a 50% chance that Q3 platform work will be finished by Aug 21, 2026.'
      )
    ).toBeInTheDocument();

    await userEvent.click(screen.getByRole('radio', { name: '95%' }));
    expect(
      screen.getByText(
        'There is a 95% chance that Q3 platform work will be finished by Aug 31, 2026.'
      )
    ).toBeInTheDocument();

    expect(fetchMock.mock.calls).toHaveLength(asked);
  });

  /**
   * <strong>The test of the sentence is whether it survives being pasted somewhere else.</strong>
   * A confidence and a day are not enough — read anywhere but this screen they describe
   * nothing — so the plan is named, and named from the prop rather than from anything this
   * component fetches. A different plan is used here for that reason: a hard-coded name would
   * pass against the fixture above.
   */
  it('names the plan, so the line means something away from this screen', async () => {
    storeAccessToken();
    answer([FORECAST]);
    renderRouted(
      <ForecastPanel
        projectId={PROJECT_ID}
        projectName="Everything the board asked for"
      />
    );

    expect(
      await screen.findByText(
        'There is a 80% chance that Everything the board asked for will be finished by Aug 25, 2026.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>One-sided, and `roadmap.md`'s own example is not.</strong> "Between 12 October and
   * 20 November" is a two-sided interval, and it invites a question about the early end that
   * nobody manages against and that the model is worst at. The sentence names one day: the one
   * somebody would commit to.
   */
  it('says one date and never a window', async () => {
    await open([FORECAST]);
    await screen.findByText(/80% chance that Q3 platform work/);

    for (const level of ['50%', '95%']) {
      await userEvent.click(screen.getByRole('radio', { name: level }));
      const sentence = screen.getByText(/chance that Q3 platform work/);
      expect(sentence.textContent).not.toMatch(/between/i);
      // One day named, not two: the band's own two numbers are hours and live elsewhere.
      expect(sentence.textContent?.match(/\d{4}/g)).toHaveLength(1);
    }
  });

  /**
   * The band is what the engine produced; the date is that with a working day on top. Take
   * the hours away and nothing on this screen came out of the model, which is how an
   * assumption stops being visible and starts being mistaken for a result.
   */
  it('keeps the hours on screen at every confidence', async () => {
    await open([FORECAST]);
    await screen.findByText(/80% chance that Q3 platform work/);

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
  // What the plan has actually been delivering -------------------------------

  /**
   * <strong>The gap, which is the whole of what this is for.</strong> Both dates at the
   * confidence already chosen, the distance between them named rather than left to be
   * subtracted, and the window a reader needs to judge whether the history contains their own
   * bad week.
   */
  it('says what the plan’s own history says, beside what its estimates say', async () => {
    await openWithHistory(DELIVERING);

    expect(
      await screen.findByText('Its own history says 80% likely by Nov 2, 2026.')
    ).toBeInTheDocument();
    expect(
      screen.getByText('The estimates above say Aug 25, 2026.')
    ).toBeInTheDocument();
    expect(
      screen.getByText(/The history is 69 days later/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '26 weeks of history, 104 delivered — best week 7, worst week 0.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>Decision 12's flag, which the window alone does not carry.</strong> A bootstrap can
   * draw nothing worse than the worst week it has seen, and at a quarter of history roughly one
   * team in four has not yet had one — so a short window is published *and* marked. Whether it
   * fires is the server's to decide; this end renders a flag it was sent.
   */
  it('marks an answer drawn from a short history', async () => {
    await openWithHistory({
      ...DELIVERING,
      limitations: [
        'throughput_excludes_unlisted_work',
        'throughput_window_is_short'
      ]
    });

    expect(
      await screen.findByText(
        /It can never produce a week worse than the worst one above/
      )
    ).toBeInTheDocument();
  });

  /** And a year of history is not marked, or the warning is one nobody reads. */
  it('leaves a long history unmarked', async () => {
    await openWithHistory(DELIVERING);

    await screen.findByText('Its own history says 80% likely by Nov 2, 2026.');
    expect(screen.queryByText(/It can never produce a week worse/)).toBeNull();
  });

  /**
   * One control, two dates, no request — which is the property M4 built the control for,
   * kept by holding both sets of percentiles on this side.
   */
  it('moves both dates when the confidence changes', async () => {
    await openWithHistory(DELIVERING);
    await screen.findByText('Its own history says 80% likely by Nov 2, 2026.');
    const before = fetchMock.mock.calls.length;

    await userEvent.click(screen.getByRole('radio', { name: '50%' }));

    expect(
      await screen.findByText(
        'Its own history says 50% likely by Oct 12, 2026.'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('The estimates above say Aug 21, 2026.')
    ).toBeInTheDocument();
    expect(fetchMock.mock.calls).toHaveLength(before);
  });

  /**
   * Decision 7's table, and the two rows that carry this run's own numbers are the two
   * somebody can act on. Named rather than folded into the difference above, because two of
   * the four make the forecast look slow and two make it look fast.
   */
  it('names what the two forecasts are not agreeing about', async () => {
    await openWithHistory(DELIVERING, {
      ...FORECAST,
      itemCount: 4,
      estimatedItemCount: 2
    });
    await screen.findByText('What the two are not agreeing about');

    expect(
      screen.getByText(/This forecast assumed the plan will grow by 20–60%/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/2 of 4 items carry no estimate/)
    ).toBeInTheDocument();
    expect(screen.getByText(/already in wall-clock weeks/)).toBeInTheDocument();
    expect(
      screen.getByText(/in the history by construction/)
    ).toBeInTheDocument();
  });

  /**
   * <strong>Earlier is a real result and not a bug</strong>, and it is what a half-estimated
   * plan looks like: the engine carries unestimated items at zero effort and says so, while
   * the history counts them like anything else. The engine is the one under-reporting there.
   */
  it('says when the history is the earlier of the two', async () => {
    await openWithHistory({
      ...DELIVERING,
      projection: { ...DELIVERING.projection, p80Date: '2026-08-11' }
    });

    expect(
      await screen.findByText(
        'The history is 14 days earlier than the estimates.'
      )
    ).toBeInTheDocument();
  });

  /** And two that land together, which is the reading to be most suspicious of. */
  it('says when the two land on the same day', async () => {
    await openWithHistory({
      ...DELIVERING,
      projection: { ...DELIVERING.projection, p80Date: '2026-08-25' }
    });

    expect(
      await screen.findByText('The two land on the same day.')
    ).toBeInTheDocument();
  });

  /**
   * Three ways to have no second date and each says which — and the window still ships,
   * because it is the half a reader can judge for themselves.
   */
  it('says why there is no second opinion rather than showing a gap', async () => {
    await openWithHistory({
      ...DELIVERING,
      projection: null,
      limitations: [
        'throughput_excludes_unlisted_work',
        'throughput_history_too_short'
      ]
    });

    expect(
      await screen.findByText(
        /There is not enough finished work here to project from/
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '26 weeks of history, 104 delivered — best week 7, worst week 0.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText(/Its own history says/)).toBeNull();
  });

  /** A reason this version has never heard of gets a sentence rather than nothing. */
  it('says so when it cannot explain the absence', async () => {
    await openWithHistory({
      ...DELIVERING,
      projection: null,
      limitations: ['throughput_gone_sideways']
    });

    expect(
      await screen.findByText(
        'This version of the app cannot say why there is no second opinion.'
      )
    ).toBeInTheDocument();
  });

  /**
   * The other side of the two live rows. A run that assumed no growth is short by unlisted
   * work exactly as the history is — the two agree, and saying so is more useful than leaving
   * a reader to notice a sentence is missing.
   */
  it('says so when the two rows that vary do not differ', async () => {
    await openWithHistory(DELIVERING, {
      ...FORECAST,
      scopeGrowthP10Percent: 0,
      scopeGrowthP90Percent: 0
    });
    await screen.findByText('What the two are not agreeing about');

    expect(
      screen.getByText(/This forecast assumed no growth at all/)
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Every item carries an estimate/)
    ).toBeInTheDocument();
  });

  /**
   * <strong>A refusal that names something fixable in somebody's own plan is passed on.</strong>
   * A task marked finished next week — which this product's own progress form accepts — used to
   * take the entire comparison off the screen with nothing anywhere saying why. Leaving the band
   * alone does not mean leaving the reader guessing.
   */
  it('says why the history could not be read rather than vanishing', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(400, { code: 'throughput_out_of_order' })
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : otherReads(url)
      )
    );
    renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );

    expect(
      await screen.findByText(
        /marked as finished on a day that has not happened yet/
      )
    ).toBeInTheDocument();
    expect(
      screen.getByRole('heading', {
        name: 'What this plan has actually been delivering'
      })
    ).toBeInTheDocument();
    // And the band it sits under is untouched.
    expect(
      screen.getByText(/An 80% chance of taking between 14.2 and 52.6 hours/)
    ).toBeInTheDocument();
  });

  // The burn-up, which is a table first and a picture second ------------------

  /**
   * <strong>The sentence somebody reads out, and it needs no picture at all.</strong>
   * "Delivered 104 of 144" is what a plan is, and the two dates beside it are the same
   * projection the second opinion above already published — read here as when the last of
   * the work lands rather than as a confidence.
   */
  it('says what has been delivered and names no second date', async () => {
    await openWithHistory(DELIVERING);

    expect(
      await screen.findByText('Delivered 104 of 144.')
    ).toBeInTheDocument();
    // **The half the review pass removed.** This step's own example in `m10-plan.md` said
    // "the last is done between 12 October and 30 November", which is the two-sided form
    // decision 2 exists to keep out — and the date it restated is already on screen
    // one-sided, three lines above. `Oct 5` is that sentence's early end, and it is the
    // whole of what was wrong with it: nobody manages against the good end.
    //
    // The band in hours above is not the same claim and stays: it is what the engine
    // produced, where this would have been a window over two dates.
    expect(screen.queryByText(/done between/)).toBeNull();
    expect(screen.queryByText(/Oct 5, 2026/)).toBeNull();
    expect(
      screen.getByText('Its own history says 80% likely by Nov 2, 2026.')
    ).toBeInTheDocument();
  });

  /**
   * <strong>The table says how many weeks it holds, and then holds them.</strong> Decision 9
   * inverted: the equivalent is not a fallback bolted to a picture, it is the thing that is
   * asserted — so a week the drawing draws and the table does not is a failing test rather
   * than a reader told less than a viewer.
   */
  it('renders every week it says it does', async () => {
    await openWithHistory(DELIVERING);

    expect(
      await screen.findByText(
        '26 weeks delivered, then 13 weeks this history projects.'
      )
    ).toBeInTheDocument();
    // Twenty-six delivered, thirteen projected, the heading over each half, and the row of
    // column headings: every week is a row and nothing is summarised away.
    expect(within(burnUpTable()).getAllByRole('row')).toHaveLength(41);
    expect(
      within(burnUpTable()).getByRole('rowheader', { name: 'Feb 16, 2026' })
    ).toBeInTheDocument();
    expect(
      within(burnUpTable()).getByRole('rowheader', { name: 'Nov 17, 2026' })
    ).toBeInTheDocument();
  });

  /**
   * <strong>The past is a figure and the future is a band, and the table is what says
   * which.</strong> A row carrying a range has not happened yet; one without a range has.
   * The group heading says it in words as well, because a range column alone is thin to hang
   * that on when it is being read out one cell at a time.
   */
  it('separates what happened from what is projected', async () => {
    await openWithHistory(DELIVERING);

    const rows = within(await burnUpTableWhenReady()).getAllByRole('row');

    // The last delivered week, which carries a number and no range.
    expect(within(rows[26]).getByRole('rowheader')).toHaveTextContent(
      'Aug 10, 2026'
    );
    expect(within(rows[26]).getAllByRole('cell')[0]).toHaveTextContent('104');
    expect(within(rows[26]).getAllByRole('cell')[1]).toBeEmptyDOMElement();
    // The heading over the projected half, and then the first week of it. `rowgroup` and
    // not `colgroup`: it labels the rows below it, and a `colgroup` scope points at a
    // `<colgroup>` element — so it would be a heading associated with nothing, which is
    // exactly the association carrying "this half has not happened yet".
    expect(rows[27]).toHaveTextContent('What the history projects');
    expect(within(rows[27]).getByRole('rowheader')).toHaveAttribute(
      'scope',
      'rowgroup'
    );
    expect(within(rows[28]).getAllByRole('cell')[1]).toHaveTextContent(
      '107 to 110'
    );
  });

  /**
   * <strong>The drawing is out of the accessibility tree and the table is not.</strong> A
   * picture and its equivalent saying the same thing twice to a screen reader is worse than
   * either alone — so the enhancement is hidden from it and the feature is what is read.
   */
  it('hides the drawing from a screen reader and not the table', async () => {
    const { container } = await openWithHistory(DELIVERING);

    await burnUpTableWhenReady();
    const drawing = container.querySelector('.burnup svg');
    expect(drawing).not.toBeNull();
    expect(drawing).toHaveAttribute('aria-hidden', 'true');
    // And it carries a cone as well as a line, or the hidden half is hiding nothing.
    expect(container.querySelector('.burnup polygon')).not.toBeNull();
    expect(container.querySelector('.burnup polyline')).not.toBeNull();
  });

  /**
   * <strong>The two halves meet, which is the whole reason the drawing holds a point the
   * table does not.</strong> The cone's first week is today with nothing further delivered —
   * the last delivered week under another name — so it is the join, and a picture whose line
   * and band did not touch would be showing a plan that delivered nothing for a week and then
   * caught up.
   */
  it('joins the delivered line to the cone', async () => {
    const { container } = await openWithHistory(DELIVERING);

    await burnUpTableWhenReady();
    const line = pointsOf(container, '.burnup .delivered');
    const band = pointsOf(container, '.burnup .cone');

    expect(line[line.length - 1]).toEqual(band[0]);
    // And nothing anywhere is a NaN, which is what a divide by no weeks would leave.
    expect([...line, ...band].join(' ')).not.toContain('NaN');
  });

  /**
   * <strong>One week is a week and not "1 weeks".</strong> Both counts in that caption reach
   * one — a plan with a single week of history, and one the history says finishes inside a
   * week — so both halves of the sentence carry their own plural rather than interpolating a
   * bare number next to a hard-coded "weeks".
   */
  it('counts a single week in the singular', async () => {
    await openWithHistory({
      ...DELIVERING,
      projection: null,
      burnUp: {
        delivered: 4,
        total: 44,
        past: [{ week: '2026-08-10', delivered: 4 }],
        cone: null
      },
      limitations: [
        'throughput_excludes_unlisted_work',
        'throughput_history_too_short'
      ]
    });

    expect(
      await screen.findByText(
        '1 week delivered, then 0 weeks this history projects.'
      )
    ).toBeInTheDocument();
  });

  /** And the other half of it: a plan the history says is finished inside one more week. */
  it('counts a single week ahead in the singular', async () => {
    await openWithHistory({
      ...DELIVERING,
      burnUp: { ...burnUp(), cone: burnUp().cone.slice(0, 2) }
    });

    expect(
      await screen.findByText(
        '26 weeks delivered, then 1 week this history projects.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>Its past and no cone, saying which</strong> — M9's three states arriving here
   * unchanged. A plan too young to project from has still delivered something, and what it
   * has delivered is worth drawing.
   */
  it('draws the past of a plan too young to project from', async () => {
    await openWithHistory({
      ...DELIVERING,
      projection: null,
      burnUp: { ...burnUp(), cone: null },
      limitations: [
        'throughput_excludes_unlisted_work',
        'throughput_history_too_short'
      ]
    });

    expect(
      await screen.findByText(
        '26 weeks delivered, then 0 weeks this history projects.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByText('What the history projects')).toBeNull();
    expect(screen.queryByText(/On this history the last of it/)).toBeNull();
    // And the reason is on screen, where it already was.
    expect(
      screen.getByText(/There is not enough finished work here to project from/)
    ).toBeInTheDocument();
  });

  /**
   * A plan nobody has finished anything in has no picture at all, for the reason it has no
   * window: an empty axis is a chart of nothing, and the sentence beside it already says why.
   */
  it('draws nothing for a plan that has delivered nothing', async () => {
    await openWithHistory(NO_HISTORY);

    await screen.findByRole('heading', {
      name: 'What this plan has actually been delivering'
    });
    expect(screen.queryByText(/Delivered 0 of/)).toBeNull();
    expect(screen.queryByText(/weeks delivered, then/)).toBeNull();
  });

  /**
   * A run made before there was a calendar has no date to compare against, and the history
   * still has one of its own. It shows what it has rather than nothing.
   */
  it('shows the history’s date beside a run that has none', async () => {
    await openWithHistory(DELIVERING, {
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
      await screen.findByText('Its own history says 80% likely by Nov 2, 2026.')
    ).toBeInTheDocument();
    expect(screen.queryByText(/The estimates above say/)).toBeNull();
    expect(screen.queryByText(/days later than the estimates/)).toBeNull();
  });

  /**
   * A forecast is not less true because the history beside it could not be read, so a failure
   * there leaves the band alone — the same rule the track-record line keeps.
   */
  it('still shows the band when the history cannot be read', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(500, null)
          : url === CALIBRATION_URL
            ? jsonResponse(200, NOTHING_SCORED)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : otherReads(url)
      )
    );
    renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );

    await screen.findByRole('heading', { name: 'Forecast' });
    expect(screen.queryByText(/Its own history says/)).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
  });

  /** Nothing arriving after somebody has navigated away may touch a panel that has gone. */
  it('ignores a history that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url.startsWith(THROUGHPUT_URL)
        ? new Promise<Response>((resolve, reject) => {
            settle = resolve;
            fail = reject;
          })
        : url === CALIBRATION_URL
          ? Promise.resolve(jsonResponse(200, NOTHING_SCORED))
          : url === '/api/auth/me'
            ? Promise.resolve(jsonResponse(200, ACCOUNT))
            : Promise.resolve(otherReads(url))
    );

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(jsonResponse(200, DELIVERING));

    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/Its own history says/)).toBeNull()
    );
  });

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
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(200, {
                ...NOTHING_SCORED,
                forecasts: {
                  ...EMPTY_CALIBRATION_RECORD,
                  scored: 40,
                  hits: 18,
                  rate: { value: 0.45, low: 0.353, high: 0.551 }
                }
              })
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : otherReads(url)
      )
    );
    renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );

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
      url.startsWith(THROUGHPUT_URL)
        ? Promise.resolve(jsonResponse(200, NO_HISTORY))
        : url === CALIBRATION_URL
          ? new Promise<Response>((resolve, reject) => {
              settle = resolve;
              fail = reject;
            })
          : url === '/api/auth/me'
            ? Promise.resolve(jsonResponse(200, ACCOUNT))
            : Promise.resolve(otherReads(url))
    );

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(
      jsonResponse(200, {
        ...NOTHING_SCORED,
        forecasts: {
          ...EMPTY_CALIBRATION_RECORD,
          scored: 40,
          hits: 18,
          rate: { value: 0.45, low: 0.353, high: 0.551 }
        }
      })
    );

    // And the same on the way out through the failure path, which is the one that would
    // otherwise clear a track record belonging to a panel that is now on screen.
    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
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
        url.startsWith(THROUGHPUT_URL)
          ? jsonResponse(200, NO_HISTORY)
          : url === CALIBRATION_URL
            ? jsonResponse(500, null)
            : url === '/api/auth/me'
              ? jsonResponse(200, ACCOUNT)
              : otherReads(url)
      )
    );
    renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );

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

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(jsonResponse(200, listing([FORECAST])));

    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
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
    await screen.findByText(/80% chance that Q3 platform work/);

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
                ? jsonResponse(200, listing([FORECAST]))
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
      url.startsWith(THROUGHPUT_URL)
        ? Promise.resolve(jsonResponse(200, NO_HISTORY))
        : url === CALIBRATION_URL
          ? Promise.resolve(jsonResponse(200, NOTHING_SCORED))
          : url === '/api/auth/me'
            ? Promise.resolve(jsonResponse(200, ACCOUNT))
            : url === CONTRIBUTIONS_URL
              ? new Promise<Response>((resolve, reject) => {
                  settle = resolve;
                  fail = reject;
                })
              : Promise.resolve(otherReads(url))
    );

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    await userEvent.click(
      await screen.findByRole('button', { name: 'What is widening this?' })
    );
    answered.unmount();
    settle(jsonResponse(200, CONTRIBUTIONS));

    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
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

    expect(
      screen.queryByLabelText('Things that can be under way at once')
    ).toBeNull();
    expect(
      screen.getByText(
        'Your one resource says how much can be under way at once, so there is nothing to answer here.'
      )
    ).toBeInTheDocument();
  });

  /** And it is there for an organisation that has described none, which is most of them. */
  it('asks for a capacity when no team has been described', async () => {
    await openWithResources([]);

    expect(
      screen.getByLabelText('Things that can be under way at once')
    ).toBeInTheDocument();
  });

  /**
   * <strong>What it was scheduled against travels with the number</strong>, which is the
   * rule the other assumptions keep. The units are the run's own — a pool that has grown
   * since did not grow this forecast.
   */
  it('says which team the forecast was scheduled against', async () => {
    await openWithHistory(DELIVERING, {
      ...FORECAST,
      resources: [
        {
          resourceId: 'r1',
          name: 'Backend engineers',
          archived: false,
          units: 3
        },
        {
          resourceId: 'r2',
          name: 'Staging environment',
          archived: false,
          units: 1
        }
      ]
    });

    expect(
      await screen.findByText(
        'Scheduled against Backend engineers (3), Staging environment (1).'
      )
    ).toBeInTheDocument();
  });

  /**
   * A pool put away since is marked rather than shown as ordinary, and one this
   * organisation no longer holds at all says so rather than rendering as a blank — the same
   * three-way rule a contribution ranking keeps for the work it names.
   */
  it('marks a resource put away since, and one that is gone altogether', async () => {
    await openWithHistory(DELIVERING, {
      ...FORECAST,
      resources: [
        { resourceId: 'r1', name: 'Contractors', archived: true, units: 2 },
        { resourceId: 'r2', name: null, archived: false, units: 1 }
      ]
    });

    expect(
      await screen.findByText(
        'Scheduled against Contractors (2, since put away), a resource that is no longer here (1).'
      )
    ).toBeInTheDocument();
  });

  /** A run against no declared team says nothing, rather than saying it had none. */
  it('says nothing about a team where there was none', async () => {
    await openWithHistory(DELIVERING);

    await screen.findByText(/An 80% chance/);
    expect(screen.queryByText(/Scheduled against/)).toBeNull();
  });

  /**
   * The plan's own history is the same, and it is the read this panel is built around: a
   * listing arriving after somebody has navigated away must not put a forecast on a screen
   * that has gone.
   */
  it('drops a history that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url.endsWith('/forecasts')
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
              : Promise.resolve(jsonResponse(200, []))
    );

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(jsonResponse(200, listing([FORECAST])));

    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() => expect(screen.queryByText(/An 80% chance/)).toBeNull());
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

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    answered.unmount();
    settle(jsonResponse(200, []));

    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByRole('heading', { name: 'Forecast' })).toBeNull()
    );
  });

  // Whether it keeps moving out, and why it moved ----------------------------

  /**
   * <strong>A plan that is sliding says so, beside the date it is about.</strong> Not down
   * beside the history it was measured from: a date that keeps moving out is worth less than
   * it looks, and that is a caveat about *this* number.
   */
  it('warns when the date keeps moving out', async () => {
    await openHistory(drifting({ movingOut: true }));

    expect(
      await screen.findByText(
        'This plan has been drifting. At 80% it said Aug 17, 2026 and now says Aug 25, 2026 — 8 days out, against a band 10 days wide.'
      )
    ).toBeInTheDocument();
  });

  /**
   * One day out against a band one day wide, which is the smallest thing this can say and
   * the one that reads as "1 days out" if the two counts share a plural rule.
   */
  it('says a single day in the singular', async () => {
    await openHistory(
      drifting({ movingOut: true, days: 1, bandDays: 1, toDate: '2026-08-18' })
    );

    expect(
      await screen.findByText(
        'This plan has been drifting. At 80% it said Aug 17, 2026 and now says Aug 18, 2026 — 1 day out, against a band 1 day wide.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>And a plan that is merely churning hears nothing.</strong> The measurement this
   * milestone is built on is that a rule about direction fires on 86% of plans that are not
   * sliding at all — so the same eight days, on a band wide enough to have admitted to them,
   * are not worth interrupting anybody about. Whether they are is the server's to decide.
   */
  it('says nothing about a plan that is merely churning', async () => {
    await openHistory(drifting({ movingOut: false }));

    await screen.findByText(/An 80% chance/);
    expect(screen.queryByText(/has been drifting/)).toBeNull();
  });

  /**
   * <strong>The account of why, which is the feature the icebox calls the one it would most
   * want.</strong> Out eight days, made of five of new scope, four of revised estimates and
   * one of progress the other way — and they add up, which is the only reason it is worth
   * publishing at all.
   */
  it('explains why the date moved when somebody asks', async () => {
    await openHistory(drifting({ movingOut: false }));

    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );

    expect(
      await screen.findByText(
        'At 80% this plan moved out 8 days: it said Aug 17, 2026 and now says Aug 25, 2026.'
      )
    ).toBeInTheDocument();
    const account = within(
      screen.getByText('Work added or put away').parentElement
        ?.parentElement as HTMLElement
    );
    expect(account.getByText('5 days later')).toBeInTheDocument();
    expect(account.getByText('4 days later')).toBeInTheDocument();
    expect(account.getByText('1 day earlier')).toBeInTheDocument();
    // What it cost, said rather than hidden — and why the terms add up at all.
    expect(screen.getByText(/It cost 6 simulations\./)).toBeInTheDocument();
  });

  /**
   * <strong>Six simulations is not a price to charge somebody who did not ask.</strong> The
   * question is on screen and the answer is not until it is clicked, which is the rule the
   * breakdown of the spread already keeps.
   */
  it('asks nothing until somebody wants to know', async () => {
    await openHistory(drifting({ movingOut: false }));

    await screen.findByRole('button', { name: 'Why did the date move?' });
    expect(
      fetchMock.mock.calls.filter((call) =>
        String(call[0]).includes('/movement')
      )
    ).toHaveLength(0);
  });

  /**
   * <strong>One control, three accounts, no request.</strong> All three confidences come
   * back together, so moving the control re-reads the account somebody already paid six
   * simulations for rather than buying another six.
   */
  it('moves the account with the confidence and sends no request', async () => {
    await openHistory(drifting({ movingOut: false }));
    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );
    await screen.findByText(/moved out 8 days/);
    const before = fetchMock.mock.calls.length;

    await userEvent.click(screen.getByRole('radio', { name: '50%' }));

    expect(await screen.findByText(/moved out 4 days/)).toBeInTheDocument();
    expect(fetchMock.mock.calls).toHaveLength(before);
  });

  /**
   * <strong>M6's argument, arriving one level up.</strong> An account of a movement between
   * two versions of the model is an exact account of a movement that never happened — so the
   * server refuses, and the refusal is what is shown rather than a blank.
   */
  it('passes on a refusal to compare two forecasts', async () => {
    await openHistory(drifting({ movingOut: false }), null);
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url.startsWith(MOVEMENT_URL)
          ? jsonResponse(400, {
              code: 'forecast_not_comparable',
              detail: 'no'
            })
          : url.startsWith(THROUGHPUT_URL)
            ? jsonResponse(200, NO_HISTORY)
            : url === CALIBRATION_URL
              ? jsonResponse(200, NOTHING_SCORED)
              : url === '/api/auth/me'
                ? jsonResponse(200, ACCOUNT)
                : url === FORECASTS_URL
                  ? jsonResponse(200, drifting({ movingOut: false }))
                  : jsonResponse(200, [])
      )
    );

    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );

    expect(
      await screen.findByText(
        /they were made by different versions of the model/
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>A run with no date is not asked why its date moved.</strong> One made before
   * there was a calendar reports hours and no day, and the question is not a question about
   * it — the same reason the confidence control is absent rather than disabled on one.
   */
  it('offers no account against a forecast that never had a date', async () => {
    await openHistory({
      runs: [
        FORECAST,
        {
          ...OLDER,
          startsOn: null,
          workingHoursPerDay: null,
          calendarRule: null,
          p10Date: null,
          p50Date: null,
          p80Date: null,
          p90Date: null,
          p95Date: null
        }
      ],
      drift: null
    });

    await screen.findByRole('heading', { name: 'Earlier forecasts' });
    expect(
      screen.queryByRole('button', { name: 'Why did the date move?' })
    ).toBeNull();
  });

  /** A plan that came in says so, rather than being reported as a move of minus eight. */
  it('says when the date came in rather than moved out', async () => {
    await openHistory(drifting({ movingOut: false }), {
      ...MOVEMENT,
      at: MOVEMENT.at.map((account) => ({
        ...account,
        fromDate: '2026-08-25',
        toDate: '2026-08-17'
      }))
    });

    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );

    expect(
      await screen.findByText(
        'At 80% this plan came in 8 days: it said Aug 25, 2026 and now says Aug 17, 2026.'
      )
    ).toBeInTheDocument();
  });

  /**
   * And one whose date has not moved at all says *that*, which is the ordinary answer for
   * two runs a day apart: the terms below are still worth reading, because a plan that
   * gained a week of scope and delivered a week of work has not stood still.
   */
  it('says when the date has not moved at all', async () => {
    await openHistory(drifting({ movingOut: false }), {
      ...MOVEMENT,
      at: MOVEMENT.at.map((account) => ({
        ...account,
        fromDate: '2026-08-25',
        toDate: '2026-08-25'
      }))
    });

    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );

    expect(
      await screen.findByText(
        'At 80% the date has not moved: Aug 25, 2026 then and now.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>Six simulations is long enough to outlive the panel that asked.</strong> The
   * same guard the breakdown keeps, and it needs its own case because it is its own request:
   * nothing arriving after somebody has navigated away may touch a panel that has gone.
   */
  it('ignores an account that arrives after the panel has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url.startsWith(MOVEMENT_URL)
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
              : url === FORECASTS_URL
                ? Promise.resolve(
                    jsonResponse(200, drifting({ movingOut: false }))
                  )
                : Promise.resolve(jsonResponse(200, []))
    );

    const answered = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );
    answered.unmount();
    settle(jsonResponse(200, MOVEMENT));

    const refused = renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );
    await screen.findByRole('heading', { name: 'Forecast' });
    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );
    refused.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() =>
      expect(screen.queryByText(/this plan moved out/)).toBeNull()
    );
  });

  /**
   * A guard rather than a scenario, and it is here for the reason the panel renders a
   * limitation it has never heard of: the server versions ahead. A term with no days to
   * report says so, where a blank would read as a term of nothing.
   */
  it('says when a term cannot be put in days', async () => {
    await openHistory(drifting({ movingOut: false }), {
      ...MOVEMENT,
      at: MOVEMENT.at.map((account) => ({
        ...account,
        fromDate: null,
        toDate: null,
        terms: account.terms.map((term) => ({ ...term, movedDays: null }))
      }))
    });

    await userEvent.click(
      await screen.findByRole('button', { name: 'Why did the date move?' })
    );

    expect(await screen.findAllByText('not in days')).toHaveLength(7);
    expect(screen.queryByText(/this plan moved out/)).toBeNull();
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
    renderRouted(
      <ForecastPanel projectId={PROJECT_ID} projectName={PROJECT_NAME} />
    );

    expect(
      await screen.findByText('That project is no longer in this organisation.')
    ).toBeInTheDocument();
  });

  it('sends a sample count when somebody opens the disclosure and gives one', async () => {
    await open();
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
