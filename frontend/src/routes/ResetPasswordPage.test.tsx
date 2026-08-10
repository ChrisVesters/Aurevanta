import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ResetPasswordPage } from './ResetPasswordPage';
import { jsonResponse, mockFetch, renderRouted } from '../test/render';

describe('ResetPasswordPage', () => {
  const fetchMock = mockFetch();

  async function chooseNewPassword(password = 'a-brand-new-passphrase') {
    await userEvent.type(screen.getByLabelText('Password'), password);
    await userEvent.click(
      screen.getByRole('button', { name: 'Save new password' })
    );
  }

  it('sends the token out of the link with the password that was typed', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    renderRouted(<ResetPasswordPage />, { route: '/reset-password?token=abc' });
    await chooseNewPassword();

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/auth/password-reset/confirm',
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      token: 'abc',
      password: 'a-brand-new-passphrase'
    });
  });

  it('says the password changed and points at signing in', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    renderRouted(<ResetPasswordPage />, { route: '/reset-password?token=abc' });
    await chooseNewPassword();

    expect(
      await screen.findByRole('heading', { name: 'Password changed' })
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login'
    );
  });

  /**
   * The token belongs to no field on this page, so its refusal has to reach the banner
   * rather than being attached to the password input — which never failed.
   */
  it('reports a spent link without blaming the password', async () => {
    fetchMock.mockResolvedValue(jsonResponse(400, { code: 'invalid_token' }));

    renderRouted(<ResetPasswordPage />, {
      route: '/reset-password?token=stale'
    });
    await chooseNewPassword();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /expired or has already been used/i
    );
    expect(screen.getByLabelText('Password')).not.toHaveAttribute(
      'aria-invalid'
    );
  });

  /** A refused link is exactly when another one is needed, so it is never a dead end. */
  it('offers a new link from the form itself', () => {
    renderRouted(<ResetPasswordPage />, {
      route: '/reset-password?token=stale'
    });

    expect(
      screen.getByRole('link', { name: 'Ask for a new one' })
    ).toHaveAttribute('href', '/forgot-password');
  });

  it('places a complaint about the password against its input', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { password: { code: 'size', min: 12, max: 72 } }
      })
    );

    renderRouted(<ResetPasswordPage />, { route: '/reset-password?token=abc' });
    await chooseNewPassword('too-short');

    expect(
      await screen.findByText('Use between 12 and 72 characters.')
    ).toBeInTheDocument();
    // Against the field, so the banner would only repeat it.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('explains a network failure in plain terms', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    renderRouted(<ResetPasswordPage />, { route: '/reset-password?token=abc' });
    await chooseNewPassword();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i
    );
  });

  /**
   * Mail clients break long links across lines often enough that this is a real arrival,
   * not a hypothetical one — and asking for the password anyway would spend it against a
   * token that was never going to work.
   */
  it('asks for a new link rather than a password when the link carries no token', () => {
    renderRouted(<ResetPasswordPage />, { route: '/reset-password' });

    expect(
      screen.getByRole('heading', { name: 'That link is incomplete' })
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    expect(
      screen.getByRole('link', { name: 'Ask for a new link' })
    ).toHaveAttribute('href', '/forgot-password');
    expect(fetchMock).not.toHaveBeenCalled();
  });

  /** `?token=` with nothing after it is a link that lost its token, not an empty token. */
  it('treats an empty token the same as none at all', () => {
    renderRouted(<ResetPasswordPage />, { route: '/reset-password?token=' });

    expect(
      screen.getByRole('heading', { name: 'That link is incomplete' })
    ).toBeInTheDocument();
  });
});
