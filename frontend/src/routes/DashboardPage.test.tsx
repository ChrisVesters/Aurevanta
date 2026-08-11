import { describe, expect, it } from 'vitest';
import { screen } from '@testing-library/react';
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

  /**
   * The header naming the organisation belongs to {@link AppLayout} now that a second
   * page shares it; what is left here is the page itself.
   */
  it('names the organisation everything it plans will belong to', async () => {
    storeAccessToken();
    fetchMock.mockResolvedValue(jsonResponse(200, ACCOUNT));

    renderRouted(<DashboardPage />);

    expect(
      await screen.findByText(/Acme Planning Co is ready/)
    ).toBeInTheDocument();
  });
});
