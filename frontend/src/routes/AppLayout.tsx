import { useTranslation } from 'react-i18next';
import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { OrganisationSwitcher } from '../members/OrganisationSwitcher';

/**
 * The frame every signed-in page sits in: which organisation the session is scoped to,
 * a way between the pages inside it, and a way out.
 *
 * Shared rather than repeated per page, which it was until there was a second one. The
 * organisation switcher in particular has to live in exactly one place, or switching
 * would mean something different depending on where you did it.
 */
export function AppLayout() {
  const { t } = useTranslation();
  const { account, logout } = useAuth();
  // The guard above this never renders it without one; defensive so a future route
  // cannot turn a missing account into a crash.
  if (!account) {
    return null;
  }

  return (
    <>
      <header className="app-header">
        <div className="identity">
          <Link className="wordmark" to="/">
            {t('app.name')}
          </Link>
          <OrganisationSwitcher organisation={account.organisation} />
          <span className="role">{t(`roles.${account.role}`)}</span>
        </div>
        <nav aria-label={t('app.nav.label')}>
          <NavLink to="/app" end>
            {t('app.nav.overview')}
          </NavLink>
          <NavLink to="/app/projects">{t('app.nav.projects')}</NavLink>
          <NavLink to="/app/members">{t('app.nav.members')}</NavLink>
          <NavLink to="/app/resources">{t('app.nav.resources')}</NavLink>
          <NavLink to="/app/calibration">{t('app.nav.trackRecord')}</NavLink>
          {/*
            Hidden from a member rather than shown and refused, as the members page hides
            its own controls. The server enforces the same rule regardless.
          */}
          {account.role === 'OWNER' && (
            <NavLink to="/app/settings">{t('app.nav.settings')}</NavLink>
          )}
        </nav>
        <div className="session">
          <span>{account.displayName}</span>
          <button type="button" className="link" onClick={logout}>
            {t('app.signOut')}
          </button>
        </div>
      </header>
      <Outlet />
    </>
  );
}
