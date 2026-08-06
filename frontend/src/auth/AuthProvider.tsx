import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { apiRequest } from '../api/client';
import { AuthContext, type AuthStatus } from './AuthContext';
import { clearStoredToken, readStoredToken, storeToken } from './session';
import type {
  Account,
  AuthenticationResponse,
  LoginRequest,
  RegistrationRequest
} from './types';

/**
 * Holds the access token and the account it belongs to.
 *
 * On load a stored token is checked against `/api/auth/me` rather than trusted, so a
 * token that has expired, or whose account is gone, drops the app back to the sign-in
 * screen instead of leaving a stale name on screen.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const token = useRef<string | null>(readStoredToken());
  const [account, setAccount] = useState<Account | null>(null);
  const [status, setStatus] = useState<AuthStatus>(
    token.current ? 'restoring' : 'anonymous'
  );

  useEffect(() => {
    if (!token.current) {
      return;
    }
    let cancelled = false;
    apiRequest<Account>('/auth/me', { token: token.current })
      .then((restored) => {
        if (!cancelled) {
          setAccount(restored);
          setStatus('authenticated');
        }
      })
      .catch(() => {
        if (!cancelled) {
          token.current = null;
          clearStoredToken();
          setAccount(null);
          setStatus('anonymous');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const accept = useCallback((response: AuthenticationResponse) => {
    token.current = response.accessToken;
    storeToken(response.accessToken);
    setAccount(response.account);
    setStatus('authenticated');
  }, []);

  const register = useCallback(
    async (request: RegistrationRequest) => {
      accept(
        await apiRequest<AuthenticationResponse>('/auth/register', {
          method: 'POST',
          body: request
        })
      );
    },
    [accept]
  );

  const login = useCallback(
    async (request: LoginRequest) => {
      accept(
        await apiRequest<AuthenticationResponse>('/auth/login', {
          method: 'POST',
          body: request
        })
      );
    },
    [accept]
  );

  const logout = useCallback(() => {
    // Tokens are stateless, so signing out is entirely client-side: discard the token and
    // it simply expires unused.
    token.current = null;
    clearStoredToken();
    setAccount(null);
    setStatus('anonymous');
  }, []);

  const value = useMemo(
    () => ({ status, account, register, login, logout }),
    [status, account, register, login, logout]
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
