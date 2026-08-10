import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { apiRequest } from '../api/client';
import { useFormFailure } from './useFormFailure';

/**
 * Asks for another confirmation link, offered where the need for one becomes apparent.
 *
 * Takes the address rather than asking for it: both places that show this already know it,
 * because the visitor has just typed it. Sending them to a page that asks again is where a
 * lost confirmation email turns into an abandoned account, and it is the whole reason this
 * exists instead of a link.
 */
export function ResendConfirmation({ email }: { email: string }) {
  const { t } = useTranslation();
  const [requested, setRequested] = useState(false);
  const [sending, setSending] = useState(false);
  // Failure state of its own, so a refused resend cannot overwrite whatever message
  // explains why a resend is being offered — and, with it, the offer itself.
  const { message, report, clear } = useFormFailure([]);

  async function sendAnotherLink() {
    setSending(true);
    clear();
    try {
      await apiRequest<void>('/auth/verify-email/resend', {
        method: 'POST',
        body: { email }
      });
      setRequested(true);
    } catch (error) {
      report(error);
    }
    setSending(false);
  }

  if (requested) {
    // As non-committal as the server's own answer, which is identical whether or not the
    // address has an account.
    return (
      <p className="lede" role="status">
        {t('auth.resendConfirmation.requested')}
      </p>
    );
  }

  return (
    <>
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      <button
        type="button"
        className="secondary"
        onClick={sendAnotherLink}
        disabled={sending}
      >
        {sending
          ? t('auth.resendConfirmation.submitting')
          : t('auth.resendConfirmation.submit')}
      </button>
    </>
  );
}
