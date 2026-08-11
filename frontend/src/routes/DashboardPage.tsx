import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';

/**
 * Landing place once signed in. The planning features described in
 * `docs/product-concept.md` go here; for now it confirms which organisation the session
 * is scoped to.
 *
 * The header around it belongs to {@link AppLayout}, which every signed-in page shares.
 */
export function DashboardPage() {
  const { t } = useTranslation();
  const { account } = useAuth();
  if (!account) {
    return null;
  }

  return (
    <main className="home">
      <h1>{t('dashboard.title')}</h1>
      <p className="lede">
        {t('dashboard.body', { organisation: account.organisation.name })}
      </p>
      <p className="lede">{t('dashboard.next')}</p>
    </main>
  );
}
