import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from './AuthContext';
import { readStoredSession } from './session';
import {
  ACCOUNT,
  ACME_MEMBERSHIP,
  AUTHENTICATION,
  UNVERIFIED_ACCOUNT,
  CHOOSE_ORGANISATION,
  NO_ORGANISATION,
  SIGNED_IN,
  UMBRELLA_MEMBERSHIP,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken,
  storeIdentityToken
} from '../test/render';

function Probe() {
  const {
    status,
    account,
    memberships,
    register,
    login,
    selectOrganisation,
    logout
  } = useAuth();
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="account">{account?.email ?? 'none'}</p>
      <p data-testid="memberships">
        {memberships.map((m) => m.organisation.slug).join(',') || 'none'}
      </p>
      <button
        type="button"
        onClick={() =>
          void register({
            organisationName: 'Acme',
            organisationSlug: 'acme',
            displayName: 'Ada',
            email: 'ada@acme.test',
            password: 'a-long-enough-passphrase'
          }).catch(() => {})
        }
      >
        register
      </button>
      <button
        type="button"
        onClick={() =>
          void login({
            email: 'ada@acme.test',
            password: 'a-long-enough-passphrase'
          }).catch(() => {})
        }
      >
        login
      </button>
      <button
        type="button"
        onClick={() =>
          void selectOrganisation(UMBRELLA_MEMBERSHIP.organisation.id).catch(
            () => {}
          )
        }
      >
        select
      </button>
      <button type="button" onClick={logout}>
        logout
      </button>
    </div>
  );
}

describe('AuthProvider', () => {
  const fetchMock = mockFetch();

  it('starts anonymous when no token was stored', async () => {
    renderRouted(<Probe />);

    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('restores a session from a stored access token', async () => {
    storeAccessToken();
    fetchMock.mockResolvedValue(jsonResponse(200, ACCOUNT));

    renderRouted(<Probe />);

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );
    expect(screen.getByTestId('account')).toHaveTextContent('ada@acme.test');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/auth/me',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer a.test.token'
        })
      })
    );
  });

  // An identity token names no organisation, so /auth/me would have nothing to answer
  // with; the membership list is what proves the token still works.
  it('restores an unfinished choice from a stored identity token', async () => {
    storeIdentityToken();
    fetchMock.mockResolvedValue(
      jsonResponse(200, [ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP])
    );

    renderRouted(<Probe />);

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('choosing')
    );
    expect(screen.getByTestId('memberships')).toHaveTextContent(
      'acme-planning-co,umbrella'
    );
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/memberships',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer an.identity.token'
        })
      })
    );
  });

  it('restores someone who belongs to nothing as unaffiliated', async () => {
    storeIdentityToken();
    fetchMock.mockResolvedValue(jsonResponse(200, []));

    renderRouted(<Probe />);

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('unaffiliated')
    );
    expect(screen.getByTestId('memberships')).toHaveTextContent('none');
  });

  // An expired token, or one whose membership is gone, must not leave a stale session.
  it('discards a stored access token the server rejects', async () => {
    storeAccessToken('stale.test.token');
    fetchMock.mockResolvedValue(jsonResponse(401));

    renderRouted(<Probe />);

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
    );
    expect(readStoredSession()).toBeNull();
  });

  it('discards a stored identity token the server rejects', async () => {
    storeIdentityToken('stale.identity.token');
    fetchMock.mockResolvedValue(jsonResponse(401));

    renderRouted(<Probe />);

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
    );
    expect(readStoredSession()).toBeNull();
  });

  /**
   * The hard gate: an unconfirmed address is refused a token, so registering cannot hand
   * one out either. Anyone signed in here would be holding a session the server would
   * never have issued.
   */
  it('creates the account without signing anybody in', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, UNVERIFIED_ACCOUNT));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'register' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('account')).toHaveTextContent('none');
    expect(readStoredSession()).toBeNull();
  });

  it('signs straight in when the account holds one membership', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, SIGNED_IN));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );
    expect(readStoredSession()).toEqual({
      token: 'a.test.token',
      kind: 'access'
    });
  });

  it('holds the choice open when the account holds several memberships', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, CHOOSE_ORGANISATION));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('choosing')
    );
    expect(screen.getByTestId('memberships')).toHaveTextContent(
      'acme-planning-co,umbrella'
    );
    expect(screen.getByTestId('account')).toHaveTextContent('none');
    // An identity token, deliberately: it reaches nothing tenant-scoped.
    expect(readStoredSession()).toEqual({
      token: 'an.identity.token',
      kind: 'identity'
    });
  });

  it('reports someone who belongs to nothing as unaffiliated', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, NO_ORGANISATION));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('unaffiliated')
    );
    expect(screen.getByTestId('memberships')).toHaveTextContent('none');
  });

  it('exchanges the identity token for the chosen organisation', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, CHOOSE_ORGANISATION));
    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('choosing')
    );

    fetchMock.mockResolvedValueOnce(jsonResponse(200, AUTHENTICATION));
    await userEvent.click(screen.getByRole('button', { name: 'select' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );
    expect(fetchMock).toHaveBeenLastCalledWith(
      `/api/auth/tenants/${UMBRELLA_MEMBERSHIP.organisation.id}/token`,
      expect.objectContaining({
        method: 'POST',
        headers: expect.objectContaining({
          Authorization: 'Bearer an.identity.token'
        })
      })
    );
    expect(readStoredSession()?.kind).toBe('access');
  });

  // A refused exchange must leave the choice open rather than stranding the visitor.
  it('keeps the choice open when the exchange is refused', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, CHOOSE_ORGANISATION));
    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('choosing')
    );

    fetchMock.mockResolvedValueOnce(
      jsonResponse(403, { code: 'not_a_member' })
    );
    await userEvent.click(screen.getByRole('button', { name: 'select' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId('status')).toHaveTextContent('choosing');
    expect(readStoredSession()?.kind).toBe('identity');
  });

  it('stays anonymous when credentials are refused', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(401, { code: 'invalid_credentials' })
    );

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(readStoredSession()).toBeNull();
  });

  it('discards the token on sign out', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, SIGNED_IN));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );

    await userEvent.click(screen.getByRole('button', { name: 'logout' }));

    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('account')).toHaveTextContent('none');
    expect(readStoredSession()).toBeNull();
  });

  it('drops the pending choice on sign out', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, CHOOSE_ORGANISATION));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('choosing')
    );

    await userEvent.click(screen.getByRole('button', { name: 'logout' }));

    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('memberships')).toHaveTextContent('none');
  });

  // Leaving the page mid-restore must not write to an unmounted component.
  it('abandons a session restore when it is unmounted first', async () => {
    storeAccessToken();
    let settle: (response: Response) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise<Response>((resolve) => {
        settle = resolve;
      })
    );
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { unmount } = renderRouted(<Probe />);
    unmount();
    settle(jsonResponse(200, ACCOUNT));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    expect(errors).not.toHaveBeenCalled();
  });

  it('abandons a membership restore when it is unmounted first', async () => {
    storeIdentityToken();
    let settle: (response: Response) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise<Response>((resolve) => {
        settle = resolve;
      })
    );
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { unmount } = renderRouted(<Probe />);
    unmount();
    settle(jsonResponse(200, [ACME_MEMBERSHIP]));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    expect(errors).not.toHaveBeenCalled();
  });

  it('abandons a failed session restore when it is unmounted first', async () => {
    storeAccessToken();
    let fail: (reason: Error) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise<Response>((_resolve, reject) => {
        fail = reject;
      })
    );
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { unmount } = renderRouted(<Probe />);
    unmount();
    fail(new TypeError('Failed to fetch'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    // The token survives, because this failure says nothing about its validity.
    expect(errors).not.toHaveBeenCalled();
  });

  it('refuses to be used outside a provider', () => {
    // React logs the thrown error; silence it so the run stays readable.
    vi.spyOn(console, 'error').mockImplementation(() => {});

    expect(() => renderRouted(<Probe />, { wrapInProvider: false })).toThrow(
      'useAuth must be used inside an AuthProvider'
    );
  });
});
