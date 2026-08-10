import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { apiRequest } from '../api/client';
import { Field } from '../auth/Field';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import {
  MAXIMUM_PASSWORD_LENGTH,
  MINIMUM_PASSWORD_LENGTH
} from '../auth/constants';
import { AuthLayout } from './AuthLayout';

/**
 * Where the link in a reset email lands.
 *
 * The token stays in the query string and is never put in a field: it is not something
 * anybody types, and an input for it would invite pasting the wrong thing. What the
 * visitor supplies is the new password, which is the only part they know.
 */
export function ResetPasswordPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  // An empty `?token=` is a link that lost its token in transit, not a token that happens
  // to be empty — treat it as absent rather than sending it to be refused.
  const token = params.get('token') || null;

  const [changed, setChanged] = useState(false);
  const [saving, setSaving] = useState(false);
  const { message, fieldErrors, report, clear } = useFormFailure(['password']);

  async function chooseNewPassword(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSaving(true);
    clear();
    try {
      await apiRequest<void>('/auth/password-reset/confirm', {
        method: 'POST',
        body: { token, password: textField(form, 'password') }
      });
      setChanged(true);
    } catch (error) {
      report(error);
      setSaving(false);
    }
  }

  if (token === null) {
    return (
      <AuthLayout>
        <h1>{t('auth.resetPassword.noToken.title')}</h1>
        <p className="lede">{t('auth.resetPassword.noToken.body')}</p>
        <p className="switch">
          <Trans
            i18nKey="auth.resetPassword.noToken.ask"
            components={{ forgot: <Link to="/forgot-password" /> }}
          />
        </p>
      </AuthLayout>
    );
  }

  if (changed) {
    return (
      <AuthLayout>
        <h1>{t('auth.resetPassword.done.title')}</h1>
        <p className="lede">{t('auth.resetPassword.done.body')}</p>
        <p className="switch">
          <Trans
            i18nKey="auth.resetPassword.done.signIn"
            components={{ signIn: <Link to="/login" /> }}
          />
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <form className="auth-form" onSubmit={chooseNewPassword} noValidate>
        <h1>{t('auth.resetPassword.title')}</h1>
        <p className="lede">{t('auth.resetPassword.body')}</p>

        {/*
          A refused token belongs to no field on this page, so it lands here rather than
          against the password input — which is exactly what useFormFailure decides.
        */}
        {message && (
          <p className="form-error" role="alert">
            {message}
          </p>
        )}

        <Field
          id="reset-password"
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

        <button type="submit" className="primary" disabled={saving}>
          {saving
            ? t('auth.resetPassword.submitting')
            : t('auth.resetPassword.submit')}
        </button>

        {/* A used or expired link is precisely when another one is needed. */}
        <p className="switch">
          <Trans
            i18nKey="auth.resetPassword.needLink"
            components={{ forgot: <Link to="/forgot-password" /> }}
          />
        </p>
      </form>
    </AuthLayout>
  );
}
