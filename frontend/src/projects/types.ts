/**
 * One plan the organisation holds.
 *
 * Names no organisation, because there is only one it could name: the session is scoped to
 * it, and the server took it from the token rather than from anything on screen.
 */
export type Project = {
  id: string;
  name: string;
  /** Null when nothing has been said about it, which is every project on its first day. */
  description: string | null;
  createdAt: string;
  /** Null while it is in use. Nothing is ever deleted, so this is how one leaves a list. */
  archivedAt: string | null;
};
