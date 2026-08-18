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
import type {
  Contribution,
  CutOptions,
  Dependency,
  Estimate,
  Forecast,
  Project,
  WorkItem
} from '../projects/types';

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
 *
 * The three quality fields carry what the server would actually compute for these ranges,
 * rather than convenient values: 3/12 implies a middle of 6 against a stated 5, which is
 * within a quarter, and none of the three bands is tight. A fixture that claimed otherwise
 * would be a double more opinionated than the thing it stands in for.
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
    consistency: 0.833,
    inconsistent: false,
    overconfident: false,
    method: 'three_point',
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
    consistency: 1,
    inconsistent: false,
    overconfident: false,
    method: 'three_point',
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
  consistency: 1,
  inconsistent: false,
  overconfident: false,
  method: 'three_point',
  createdAt: '2026-08-13T12:00:00Z'
};

/**
 * The runbook cannot be written until the auth service has moved, which is the plan's
 * whole shape at this size — and enough for a row to have to say both ends of an arrow.
 */
export const DEPENDENCIES: Dependency[] = [
  {
    id: '30303030-3030-3030-3030-303030303030',
    predecessorItemId: WORK_ITEMS[0].id,
    successorItemId: WORK_ITEMS[1].id,
    lagHours: 0,
    createdAt: '2026-08-13T13:00:00Z'
  }
];

/**
 * One answer the engine gave, with both of M3b's assumptions answered and neither of them
 * zero — so a screen that dropped them, or that only rendered them when they happened to
 * be interesting, fails rather than passes.
 *
 * It carries a limitation for the same reason: one a plan can still earn, since the two
 * that described the engine were retired when M3b built what they named.
 */
export const FORECAST: Forecast = {
  id: '50505050-5050-5050-5050-505050505050',
  projectId: PROJECTS[0].id,
  createdAt: '2026-08-14T10:00:00Z',
  requestedById: ACCOUNT.userId,
  requestedByName: 'Ada',
  capacity: 2,
  sampleCount: 10000,
  teamFactorWorseByPercent: 30,
  scopeGrowthP10Percent: 20,
  scopeGrowthP90Percent: 60,
  // A Monday, a six-hour day and the one rule there is. All three are null together on a
  // run made before M4, and a case about that says so rather than inheriting these.
  startsOn: '2026-08-17',
  workingHoursPerDay: 6,
  calendarRule: 'five_day_week',
  // A string, because a seed is sixty-four bits and a JSON number here is a double.
  seed: '-7203484712345678901',
  // Version 2 models the shared team factor and scope growth; a run made without either
  // still says so through the limitations below, which is what M3b step 4 deletes.
  engineVersion: 2,
  priorityRule: 'most_work_waiting',
  itemCount: 2,
  estimatedItemCount: 2,
  meanHours: 31.4,
  p10Hours: 14.2,
  p50Hours: 29.5,
  p80Hours: 41.8,
  p90Hours: 52.6,
  p95Hours: 61.9,
  // The same five percentiles as days, and deliberately three different ones across the
  // 50/80/95 the control offers: a screen that showed one date whatever was chosen would
  // pass against a fixture where they agreed.
  p10Date: '2026-08-19',
  p50Date: '2026-08-21',
  p80Date: '2026-08-25',
  p90Date: '2026-08-27',
  p95Date: '2026-08-31',
  limitations: ['inconsistent_estimates'],
  histogram: { fromHours: 8.1, toHours: 96.4, counts: [1, 2, 3] }
};

/**
 * What the spread of `FORECAST` turned out to be made of, ranked and overlapping — the
 * shares add to well over one on purpose, because a screen that treated them as a partition
 * is the mistake this ranking exists to avoid.
 */
export const CONTRIBUTIONS: Contribution[] = [
  {
    kind: 'team_factor',
    itemId: null,
    title: null,
    archived: false,
    correlation: 0.82,
    shareOfSpread: 0.672
  },
  {
    kind: 'item',
    itemId: WORK_ITEMS[0].id,
    title: WORK_ITEMS[0].title,
    archived: false,
    correlation: 0.61,
    shareOfSpread: 0.372
  },
  {
    kind: 'discovered_work',
    itemId: null,
    title: null,
    archived: false,
    correlation: 0.4,
    shareOfSpread: 0.16
  },
  {
    kind: 'item',
    itemId: WORK_ITEMS[1].id,
    title: WORK_ITEMS[1].title,
    archived: false,
    correlation: 0.12,
    shareOfSpread: 0.014
  }
];

/**
 * What it would take to hit a date, against `FORECAST`.
 *
 * **The two answers deliberately disagree with each other's arithmetic**, because that is
 * the property the screen has to render honestly: the two singles buy 41 and 24 points on
 * their own, and dropping both is measured at 88 rather than at the 41 + 24 + 30 a reader
 * adding a column would arrive at. A fixture whose numbers happened to add up would let a
 * screen that summed them pass.
 */
export const CUT_OPTIONS: CutOptions = {
  targetHours: 18,
  baselineConfidence: 30.4,
  meets: false,
  simulations: 4,
  cuts: [
    {
      itemId: WORK_ITEMS[0].id,
      title: WORK_ITEMS[0].title,
      archived: false,
      confidence: 71.8,
      buys: 41.4,
      meets: false
    },
    {
      itemId: WORK_ITEMS[1].id,
      title: WORK_ITEMS[1].title,
      archived: false,
      confidence: 54.2,
      buys: 23.8,
      meets: false
    }
  ],
  together: {
    steps: [
      {
        itemId: WORK_ITEMS[0].id,
        title: WORK_ITEMS[0].title,
        archived: false,
        confidence: 71.8
      },
      {
        itemId: WORK_ITEMS[1].id,
        title: WORK_ITEMS[1].title,
        archived: false,
        confidence: 88.1
      }
    ],
    ending: 'met'
  }
};

export const INVITATION: Invitation = {
  id: '88888888-8888-8888-8888-888888888888',
  email: 'dave@elsewhere.test',
  role: 'MEMBER',
  status: 'PENDING',
  // Relative to now rather than written down, because a fixture with a date in it is a
  // fixture that expires: this one said 2026-08-18 and the suite went red on the morning of
  // the eighteenth. The case that wants an expired invitation overrides it with one.
  expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000).toISOString(),
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
