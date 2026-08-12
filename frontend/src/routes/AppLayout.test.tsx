import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { AppLayout } from './AppLayout';
import { SettingsPage } from './SettingsPage';
import { readStoredSession } from '../auth/session';
import {
  ACCOUNT,
  ACME_MEMBERSHIP,
  AUTHENTICATION,
  UMBRELLA_MEMBERSHIP,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';

describe('AppLayout', () => {
  const fetchMock = mockFetch();

  /**
   * Answers by URL: restoring the session and loading the switcher's options both fire on
   * mount, and which lands first is React's business rather than ours.
   */
  async function signedIn(memberships: unknown[], account = ACCOUNT) {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, account)
          : jsonResponse(200, memberships)
      )
    );
    renderRouted(
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/app" element={<p>Overview page</p>} />
        </Route>
      </Routes>,
      { route: '/app' }
    );
    await screen.findByText('Overview page');
  }

  it('names the organisation the session is scoped to', async () => {
    await signedIn([ACME_MEMBERSHIP]);

    expect(screen.getByText('Acme Planning Co')).toBeInTheDocument();
    expect(screen.getByText('Owner')).toBeInTheDocument();
    expect(screen.getByText('Ada')).toBeInTheDocument();
  });

  it('describes a non-owner as a member', async () => {
    await signedIn([ACME_MEMBERSHIP], { ...ACCOUNT, role: 'MEMBER' });

    expect(screen.getByText('Member')).toBeInTheDocument();
  });

  it('offers a way between the pages inside the app', async () => {
    await signedIn([ACME_MEMBERSHIP]);

    expect(screen.getByRole('link', { name: 'Members' })).toHaveAttribute(
      'href',
      '/app/members'
    );
  });

  it('signs out', async () => {
    await signedIn([ACME_MEMBERSHIP]);

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(readStoredSession()).toBeNull();
  });

  /**
   * A control offering one choice reads as broken, and one choice is what everybody has
   * until an invitation has been accepted.
   */
  it('offers no switcher to somebody who belongs to one organisation', async () => {
    await signedIn([ACME_MEMBERSHIP]);

    expect(screen.queryByLabelText('Organisation')).not.toBeInTheDocument();
    expect(screen.getByText('Acme Planning Co')).toBeInTheDocument();
  });

  it('switches the session to another organisation', async () => {
    await signedIn([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);
    const switcher = await screen.findByLabelText('Organisation');

    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, {
        ...AUTHENTICATION,
        account: { ...ACCOUNT, ...UMBRELLA_MEMBERSHIP }
      })
    );
    await userEvent.selectOptions(
      switcher,
      UMBRELLA_MEMBERSHIP.organisation.id
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/auth/tenants/${UMBRELLA_MEMBERSHIP.organisation.id}/token`,
        expect.objectContaining({ method: 'POST' })
      )
    );
    // The page below re-renders against the organisation just switched to.
    await waitFor(() =>
      expect(screen.getByLabelText('Organisation')).toHaveValue(
        UMBRELLA_MEMBERSHIP.organisation.id
      )
    );
  });

  /** An <option> has nowhere to put a second line, so the handle goes inline. */
  it('tells two organisations with one name apart by their handles', async () => {
    const otherAcme = {
      ...ACME_MEMBERSHIP,
      id: 'a-second-acme',
      organisation: {
        id: 'the-other-acme',
        name: 'Acme Planning Co',
        slug: 'acme-planning-co-2'
      }
    };
    await signedIn([ACME_MEMBERSHIP, otherAcme]);

    const switcher = await screen.findByLabelText('Organisation');
    expect(switcher).toHaveTextContent(
      'Acme Planning Co (acme-planning-co)Acme Planning Co (acme-planning-co-2)'
    );
  });

  /**
   * The switcher loads its own list, and a rename changes neither the id it is keyed on
   * nor the membership behind it — so without a reason to reload, the option goes on
   * offering the old name until a full reload or a switch. Since M1a a name is not
   * unique, which is what makes a stale one worse than merely out of date: the rename may
   * have been the thing telling two options apart.
   */
  it('reloads the switcher when the organisation is renamed', async () => {
    const renamed = { ...ACCOUNT.organisation, name: 'Acme Ltd' };
    let account = ACCOUNT;
    let held = [ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP];
    storeAccessToken();
    fetchMock.mockImplementation((url: string) => {
      if (url === '/api/auth/me') {
        return Promise.resolve(jsonResponse(200, account));
      }
      if (url === '/api/memberships') {
        return Promise.resolve(jsonResponse(200, held));
      }
      // The rename itself. What the server answers with is also what the session and the
      // switcher's list will say from here on.
      account = { ...ACCOUNT, organisation: renamed };
      held = [
        { ...ACME_MEMBERSHIP, organisation: renamed },
        UMBRELLA_MEMBERSHIP
      ];
      return Promise.resolve(jsonResponse(200, renamed));
    });
    renderRouted(
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/app/settings" element={<SettingsPage />} />
        </Route>
      </Routes>,
      { route: '/app/settings' }
    );
    await screen.findByRole('heading', { name: 'Organisation' });
    expect(await screen.findByLabelText('Organisation')).toHaveTextContent(
      'Acme Planning Co'
    );

    await userEvent.clear(screen.getByLabelText('Organisation name'));
    await userEvent.type(
      screen.getByLabelText('Organisation name'),
      'Acme Ltd'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(screen.getByLabelText('Organisation')).toHaveTextContent(
        'Acme Ltd'
      )
    );
    expect(screen.getByLabelText('Organisation')).not.toHaveTextContent(
      'Acme Planning Co'
    );
  });

  it('says nothing about a handle when every name is its own', async () => {
    await signedIn([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);

    const switcher = await screen.findByLabelText('Organisation');
    expect(switcher).toHaveTextContent('Acme Planning CoUmbrella');
    expect(switcher).not.toHaveTextContent('acme-planning-co');
  });

  it('says so when the exchange is refused', async () => {
    await signedIn([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);
    const switcher = await screen.findByLabelText('Organisation');

    fetchMock.mockResolvedValueOnce(
      jsonResponse(403, { code: 'not_a_member' })
    );
    await userEvent.selectOptions(
      switcher,
      UMBRELLA_MEMBERSHIP.organisation.id
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You do not belong to that organisation.'
    );
  });

  /** A switcher that cannot load its options must not take the page down with it. */
  it('falls back to the organisation’s name when the list cannot be loaded', async () => {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(500, null)
      )
    );
    renderRouted(
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/app" element={<p>Overview page</p>} />
        </Route>
      </Routes>,
      { route: '/app' }
    );

    expect(await screen.findByText('Overview page')).toBeInTheDocument();
    expect(screen.getByText('Acme Planning Co')).toBeInTheDocument();
  });

  /** The guard that keeps a list arriving late from touching a header that has gone. */
  it('drops a list that arrives after the header has been left', async () => {
    storeAccessToken();
    let deliver: (value: Response) => void = () => {};
    fetchMock.mockImplementation((url: string) =>
      url === '/api/auth/me'
        ? Promise.resolve(jsonResponse(200, ACCOUNT))
        : new Promise<Response>((resolve) => {
            deliver = resolve;
          })
    );
    const { unmount } = renderRouted(
      <Routes>
        <Route element={<AppLayout />}>
          <Route path="/app" element={<p>Overview page</p>} />
        </Route>
      </Routes>,
      { route: '/app' }
    );
    await screen.findByText('Overview page');

    unmount();
    deliver(jsonResponse(200, [ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]));

    await waitFor(() =>
      expect(screen.queryByLabelText('Organisation')).toBeNull()
    );
  });

  // Reached only if the guard above it is ever bypassed.
  it('renders nothing without an account', () => {
    const { container } = renderRouted(<AppLayout />, { route: '/app' });

    expect(container).toBeEmptyDOMElement();
  });
});
