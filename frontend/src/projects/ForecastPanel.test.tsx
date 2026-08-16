import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForecastPanel } from './ForecastPanel';
import {
  ACCOUNT,
  FORECAST,
  PROJECTS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Forecast } from './types';

const PROJECT_ID = PROJECTS[0].id;
const FORECASTS_URL = `/api/projects/${PROJECT_ID}/forecasts`;

describe('ForecastPanel', () => {
  const fetchMock = mockFetch();

  /**
   * The session and the plan's forecasts, answered separately. A double that answered both
   * alike would hand the panel an account where it expected a list, and every assertion
   * about what is on screen would be testing the wrong thing.
   */
  function answer(runs: Forecast[]) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === FORECASTS_URL
            ? jsonResponse(200, runs)
            : jsonResponse(404)
      )
    );
  }

  async function open(runs: Forecast[] = []) {
    storeAccessToken();
    answer(runs);
    renderRouted(<ForecastPanel projectId={PROJECT_ID} />);
    await screen.findByRole('heading', { name: 'Forecast' });
  }

  /** Whatever the next request for a forecast is refused with. */
  function refuse(problem: Record<string, unknown>) {
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
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
    growthHigh: string
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
  }

  it('says there is nothing yet before anybody has asked', async () => {
    await open();

    expect(
      await screen.findByText(
        'No forecast yet. Answer the four questions below and ask for one — none of them has an answer this application can give for you.'
      )
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
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : init?.method === 'POST'
            ? jsonResponse(201, FORECAST)
            : jsonResponse(200, [FORECAST])
      )
    );

    await answerTheAssumptions('2', '30', '20', '60');
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
      sampleCount: null
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
        url === '/api/auth/me'
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
        url === '/api/auth/me'
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
        url === '/api/auth/me'
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
      url === '/api/auth/me'
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
      requestedByName: 'Bob'
    };
    await open([FORECAST, earlier]);

    const history = within(
      screen.getByRole('heading', { name: 'Earlier forecasts' })
        .parentElement as HTMLElement
    );
    expect(
      history.getByText(
        '44 h as likely as not, 71.5 h at the cautious end — 1 at a time, up to 0% longer in a bad stretch, 0–0% more work, asked for by Bob.'
      )
    ).toBeInTheDocument();
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
        url === '/api/auth/me'
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
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : init?.method === 'POST'
            ? jsonResponse(201, FORECAST)
            : jsonResponse(200, [FORECAST])
      )
    );

    await answerTheAssumptions('3', '0', '0', '0');
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
        sampleCount: 2000
      });
    });
  });
});
