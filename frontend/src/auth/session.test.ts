import { describe, expect, it, vi } from 'vitest';
import { clearStoredToken, readStoredToken, storeToken } from './session';

describe('session storage', () => {
  it('has no token before one is stored', () => {
    expect(readStoredToken()).toBeNull();
  });

  it('reads back a stored token', () => {
    storeToken('a.test.token');

    expect(readStoredToken()).toBe('a.test.token');
  });

  it('forgets a cleared token', () => {
    storeToken('a.test.token');

    clearStoredToken();

    expect(readStoredToken()).toBeNull();
  });

  // Private browsing modes can refuse storage; the session should degrade, not crash.
  it('reports no token when reading throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('denied');
    });

    expect(readStoredToken()).toBeNull();
  });

  it('survives a refused write', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('denied');
    });

    expect(() => storeToken('a.test.token')).not.toThrow();
  });

  it('survives a refused removal', () => {
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('denied');
    });

    expect(() => clearStoredToken()).not.toThrow();
  });
});
