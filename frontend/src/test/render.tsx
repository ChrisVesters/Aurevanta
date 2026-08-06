import type { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthProvider } from '../auth/AuthProvider';
import type { Account, AuthenticationResponse } from '../auth/types';

export const ACCOUNT: Account = {
  userId: '11111111-1111-1111-1111-111111111111',
  email: 'ada@acme.test',
  displayName: 'Ada',
  role: 'OWNER',
  organisation: {
    id: '22222222-2222-2222-2222-222222222222',
    name: 'Acme Planning Co',
    slug: 'acme-planning-co'
  }
};

export const AUTHENTICATION: AuthenticationResponse = {
  accessToken: 'a.test.token',
  tokenType: 'Bearer',
  expiresInSeconds: 43200,
  account: ACCOUNT
};

/** Renders inside a router and a real AuthProvider, so guards behave as they do live. */
export function renderRouted(
  ui: ReactNode,
  {
    route = '/',
    wrapInProvider = true
  }: { route?: string; wrapInProvider?: boolean } = {}
) {
  return render(
    <MemoryRouter initialEntries={[route]}>
      {wrapInProvider ? <AuthProvider>{ui}</AuthProvider> : ui}
    </MemoryRouter>
  );
}

/** Builds a `fetch` response double; `body` of `undefined` produces an empty body. */
export function jsonResponse(status: number, body?: unknown): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => (body === undefined ? '' : JSON.stringify(body))
  } as Response;
}

/**
 * Installs a fresh `fetch` double before each test in the calling suite and returns a
 * stable handle to it. Call once at the top of a `describe`.
 */
export function mockFetch() {
  const fetchMock = vi.fn();
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
  });
  return fetchMock;
}
