import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App';
import {
  ACCOUNT,
  ACME_MEMBERSHIP,
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

  function signedIn() {
    storeAccessToken();
    fetchMock.mockResolvedValue(jsonResponse(200, ACCOUNT));
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
    fetchMock.mockResolvedValue(jsonResponse(200, SIGNED_IN));

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

    fetchMock.mockResolvedValue(jsonResponse(200, SIGNED_IN));
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
