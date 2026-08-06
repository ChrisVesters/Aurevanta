import { useState } from 'react';
import type { FormEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router';
import { describeFailure } from '../i18n/problems';
import { useAuth } from './AuthContext';
import { Field } from './Field';
import { textField } from './formValues';
import { MAXIMUM_EMAIL_LENGTH, MAXIMUM_PASSWORD_LENGTH } from './constants';

export function LoginForm() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setMessage(null);
    try {
      await login({
        email: textField(form, 'email'),
        password: textField(form, 'password')
      });
    } catch (error) {
      setMessage(describeFailure(t, error));
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <h1>{t('auth.login.title')}</h1>

      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}

      <Field
        id="login-email"
        name="email"
        type="email"
        label={t('auth.fields.email.label')}
        autoComplete="email"
        required
        maxLength={MAXIMUM_EMAIL_LENGTH}
      />
      <Field
        id="login-password"
        name="password"
        type="password"
        label={t('auth.fields.password.label')}
        autoComplete="current-password"
        required
        maxLength={MAXIMUM_PASSWORD_LENGTH}
      />

      <button type="submit" className="primary" disabled={submitting}>
        {submitting ? t('auth.login.submitting') : t('auth.login.submit')}
      </button>

      <p className="switch">
        <Trans
          i18nKey="auth.login.noAccount"
          components={{
            register: <Link to="/register" state={location.state} />
          }}
        />
      </p>
    </form>
  );
}
