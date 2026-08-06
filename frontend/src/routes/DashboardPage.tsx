import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';

/**
 * Landing place once signed in. The planning features described in
 * `docs/product-concept.md` go here; for now it confirms which organisation the session
 * is scoped to.
 */
export function DashboardPage() {
  const { t } = useTranslation();
  const { account, logout } = useAuth();
  if (!account) {
    return null;
  }

  return (
    <>
      <header className="app-header">
        <div>
          <Link className="wordmark" to="/">
            {t('app.name')}
          </Link>
          <span className="organisation">{account.organisation.name}</span>
          <span className="role">{t(`dashboard.role.${account.role}`)}</span>
        </div>
        <div className="session">
          <span>{account.displayName}</span>
          <button type="button" className="link" onClick={logout}>
            {t('dashboard.signOut')}
          </button>
        </div>
      </header>

      <main className="home">
        <h1>{t('dashboard.title')}</h1>
        <p className="lede">
          {t('dashboard.body', { organisation: account.organisation.name })}
        </p>
        <p className="lede">{t('dashboard.next')}</p>
      </main>
    </>
  );
}
