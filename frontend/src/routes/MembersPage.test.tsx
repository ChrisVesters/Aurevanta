import { describe, expect, it } from 'vitest';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MembersPage } from './MembersPage';
import { readStoredSession } from '../auth/session';
import {
  ACCOUNT,
  INVITATION,
  MEMBERS,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Account } from '../auth/types';

describe('MembersPage', () => {
  const fetchMock = mockFetch();

  /**
   * Restores a session and answers the two loads the page makes: everybody in the
   * organisation, then — only for an owner — what is still outstanding.
   */
  async function open(account: Account = ACCOUNT, invitations = [INVITATION]) {
    storeAccessToken();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, account))
      .mockResolvedValueOnce(jsonResponse(200, MEMBERS))
      .mockResolvedValueOnce(jsonResponse(200, invitations));
    renderRouted(<MembersPage />, { route: '/app/members' });
    await screen.findByText('Ada');
  }

  it('lists everybody in the organisation', async () => {
    await open();

    expect(screen.getByText('ada@acme.test')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('bob@acme.test')).toBeInTheDocument();
  });

  /** So removing yourself is a deliberate act rather than a mis-click. */
  it('marks the reader’s own row', async () => {
    await open();

    expect(screen.getByText('You')).toBeInTheDocument();
  });

  it('promotes somebody and reloads from the server', async () => {
    await open();
    const promoted = [{ ...MEMBERS[1], role: 'OWNER' }, MEMBERS[0]];
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse(200, { ...MEMBERS[1], role: 'OWNER' })
      )
      .mockResolvedValueOnce(jsonResponse(200, promoted))
      .mockResolvedValueOnce(jsonResponse(200, []));

    await userEvent.selectOptions(
      screen.getByLabelText('Role for Bob'),
      'OWNER'
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/members/${MEMBERS[1].id}`,
        expect.objectContaining({ method: 'PATCH' })
      )
    );
    await waitFor(() =>
      expect(screen.getByLabelText('Role for Bob')).toHaveValue('OWNER')
    );
  });

  /**
   * The rule the server exists to enforce, seen from the screen that would break it: an
   * organisation with nobody able to administer it cannot be repaired from inside the
   * product at all.
   */
  it('says so when a change would leave the organisation with no owner', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(jsonResponse(409, { code: 'last_owner' }));

    await userEvent.selectOptions(
      screen.getByLabelText('Role for Ada'),
      'MEMBER'
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /must always have at least one owner/i
    );
  });

  /** Removal is the one action here that cannot be undone from this screen. */
  it('asks before removing somebody', async () => {
    await open();

    await userEvent.click(screen.getByRole('button', { name: 'Remove Bob' }));

    expect(
      screen.getByText('Remove Bob from this organisation?')
    ).toBeInTheDocument();
    // Nothing has been asked of the server yet.
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('lets the question be answered no', async () => {
    await open();
    await userEvent.click(screen.getByRole('button', { name: 'Remove Bob' }));

    await userEvent.click(screen.getByRole('button', { name: 'Keep them' }));

    expect(
      screen.queryByText('Remove Bob from this organisation?')
    ).not.toBeInTheDocument();
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it('removes somebody once the question is answered yes', async () => {
    await open();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(204))
      .mockResolvedValueOnce(jsonResponse(200, [MEMBERS[0]]))
      .mockResolvedValueOnce(jsonResponse(200, []));

    await userEvent.click(screen.getByRole('button', { name: 'Remove Bob' }));
    await userEvent.click(screen.getByRole('button', { name: 'Yes, remove' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/members/${MEMBERS[1].id}`,
        expect.objectContaining({ method: 'DELETE' })
      )
    );
    // Says what did *not* happen, because "removed" reads like a deleted account.
    expect(await screen.findByRole('status')).toHaveTextContent(
      /account is untouched/i
    );
  });

  it('sends an invitation and says where it went', async () => {
    await open();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(201, INVITATION))
      .mockResolvedValueOnce(jsonResponse(200, MEMBERS))
      .mockResolvedValueOnce(jsonResponse(200, [INVITATION]));

    await userEvent.type(screen.getByLabelText('Email'), 'dave@elsewhere.test');
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'OWNER');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send invitation' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/invitations',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            email: 'dave@elsewhere.test',
            role: 'OWNER'
          })
        })
      )
    );
    expect(await screen.findByRole('status')).toHaveTextContent(
      'An invitation is on its way to dave@elsewhere.test.'
    );
  });

  it('reports a refused invitation against the address it was for', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, { code: 'already_a_member' })
    );

    await userEvent.type(screen.getByLabelText('Email'), 'bob@acme.test');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send invitation' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'That address already belongs to this organisation.'
    );
  });

  /** The role is a field on the form, so a complaint about it belongs beside it. */
  it('reports a refused role against the control it was chosen in', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(400, {
        code: 'validation_failed',
        errors: { role: { code: 'not_null' } }
      })
    );

    await userEvent.type(screen.getByLabelText('Email'), 'dave@elsewhere.test');
    await userEvent.click(
      screen.getByRole('button', { name: 'Send invitation' })
    );

    expect(await screen.findByText('This is required.')).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  /** The guard that keeps an answer arriving late from touching a page that has gone. */
  it('drops a failure that arrives after the page has been left', async () => {
    storeAccessToken();
    let refuse: (value: Response) => void = () => {};
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, ACCOUNT))
      .mockReturnValueOnce(
        new Promise<Response>((resolve) => {
          refuse = resolve;
        })
      );
    const { unmount } = renderRouted(<MembersPage />, {
      route: '/app/members'
    });
    await screen.findByRole('status');

    unmount();
    refuse(jsonResponse(500, null));

    await waitFor(() => expect(screen.queryByRole('alert')).toBeNull());
  });

  it('lists what is still outstanding, and never the link itself', async () => {
    await open();

    const pending = within(
      screen.getByRole('button', {
        name: 'Withdraw the invitation to dave@elsewhere.test'
      }).parentElement as HTMLElement
    );
    expect(pending.getByText('dave@elsewhere.test')).toBeInTheDocument();
    expect(pending.getByText(/^Expires/)).toBeInTheDocument();
    // Only a hash of the token was kept, so there is nothing here that could be one.
    expect(screen.queryByText(/token/i)).not.toBeInTheDocument();
  });

  it('sends an invitation again behind a new link', async () => {
    await open();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, INVITATION))
      .mockResolvedValueOnce(jsonResponse(200, MEMBERS))
      .mockResolvedValueOnce(jsonResponse(200, [INVITATION]));

    await userEvent.click(
      screen.getByRole('button', {
        name: 'Send dave@elsewhere.test a new link'
      })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/invitations/${INVITATION.id}/resend`,
        expect.objectContaining({ method: 'POST' })
      )
    );
    // The point of resending is that the message went astray, and a message that went
    // astray is one somebody else may be holding.
    expect(await screen.findByRole('status')).toHaveTextContent(
      /previous one has stopped working/i
    );
  });

  it('withdraws an invitation', async () => {
    await open();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(204))
      .mockResolvedValueOnce(jsonResponse(200, MEMBERS))
      .mockResolvedValueOnce(jsonResponse(200, []));

    await userEvent.click(
      screen.getByRole('button', {
        name: 'Withdraw the invitation to dave@elsewhere.test'
      })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        `/api/invitations/${INVITATION.id}`,
        expect.objectContaining({ method: 'DELETE' })
      )
    );
    await waitFor(() =>
      expect(
        screen.getByText('Nobody is waiting on an invitation.')
      ).toBeInTheDocument()
    );
  });

  /**
   * An invitation that has run out of time is still outstanding — it holds that address's
   * one live slot until somebody withdraws it or sends a new link — so "Expires" beside a
   * date already past would read as a bug rather than as something to act on.
   */
  it('marks an invitation that has run out of time', async () => {
    await open(ACCOUNT, [{ ...INVITATION, expiresAt: '2020-01-01T00:00:00Z' }]);

    expect(screen.getByText(/^Expired/)).toBeInTheDocument();
    // Still listed, and still with both things an owner can do about it.
    expect(
      screen.getByRole('button', {
        name: 'Send dave@elsewhere.test a new link'
      })
    ).toBeInTheDocument();
  });

  /**
   * Demoting yourself changes what you may do, and the token still says otherwise for as
   * long as twelve hours. An interface that went on offering what the server would refuse
   * is the thing hiding the controls from a member was for.
   */
  it('takes the owner controls away when you demote yourself', async () => {
    await open();
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse(200, { ...MEMBERS[0], role: 'MEMBER' })
      )
      .mockResolvedValueOnce(jsonResponse(200, { ...ACCOUNT, role: 'MEMBER' }))
      .mockResolvedValueOnce(
        jsonResponse(200, [{ ...MEMBERS[0], role: 'MEMBER' }, MEMBERS[1]])
      );

    await userEvent.selectOptions(
      screen.getByLabelText('Role for Ada'),
      'MEMBER'
    );

    await waitFor(() =>
      expect(screen.queryByLabelText('Role for Bob')).not.toBeInTheDocument()
    );
    expect(
      screen.queryByRole('button', { name: 'Send invitation' })
    ).not.toBeInTheDocument();
  });

  /**
   * A session scoped to an organisation you have just left can do nothing but be
   * refused — starting with reloading the list it is sitting on.
   */
  it('ends the session when you remove yourself', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(jsonResponse(204));

    await userEvent.click(screen.getByRole('button', { name: 'Remove Ada' }));
    await userEvent.click(screen.getByRole('button', { name: 'Yes, remove' }));

    await waitFor(() => expect(readStoredSession()).toBeNull());
    // And no attempt to read a list it is no longer entitled to.
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it('says when nobody is waiting on an invitation', async () => {
    await open(ACCOUNT, []);

    expect(
      screen.getByText('Nobody is waiting on an invitation.')
    ).toBeInTheDocument();
  });

  /**
   * Hidden rather than disabled: an interface that offers an action and then refuses it
   * teaches people to distrust it. The server enforces the same rule regardless.
   */
  it('shows a member none of the owner controls', async () => {
    storeAccessToken();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, { ...ACCOUNT, role: 'MEMBER' }))
      .mockResolvedValueOnce(jsonResponse(200, MEMBERS));
    renderRouted(<MembersPage />, { route: '/app/members' });
    await screen.findByText('Ada');

    expect(screen.queryByLabelText('Role for Bob')).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Remove Bob' })
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole('button', { name: 'Send invitation' })
    ).not.toBeInTheDocument();
    // The roles are still readable; it is only acting on them that is an owner's.
    expect(screen.getAllByText('Member').length).toBeGreaterThan(0);
    // And nothing was asked of the endpoint that would have refused them.
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it('says so when the list cannot be loaded', async () => {
    storeAccessToken();
    fetchMock
      .mockResolvedValueOnce(jsonResponse(200, ACCOUNT))
      .mockResolvedValueOnce(jsonResponse(403, { code: 'not_a_member' }));
    renderRouted(<MembersPage />, { route: '/app/members' });

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You do not belong to that organisation.'
    );
  });

  // Reached only if the guard above it is ever bypassed.
  it('renders nothing without an account', () => {
    const { container } = renderRouted(<MembersPage />, {
      route: '/app/members'
    });

    expect(container).toBeEmptyDOMElement();
  });
});
