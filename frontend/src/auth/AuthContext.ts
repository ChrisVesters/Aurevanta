import { createContext, useContext } from 'react';
import type { Account, LoginRequest, RegistrationRequest } from './types';

export type AuthStatus = 'restoring' | 'anonymous' | 'authenticated';

export type AuthContextValue = {
  status: AuthStatus;
  account: Account | null;
  register: (request: RegistrationRequest) => Promise<void>;
  login: (request: LoginRequest) => Promise<void>;
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
