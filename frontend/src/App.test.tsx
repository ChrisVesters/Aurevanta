import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import {
  ACCOUNT,
  ACME_MEMBERSHIP,
  AUTHENTICATION,
  INVITATION_PREVIEW,
  MEMBERS,
  SIGNED_IN,
  UMBRELLA_MEMBERSHIP,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken,
  storeIdentityToken
} from './test/render';

describe('routing', () => {
  const fetchMock = mockFetch();

  /**
   * Answers by URL rather than in bulk: a signed-in page restores the session *and* asks
   * which organisations the caller belongs to, for the switcher in its header. A double
   * that answered both with the same payload would hand the switcher an account.
   */
  function signedIn() {
    storeAccessToken();
    answering({
      '/api/auth/me': jsonResponse(200, ACCOUNT),
      '/api/memberships': jsonResponse(200, [ACME_MEMBERSHIP])
    });
  }

  /** The same, for tests that sign in through the form rather than restoring a token. */
  function answering(routes: Record<string, Response>) {
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(routes[url] ?? jsonResponse(404, null))
    );
  }

  /** Authenticated, but holding an identity token with an organisation still to pick. */
  function choosing(
    memberships: unknown[] = [ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]
  ) {
    storeIdentityToken();
    fetchMock.mockResolvedValue(jsonResponse(200, memberships));
  }

  it('shows the landing page at the root, not a sign-in form', () => {
    renderRouted(<App />, { route: '/' });

    expect(
      screen.getByRole('heading', { name: /plan with ranges/i, level: 1 })
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
  });

  it('offers sign-up and sign-in from the landing page', () => {
    renderRouted(<App />, { route: '/' });

    expect(
      screen.getByRole('link', { name: 'Create your organisation' })
    ).toHaveAttribute('href', '/register');
    expect(screen.getAllByRole('link', { name: 'Sign in' })[0]).toHaveAttribute(
      'href',
      '/login'
    );
  });

  it('serves the sign-up form at its own address', () => {
    renderRouted(<App />, { route: '/register' });

    expect(
      screen.getByRole('heading', { name: 'Create your organisation' })
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Organisation name')).toBeInTheDocument();
  });

  it('serves the sign-in form at its own address', () => {
    renderRouted(<App />, { route: '/login' });

    expect(
      screen.getByRole('heading', { name: 'Sign in' })
    ).toBeInTheDocument();
  });

  it('sends an anonymous visitor from the app to sign in', async () => {
    renderRouted(<App />, { route: '/app' });

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Sign in' })
      ).toBeInTheDocument()
    );
  });

  it('shows the dashboard to a signed-in visitor', async () => {
    signedIn();

    renderRouted(<App />, { route: '/app' });

    await waitFor(() =>
      expect(screen.getByText('Acme Planning Co')).toBeInTheDocument()
    );
    expect(
      screen.getByRole('heading', { name: /you.re set up/i })
    ).toBeInTheDocument();
  });

  it('keeps a signed-in visitor off the sign-in page', async () => {
    signedIn();

    renderRouted(<App />, { route: '/login' });

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: /you.re set up/i })
      ).toBeInTheDocument()
    );
  });

  it('keeps a signed-in visitor off the sign-up page', async () => {
    signedIn();

    renderRouted(<App />, { route: '/register' });

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: /you.re set up/i })
      ).toBeInTheDocument()
    );
  });

  it('points a signed-in visitor on the landing page at their organisation', async () => {
    signedIn();

    renderRouted(<App />, { route: '/' });

    await waitFor(() =>
      expect(
        screen.getByRole('link', { name: 'Open Aurevanta' })
      ).toHaveAttribute('href', '/app')
    );
    expect(
      screen.queryByRole('link', { name: 'Sign in' })
    ).not.toBeInTheDocument();
  });

  /**
   * The chooser stands in for the route rather than living at an address of its own: it
   * belongs to the state of the session, not to somewhere a visitor navigates to.
   */
  it('asks a visitor with several organisations to pick one', async () => {
    choosing();

    renderRouted(<App />, { route: '/app' });

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Choose an organisation' })
      ).toBeInTheDocument()
    );
    expect(
      screen.queryByRole('heading', { name: /you.re set up/i })
    ).not.toBeInTheDocument();
  });

  it('shows the empty state to a visitor who belongs to no organisation', async () => {
    choosing([]);

    renderRouted(<App />, { route: '/app' });

    await waitFor(() =>
      expect(
        screen.getByText(/do not belong to an organisation yet/i)
      ).toBeInTheDocument()
    );
  });

  // Signing in again is not what they are missing, so the sign-in form would be a dead end.
  it('keeps a visitor still choosing an organisation off the sign-in page', async () => {
    choosing();

    renderRouted(<App />, { route: '/login' });

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Choose an organisation' })
      ).toBeInTheDocument()
    );
  });

  it('moves the visitor to the dashboard once they sign in', async () => {
    answering({
      '/api/auth/login': jsonResponse(200, SIGNED_IN),
      '/api/memberships': jsonResponse(200, [ACME_MEMBERSHIP])
    });

    renderRouted(<App />, { route: '/login' });
    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-long-enough-passphrase'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: /you.re set up/i })
      ).toBeInTheDocument()
    );
  });

  // Someone deep-linked to /app is sent to sign in, and should land back on /app after.
  it('returns the visitor to where they were heading after signing in', async () => {
    renderRouted(<App />, { route: '/app' });
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Sign in' })
      ).toBeInTheDocument()
    );

    answering({
      '/api/auth/login': jsonResponse(200, SIGNED_IN),
      '/api/memberships': jsonResponse(200, [ACME_MEMBERSHIP])
    });
    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-long-enough-passphrase'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: /you.re set up/i })
      ).toBeInTheDocument()
    );
  });

  it('lets the visitor cross between sign-in and sign-up', async () => {
    renderRouted(<App />, { route: '/login' });

    await userEvent.click(
      screen.getByRole('link', { name: 'Create an organisation' })
    );

    expect(
      screen.getByRole('heading', { name: 'Create your organisation' })
    ).toBeInTheDocument();
  });

  /**
   * The address the confirmation email actually sends people to. It had no route, so
   * following the link landed on "page not found" and the account stayed unusable — the
   * one journey the whole gate depends on.
   */
  it('serves the confirmation link the email sends people to', async () => {
    fetchMock.mockResolvedValue(jsonResponse(204));

    renderRouted(<App />, { route: '/verify-email?token=abc123' });

    expect(
      await screen.findByRole('heading', { name: 'Address confirmed' })
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Page not found' })
    ).not.toBeInTheDocument();
  });

  it('serves the same page without a token, for a link that no longer works', () => {
    renderRouted(<App />, { route: '/verify-email' });

    expect(
      screen.getByRole('heading', { name: 'Ask for a new confirmation link' })
    ).toBeInTheDocument();
  });

  /**
   * The address the reset email sends people to. Same lesson as the confirmation link:
   * an endpoint that emails a link is not finished until the link lands somewhere.
   */
  it('serves the reset link the email sends people to', () => {
    renderRouted(<App />, { route: '/reset-password?token=abc123' });

    expect(
      screen.getByRole('heading', { name: 'Choose a new password' })
    ).toBeInTheDocument();
    expect(
      screen.queryByRole('heading', { name: 'Page not found' })
    ).not.toBeInTheDocument();
  });

  it('serves the page that asks for a reset link', () => {
    renderRouted(<App />, { route: '/forgot-password' });

    expect(
      screen.getByRole('heading', { name: 'Reset your password' })
    ).toBeInTheDocument();
  });

  /** The entry point: without this the reset pages exist but nobody can reach them. */
  it('lets someone who cannot sign in ask for a new password', async () => {
    renderRouted(<App />, { route: '/login' });

    await userEvent.click(
      screen.getByRole('link', { name: 'Choose a new one' })
    );

    expect(
      screen.getByRole('heading', { name: 'Reset your password' })
    ).toBeInTheDocument();
  });

  /**
   * The whole recovery journey, as somebody locked out actually walks it: sign-in, ask
   * for a link, follow it, choose a password, sign in again.
   */
  it('carries a locked-out visitor from sign-in to a new password', async () => {
    renderRouted(<App />, { route: '/login' });
    await userEvent.click(
      screen.getByRole('link', { name: 'Choose a new one' })
    );

    fetchMock.mockResolvedValue(jsonResponse(202));
    await userEvent.type(screen.getByLabelText('Email'), 'ada@acme.test');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send a reset link' })
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      /a link is on its way/i
    );

    // The link out of the email arrives as a fresh page load rather than a navigation.
    fetchMock.mockResolvedValue(jsonResponse(204));
    renderRouted(<App />, { route: '/reset-password?token=abc123' });
    await userEvent.type(
      screen.getAllByLabelText('Password')[0],
      'a-brand-new-passphrase'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Save new password' })
    );

    expect(
      await screen.findByRole('heading', { name: 'Password changed' })
    ).toBeInTheDocument();
  });

  /**
   * The whole invitation journey, at the address the emailed link actually points at:
   * public, outside both guards, and ending in a session the dashboard will accept.
   */
  it('carries somebody with no account from an invitation link into the app', async () => {
    answering({
      '/api/invitations/a-link': jsonResponse(200, INVITATION_PREVIEW),
      '/api/invitations/a-link/accept': jsonResponse(200, AUTHENTICATION),
      '/api/memberships': jsonResponse(200, [ACME_MEMBERSHIP])
    });

    renderRouted(<App />, { route: '/invite/a-link' });
    await screen.findByRole('heading', { name: 'Join Acme Planning Co' });
    await userEvent.type(screen.getByLabelText('Your name'), 'Dave');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-long-enough-passphrase'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Join Acme Planning Co' })
    );

    await userEvent.click(
      await screen.findByRole('link', { name: 'Open Aurevanta' })
    );

    expect(
      await screen.findByRole('heading', { name: /you.re set up/i })
    ).toBeInTheDocument();
  });

  /** Owner-only work reached through the header, not by knowing the URL. */
  it('reaches member management from the signed-in header', async () => {
    signedIn();
    renderRouted(<App />, { route: '/app' });
    await screen.findByRole('heading', { name: /you.re set up/i });

    answering({
      '/api/auth/me': jsonResponse(200, ACCOUNT),
      '/api/memberships': jsonResponse(200, [ACME_MEMBERSHIP]),
      '/api/members': jsonResponse(200, MEMBERS),
      '/api/invitations': jsonResponse(200, [])
    });
    await userEvent.click(screen.getByRole('link', { name: 'Members' }));

    expect(
      await screen.findByRole('heading', { name: 'Members' })
    ).toBeInTheDocument();
    expect(await screen.findByText('bob@acme.test')).toBeInTheDocument();
  });

  it('shows a not-found page for an unknown address', () => {
    renderRouted(<App />, { route: '/nowhere' });

    expect(
      screen.getByRole('heading', { name: 'Page not found' })
    ).toBeInTheDocument();
  });

  it('waits rather than flashing the sign-in page while a session is restored', () => {
    storeAccessToken();
    fetchMock.mockReturnValue(new Promise(() => {}));

    renderRouted(<App />, { route: '/app' });

    expect(screen.getByRole('status')).toHaveTextContent('Loading');
    expect(
      screen.queryByRole('heading', { name: 'Sign in' })
    ).not.toBeInTheDocument();
  });

  it('signs the visitor out from the dashboard', async () => {
    signedIn();
    renderRouted(<App />, { route: '/app' });
    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: /you.re set up/i })
      ).toBeInTheDocument()
    );

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    await waitFor(() =>
      expect(
        screen.getByRole('heading', { name: 'Sign in' })
      ).toBeInTheDocument()
    );
  });
});
