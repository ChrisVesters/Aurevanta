import { describe, expect, it, vi } from 'vitest';
import { clearStoredSession, readStoredSession, storeSession } from './session';

describe('session storage', () => {
  it('has no session before one is stored', () => {
    expect(readStoredSession()).toBeNull();
  });

  it('reads back a stored access token', () => {
    storeSession({ token: 'a.test.token', kind: 'access' });

    expect(readStoredSession()).toEqual({
      token: 'a.test.token',
      kind: 'access'
    });
  });

  // The kind is stored because it decides which endpoint restores the session.
  it('reads back a stored identity token as such', () => {
    storeSession({ token: 'an.identity.token', kind: 'identity' });

    expect(readStoredSession()?.kind).toBe('identity');
  });

  it('forgets a cleared session', () => {
    storeSession({ token: 'a.test.token', kind: 'access' });

    clearStoredSession();

    expect(readStoredSession()).toBeNull();
  });

  // Private browsing modes can refuse storage; the session should degrade, not crash.
  it('reports no session when reading throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });

    expect(readStoredSession()).toBeNull();
  });

  // Anything left by an older version of the app is unusable, not a crash.
  it('reports no session when the stored value is not a session', () => {
    window.localStorage.setItem('aurevanta.session', 'a.bare.token');

    expect(readStoredSession()).toBeNull();
  });

  it('survives a refused write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });

    expect(() =>
      storeSession({ token: 'a.test.token', kind: 'access' })
    ).not.toThrow();
  });

  it('survives a refused removal', () => {
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('denied');
    });

    expect(() => clearStoredSession()).not.toThrow();
  });
});
