import { useEffect, useState } from 'react';
import type { SubmitEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { apiRequest } from '../api/client';
import { Field } from '../auth/Field';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import { MAXIMUM_EMAIL_LENGTH } from '../auth/constants';
import { describeFailure } from '../i18n/problems';

/**
 * Where the link in a confirmation email lands.
 *
 * Also reachable without a token, which is the point: a link that has expired or been used
 * is the moment someone most needs a new one, so this page asks for the address rather than
 * leaving them at a dead end.
 */
export function VerifyEmailPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const token = params.get('token');

  const [confirming, setConfirming] = useState(token !== null);
  const [confirmed, setConfirmed] = useState(false);
  const [linkFailure, setLinkFailure] = useState<string | null>(null);
  const [linkRequested, setLinkRequested] = useState(false);
  const [sending, setSending] = useState(false);
  const { message, fieldErrors, report, clear } = useFormFailure(['email']);

  useEffect(() => {
    if (token === null) {
      return;
    }
    let cancelled = false;
    apiRequest<void>('/auth/verify-email', { method: 'POST', body: { token } })
      .then(() => {
        if (!cancelled) {
          setConfirmed(true);
          setConfirming(false);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setLinkFailure(describeFailure(t, error));
          setConfirming(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token, t]);

  async function requestAnotherLink(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSending(true);
    clear();
    try {
      await apiRequest<void>('/auth/verify-email/resend', {
        method: 'POST',
        body: { email: textField(form, 'email') }
      });
      setLinkRequested(true);
    } catch (error) {
      report(error);
      setSending(false);
    }
  }

  if (confirming) {
    return (
      <main className="auth-screen">
        <p className="loading" role="status">
          {t('auth.verifyEmail.confirming')}
        </p>
      </main>
    );
  }

  if (confirmed) {
    return (
      <main className="auth-screen">
        <h1>{t('auth.verifyEmail.confirmed.title')}</h1>
        <p className="lede">{t('auth.verifyEmail.confirmed.body')}</p>
        <p className="switch">
          <Trans
            i18nKey="auth.verifyEmail.confirmed.signIn"
            components={{ signIn: <Link to="/login" /> }}
          />
        </p>
      </main>
    );
  }

  return (
    <main className="auth-screen">
      <h1>{t('auth.verifyEmail.needLink.title')}</h1>

      {/* Why they are here, when they arrived by following a link that did not work. */}
      {linkFailure && (
        <p className="form-error" role="alert">
          {linkFailure}
        </p>
      )}

      {linkRequested ? (
        // Deliberately non-committal, matching a server that answers identically whether
        // or not the address has an account: saying more would disclose who is registered.
        <p className="lede" role="status">
          {t('auth.verifyEmail.needLink.requested')}
        </p>
      ) : (
        <form className="auth-form" onSubmit={requestAnotherLink} noValidate>
          <p className="lede">{t('auth.verifyEmail.needLink.body')}</p>

          {message && (
            <p className="form-error" role="alert">
              {message}
            </p>
          )}

          <Field
            id="verify-email-address"
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
              ? t('auth.verifyEmail.needLink.submitting')
              : t('auth.verifyEmail.needLink.submit')}
          </button>
        </form>
      )}
    </main>
  );
}
