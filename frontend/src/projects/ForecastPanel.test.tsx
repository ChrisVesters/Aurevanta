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

  it('says there is nothing yet before anybody has asked', async () => {
    await open();

    expect(
      await screen.findByText(
        'No forecast yet. Say how many things can be worked on at once and ask for one.'
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
   * The assumption that moves the answer most, said beside the answer — and the coverage
   * as it was when the run happened, which is the disclosure this whole product turns on.
   */
  it('states what it assumed and how much of the plan it covered', async () => {
    await open([FORECAST]);

    expect(
      await screen.findByText(
        'Assuming 2 things under way at once, over 10000 simulated runs.'
      )
    ).toBeInTheDocument();
    expect(screen.getByText('2 of 2 items estimated')).toBeInTheDocument();
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
      caveats.getByText(/treats every task as independent/)
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

    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      '2'
    );
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
      sampleCount: null
    });
  });

  /** Not pre-filled, so an empty box is what the server is asked to judge. */
  it('starts with nothing in the capacity box', async () => {
    await open([FORECAST]);

    expect(
      screen.getByLabelText('Things that can be under way at once')
    ).toHaveValue(null);
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

  it('lists the forecasts made before this one', async () => {
    const earlier: Forecast = {
      ...FORECAST,
      id: '60606060-6060-6060-6060-606060606060',
      capacity: 1,
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
        '44 h as likely as not, 71.5 h at the cautious end — 1 at a time, asked for by Bob.'
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

    await userEvent.type(
      screen.getByLabelText('Things that can be under way at once'),
      '3'
    );
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
        sampleCount: 2000
      });
    });
  });
});
