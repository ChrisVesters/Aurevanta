import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router';
import { apiRequest } from '../api/client';
import { useAuth } from './AuthContext';
import { Field } from './Field';
import { textField } from './formValues';
import { useFormFailure } from './useFormFailure';
import { MAXIMUM_EMAIL_LENGTH, MAXIMUM_PASSWORD_LENGTH } from './constants';

/** What the gate answers with when the address was never confirmed. */
const NOT_VERIFIED = 'email_not_verified';

export function LoginForm() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const location = useLocation();
  const [submitting, setSubmitting] = useState(false);
  const { message, code, fieldErrors, report, clear } = useFormFailure([
    'email',
    'password'
  ]);

  // The address that was refused, kept so asking for a link does not mean typing it again.
  // Deliberately the address as submitted rather than whatever the input holds now: the
  // message on screen is about that one.
  const [refusedAddress, setRefusedAddress] = useState<string | null>(null);
  const [linkRequested, setLinkRequested] = useState(false);
  const [sendingLink, setSendingLink] = useState(false);
  // Its own failure state, so a resend that fails cannot overwrite the banner explaining
  // why the visitor is being offered a resend at all.
  const resendFailure = useFormFailure(['email']);

  async function handleSubmit(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const email = textField(form, 'email');
    setSubmitting(true);
    clear();
    resendFailure.clear();
    setLinkRequested(false);
    try {
      await login({ email, password: textField(form, 'password') });
    } catch (error) {
      report(error);
      setRefusedAddress(email);
      setSubmitting(false);
    }
  }

  async function sendAnotherLink() {
    setSendingLink(true);
    resendFailure.clear();
    try {
      await apiRequest<void>('/auth/verify-email/resend', {
        method: 'POST',
        body: { email: refusedAddress }
      });
      setLinkRequested(true);
    } catch (error) {
      resendFailure.report(error);
    }
    // Reset on both paths. On the success path the button is replaced by the
    // acknowledgement and looks finished either way — but signing in again brings it back,
    // and it would return still reading "Sending…" and still disabled.
    setSendingLink(false);
  }

  const refusedByTheGate = code === NOT_VERIFIED;

  return (
    <form className="auth-form" onSubmit={handleSubmit} noValidate>
      <h1>{t('auth.login.title')}</h1>

      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}

      {/*
        The rescue, offered where the refusal happened. Sending someone to another page to
        retype an address they have just typed is how a lost confirmation email turns into
        an abandoned account.
      */}
      {refusedByTheGate &&
        (linkRequested ? (
          // As non-committal as the server's own answer, which is identical whether or not
          // the address has an account.
          <p className="lede" role="status">
            {t('auth.login.resend.requested')}
          </p>
        ) : (
          <>
            {resendFailure.message && (
              <p className="form-error" role="alert">
                {resendFailure.message}
              </p>
            )}
            <button
              type="button"
              className="secondary"
              onClick={sendAnotherLink}
              disabled={sendingLink}
            >
              {sendingLink
                ? t('auth.login.resend.submitting')
                : t('auth.login.resend.submit')}
            </button>
          </>
        ))}

      <Field
        id="login-email"
        name="email"
        type="email"
        label={t('auth.fields.email.label')}
        autoComplete="email"
        required
        maxLength={MAXIMUM_EMAIL_LENGTH}
        error={fieldErrors.email ?? resendFailure.fieldErrors.email}
      />
      <Field
        id="login-password"
        name="password"
        type="password"
        label={t('auth.fields.password.label')}
        autoComplete="current-password"
        required
        maxLength={MAXIMUM_PASSWORD_LENGTH}
        error={fieldErrors.password}
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
      {/*
        For somebody who knows the link never arrived and has not tried to sign in. Hidden
        once the resend is offered in place, so there are not two ways to ask on screen.
      */}
      {!refusedByTheGate && (
        <p className="switch">
          <Trans
            i18nKey="auth.login.needLink"
            components={{ verify: <Link to="/verify-email" /> }}
          />
        </p>
      )}
      <p className="switch">
        <Trans
          i18nKey="auth.login.forgotPassword"
          components={{ reset: <Link to="/forgot-password" /> }}
        />
      </p>
    </form>
  );
}
