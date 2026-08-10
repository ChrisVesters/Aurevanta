import { createContext, useContext } from 'react';
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
  logout: () => void;
};

export const AuthContext = createContext<AuthContextValue | null>(null);

export function useAuth(): AuthContextValue {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return value;
}
