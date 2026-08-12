import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../auth/Field';
import { MAXIMUM_NAME_LENGTH } from '../auth/constants';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { ApiError } from '../api/client';
import { SLUG_TAKEN, SlugField } from '../tenant/SlugField';
import { sharedNames } from '../tenant/sharedNames';
import { useProposedSlug } from '../tenant/useProposedSlug';

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
    code,
    fieldErrors,
    report,
    clear
  } = useFormFailure(['name', 'slug']);
  const handle = useProposedSlug();
  // Two organisations may share a name since M1a; the handle is what tells them apart,
  // and only where they actually do.
  const sharesName = sharedNames(memberships);

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
      await createOrganisation(textField(values, 'name'), handle.slug);
    } catch (error) {
      report(error);
      // The refusal arrives holding a free handle; taking it up is what the visitor
      // would otherwise do by hand.
      if (error instanceof ApiError && error.suggested) {
        handle.choose(error.suggested);
      }
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

            {/* Quiet while the handle field is saying it; see SLUG_TAKEN. */}
            {formMessage && code !== SLUG_TAKEN && (
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
              onChange={(event) => handle.followName(event.target.value)}
            />
            <SlugField
              id="slug"
              value={handle.slug}
              onChange={handle.choose}
              error={fieldErrors.slug}
              taken={code === SLUG_TAKEN}
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
                  {/*
                    Inside the button rather than beside it: two buttons that read the
                    same are two buttons anybody navigating by their names cannot choose
                    between, which is the same problem one layer down.

                    The space is deliberate and load-bearing. An accessible name is the
                    button's text run together, so without it the two lines are announced
                    as one word — which would leave the handle no easier to hear than the
                    name it is there to disambiguate. It is invisible on screen, where the
                    two are separate rows of a column.
                  */}
                  {sharesName(membership.organisation) && (
                    <>
                      {' '}
                      <span className="handle">
                        {membership.organisation.slug}
                      </span>
                    </>
                  )}
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
