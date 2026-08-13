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
import type { Invitation, InvitationPreview, Member } from '../members/types';
import type { Estimate, Project, WorkItem } from '../projects/types';

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

/** The organisation as its own members see it: Ada owns it, Bob does not. */
export const MEMBERS: Member[] = [
  {
    id: ACME_MEMBERSHIP.id,
    userId: ACCOUNT.userId,
    displayName: 'Ada',
    email: 'ada@acme.test',
    role: 'OWNER',
    joinedAt: '2026-08-06T08:00:00Z'
  },
  {
    id: '66666666-6666-6666-6666-666666666666',
    userId: '77777777-7777-7777-7777-777777777777',
    displayName: 'Bob',
    email: 'bob@acme.test',
    role: 'MEMBER',
    joinedAt: '2026-08-06T09:00:00Z'
  }
];

/** Two plans in one organisation, one of them with nothing said about it yet. */
export const PROJECTS: Project[] = [
  {
    id: '99999999-9999-9999-9999-999999999999',
    name: 'Q3 platform work',
    description: 'Everything we promised the board',
    createdAt: '2026-08-13T08:00:00Z',
    archivedAt: null,
    itemCount: 2,
    estimatedItemCount: 1
  },
  {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    name: 'Migration',
    description: null,
    createdAt: '2026-08-13T09:00:00Z',
    archivedAt: null,
    itemCount: 0,
    estimatedItemCount: 0
  }
];

/** Put away rather than deleted, which is the only way anything leaves a list in M2. */
export const ARCHIVED_PROJECT: Project = {
  id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
  name: 'Last year',
  description: null,
  createdAt: '2025-08-13T08:00:00Z',
  archivedAt: '2026-01-06T08:00:00Z',
  itemCount: 0,
  estimatedItemCount: 0
};

/** The work inside `PROJECTS[0]`, in the order it was written down. */
export const WORK_ITEMS: WorkItem[] = [
  {
    id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
    projectId: PROJECTS[0].id,
    title: 'Migrate the auth service',
    description: 'Blocked on the vendor',
    createdAt: '2026-08-13T08:10:00Z',
    archivedAt: null,
    status: 'NOT_STARTED',
    startedOn: null,
    completedOn: null,
    actualEffortHours: null
  },
  {
    id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    projectId: PROJECTS[0].id,
    title: 'Write the runbook',
    description: null,
    createdAt: '2026-08-13T08:20:00Z',
    archivedAt: null,
    // Under way, so the row has a date to stand behind its claim.
    status: 'IN_PROGRESS',
    startedOn: '2026-08-10',
    completedOn: null,
    actualEffortHours: null
  }
];

export const ARCHIVED_WORK_ITEM: WorkItem = {
  id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
  projectId: PROJECTS[0].id,
  title: 'Something we dropped',
  description: null,
  createdAt: '2026-08-13T08:30:00Z',
  archivedAt: '2026-08-13T09:00:00Z',
  status: 'NOT_STARTED',
  startedOn: null,
  completedOn: null,
  actualEffortHours: null
};

/**
 * What Ada last said about the first item, and Bob about it too — one current estimate per
 * person, which is the shape the schema is built for even though M2 shows it plainly.
 */
export const ESTIMATES: Estimate[] = [
  {
    id: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
    itemId: WORK_ITEMS[0].id,
    estimatorId: ACCOUNT.userId,
    estimatorName: 'Ada',
    p10Hours: 3,
    p50Hours: 5,
    p90Hours: 12,
    createdAt: '2026-08-13T10:00:00Z'
  },
  {
    id: '10101010-1010-1010-1010-101010101010',
    itemId: WORK_ITEMS[0].id,
    estimatorId: '77777777-7777-7777-7777-777777777777',
    estimatorName: 'Bob',
    p10Hours: 10,
    p50Hours: 20,
    p90Hours: 40,
    createdAt: '2026-08-13T11:00:00Z'
  }
];

/** One nobody but a colleague has estimated, so the reader's own boxes are empty. */
export const COLLEAGUES_ESTIMATE: Estimate = {
  id: '20202020-2020-2020-2020-202020202020',
  itemId: WORK_ITEMS[1].id,
  estimatorId: '77777777-7777-7777-7777-777777777777',
  estimatorName: 'Bob',
  p10Hours: 1,
  p50Hours: 2,
  p90Hours: 4,
  createdAt: '2026-08-13T12:00:00Z'
};

export const INVITATION: Invitation = {
  id: '88888888-8888-8888-8888-888888888888',
  email: 'dave@elsewhere.test',
  role: 'MEMBER',
  status: 'PENDING',
  expiresAt: '2026-08-18T08:00:00Z',
  createdAt: '2026-08-11T08:00:00Z'
};

/**
 * Four fields and no more, exactly as the unauthenticated preview sends them. `claimed`
 * is false here because the default fixture is the invitation to a stranger; a case about
 * somebody who already has an account overrides it.
 */
export const INVITATION_PREVIEW: InvitationPreview = {
  organisationName: 'Acme Planning Co',
  invitedBy: 'Ada',
  role: 'MEMBER',
  claimed: false
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
