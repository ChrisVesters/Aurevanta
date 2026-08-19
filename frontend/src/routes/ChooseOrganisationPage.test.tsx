import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ChooseOrganisationPage } from './ChooseOrganisationPage';
import { readStoredSession } from '../auth/session';
import {
  ACME_MEMBERSHIP,
  AUTHENTICATION,
  UMBRELLA_MEMBERSHIP,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeIdentityToken
} from '../test/render';

describe('ChooseOrganisationPage', () => {
  const fetchMock = mockFetch();

  /** Drives the provider into the state this page exists for. */
  async function choosing(memberships: unknown[]) {
    storeIdentityToken();
    fetchMock.mockResolvedValueOnce(jsonResponse(200, memberships));
    renderRouted(<ChooseOrganisationPage />);
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
  }

  it('lists every organisation with the role held in it', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Acme Planning Co' })
      ).toBeInTheDocument()
    );
    expect(
      screen.getByRole('button', { name: 'Umbrella' })
    ).toBeInTheDocument();
    expect(screen.getByText('Owner')).toBeInTheDocument();
    expect(screen.getByText('Member')).toBeInTheDocument();
  });

  /**
   * Since chosen handles a name is not unique, so this list can offer the same word twice. Inside
   * the button, because two buttons that read the same are two buttons anybody
   * navigating by their names cannot choose between.
   */
  it('tells two organisations with one name apart by their handles', async () => {
    const otherAcme = {
      ...ACME_MEMBERSHIP,
      id: 'a-second-acme',
      role: 'MEMBER',
      organisation: {
        id: 'the-other-acme',
        name: 'Acme Planning Co',
        slug: 'acme-planning-co-2'
      }
    };
    await choosing([ACME_MEMBERSHIP, otherAcme, UMBRELLA_MEMBERSHIP]);

    await waitFor(() =>
      expect(
        screen.getByRole('button', {
          name: 'Acme Planning Co acme-planning-co'
        })
      ).toBeInTheDocument()
    );
    expect(
      screen.getByRole('button', {
        name: 'Acme Planning Co acme-planning-co-2'
      })
    ).toBeInTheDocument();
  });

  /** Noise for the overwhelming majority, who have no collision at all. */
  it('says nothing about a handle when every name is its own', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Acme Planning Co' })
      ).toBeInTheDocument()
    );
    expect(screen.queryByText('umbrella')).not.toBeInTheDocument();
  });

  it('exchanges the token for the organisation that was picked', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Umbrella' })).toBeEnabled()
    );

    fetchMock.mockResolvedValueOnce(jsonResponse(200, AUTHENTICATION));
    await userEvent.click(screen.getByRole('button', { name: 'Umbrella' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenLastCalledWith(
        `/api/auth/tenants/${UMBRELLA_MEMBERSHIP.organisation.id}/token`,
        expect.objectContaining({ method: 'POST' })
      )
    );
    expect(readStoredSession()?.kind).toBe('access');
  });

  it('says so when the organisation is refused', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Umbrella' })).toBeEnabled()
    );

    fetchMock.mockResolvedValueOnce(
      jsonResponse(403, { code: 'not_a_member' })
    );
    await userEvent.click(screen.getByRole('button', { name: 'Umbrella' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'You do not belong to that organisation.'
    );
    // The choice stays open, so a second attempt is possible.
    expect(screen.getByRole('button', { name: 'Umbrella' })).toBeEnabled();
  });

  /**
   * Not an error: this is where being removed from your last organisation leaves you,
   * with the account and password intact.
   */
  it('explains the empty state rather than offering nothing', async () => {
    await choosing([]);

    await waitFor(() =>
      expect(
        screen.getByText(/do not belong to an organisation yet/i)
      ).toBeInTheDocument()
    );
    expect(screen.queryByRole('list')).not.toBeInTheDocument();
  });

  /**
   * The only way out of this state a person can take by themselves. Being invited works
   * too, but waiting for somebody else to act is not a way out — it is a hope.
   */
  it('lets somebody who belongs to nothing start an organisation', async () => {
    await choosing([]);
    await screen.findByLabelText('Organisation name');

    fetchMock.mockResolvedValueOnce(jsonResponse(201, AUTHENTICATION));
    await userEvent.type(
      screen.getByLabelText('Organisation name'),
      'Nowhere Consulting'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Create organisation' })
    );

    await waitFor(() =>
      expect(fetchMock).toHaveBeenLastCalledWith(
        '/api/organisations',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            name: 'Nowhere Consulting',
            slug: 'nowhere-consulting'
          })
        })
      )
    );
    // A session for the organisation just made, so nothing else has to be chosen.
    expect(readStoredSession()?.kind).toBe('access');
  });

  /**
   * The name is not the thing that has to be unique any more. The handle is, and a
   * refusal about it arrives holding a free one.
   */
  it('takes up the free handle a refusal offers', async () => {
    await choosing([]);
    await screen.findByLabelText('Organisation name');

    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, { code: 'slug_taken', suggested: 'acme-planning-co-2' })
    );
    await userEvent.type(
      screen.getByLabelText('Organisation name'),
      'Acme Planning Co'
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Create organisation' })
    );

    expect(await screen.findByLabelText('Handle')).toHaveValue(
      'acme-planning-co-2'
    );
    // Said once, beside the field it is about — not in the banner as well.
    expect(await screen.findAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('alert')).toHaveTextContent(
      /already has that handle/i
    );
  });

  /**
   * A refusal that is not about the handle has nothing to put beside the field and no
   * alternative to offer, so it goes in the banner and leaves what was typed alone.
   */
  it('reports any other refusal in the banner, leaving the handle alone', async () => {
    await choosing([]);
    await screen.findByLabelText('Organisation name');
    await userEvent.type(
      screen.getByLabelText('Organisation name'),
      'Nowhere Consulting'
    );

    fetchMock.mockResolvedValueOnce(
      jsonResponse(401, { code: 'invalid_credentials' })
    );
    await userEvent.click(
      screen.getByRole('button', { name: 'Create organisation' })
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Email or password is incorrect.'
    );
    expect(screen.getByLabelText('Handle')).toHaveValue('nowhere-consulting');
  });

  /** Somebody with a choice already has a way forward; the form would only distract. */
  it('does not offer to start one when there is already a choice', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'Acme Planning Co' })
      ).toBeInTheDocument()
    );
    expect(
      screen.queryByLabelText('Organisation name')
    ).not.toBeInTheDocument();
  });

  it('lets the visitor sign out instead of choosing', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(readStoredSession()).toBeNull();
  });
});
