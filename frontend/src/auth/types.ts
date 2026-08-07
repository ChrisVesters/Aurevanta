export type UserRole = 'OWNER' | 'MEMBER';

export type Organisation = {
  id: string;
  name: string;
  slug: string;
};

/** One organisation the signed-in person belongs to, and their role in it. */
export type Membership = {
  id: string;
  role: UserRole;
  organisation: Organisation;
  /** ISO timestamp of when this organisation was last chosen, or null if never. */
  lastAccessedAt: string | null;
};

/** The signed-in user and the organisation whose data they can see. */
export type Account = {
  userId: string;
  email: string;
  displayName: string;
  role: UserRole;
  organisation: Organisation;
};

/** A session scoped to one organisation. */
export type AuthenticationResponse = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  account: Account;
};

/**
 * A token that names the person but no organisation, plus the organisations it may be
 * exchanged for. `memberships` is empty for someone who belongs to none.
 */
export type Identity = {
  identityToken: string;
  tokenType: string;
  expiresInSeconds: number;
  memberships: Membership[];
};

/**
 * Signing in has three endings, because one address may hold no, one, or several
 * memberships. `outcome` says which, and only the matching field is present.
 */
export type SignInResponse =
  | { outcome: 'SIGNED_IN'; session: AuthenticationResponse }
  | { outcome: 'CHOOSE_ORGANISATION'; identity: Identity }
  | { outcome: 'NO_ORGANISATION'; identity: Identity };

export type RegistrationRequest = {
  organisationName: string;
  displayName: string;
  email: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};
