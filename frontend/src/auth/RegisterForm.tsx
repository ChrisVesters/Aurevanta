import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router';
import { useAuth } from './AuthContext';
import { Field } from './Field';
import { textField } from './formValues';
import { ResendConfirmation } from './ResendConfirmation';
import { useFormFailure } from './useFormFailure';
import { SLUG_TAKEN, SlugField } from '../tenant/SlugField';
import { useProposedSlug } from '../tenant/useProposedSlug';
import {
  MAXIMUM_EMAIL_LENGTH,
  MAXIMUM_NAME_LENGTH,
  MAXIMUM_PASSWORD_LENGTH,
  MINIMUM_PASSWORD_LENGTH
} from './constants';

/** What the server answers when the address is taken. */
const ALREADY_REGISTERED = 'email_already_registered';

export function RegisterForm() {
  const { t } = useTranslation();
  const { register } = useAuth();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);
  const [confirmationSentTo, setConfirmationSentTo] = useState<string | null>(
    null
  );
  const [attemptedEmail, setAttemptedEmail] = useState<string | null>(null);
  const { message, code, fieldErrors, report, clear } = useFormFailure([
    'organisationName',
    'organisationSlug',
    'displayName',
    'email',
    'password'
  ]);
  const handle = useProposedSlug();

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    clear();
    try {
      const email = textField(form, 'email');
      setAttemptedEmail(email);
      const account = await register({
        organisationName: textField(form, 'organisationName'),
        organisationSlug: handle.slug,
        displayName: textField(form, 'displayName'),
        email,
        password: textField(form, 'password')
      });
      // Registering no longer signs anybody in, so nothing navigates: the account exists
      // but cannot be used until the address is confirmed. Say so, and say where.
      setConfirmationSentTo(account.email);
    } catch (error) {
      report(error);
      // A refused handle usually arrives holding a free one. Taking it up is what the
      // visitor would otherwise do by hand.
      handle.takeSuggestion(error);
      setSubmitting(false);
    }
  }

  if (confirmationSentTo) {
    return (
      <div className="auth-form">
        <h1>{t('auth.register.checkEmail.title')}</h1>
        <p className="lede">
          {t('auth.register.checkEmail.body', { email: confirmationSentTo })}
        </p>
        <p className="lede">{t('auth.register.checkEmail.nothingYet')}</p>
        {/*
          Where a message that never arrives is first noticed. Without this the only way
          on is to attempt a sign-in that is certain to be refused, and read the way out
          of that refusal.
        */}
        <p className="switch">
          <Trans
            i18nKey="auth.register.checkEmail.needLink"
            components={{ verify: <Link to="/verify-email" /> }}
          />
        </p>
        <p className="switch">
          <Trans
            i18nKey="auth.register.checkEmail.signIn"
            components={{ signIn: <Link to="/login" /> }}
          />
        </p>
      </div>
    );
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <h1>{t('auth.register.title')}</h1>
      <p className="lede">{t('auth.register.lede')}</p>

      {/* Quiet while the handle field is saying it; see SLUG_TAKEN. */}
      {message && code !== SLUG_TAKEN && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}

      {/*
        "That address is already registered" is true and useless on its own: the likeliest
        person to see it is somebody whose confirmation link never arrived, trying again
        because signing in does not work either. Offering the link here says nothing about
        whether the account is confirmed — the message only ever goes to that address.
      */}
      {code === ALREADY_REGISTERED && attemptedEmail && (
        <>
          <p className="lede">{t('auth.register.alreadyRegistered')}</p>
          <ResendConfirmation email={attemptedEmail} />
        </>
      )}

      <Field
        id="organisationName"
        name="organisationName"
        label={t('auth.fields.organisationName.label')}
        autoComplete="organization"
        required
        maxLength={MAXIMUM_NAME_LENGTH}
        error={fieldErrors.organisationName}
        hint={t('auth.fields.organisationName.hint')}
        onChange={(event) => handle.followName(event.target.value)}
      />
      <SlugField
        id="organisationSlug"
        value={handle.slug}
        onChange={handle.choose}
        error={fieldErrors.organisationSlug}
        taken={code === SLUG_TAKEN}
        suggested={handle.suggested}
      />
      <Field
        id="displayName"
        name="displayName"
        label={t('auth.fields.displayName.label')}
        autoComplete="name"
        required
        maxLength={MAXIMUM_NAME_LENGTH}
        error={fieldErrors.displayName}
      />
      <Field
        id="email"
        name="email"
        type="email"
        label={t('auth.fields.email.label')}
        autoComplete="email"
        required
        maxLength={MAXIMUM_EMAIL_LENGTH}
        error={fieldErrors.email}
      />
      <Field
        id="password"
        name="password"
        type="password"
        label={t('auth.fields.password.label')}
        autoComplete="new-password"
        required
        minLength={MINIMUM_PASSWORD_LENGTH}
        maxLength={MAXIMUM_PASSWORD_LENGTH}
        error={fieldErrors.password}
        hint={t('auth.fields.password.hint', {
          count: MINIMUM_PASSWORD_LENGTH
        })}
      />

      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? t('auth.register.submitting') : t('auth.register.submit')}
      </button>

      <p className="switch">
        <Trans
          i18nKey="auth.register.haveAccount"
          components={{
            signIn: <Link to="/login" state={location.state} />
          }}
        />
      </p>
    </form>
  );
}
