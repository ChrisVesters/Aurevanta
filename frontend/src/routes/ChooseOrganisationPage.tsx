import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { describeFailure } from '../i18n/problems';

/**
 * Shown to someone who has authenticated but is not acting for an organisation: either
 * they belong to several and must pick one, or they belong to none at all.
 *
 * The empty case is a real state rather than an error — it is where being removed from
 * your last organisation leaves you, with the account and password intact.
 */
export function ChooseOrganisationPage() {
  const { t } = useTranslation();
  const { memberships, selectOrganisation, logout } = useAuth();
  const [message, setMessage] = useState<string | null>(null);
  const [selecting, setSelecting] = useState(false);

  async function choose(organisationId: string) {
    setSelecting(true);
    setMessage(null);
    try {
      await selectOrganisation(organisationId);
    } catch (error) {
      setMessage(describeFailure(t, error));
      setSelecting(false);
    }
  }

  return (
    <main className="auth-screen">
      <h1>{t('chooseOrganisation.title')}</h1>

      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}

      {memberships.length === 0 ? (
        <p className="lede">{t('chooseOrganisation.none')}</p>
      ) : (
        <>
          <p className="lede">{t('chooseOrganisation.lede')}</p>
          <ul className="organisations">
            {memberships.map((membership) => (
              <li key={membership.id}>
                <button
                  type="button"
                  className="primary"
                  disabled={selecting}
                  onClick={() => void choose(membership.organisation.id)}
                >
                  {membership.organisation.name}
                </button>
                <span className="role">{t(`roles.${membership.role}`)}</span>
              </li>
            ))}
          </ul>
        </>
      )}

      <p className="switch">
        <button type="button" className="link" onClick={logout}>
          {t('app.signOut')}
        </button>
      </p>
    </main>
  );
}
