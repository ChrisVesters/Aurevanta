export type UserRole = 'OWNER' | 'MEMBER';

export type Organisation = {
  id: string;
  name: string;
  slug: string;
};

/** The signed-in user and the organisation whose data they can see. */
export type Account = {
  userId: string;
  email: string;
  displayName: string;
  role: UserRole;
  organisation: Organisation;
};

export type AuthenticationResponse = {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  account: Account;
};

export type RegistrationRequest = {
  organisationName: string;
  displayName: string;
  email: string;
  password: string;
};

export type LoginRequest = {
  email: string;
  password: string;
};
