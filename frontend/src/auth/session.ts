/**
 * Where the token lives between page loads.
 *
 * Its *kind* is stored alongside it because the two are restored differently: an access
 * token is checked against `/api/auth/me`, an identity token against `/api/memberships`.
 * Guessing wrong would cost a rejected request and a needless sign-out.
 *
 * localStorage is readable by any script on the origin, so a cross-site scripting hole
 * would expose the token. That is the accepted cost of bearer tokens; if it stops being
 * acceptable, the change is to have the backend set an HttpOnly cookie instead, and this
 * module is the only place the frontend would need to follow.
 */

const SESSION_KEY = 'aurevanta.session';

/** `identity` means an organisation has not been chosen — see `SignInResponse`. */
export type StoredSession = {
  token: string;
  kind: 'access' | 'identity';
};

export function readStoredSession(): StoredSession | null {
  try {
    const stored = window.localStorage.getItem(SESSION_KEY);
    return stored ? (JSON.parse(stored) as StoredSession) : null;
  } catch {
    // Unreadable or unparseable is the same as absent: start anonymous.
    return null;
  }
}

export function storeSession(session: StoredSession): void {
  try {
    window.localStorage.setItem(SESSION_KEY, JSON.stringify(session));
  } catch {
    // Private browsing modes can refuse writes; the session then lasts until reload.
  }
}

export function clearStoredSession(): void {
  try {
    window.localStorage.removeItem(SESSION_KEY);
  } catch {
    // Nothing to recover from: the token is already unusable to us.
  }
}
