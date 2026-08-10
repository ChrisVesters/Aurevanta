import type { ReactNode } from 'react';
import { render } from '@testing-library/react';
import { beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router';
import { AuthProvider } from '../auth/AuthProvider';
import { storeSession } from '../auth/session';
import type {
  Account,
  AuthenticationResponse,
  Identity,
  Membership,
  SignInResponse
} from '../auth/types';

export const ACCOUNT: Account = {
  userId: '11111111-1111-1111-1111-111111111111',
  email: 'ada@acme.test',
  displayName: 'Ada',
  emailVerified: true,
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

/** What registration returns now: the account, and deliberately no session. */
export const UNVERIFIED_ACCOUNT: Account = {
  ...ACCOUNT,
  emailVerified: false
};

export const ACME_MEMBERSHIP: Membership = {
  id: '33333333-3333-3333-3333-333333333333',
  role: 'OWNER',
  organisation: ACCOUNT.organisation,
  lastAccessedAt: '2026-08-07T09:00:00Z'
};

export const UMBRELLA_MEMBERSHIP: Membership = {
  id: '44444444-4444-4444-4444-444444444444',
  role: 'MEMBER',
  organisation: {
    id: '55555555-5555-5555-5555-555555555555',
    name: 'Umbrella',
    slug: 'umbrella'
  },
  lastAccessedAt: null
};

export function identity(memberships: Membership[]): Identity {
  return {
    identityToken: 'an.identity.token',
    tokenType: 'Bearer',
    expiresInSeconds: 43200,
    memberships
  };
}

export const SIGNED_IN: SignInResponse = {
  outcome: 'SIGNED_IN',
  session: AUTHENTICATION
};

export const CHOOSE_ORGANISATION: SignInResponse = {
  outcome: 'CHOOSE_ORGANISATION',
  identity: identity([ACME_MEMBERSHIP, UMBRELLA_MEMBERSHIP])
};

export const NO_ORGANISATION: SignInResponse = {
  outcome: 'NO_ORGANISATION',
  identity: identity([])
};

/** Puts a token of either kind in storage, as a previous visit would have left it. */
export function storeAccessToken(token = 'a.test.token') {
  storeSession({ token, kind: 'access' });
}

export function storeIdentityToken(token = 'an.identity.token') {
  storeSession({ token, kind: 'identity' });
}

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

/**
 * Builds a `fetch` response double; `body` of `undefined` produces an empty body.
 *
 * `json()` rejects on an empty body rather than resolving to `undefined`, because that is
 * what a real `Response` does. The earlier double resolved instead, which made a body-less
 * `202` look fine here and report "could not reach the server" in a browser — so the
 * faithfulness is the point, not a detail.
 */
export function jsonResponse(status: number, body?: unknown): Response {
  const text = body === undefined ? '' : JSON.stringify(body);
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => {
      if (!text) {
        throw new SyntaxError('Unexpected end of JSON input');
      }
      return JSON.parse(text) as unknown;
    },
    text: async () => text
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
