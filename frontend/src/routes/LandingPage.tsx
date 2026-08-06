import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';

/**
 * Public front page. Describes the approach set out in `docs/product-concept.md`; the
 * planning features themselves are still to be built.
 */
export function LandingPage() {
  const { t } = useTranslation();
  // Keyed off the account rather than the status, so the signed-in branches never have
  // to cope with an absent name.
  const { account } = useAuth();

  return (
    <div className="landing">
      <header className="site-header">
        <span className="wordmark">{t('app.name')}</span>
        <nav aria-label={t('landing.nav.label')}>
          {account ? (
            <Link className="button-link" to="/app">
              {t('landing.nav.openApp')}
            </Link>
          ) : (
            <>
              <Link to="/login">{t('landing.nav.signIn')}</Link>
              <Link className="button-link" to="/register">
                {t('landing.nav.getStarted')}
              </Link>
            </>
          )}
        </nav>
      </header>

      <main>
        <section className="hero">
          <h1>{t('landing.hero.title')}</h1>
          <p className="lede">{t('landing.hero.lede')}</p>
          <p className="cta-row">
            {account ? (
              <Link className="button-link primary" to="/app">
                {t('landing.hero.continueAs', { name: account.displayName })}
              </Link>
            ) : (
              <>
                <Link className="button-link primary" to="/register">
                  {t('landing.hero.createOrganisation')}
                </Link>
                <Link className="button-link" to="/login">
                  {t('landing.nav.signIn')}
                </Link>
              </>
            )}
          </p>
        </section>

        <section className="principle" aria-labelledby="principle-heading">
          <h2 id="principle-heading">{t('landing.principle.title')}</h2>
          <p>{t('landing.principle.body1')}</p>
          <p>{t('landing.principle.body2')}</p>
        </section>

        <section className="features" aria-labelledby="features-heading">
          <h2 id="features-heading">{t('landing.features.title')}</h2>
          <ul>
            <li>
              <h3>{t('landing.features.date.title')}</h3>
              <p>{t('landing.features.date.body')}</p>
            </li>
            <li>
              <h3>{t('landing.features.variance.title')}</h3>
              <p>{t('landing.features.variance.body')}</p>
            </li>
            <li>
              <h3>{t('landing.features.calibration.title')}</h3>
              <p>{t('landing.features.calibration.body')}</p>
            </li>
          </ul>
        </section>
      </main>
    </div>
  );
}
