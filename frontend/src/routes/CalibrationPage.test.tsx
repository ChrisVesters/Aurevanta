import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { CalibrationPage } from './CalibrationPage';
import {
  ACCOUNT,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Calibration, CalibrationRecord } from '../calibration/types';

const NOTHING: CalibrationRecord = {
  scored: 0,
  hits: 0,
  belowP10: 0,
  aboveP90: 0,
  pointEstimates: 0,
  rate: null,
  corrections: null
};

/**
 * The counts from the table this work is designed around: eighteen of forty is 45%,
 * with an interval that does not reach 80% — the first count at which a record says
 * anything at all.
 */
const FORTY: CalibrationRecord = {
  scored: 40,
  hits: 18,
  belowP10: 3,
  aboveP90: 19,
  pointEstimates: 2,
  rate: { value: 0.45, low: 0.353, high: 0.551 },
  corrections: { medianPercentile: 0.78, bandWidthMultiplier: 2.4 }
};

const EMPTY: Calibration = {
  forecasts: NOTHING,
  reports: NOTHING,
  unbounded: NOTHING,
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

const RECORDED: Calibration = {
  forecasts: FORTY,
  reports: {
    ...NOTHING,
    scored: 6,
    hits: 6,
    rate: { value: 1, low: 0.63, high: 1 }
  },
  unbounded: {
    ...NOTHING,
    scored: 2,
    hits: 1,
    rate: { value: 0.5, low: 0.17, high: 0.83 }
  },
  byEstimator: [
    {
      estimatorId: '11111111-1111-1111-1111-111111111111',
      estimatorName: 'Ada',
      record: {
        ...FORTY,
        scored: 22,
        hits: 8,
        rate: { value: 0.36, low: 0.26, high: 0.48 }
      }
    },
    {
      estimatorId: '33333333-3333-3333-3333-333333333333',
      estimatorName: 'Zara',
      record: {
        ...FORTY,
        scored: 18,
        hits: 10,
        rate: { value: 0.56, low: 0.42, high: 0.69 }
      }
    }
  ],
  byMethod: [
    {
      method: 'surprise_framed',
      record: {
        ...FORTY,
        scored: 12,
        rate: { value: 0.75, low: 0.6, high: 0.86 }
      }
    },
    {
      method: 'three_point',
      record: {
        ...FORTY,
        scored: 28,
        rate: { value: 0.32, low: 0.23, high: 0.43 }
      }
    }
  ],
  coverage: {
    completedItems: 96,
    withActual: 51,
    withEstimate: 74,
    scoredItems: 40,
    movedByTheStartDay: 4
  },
  firstScored: '2026-02-03T09:00:00Z',
  lastScored: '2026-08-15T02:00:00Z'
};

/**
 * How often the ranges written here contained the truth.
 *
 * Three of these cases are the work rather than the page. **The empty state**, because
 * that is what most organisations see for months and a screen that says only "no data"
 * guarantees it stays true. **The rate never appearing without its count, its interval and
 * the band-width reading beside it**, because each of those is what stops the number being
 * misread — four out of five is 80% and a hit rate on its own is won by estimating one to a
 * thousand hours. And **name order rather than rank order**, because a leaderboard is what
 * makes the gaming worth doing.
 */
describe('CalibrationPage', () => {
  const fetchMock = mockFetch();

  async function open(record: Calibration) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(200, record)
      )
    );
    renderRouted(<CalibrationPage />, { route: '/app/calibration' });
    // The coverage section is the one part of this page that is always there, record or no
    // record — waiting on the title would wait for nothing, since it is on screen while the
    // request is still in flight.
    await screen.findByRole('heading', { name: 'What is not being scored' });
  }

  // The state this page spends its first year in ------------------------------

  /**
   * The counts are the answer, not an apology for not having one: each line is a different
   * thing to go and do.
   */
  it('says what is missing rather than that there is no data', async () => {
    await open(EMPTY);

    expect(
      await screen.findByText(
        'Nothing has been finished here yet. A track record starts with work that is done, estimated, and measured.'
      )
    ).toBeInTheDocument();
    expect(
      // The lede says the same words, so this asks for the headline's own shape.
      screen.queryByText(/^\d+% contained/)
    ).toBeNull();
  });

  /**
   * The two reasons nothing is scored are different problems — one is a box nobody filled
   * in, the other is work nobody predicted — so they are two lines with two counts.
   */
  it('counts each half of the missing evidence separately', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        '45 finished tasks did not record how long they took. That is the number that has to change: without it there is nothing to compare a range against.'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        '22 finished tasks were never estimated, so there was no prediction to be right or wrong about.'
      )
    ).toBeInTheDocument();
    expect(
      screen.getByText('40 finished tasks are being scored.')
    ).toBeInTheDocument();
  });

  /** Decision 1's price, published rather than absorbed into a kinder number. */
  it('says what refusing to guess at a time of day cost', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        /4 finished tasks would have counted as predictions if an estimate written on the day the work began counted as one/
      )
    ).toBeInTheDocument();
  });

  /**
   * Moments, so they are shown where the reader is sitting — and this suite runs in
   * New York, where two in the morning UTC on the fifteenth is the evening of the
   * fourteenth.
   */
  it('shows the span in the reader’s own days', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        'Covering estimates written between Feb 3, 2026 and Aug 14, 2026.'
      )
    ).toBeInTheDocument();
  });

  // The number, and everything that stops it being misread ---------------------

  it('leads on the forecasts and states what a well-judged set scores', async () => {
    await open(RECORDED);

    expect(
      screen.getByText('45% contained what the work actually took')
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        /A well-judged set of ranges contains the outcome about 8 times in 10/
      )
    ).toBeInTheDocument();
  });

  /** On the same line as the rate, because a reader who has moved on has moved on. */
  it('never shows the rate without its count and its interval', async () => {
    await open(RECORDED);

    expect(
      screen.getByText('18 of 40 · likely between 35% and 55%')
    ).toBeInTheDocument();
  });

  /**
   * All above is optimism and both ways is a range too tight, and they are different things
   * to do something about.
   */
  it('says which way the misses went', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        '19 ran past the top of the range, 3 came in under the bottom.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>The pairing that makes the rate safe to publish.</strong> Widening every range
   * until it contains everything scores 100% and predicts nothing; this is the number that
   * reports it.
   */
  it('always shows how wide the ranges should have been', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        'These ranges would have had to be 2.4 times as wide to have contained the outcome as often as they claimed. Well-judged ranges read 1.0.'
      )
    ).toBeInTheDocument();
  });

  /** A rate with one outcome behind it has no spread, and is not left standing alone. */
  it('says so when there is too little to correct anything', async () => {
    await open({
      ...EMPTY,
      forecasts: {
        ...NOTHING,
        scored: 1,
        hits: 1,
        rate: { value: 1, low: 0.38, high: 1 }
      }
    });

    expect(
      screen.getByText(
        'Not enough finished work yet to say whether these ranges sit high or low, or how wide they should have been.'
      )
    ).toBeInTheDocument();
  });

  /**
   * <strong>Nothing on this page names a percentile.</strong> Where the truth typically
   * lands inside somebody's own range is shown as a position between the two ends they were
   * actually asked for, which is the same refusal the estimate form makes.
   */
  it('never names a percentile', async () => {
    await open(RECORDED);

    expect(screen.queryByText(/percentile/i)).toBeNull();
    expect(screen.queryByText(/P10|P50|P90/)).toBeNull();
    expect(screen.getByText('Good case')).toBeInTheDocument();
    expect(screen.getByText('Bad case')).toBeInTheDocument();
  });

  it('counts the claims of certainty that the corrections cannot use', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        '2 estimates claimed certainty and are counted in the rate above but not here.'
      )
    ).toBeInTheDocument();
  });

  // The three buckets and the two breakdowns ----------------------------------

  it('keeps the three buckets apart and says they do not add up', async () => {
    await open(RECORDED);

    expect(
      screen.getByText(
        'These are three separate answers about three sets of estimates. They do not add up to anything.'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('Before the work began')).toBeInTheDocument();
    // Short, because the section has already said what the number means — and the interval
    // is on the row underneath it, because decision 7 puts it everywhere the rate goes.
    expect(screen.getByText('35–55% · 40 estimates')).toBeInTheDocument();
    expect(screen.getByText('After the work began')).toBeInTheDocument();
    expect(
      screen.getByText('Work with no start date reported')
    ).toBeInTheDocument();
  });

  /** The split V15 exists for, and the only evidence elicitation's claim will ever have. */
  it('splits the record by how the range was asked for', async () => {
    await open(RECORDED);

    expect(
      screen.getByText('One question at a time, bad case first')
    ).toBeInTheDocument();
    expect(
      screen.getByText('Three boxes, filled in together')
    ).toBeInTheDocument();
  });

  /**
   * The server is what versions ahead here, so a method this page has never heard of gets
   * its own row saying so rather than being dropped or having the server's string printed.
   */
  it('gives a method it has never heard of a row of its own', async () => {
    await open({
      ...RECORDED,
      byMethod: [{ method: 'reference_class', record: FORTY }]
    });

    expect(
      screen.getByText('Asked in a way this page does not recognise')
    ).toBeInTheDocument();
  });

  /**
   * Six outcomes and ninety produce rates that look alike and mean nothing alike, so the
   * interval goes on every row and not only on the headline — decision 7 read literally.
   */
  it('puts the interval and the count on every row, not only on the headline', async () => {
    await open(RECORDED);

    expect(screen.getByText('26–48% · 22 estimates')).toBeInTheDocument();
    expect(screen.getByText('42–69% · 18 estimates')).toBeInTheDocument();
    expect(screen.getByText('60–86% · 12 estimates')).toBeInTheDocument();
  });

  /**
   * <strong>Named, never ranked.</strong> Zara scores better than Ada and still comes
   * second, because a hit-rate leaderboard is won by writing one to a thousand.
   */
  it('lists people in name order and not in order of merit', async () => {
    await open(RECORDED);

    const people = screen.getAllByRole('listitem');
    const named = people.filter((row) =>
      /Ada|Zara/.test(row.textContent ?? '')
    );
    expect(named[0]).toHaveTextContent('Ada');
    expect(named[1]).toHaveTextContent('Zara');
    expect(
      screen.getByText(/In name order, not in any order of merit/)
    ).toBeInTheDocument();
  });

  /** Nothing arriving after somebody has navigated away may touch a page that has gone. */
  it('ignores a record that arrives after the page has gone', async () => {
    storeAccessToken();
    let settle: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => {
            settle = resolve;
          })
    );
    const opened = renderRouted(<CalibrationPage />, {
      route: '/app/calibration'
    });
    await screen.findByRole('heading', { name: 'Track record' });
    opened.unmount();
    settle(jsonResponse(200, RECORDED));

    await waitFor(() =>
      expect(screen.queryByText(/^\d+% contained/)).toBeNull()
    );
  });

  /** And a failure arriving then may not either — a banner needs a page to sit on. */
  it('ignores a failure that arrives after the page has gone', async () => {
    storeAccessToken();
    let fail: (reason: unknown) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((_resolve, reject) => {
            fail = reject;
          })
    );
    const opened = renderRouted(<CalibrationPage />, {
      route: '/app/calibration'
    });
    await screen.findByRole('heading', { name: 'Track record' });
    opened.unmount();
    fail(new TypeError('Failed to fetch'));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  it('says so when the record cannot be loaded', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(500, null)
      )
    );
    renderRouted(<CalibrationPage />, { route: '/app/calibration' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Something went wrong. Please try again.'
    );
  });
});
