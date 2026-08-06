import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from './AuthContext';
import { RestoringSession } from './RestoringSession';

/**
 * Gate for signed-in routes. Remembers where the visitor was heading so signing in
 * returns them there rather than dumping them on a default page.
 */
export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'restoring') {
    return <RestoringSession />;
  }
  if (status !== 'authenticated') {
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  return <Outlet />;
}
