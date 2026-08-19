import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResourcesPage } from './ResourcesPage';
import {
  ACCOUNT,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Resource } from '../resource/types';

const BACKEND: Resource = {
  id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1',
  name: 'Backend engineers',
  units: 3,
  personId: null,
  personName: null,
  createdAt: '2026-08-01T09:00:00Z',
  archivedAt: null
};

const ADA: Resource = {
  id: 'a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1',
  name: 'Ada',
  units: 1,
  personId: ACCOUNT.userId,
  personName: 'Ada Lovelace',
  createdAt: '2026-08-02T09:00:00Z',
  archivedAt: null
};

const MEMBERS = [
  {
    id: 'm1',
    userId: ACCOUNT.userId,
    displayName: 'Ada Lovelace',
    email: 'ada@acme.test',
    role: 'OWNER',
    joinedAt: '2026-07-01T09:00:00Z'
  }
];

/**
 * What this organisation has to work with.
 *
 * <strong>The case worth reading first is {@link #aPersonIsAPoolOfOne}</strong> — or rather
 * the absence of anything else about people. A pool may name somebody and that is a label;
 * nothing on this screen says what anybody is working on, and the moment it ranked people by
 * how busy they are this would be a different product with different ethics.
 */
describe('ResourcesPage', () => {
  const fetchMock = mockFetch();

  /** Answered by URL, because the pools and the colleagues are two different lists. */
  function answer(pools: Resource[], members: unknown = MEMBERS) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === '/api/members'
            ? jsonResponse(200, members)
            : url.startsWith('/api/resources?archived=true')
              ? jsonResponse(
                  200,
                  pools.filter((pool) => pool.archivedAt)
                )
              : url.startsWith('/api/resources')
                ? jsonResponse(
                    200,
                    pools.filter((pool) => !pool.archivedAt)
                  )
                : jsonResponse(404)
      )
    );
  }

  /**
   * Waits for the listing itself and not only for the heading.
   *
   * The heading renders before the pools arrive, so a case that reached for a row's own
   * button straight afterwards raced the request — and passed on its own and failed in a
   * full run, which is the worst way for a test to be wrong.
   */
  async function open(pools: Resource[] = [BACKEND]) {
    storeAccessToken();
    answer(pools);
    renderRouted(<ResourcesPage />);
    await screen.findByRole('heading', { name: 'Resources' });
    await waitFor(() => expect(screen.queryByRole('status')).toBeNull());
  }

  it('lists what the organisation has, with how many of each', async () => {
    await open([BACKEND, ADA]);

    expect(
      await screen.findByText('Backend engineers — 3 units')
    ).toBeInTheDocument();
    // One is a unit, not "1 units": a pool of one is the ordinary way to say a person.
    expect(screen.getByText('Ada — 1 unit')).toBeInTheDocument();
    expect(screen.getByText('This is Ada Lovelace.')).toBeInTheDocument();
  });

  /**
   * The empty state is the screen every organisation sees until it describes a team, and it
   * says what happens meanwhile rather than only that there is nothing here.
   */
  it('says what a forecast does while there is nothing here', async () => {
    await open([]);

    expect(
      await screen.findByText(
        /Until there are, a forecast asks how much can be under way at once/
      )
    ).toBeInTheDocument();
  });

  it('declares a pool and asks the server what the list looks like now', async () => {
    await open([]);
    await userEvent.type(
      screen.getByLabelText('What is it called?'),
      'Designers'
    );
    await userEvent.type(screen.getByLabelText('How many are there?'), '2');

    await userEvent.click(screen.getByRole('button', { name: 'Add it' }));

    const sent = fetchMock.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === 'POST'
    );
    expect(JSON.parse(String((sent?.[1] as RequestInit).body))).toEqual({
      name: 'Designers',
      units: 2,
      personId: null
    });
  });

  /**
   * <strong>An untouched box is nothing, never zero.</strong> `Number('')` is zero, so the
   * obvious version of this form declares a pool of nobody and is refused for a number
   * nobody typed.
   */
  it('sends no units at all when the box is untouched', async () => {
    await open([]);
    await userEvent.type(
      screen.getByLabelText('What is it called?'),
      'Anybody'
    );

    await userEvent.click(screen.getByRole('button', { name: 'Add it' }));

    const sent = fetchMock.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === 'POST'
    );
    expect(
      JSON.parse(String((sent?.[1] as RequestInit).body)).units
    ).toBeNull();
  });

  /** A pool may only name a colleague, so the list is offered rather than typed into. */
  it('offers colleagues rather than asking for an identifier', async () => {
    await open([]);

    expect(
      screen.getByRole('option', { name: 'Ada Lovelace' })
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', { name: 'Not a particular person' })
    ).toBeInTheDocument();
  });

  /**
   * A team is still a team if the colleagues could not be listed: what goes missing is the
   * ability to name one of them, and not the page.
   */
  it('survives not being able to list colleagues', async () => {
    storeAccessToken();
    answer([BACKEND], null);
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === '/api/members'
            ? jsonResponse(500, null)
            : jsonResponse(200, [BACKEND])
      )
    );
    renderRouted(<ResourcesPage />);

    expect(
      await screen.findByText('Backend engineers — 3 units')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('option', { name: 'Not a particular person' })
    ).toBeInTheDocument();
  });

  /** Put away rather than deleted, like every other domain row. */
  it('puts a pool away without losing it', async () => {
    await open([BACKEND]);

    await userEvent.click(screen.getByRole('button', { name: 'Put away' }));

    expect(
      fetchMock.mock.calls.some((call) =>
        String(call[0]).endsWith(`/resources/${BACKEND.id}/archive`)
      )
    ).toBe(true);
  });

  it('shows what has been put away when asked', async () => {
    await open([{ ...BACKEND, archivedAt: '2026-08-10T09:00:00Z' }]);

    await userEvent.click(
      screen.getByRole('button', { name: 'Show what has been put away' })
    );

    expect(
      await screen.findByText('Backend engineers — 3 units')
    ).toBeInTheDocument();
    expect(
      screen.getByRole('button', { name: 'Bring back' })
    ).toBeInTheDocument();
  });

  it('changes a pool in place', async () => {
    await open([BACKEND]);
    await userEvent.click(screen.getByRole('button', { name: 'Change' }));
    const units = screen.getByLabelText('How many are there?', {
      selector: `#resource-${BACKEND.id}-units`
    });
    await userEvent.clear(units);
    await userEvent.type(units, '4');

    await userEvent.click(screen.getAllByRole('button', { name: 'Change' })[1]);

    const sent = fetchMock.mock.calls.find(
      (call) => (call[1] as RequestInit | undefined)?.method === 'PATCH'
    );
    expect(JSON.parse(String((sent?.[1] as RequestInit).body)).units).toBe(4);
  });

  /**
   * A listing that could not be read says so rather than showing an empty team, which would
   * read as an organisation that has described nothing — the state that changes what the
   * forecast form asks.
   */
  it('reports a listing it could not load', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === '/api/members'
            ? jsonResponse(200, MEMBERS)
            : jsonResponse(500, null)
      )
    );
    renderRouted(<ResourcesPage />);

    expect(
      await screen.findByText('Something went wrong. Please try again.')
    ).toBeInTheDocument();
  });

  /** And so does one that could not be put away, rather than the row simply not moving. */
  it('reports a pool it could not put away', async () => {
    await open([BACKEND]);
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url.endsWith('/archive')
            ? jsonResponse(404, { code: 'resource_not_found' })
            : jsonResponse(200, [BACKEND])
      )
    );

    await userEvent.click(screen.getByRole('button', { name: 'Put away' }));

    expect(
      await screen.findByText(
        'That resource is no longer in this organisation.'
      )
    ).toBeInTheDocument();
  });

  /** The change form is a toggle, so the same button closes what it opened. */
  it('closes the change form when it is asked to again', async () => {
    await open([BACKEND]);
    await userEvent.click(screen.getByRole('button', { name: 'Change' }));
    expect(screen.getAllByLabelText('What is it called?')).toHaveLength(2);

    await userEvent.click(screen.getAllByRole('button', { name: 'Change' })[0]);

    expect(screen.getAllByLabelText('What is it called?')).toHaveLength(1);
  });

  /** A refused change is said in the banner of the form it was refused from. */
  it('says why a change was refused', async () => {
    await open([BACKEND]);
    await userEvent.click(screen.getByRole('button', { name: 'Change' }));
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : init?.method === 'PATCH'
            ? jsonResponse(400, { code: 'person_not_a_member' })
            : jsonResponse(200, [BACKEND])
      )
    );

    await userEvent.click(screen.getAllByRole('button', { name: 'Change' })[1]);

    expect(
      await screen.findByText(
        'That person is not in this organisation, so a resource cannot be named after them.'
      )
    ).toBeInTheDocument();
  });

  it('brings a pool back', async () => {
    await open([{ ...BACKEND, archivedAt: '2026-08-10T09:00:00Z' }]);
    await userEvent.click(
      screen.getByRole('button', { name: 'Show what has been put away' })
    );
    await screen.findByText('Backend engineers — 3 units');

    await userEvent.click(screen.getByRole('button', { name: 'Bring back' }));

    expect(
      fetchMock.mock.calls.some((call) =>
        String(call[0]).endsWith(`/resources/${BACKEND.id}/unarchive`)
      )
    ).toBe(true);
  });

  /**
   * Nothing arriving after somebody has navigated away may touch a page that has gone —
   * the guard every read in this product keeps, and there are two of them here because a
   * team and the colleagues who might be in it are two lists.
   */
  it('drops answers that arrive after the page has been left', async () => {
    storeAccessToken();
    const pending: ((value: Response) => void)[] = [];
    const failing: ((reason: unknown) => void)[] = [];
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve, reject) => {
            pending.push(resolve);
            failing.push(reject);
          })
    );
    const { unmount } = renderRouted(<ResourcesPage />);
    await screen.findByRole('status');
    await waitFor(() => expect(pending.length).toBeGreaterThanOrEqual(2));

    unmount();
    pending.forEach((resolve) => resolve(jsonResponse(200, [BACKEND])));

    // And the same again, refused rather than answered: both halves of both reads.
    const already = failing.length;
    const second = renderRouted(<ResourcesPage />);
    await screen.findByRole('status');
    await waitFor(() => expect(failing.length).toBeGreaterThan(already + 1));
    second.unmount();
    failing
      .slice(already)
      .forEach((reject) => reject(new TypeError('Failed to fetch')));

    await waitFor(() =>
      expect(screen.queryByText('Backend engineers — 3 units')).toBeNull()
    );
  });

  it('says why a pool was refused, against the field it was refused for', async () => {
    await open([]);
    fetchMock.mockImplementation((url: string, init?: RequestInit) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : url === '/api/members'
            ? jsonResponse(200, MEMBERS)
            : init?.method === 'POST'
              ? jsonResponse(400, {
                  code: 'validation_failed',
                  errors: { units: { code: 'positive' } }
                })
              : jsonResponse(200, [])
      )
    );
    await userEvent.type(screen.getByLabelText('What is it called?'), 'Nobody');
    await userEvent.type(screen.getByLabelText('How many are there?'), '0');

    await userEvent.click(screen.getByRole('button', { name: 'Add it' }));

    expect(
      await screen.findByText('Use a number greater than zero.')
    ).toBeInTheDocument();
  });
});
