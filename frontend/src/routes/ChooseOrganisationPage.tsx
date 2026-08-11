import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../auth/Field';
import { MAXIMUM_NAME_LENGTH } from '../auth/constants';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';

/**
 * Shown to someone who has authenticated but is not acting for an organisation: either
 * they belong to several and must pick one, or they belong to none at all.
 *
 * The empty case is a real state rather than an error — it is where being removed from
 * your last organisation leaves you, with the account and password intact. It offers a
 * way to start one, because the alternative is waiting for somebody else to send an
 * invitation, and waiting is not something a person can do for themselves.
 */
export function ChooseOrganisationPage() {
  const { t } = useTranslation();
  const { memberships, selectOrganisation, createOrganisation, logout } =
    useAuth();
  const [message, setMessage] = useState<string | null>(null);
  const [selecting, setSelecting] = useState(false);
  const [creating, setCreating] = useState(false);
  const {
    message: formMessage,
    fieldErrors,
    report,
    clear
  } = useFormFailure(['name']);

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

  async function startOne(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setCreating(true);
    clear();
    try {
      await createOrganisation(textField(values, 'name'));
    } catch (error) {
      report(error);
      setCreating(false);
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
        <>
          <p className="lede">{t('chooseOrganisation.none')}</p>

          <form
            className="auth-form"
            onSubmit={(event) => void startOne(event)}
            noValidate
          >
            <h2>{t('chooseOrganisation.start.title')}</h2>
            <p className="lede">{t('chooseOrganisation.start.body')}</p>

            {formMessage && (
              <p className="form-error" role="alert">
                {formMessage}
              </p>
            )}

            <Field
              id="new-organisation-name"
              name="name"
              label={t('auth.fields.organisationName.label')}
              autoComplete="organization"
              required
              maxLength={MAXIMUM_NAME_LENGTH}
              error={fieldErrors.name}
            />

            <button type="submit" className="primary" disabled={creating}>
              {creating
                ? t('chooseOrganisation.start.submitting')
                : t('chooseOrganisation.start.submit')}
            </button>
          </form>
        </>
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
