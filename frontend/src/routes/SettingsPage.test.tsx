import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { SettingsPage } from './SettingsPage';
import {
  ACCOUNT,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';
import type { Account } from '../auth/types';

describe('SettingsPage', () => {
  const fetchMock = mockFetch();

  async function open(account: Account = ACCOUNT) {
    storeAccessToken();
    fetchMock.mockResolvedValueOnce(jsonResponse(200, account));
    renderRouted(<SettingsPage />, { route: '/app/settings' });
    await screen.findByRole('heading', { name: 'Organisation' });
  }

  it('starts from what the organisation is called and answers to', async () => {
    await open();

    expect(screen.getByLabelText('Organisation name')).toHaveValue(
      'Acme Planning Co'
    );
    expect(screen.getByLabelText('Handle')).toHaveValue('acme-planning-co');
  });

  it('renames the organisation and re-reads the session', async () => {
    await open();
    fetchMock
      .mockResolvedValueOnce(
        jsonResponse(200, { ...ACCOUNT.organisation, name: 'Acme Ltd' })
      )
      .mockResolvedValueOnce(
        jsonResponse(200, {
          ...ACCOUNT,
          organisation: { ...ACCOUNT.organisation, name: 'Acme Ltd' }
        })
      );

    await userEvent.clear(screen.getByLabelText('Organisation name'));
    await userEvent.type(
      screen.getByLabelText('Organisation name'),
      'Acme Ltd'
    );
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/organisations',
        expect.objectContaining({
          method: 'PATCH',
          body: JSON.stringify({
            name: 'Acme Ltd',
            slug: 'acme-planning-co'
          })
        })
      )
    );
    // The header names the organisation and reads it off the session, so the session has
    // to be re-read or it goes on showing the old name.
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/auth/me',
      expect.anything()
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Saved.');
  });

  /**
   * The handle does not follow the name here. It already has an owner, and moving it
   * because somebody fixed a typo above it is the bug the creation forms guard against.
   */
  it('leaves the handle alone when the name is edited', async () => {
    await open();

    await userEvent.type(screen.getByLabelText('Organisation name'), ' Ltd');

    expect(screen.getByLabelText('Handle')).toHaveValue('acme-planning-co');
  });

  /**
   * Said before it saves rather than after, because after is too late: nothing redirects
   * from a handle that has moved.
   */
  it('warns that moving the handle breaks every link to it', async () => {
    await open();

    expect(screen.queryByText(/stop any link/i)).not.toBeInTheDocument();
    await userEvent.clear(screen.getByLabelText('Handle'));
    await userEvent.type(screen.getByLabelText('Handle'), 'acme');

    expect(screen.getByText(/stop any link/i)).toHaveTextContent(
      'acme-planning-co'
    );
  });

  it('takes up the free handle a refusal offers', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, { code: 'slug_taken', suggested: 'umbrella-2' })
    );

    await userEvent.clear(screen.getByLabelText('Handle'));
    await userEvent.type(screen.getByLabelText('Handle'), 'umbrella');
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    await waitFor(() =>
      expect(screen.getByLabelText('Handle')).toHaveValue('umbrella-2')
    );
    // Said once, beside the field it is about — not in the banner as well.
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect(screen.getByRole('alert')).toHaveTextContent(
      /already has that handle/i
    );
  });

  it('reports any other refusal in the banner', async () => {
    await open();
    fetchMock.mockResolvedValueOnce(
      jsonResponse(403, { code: 'not_an_owner' })
    );

    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'Only an owner of this organisation can do that.'
    );
  });

  /** Hidden rather than offered and refused, as the members page hides its controls. */
  it('offers a member nothing to change', async () => {
    await open({ ...ACCOUNT, role: 'MEMBER' });

    expect(screen.getByText(/Only an owner/)).toBeInTheDocument();
    expect(screen.queryByLabelText('Handle')).not.toBeInTheDocument();
  });

  // Reached only if the guard above it is ever bypassed.
  it('renders nothing without an account', () => {
    const { container } = renderRouted(<SettingsPage />, {
      route: '/app/settings'
    });

    expect(container).toBeEmptyDOMElement();
  });
});
