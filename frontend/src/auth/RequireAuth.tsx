import { Navigate, Outlet, useLocation } from 'react-router';
import { ChooseOrganisationPage } from '../routes/ChooseOrganisationPage';
import { useAuth } from './AuthContext';
import { RestoringSession } from './RestoringSession';

/**
 * Gate for signed-in routes. Remembers where the visitor was heading so signing in
 * returns them there rather than dumping them on a default page.
 *
 * Someone authenticated but not yet acting for an organisation gets the chooser in place
 * of the route, rather than a URL of its own: the state belongs to the session, not to an
 * address a visitor could arrive at or bookmark.
 */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'restoring') {
    return <RestoringSession />;
  }
  if (status === 'choosing' || status === 'unaffiliated') {
    return <ChooseOrganisationPage />;
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
