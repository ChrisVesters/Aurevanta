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
  register: (request: RegistrationRequest) => Promise<void>;
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
