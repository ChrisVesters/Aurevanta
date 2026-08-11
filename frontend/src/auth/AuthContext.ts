import { createContext, useContext } from 'react';
import type { NewAccount } from '../members/types';
import type {
  Account,
  LoginRequest,
  Membership,
  RegistrationRequest
} from './types';

/**
 * `choosing` and `unaffiliated` are both "authenticated, but not acting for any
 * organisation": the first has some to pick from, the second has none. They are kept
 * apart because they need different words on screen, not different guards.
 */
export type AuthStatus =
  'restoring' | 'anonymous' | 'authenticated' | 'choosing' | 'unaffiliated';

export type AuthContextValue = {
  status: AuthStatus;
  account: Account | null;
  /** The organisations to choose between; empty unless `status` is `choosing`. */
  memberships: Membership[];
  /**
   * Creates the account and returns it. Deliberately does *not* sign anybody in: the
   * address has not been confirmed yet, and an unconfirmed one is refused a token.
   */
  register: (request: RegistrationRequest) => Promise<Account>;
  login: (request: LoginRequest) => Promise<void>;
  /** Trades the identity token for one scoped to the chosen organisation. */
  selectOrganisation: (organisationId: string) => Promise<void>;
  /**
   * Starts an organisation, with the caller as its owner, and switches into it.
   *
   * The way out of belonging to nothing, which is why it is reachable while holding an
   * identity token: somebody in that state has no organisation for a session to name.
   */
  createOrganisation: (name: string) => Promise<void>;
  /**
   * Redeems an invitation and takes up the session it hands back.
   *
   * `credentials` belong to one of the two ways through: somebody with no account yet
   * sends a name and a password, and somebody who already has one signs in first and
   * sends nothing. Which applies is decided by the server, from the invited address.
   */
  acceptInvitation: (
    token: string,
    credentials: NewAccount | null
  ) => Promise<string>;
  /**
   * Re-reads the account behind the current session.
   *
   * The role in an access token was true when it was issued and stays there for twelve
   * hours, while the server reads the membership back on every request. Anything that
   * causes its own role to change has to ask, or it goes on offering what it may no
   * longer do.
   */
  refreshAccount: () => Promise<void>;
  logout: () => void;
  /**
   * Calls the API as whoever is signed in.
   *
   * Here rather than on `apiRequest` so that the token stays inside this provider: a
   * component that had to be handed one in order to ask for anything would be a component
   * that could store it, log it, or pass it somewhere it does not belong.
   */
  request: <T>(
    path: string,
    options?: { method?: string; body?: unknown }
  ) => Promise<T>;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return value;
}
