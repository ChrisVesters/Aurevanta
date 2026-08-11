import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { apiRequest } from '../api/client';
import type { NewAccount } from '../members/types';
import { AuthContext, type AuthStatus } from './AuthContext';
import {
  clearStoredSession,
  readStoredSession,
  storeSession,
  type StoredSession
} from './session';
import type {
  Account,
  AuthenticationResponse,
  Identity,
  LoginRequest,
  Membership,
  RegistrationRequest,
  SignInResponse
} from './types';

/**
 * Holds the token and whatever it entitles the holder to.
 *
 * Signing in has three endings, because an address may belong to no, one, or several
 * organisations. Only one of them produces a session; the other two produce an identity
 * token and a choice, so this provider tracks both.
 *
 * On load a stored token is checked against the server rather than trusted, so one that
 * has expired, or whose membership is gone, drops the app back to the sign-in screen
 * instead of leaving a stale name on screen.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const session = useRef<StoredSession | null>(readStoredSession());
  const [account, setAccount] = useState<Account | null>(null);
  const [memberships, setMemberships] = useState<Membership[]>([]);
  const [status, setStatus] = useState<AuthStatus>(
    session.current ? 'restoring' : 'anonymous'
  );

  const forget = useCallback(() => {
    // Tokens are stateless, so signing out is entirely client-side: discard the token and
    // it simply expires unused.
    session.current = null;
    clearStoredSession();
    setAccount(null);
    setMemberships([]);
    setStatus('anonymous');
  }, []);

  const acceptSession = useCallback((response: AuthenticationResponse) => {
    session.current = { token: response.accessToken, kind: 'access' };
    storeSession(session.current);
    setAccount(response.account);
    setMemberships([]);
    setStatus('authenticated');
  }, []);

  const acceptIdentity = useCallback((identity: Identity) => {
    session.current = { token: identity.identityToken, kind: 'identity' };
    storeSession(session.current);
    setAccount(null);
    setMemberships(identity.memberships);
    setStatus(identity.memberships.length > 0 ? 'choosing' : 'unaffiliated');
  }, []);

  useEffect(() => {
    const stored = session.current;
    if (!stored) {
      return;
    }
    let cancelled = false;

    // Which endpoint proves the token still works depends on what kind it is: an access
    // token names an organisation, an identity token has yet to choose one.
    async function restore(from: StoredSession) {
      if (from.kind === 'access') {
        const restored = await apiRequest<Account>('/auth/me', {
          token: from.token
        });
        if (!cancelled) {
          setAccount(restored);
          setStatus('authenticated');
        }
        return;
      }
      const held = await apiRequest<Membership[]>('/memberships', {
        token: from.token
      });
      if (!cancelled) {
        setMemberships(held);
        setStatus(held.length > 0 ? 'choosing' : 'unaffiliated');
      }
    }

    restore(stored).catch(() => {
      if (!cancelled) {
        forget();
      }
    });
    return () => {
      cancelled = true;
    };
  }, [forget]);

  const register = useCallback(
    async (request: RegistrationRequest) =>
      // No session comes back, and none is established. The account exists but its address
      // is unconfirmed, and sign-in refuses those — so the visitor goes to their inbox,
      // not to the dashboard.
      apiRequest<Account>('/auth/register', { method: 'POST', body: request }),
    []
  );

  const login = useCallback(
    async (request: LoginRequest) => {
      const response = await apiRequest<SignInResponse>('/auth/login', {
        method: 'POST',
        body: request
      });
      if (response.outcome === 'SIGNED_IN') {
        acceptSession(response.session);
      } else {
        acceptIdentity(response.identity);
      }
    },
    [acceptSession, acceptIdentity]
  );

  const selectOrganisation = useCallback(
    async (organisationId: string) => {
      acceptSession(
        await apiRequest<AuthenticationResponse>(
          `/auth/tenants/${organisationId}/token`,
          { method: 'POST', token: session.current?.token }
        )
      );
    },
    [acceptSession]
  );

  const createOrganisation = useCallback(
    async (name: string, slug: string) => {
      acceptSession(
        await apiRequest<AuthenticationResponse>('/organisations', {
          method: 'POST',
          body: { name, slug },
          token: session.current?.token
        })
      );
    },
    [acceptSession]
  );

  const acceptInvitation = useCallback(
    async (token: string, credentials: NewAccount | null) => {
      const joined = await apiRequest<AuthenticationResponse>(
        `/invitations/${token}/accept`,
        {
          method: 'POST',
          // No body at all when there is nothing to send, rather than an empty one: the
          // server tells the two apart, and a `{}` is a request to make an account.
          body: credentials ?? undefined,
          token: session.current?.token
        }
      );
      acceptSession(joined);
      return joined.account.organisation.name;
    },
    [acceptSession]
  );

  const refreshAccount = useCallback(async () => {
    setAccount(
      await apiRequest<Account>('/auth/me', { token: session.current?.token })
    );
  }, []);

  const request = useCallback(
    <T,>(path: string, options: { method?: string; body?: unknown } = {}) =>
      apiRequest<T>(path, { ...options, token: session.current?.token }),
    []
  );

  const value = useMemo(
    () => ({
      status,
      account,
      memberships,
      register,
      login,
      selectOrganisation,
      createOrganisation,
      acceptInvitation,
      request,
      refreshAccount,
      logout: forget
    }),
    [
      status,
      account,
      memberships,
      register,
      login,
      selectOrganisation,
      createOrganisation,
      acceptInvitation,
      request,
      refreshAccount,
      forget
    ]
  );

  return <AuthContext value={value}>{children}</AuthContext>;
}
