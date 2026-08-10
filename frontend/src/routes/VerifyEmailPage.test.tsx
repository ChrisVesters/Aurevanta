import { describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { VerifyEmailPage } from './VerifyEmailPage';
import { jsonResponse, mockFetch, renderRouted } from '../test/render';

describe('VerifyEmailPage', () => {
  const fetchMock = mockFetch();

  it('redeems the token out of the link it was reached by', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    renderRouted(<VerifyEmailPage />, { route: '/verify-email?token=abc123' });

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/auth/verify-email',
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(JSON.parse(fetchMock.mock.calls[0][1].body)).toEqual({
      token: 'abc123'
    });
  });

  it('says the address is confirmed and points at signing in', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    renderRouted(<VerifyEmailPage />, { route: '/verify-email?token=abc123' });

    expect(
      await screen.findByRole('heading', { name: 'Address confirmed' })
    ).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login'
    );
  });

  it('waits rather than guessing while the link is being redeemed', () => {
    fetchMock.mockReturnValue(new Promise(() => {}));

    renderRouted(<VerifyEmailPage />, { route: '/verify-email?token=abc123' });

    expect(screen.getByRole('status')).toHaveTextContent('Confirming');
  });

  /**
   * A link that has expired or been used is exactly when someone most needs another one,
   * so this must not be a dead end.
   */
  it('offers a new link when the one followed no longer works', async () => {
    fetchMock.mockResolvedValue(jsonResponse(400, { code: 'invalid_token' }));

    renderRouted(<VerifyEmailPage />, { route: '/verify-email?token=stale' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /expired or has already been used/i
    );
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
  });

  it('offers a new link when reached with no token at all', async () => {
    renderRouted(<VerifyEmailPage />, { route: '/verify-email' });

    expect(
      screen.getByRole('heading', { name: 'Ask for a new confirmation link' })
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toBeInTheDocument();
    // Nothing to redeem, so nothing is asked of the server.
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('asks the server for another link', async () => {
    renderRouted(<VerifyEmailPage />, { route: '/verify-email' });
    fetchMock.mockResolvedValue(jsonResponse(202));

    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a new link' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/auth/verify-email/resend',
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
    renderRouted(<VerifyEmailPage />, { route: '/verify-email' });
    fetchMock.mockResolvedValue(jsonResponse(202));

    await userEvent.type(screen.getByLabelText('Email'), 'nobody@acme.test');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a new link' })
    );

    expect(await screen.findByRole('status')).toHaveTextContent(
      /if that address needs confirming/i
    );
    expect(
      screen.queryByRole('button', { name: 'Send a new link' })
    ).not.toBeInTheDocument();
  });

  it('places a complaint about the address against its input', async () => {
    renderRouted(<VerifyEmailPage />, { route: '/verify-email' });
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { email: { code: 'email' } }
      })
    );

    await userEvent.type(screen.getByLabelText('Email'), 'not-an-address');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a new link' })
    );

    expect(
      await screen.findByText('Enter a valid email address.')
    ).toBeInTheDocument();
  });

  // Leaving the page before the server answers must not write to an unmounted component.
  it('abandons a redemption when it is unmounted first', async () => {
    let settle: (response: Response) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise<Response>((resolve) => {
        settle = resolve;
      })
    );
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { unmount } = renderRouted(<VerifyEmailPage />, {
      route: '/verify-email?token=abc123'
    });
    unmount();
    settle(jsonResponse(204));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    expect(errors).not.toHaveBeenCalled();
  });

  it('abandons a failed redemption when it is unmounted first', async () => {
    let fail: (reason: Error) => void = () => {};
    fetchMock.mockReturnValue(
      new Promise<Response>((_resolve, reject) => {
        fail = reject;
      })
    );
    const errors = vi.spyOn(console, 'error').mockImplementation(() => {});

    const { unmount } = renderRouted(<VerifyEmailPage />, {
      route: '/verify-email?token=abc123'
    });
    unmount();
    fail(new TypeError('Failed to fetch'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());

    expect(errors).not.toHaveBeenCalled();
  });

  it('explains a network failure in plain terms', async () => {
    renderRouted(<VerifyEmailPage />, { route: '/verify-email' });
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a new link' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /could not reach the server/i
    );
  });
});
