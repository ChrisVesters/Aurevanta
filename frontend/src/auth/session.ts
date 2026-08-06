/**
 * Where the access token lives between page loads.
 *
 * localStorage is readable by any script on the origin, so a cross-site scripting hole
 * would expose the token. That is the accepted cost of bearer tokens; if it stops being
 * acceptable, the change is to have the backend set an HttpOnly cookie instead, and this
 * module is the only place the frontend would need to follow.
 */

const TOKEN_KEY = 'aurevanta.accessToken';

export function readStoredToken(): string | null {
  try {
    return window.localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function storeToken(token: string): void {
  try {
    window.localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // Private browsing modes can refuse writes; the session then lasts until reload.
  }
}

export function clearStoredToken(): void {
  try {
    window.localStorage.removeItem(TOKEN_KEY);
  } catch {
    // Nothing to recover from: the token is already unusable to us.
  }
}
