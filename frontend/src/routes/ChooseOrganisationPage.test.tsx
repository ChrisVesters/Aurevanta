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

  it('lets the visitor sign out instead of choosing', async () => {
    await choosing([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP]);

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }));

    expect(readStoredSession()).toBeNull();
  });
});
