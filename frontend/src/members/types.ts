import type { UserRole } from '../auth/types';

/** One person in the organisation the session is scoped to, as colleagues see them. */
export type Member = {
  /** Names the membership, not the person: what a role change or a removal addresses. */
  id: string;
  userId: string;
  displayName: string;
  email: string;
  role: UserRole;
  joinedAt: string;
};

/**
 * An invitation an owner has sent and nobody has acted on yet.
 *
 * Carries no token, and cannot: the server kept only a hash of it. An owner who wants the
 * invitee to have another link asks for one to be sent rather than reading one out of here.
 */
export type Invitation = {
  id: string;
  email: string;
  role: UserRole;
  status: 'PENDING' | 'ACCEPTED' | 'REVOKED';
  expiresAt: string;
  createdAt: string;
};

/**
 * What somebody holding an invitation link is shown before acting on it.
 *
 * Four fields, and the server sends no more: this is served without credentials to
 * anyone who has the link, so it says who is asking and what they are being asked to
 * join, and nothing a member would have had to sign in to see.
 */
export type InvitationPreview = {
  organisationName: string;
  invitedBy: string;
  role: UserRole;
  /**
   * Whether an account already holds the invited address — the one thing said about it,
   * and the only way this page can ask the right thing of a visitor who is not signed
   * in. The alternative is guessing from the visitor's own session, which answers a
   * different question and gets this one wrong.
   */
  claimed: boolean;
};

/** What accepting needs from somebody who has no account yet. */
export type NewAccount = {
  displayName: string;
  password: string;
};
