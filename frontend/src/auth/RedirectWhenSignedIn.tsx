import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';
import { RestoringSession } from './RestoringSession';

type LocationState = { from?: string } | null;

/**
 * Keeps signed-in visitors off the sign-up and sign-in pages. Also the mechanism that
 * moves someone onward once they authenticate: the forms just update the session, and
 * this sends them where they were going.
 */
export function RedirectWhenSignedIn() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'restoring') {
    return <RestoringSession />;
  }
  if (status === 'authenticated') {
    const state = location.state as LocationState;
    return <Navigate to={state?.from ?? '/app'} replace />;
  }
  return <Outlet />;
}
