import { useState } from 'react';
import type { FormEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router';
import { describeFailure, describeFieldErrors } from '../i18n/problems';
import { useAuth } from './AuthContext';
import { Field } from './Field';
import { textField } from './formValues';
import {
  MAXIMUM_EMAIL_LENGTH,
  MAXIMUM_NAME_LENGTH,
  MAXIMUM_PASSWORD_LENGTH,
  MINIMUM_PASSWORD_LENGTH
} from './constants';

export function RegisterForm() {
  const { t } = useTranslation();
  const { register } = useAuth();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setMessage(null);
    setFieldErrors({});
    try {
      await register({
        organisationName: textField(form, 'organisationName'),
        displayName: textField(form, 'displayName'),
        email: textField(form, 'email'),
        password: textField(form, 'password')
      });
      // On success the session changes and RedirectWhenSignedIn navigates onward, so
      // this component is unmounted; leave `submitting` set rather than flicker.
    } catch (error) {
      const perField = describeFieldErrors(t, error);
      setFieldErrors(perField);
      // A field-level complaint is already shown against its input; only summarise
      // failures that belong to the form as a whole.
      setMessage(
        Object.keys(perField).length > 0 ? null : describeFailure(t, error)
      );
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <h1>{t('auth.register.title')}</h1>
      <p className="lede">{t('auth.register.lede')}</p>

      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
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
