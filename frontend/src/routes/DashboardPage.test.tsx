import { describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { DashboardPage } from './DashboardPage';
import {
  ACCOUNT,
  jsonResponse,
  mockFetch,
  renderRouted,
  storeAccessToken
} from '../test/render';

describe('DashboardPage', () => {
  const fetchMock = mockFetch();

  // Reached only if the guard is ever bypassed; it must not throw on a missing account.
  it('renders nothing when there is no account', () => {
    const { container } = renderRouted(<DashboardPage />);

    expect(container).toBeEmptyDOMElement();
  });

  it('names the organisation the session is scoped to', async () => {
    storeAccessToken();
    fetchMock.mockResolvedValue(jsonResponse(200, ACCOUNT));

    renderRouted(<DashboardPage />);

    await waitFor(() =>
      expect(screen.getByText('Acme Planning Co')).toBeInTheDocument()
    );
    expect(screen.getByText('Owner')).toBeInTheDocument();
    expect(screen.getByText('Ada')).toBeInTheDocument();
  });

  it('describes a non-owner as a member', async () => {
    storeAccessToken();
    fetchMock.mockResolvedValue(
      jsonResponse(200, { ...ACCOUNT, role: 'MEMBER' })
    );

    renderRouted(<DashboardPage />);

    await waitFor(() => expect(screen.getByText('Member')).toBeInTheDocument());
  });
});
