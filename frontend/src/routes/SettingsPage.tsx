import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../auth/Field';
import { MAXIMUM_NAME_LENGTH } from '../auth/constants';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import type { Organisation } from '../auth/types';
import { SLUG_TAKEN, SlugField } from '../tenant/SlugField';

/**
 * What an owner may change about their organisation: what it is called, and what it
 * answers to.
 *
 * The handle does not follow the name here, and nothing proposes one — it already has an
 * owner, and moving it under them because they fixed a typo in the name is exactly the
 * bug `useProposedSlug` exists to prevent on the forms that create one.
 */
export function SettingsPage() {
  const { t } = useTranslation();
  const { account } = useAuth();

  if (!account) {
    return null;
  }

  // Hidden from a member rather than disabled, as the members page hides its own
  // controls — and refused by the server either way, which is what actually decides it.
  if (account.role !== 'OWNER') {
    return (
      <main className="members">
        <h1>{t('settings.title')}</h1>
        <p className="lede">{t('settings.ownersOnly')}</p>
      </main>
    );
  }

  return (
    <main className="members">
      <h1>{t('settings.title')}</h1>
      <p className="lede">{t('settings.lede')}</p>
      <OrganisationForm organisation={account.organisation} />
    </main>
  );
}

/**
 * Split out so that it mounts with an organisation rather than before one.
 *
 * The handle field starts at the handle the organisation has, and a component that
 * rendered first and learned second would have to reach for an effect to catch up — one
 * that would then have to be careful not to overwrite whatever had been typed since.
 * Mounting once there is something to start from removes the question.
 */
function OrganisationForm({ organisation }: { organisation: Organisation }) {
  const { t } = useTranslation();
  const { request, refreshAccount } = useAuth();
  const [slug, setSlug] = useState(organisation.slug);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState(false);
  const { message, code, fieldErrors, report, clear } = useFormFailure([
    'name',
    'slug'
  ]);

  async function save(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setSaving(true);
    setSaved(false);
    clear();
    try {
      await request<Organisation>('/organisations', {
        method: 'PATCH',
        body: { name: textField(values, 'name'), slug }
      });
      // The header names the organisation, and the session is where it reads that from.
      await refreshAccount();
      setSaved(true);
    } catch (error) {
      report(error);
      if (error instanceof ApiError && error.suggested) {
        setSlug(error.suggested);
      }
    }
    setSaving(false);
  }

  return (
    <form
      className="invite-form"
      onSubmit={(event) => void save(event)}
      noValidate
    >
      {/* Quiet while the handle field is saying it; see SLUG_TAKEN. */}
      {message && code !== SLUG_TAKEN && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {saved && (
        <p className="notice" role="status">
          {t('settings.saved')}
        </p>
      )}

      <Field
        id="name"
        name="name"
        defaultValue={organisation.name}
        label={t('auth.fields.organisationName.label')}
        autoComplete="organization"
        required
        maxLength={MAXIMUM_NAME_LENGTH}
        error={fieldErrors.name}
      />
      <SlugField
        id="slug"
        value={slug}
        onChange={setSlug}
        error={fieldErrors.slug}
        taken={code === SLUG_TAKEN}
      />

      {/*
          Said before it saves rather than after, because after is too late: nothing
          redirects from a handle that has moved, so every link anybody holds to the old
          one stops working the moment this is submitted.
        */}
      {slug !== organisation.slug && (
        <p className="notice" role="status">
          {t('settings.handleMoves', { from: organisation.slug })}
        </p>
      )}

      <button type="submit" className="primary" disabled={saving}>
        {saving ? t('settings.submitting') : t('settings.submit')}
      </button>
    </form>
  );
}
