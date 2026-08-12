import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { InvitePage } from './InvitePage';
import { readStoredSession } from '../auth/session';
import {
  ACCOUNT,
  AUTHENTICATION,
  INVITATION_PREVIEW,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';

const TOKEN = 'an-invitation-link';

describe('InvitePage', () => {
  const fetchMock = mockFetch();

  /** Renders at the real route, so the token comes from the path as it does live. */
  function open() {
    return renderRouted(
      <Routes>
        <Route path="/invite/:token" element={<InvitePage />} />
      </Routes>,
      { route: `/invite/${TOKEN}` }
    );
  }

  async function previewed() {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, INVITATION_PREVIEW));
    open();
    await screen.findByRole('heading', { name: 'Join Acme Planning Co' });
  }

  /**
   * Answers by URL rather than in order: restoring the session and looking the invitation
   * up both fire on mount, and which lands first is React's business rather than ours.
   */
  async function signedInAndPreviewed() {
    storeAccessToken();
    fetchMock.mockImplementation((url: string) =>
      Promise.resolve(
        url === '/api/auth/me'
          ? jsonResponse(200, ACCOUNT)
          : jsonResponse(200, INVITATION_PREVIEW)
      )
    );
    open();
    await screen.findByRole('button', { name: 'Accept invitation' });
  }

  it('says who is asking and what they are asking, before anything is typed', async () => {
    await previewed();

    expect(
      screen.getByText(
        'Ada has invited you to join Acme Planning Co on Aurevanta as Member.'
      )
    ).toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledWith(
      `/api/invitations/${TOKEN}`,
      expect.objectContaining({ method: 'GET' })
    );
  });

  it('creates an account and joins in one step for somebody who has none', async () => {
    await previewed();
    fetchMock.mockResolvedValueOnce(jsonResponse(200, AUTHENTICATION));

    await userEvent.type(screen.getByLabelText('Your name'), 'Dave');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-passphrase-of-my-own'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Join Acme Planning Co' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenLastCalledWith(
        `/api/invitations/${TOKEN}/accept`,
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            displayName: 'Dave',
            password: 'a-passphrase-of-my-own'
          })
        })
      )
    );
    // The session that came back is taken up, so the link at the end goes somewhere.
    expect(readStoredSession()?.kind).toBe('access');
    expect(
      await screen.findByRole('heading', {
        name: 'You have joined Acme Planning Co'
      })
    ).toBeInTheDocument();
  });

  it('reports a refused password against the field it belongs to', async () => {
    await previewed();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { password: { code: 'size', min: 12, max: 72 } }
      })
    );

    await userEvent.type(screen.getByLabelText('Your name'), 'Dave');
    await userEvent.type(screen.getByLabelText('Password'), 'short');
    await userEvent.click(
      screen.getByRole('button', { name: 'Join Acme Planning Co' })
    );

    expect(
      await screen.findByText('Use between 12 and 72 characters.')
    ).toBeInTheDocument();
    // Belongs to a field the visitor can see, so the banner would only repeat it.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /**
   * The bug this closes: an invited colleague who already has an account was shown the
   * form for making one, and only told otherwise after inventing a name and a password.
   * The preview answers it first, so nothing is asked that cannot be given.
   */
  it('asks for no account at all when the address already has one', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(200, { ...INVITATION_PREVIEW, claimed: true })
    );
    open();

    expect(
      await screen.findByText(
        'This invitation is for an address that already has an Aurevanta account.'
      )
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('Your name')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
    // Back here afterwards, or accepting would mean finding the email again.
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login'
    );
    // Nothing was sent: the preview was the whole of the exchange.
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  /**
   * The same answer arriving late, for an account registered between the preview and the
   * attempt — and the hole the server closes either way: a token emailed to a mailbox
   * proves control of the mailbox, not ownership of the account registered with it.
   */
  it('sends somebody whose address gained an account meanwhile to sign in', async () => {
    await previewed();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, { code: 'sign_in_required' })
    );

    await userEvent.type(screen.getByLabelText('Your name'), 'Not Erin');
    await userEvent.type(
      screen.getByLabelText('Password'),
      'a-passphrase-of-my-own'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Join Acme Planning Co' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That address already has an Aurevanta account.'
    );
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute(
      'href',
      '/login'
    );
    // The form goes with it: resubmitting it could only be refused the same way.
    expect(screen.queryByLabelText('Your name')).not.toBeInTheDocument();
  });

  it('accepts with the account somebody already holds, sending no credentials', async () => {
    await signedInAndPreviewed();
    expect(
      screen.getByText('You are signed in as ada@acme.test.')
    ).toBeInTheDocument();

    fetchMock.mockResolvedValueOnce(jsonResponse(200, AUTHENTICATION));
    await userEvent.click(
      screen.getByRole('button', { name: 'Accept invitation' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenLastCalledWith(
        `/api/invitations/${TOKEN}/accept`,
        // No body at all: a `{}` would be a request to make an account.
        expect.objectContaining({ method: 'POST', body: undefined })
      )
    );
    expect(
      await screen.findByRole('heading', {
        name: 'You have joined Acme Planning Co'
      })
    ).toBeInTheDocument();
  });

  /** A shared computer, or a message forwarded to a colleague. */
  it('offers to sign out when the invitation was for somebody else', async () => {
    await signedInAndPreviewed();

    fetchMock.mockResolvedValueOnce(
      jsonResponse(403, { code: 'invitation_for_another_address' })
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Accept invitation' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This invitation was sent to a different address.'
    );
    await userEvent.click(
      screen.getByRole('button', {
        name: 'Sign out and accept it as somebody else'
      })
    );

    // Signed out, and now offered the account they do not have.
    expect(readStoredSession()).toBeNull();
    expect(await screen.findByLabelText('Your name')).toBeInTheDocument();
  });

  it('says an expired invitation has expired, and a withdrawn one has not', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'invitation_expired' })
    );
    open();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This invitation has expired. Ask whoever invited you to send another.'
    );
    expect(screen.queryByLabelText('Your name')).not.toBeInTheDocument();
  });

  it('says a withdrawn invitation was withdrawn', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'invitation_revoked' })
    );
    open();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'This invitation is no longer available.'
    );
  });

  it('says a link nobody recognises no longer works', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, { code: 'invalid_token' })
    );
    open();

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /has expired or has already been used/i
    );
  });

  /**
   * Unreachable through the router, which will not match `/invite/` without a token —
   * but the page must not ask the server about a link it does not have.
   */
  it('asks for nothing when the path carries no token', () => {
    renderRouted(<InvitePage />, { route: '/invite/' });

    expect(fetchMock).not.toHaveBeenCalled();
  });

  /**
   * The guard that keeps an answer arriving late from touching a page that has gone —
   * whichever answer it turns out to be, since a refusal sets state just as a preview
   * does.
   */
  it('drops an answer that arrives after the page has been left', async () => {
    const late = [
      jsonResponse(200, INVITATION_PREVIEW),
      jsonResponse(400, { code: 'invitation_expired' })
    ];

    for (const answer of late) {
      let deliver: (value: Response) => void = () => {};
      fetchMock.mockReturnValueOnce(
        new Promise<Response>((resolve) => {
          deliver = resolve;
        })
      );
      const { unmount } = open();
      await screen.findByRole('status');

      unmount();
      deliver(answer);

      await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
      expect(screen.queryByText('Join Acme Planning Co')).toBeNull();
    }
  });

  it('shows that it is looking the invitation up', async () => {
    fetchMock.mockReturnValueOnce(new Promise(() => {}));
    open();

    expect(await screen.findByRole('status')).toHaveTextContent(
      'Checking your invitation…'
    );
  });
});
