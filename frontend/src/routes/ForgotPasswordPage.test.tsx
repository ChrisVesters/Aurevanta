import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ForgotPasswordPage } from './ForgotPasswordPage';
import { jsonResponse, mockFetch, renderRouted } from '../test/render';

describe('ForgotPasswordPage', () => {
  const fetchMock = mockFetch();

  async function askFor(email: string) {
    await userEvent.type(screen.getByLabelText('Email'), email);
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a reset link' })
    );
  }

  it('asks the server for a reset link', async () => {
    fetchMock.mockResolvedValue(jsonResponse(202));

    renderRouted(<ForgotPasswordPage />, { route: '/forgot-password' });
    await askFor('ada@acme.test');

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/auth/password-reset',
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      email: 'ada@acme.test'
    });
  });

  /**
   * The server answers identically whether or not the address has an account, and so must
   * this: a confirmation that varied would disclose who is registered.
   */
  it('acknowledges without saying whether the address has an account', async () => {
    fetchMock.mockResolvedValue(jsonResponse(202));

    renderRouted(<ForgotPasswordPage />, { route: '/forgot-password' });
    await askFor('nobody@acme.test');

    expect(await screen.findByRole('status')).toHaveTextContent(
      /if that address has an account/i
    );
    expect(
      screen.queryByRole('button', { name: 'Send a reset link' })
    ).not.toBeInTheDocument();
  });

  it('places a complaint about the address against its input', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { email: { code: 'email' } }
      })
    );

    renderRouted(<ForgotPasswordPage />, { route: '/forgot-password' });
    await askFor('not-an-address');

    expect(
      await screen.findByText('Enter a valid email address.')
    ).toBeInTheDocument();
    // Still on the form, so it can be corrected rather than started again.
    expect(
      screen.getByRole('button', { name: 'Send a reset link' })
    ).toBeInTheDocument();
  });

  it('explains a network failure in plain terms', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    renderRouted(<ForgotPasswordPage />, { route: '/forgot-password' });
    await askFor('ada@acme.test');

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i
    );
  });

  it('offers the way back to signing in', () => {
    renderRouted(<ForgotPasswordPage />, { route: '/forgot-password' });

    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login'
    );
  });
});
