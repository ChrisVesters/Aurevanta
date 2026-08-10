import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { apiRequest } from '../api/client';
import { Field } from '../auth/Field';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import { MAXIMUM_EMAIL_LENGTH } from '../auth/constants';
import { AuthLayout } from './AuthLayout';

/**
 * Where somebody who cannot get in asks for a way back.
 *
 * Public, and it has to be: under the verification gate this is the only route back into
 * an account whose confirmation message never arrived, so requiring a session would make
 * it useless to exactly the people it exists for.
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [requested, setRequested] = useState(false);
  const [sending, setSending] = useState(false);
  const { message, fieldErrors, report, clear } = useFormFailure(['email']);

  async function requestReset(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSending(true);
    clear();
    try {
      await apiRequest<void>('/auth/password-reset', {
        method: 'POST',
        body: { email: textField(form, 'email') }
      });
      setRequested(true);
    } catch (error) {
      report(error);
      setSending(false);
    }
  }

  return (
    <AuthLayout>
      <h1>{t('auth.forgotPassword.title')}</h1>

      {requested ? (
        // Deliberately non-committal, matching a server that answers identically whether
        // or not the address has an account: saying more would disclose who is registered.
        <p className="lede" role="status">
          {t('auth.forgotPassword.requested')}
        </p>
      ) : (
        <form className="auth-form" onSubmit={requestReset} noValidate>
          <p className="lede">{t('auth.forgotPassword.body')}</p>

          {message && (
            <p className="form-error" role="alert">
              {message}
            </p>
          )}

          <Field
            id="forgot-password-email"
            name="email"
            type="email"
            label={t('auth.fields.email.label')}
            autoComplete="email"
            required
            maxLength={MAXIMUM_EMAIL_LENGTH}
            error={fieldErrors.email}
          />

          <button type="submit" className="primary" disabled={sending}>
            {sending
              ? t('auth.forgotPassword.submitting')
              : t('auth.forgotPassword.submit')}
          </button>
        </form>
      )}

      <p className="switch">
        <Trans
          i18nKey="auth.forgotPassword.signIn"
          components={{ signIn: <Link to="/login" /> }}
        />
      </p>
    </AuthLayout>
  );
}
