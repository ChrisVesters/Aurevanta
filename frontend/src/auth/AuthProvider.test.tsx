import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useAuth } from './AuthContext';
import { storeToken, readStoredToken } from './session';
import {
  ACCOUNT,
  AUTHENTICATION,
  jsonResponse,
  mockFetch,
  renderRouted
} from '../test/render';

function Probe() {
  const { status, account, register, login, logout } = useAuth();
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="account">{account?.email ?? 'none'}</p>
      <button
        type="button"
        onClick={() =>
          void register({
            organisationName: 'Acme',
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

  it('restores a session from a stored token', async () => {
    storeToken('a.test.token');
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

  // An expired token, or one whose account is gone, must not leave a stale session.
  it('discards a stored token the server rejects', async () => {
    storeToken('stale.test.token');
    fetchMock.mockResolvedValue(jsonResponse(401));

    renderRouted(<Probe />);

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('anonymous')
    );
    expect(readStoredToken()).toBeNull();
  });

  it('signs in and stores the token on registration', async () => {
    fetchMock.mockResolvedValue(jsonResponse(201, AUTHENTICATION));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'register' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );
    expect(readStoredToken()).toBe('a.test.token');
  });

  it('signs in and stores the token on login', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, AUTHENTICATION));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );
    expect(readStoredToken()).toBe('a.test.token');
  });

  it('stays anonymous when credentials are refused', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(401, { code: 'invalid_credentials' })
    );

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(readStoredToken()).toBeNull();
  });

  it('discards the token on sign out', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, AUTHENTICATION));

    renderRouted(<Probe />);
    await userEvent.click(screen.getByRole('button', { name: 'login' }));
    await waitFor(() =>
      expect(screen.getByTestId('status')).toHaveTextContent('authenticated')
    );

    await userEvent.click(screen.getByRole('button', { name: 'logout' }));

    expect(screen.getByTestId('status')).toHaveTextContent('anonymous');
    expect(screen.getByTestId('account')).toHaveTextContent('none');
    expect(readStoredToken()).toBeNull();
  });

  // Leaving the page mid-restore must not write to an unmounted component.
  it('abandons a session restore when it is unmounted first', async () => {
    storeToken('a.test.token');
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

  it('abandons a failed session restore when it is unmounted first', async () => {
    storeToken('a.test.token');
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
