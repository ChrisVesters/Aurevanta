import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';
import { RestoringSession } from './RestoringSession';

type LocationState = { from?: string } | null;

/**
 * Keeps signed-in visitors off the sign-up and sign-in pages. Also the mechanism that
 * moves someone onward once they authenticate: the forms just update the session, and
 * this sends them where they were going.
 *
 * Anyone who has authenticated counts, including someone who has yet to choose an
 * organisation — leaving them on the sign-in form would only invite them to sign in
 * again, which is not what they are missing.
 */
export function RedirectWhenSignedIn() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'restoring') {
    return <RestoringSession />;
  }
  if (status !== 'anonymous') {
    const state = location.state as LocationState;
    return <Navigate to={state?.from ?? '/app'} replace />;
  }
  return <Outlet />;
}
