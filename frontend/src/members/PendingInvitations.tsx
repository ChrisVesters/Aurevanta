import { useTranslation } from 'react-i18next';
import type { Invitation } from './types';

type PendingInvitationsProps = {
  invitations: Invitation[];
  busy: boolean;
  onResend: (invitation: Invitation) => void;
  onRevoke: (invitation: Invitation) => void;
};

/**
 * Invitations sent and not yet acted on, with the two things an owner can do about one.
 *
 * Sending again is the answer to a message that went astray, and withdrawing is the answer
 * to one sent to the wrong address — which is the only remedy for that, since an
 * invitation cannot be edited and the address is what it is addressed to.
 */
export function PendingInvitations({
  invitations,
  busy,
  onResend,
  onRevoke
}: PendingInvitationsProps) {
  const { t, i18n } = useTranslation();

  if (invitations.length === 0) {
    return (
      <section className="pending">
        <h2>{t('members.pending.title')}</h2>
        <p className="lede">{t('members.pending.none')}</p>
      </section>
    );
  }

  const expiry = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium'
  });

  return (
    <section className="pending">
      <h2>{t('members.pending.title')}</h2>
      <ul className="invitation-list">
        {invitations.map((invitation) => (
          <li key={invitation.id}>
            <span className="who">
              <span className="name">{invitation.email}</span>
              <span className="email">
                {t('members.pending.expires', {
                  date: expiry.format(new Date(invitation.expiresAt))
                })}
              </span>
            </span>
            <span className="role">{t(`roles.${invitation.role}`)}</span>
            {/* Addressed for a screen reader, one width wide for everybody else. */}
            <button
              type="button"
              className="secondary"
              disabled={busy}
              aria-label={t('members.pending.resendNamed', {
                email: invitation.email
              })}
              onClick={() => onResend(invitation)}
            >
              {t('members.pending.resend')}
            </button>
            <button
              type="button"
              className="secondary"
              disabled={busy}
              aria-label={t('members.pending.revokeNamed', {
                email: invitation.email
              })}
              onClick={() => onRevoke(invitation)}
            >
              {t('members.pending.revoke')}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
