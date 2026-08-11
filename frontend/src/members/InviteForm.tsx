import { useState } from 'react';
import type { SubmitEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../auth/Field';
import { MAXIMUM_EMAIL_LENGTH } from '../auth/constants';
import { textField } from '../auth/formValues';
import type { UserRole } from '../auth/types';
import { useFormFailure } from '../auth/useFormFailure';
import type { Invitation } from './types';

const ROLES: readonly UserRole[] = ['MEMBER', 'OWNER'];

/**
 * Sends somebody an invitation to this organisation.
 *
 * `MEMBER` first among the roles, and so the default: inviting a second owner is a
 * deliberate act, and a form that offered it by accident would hand out the right to
 * remove the person who filled it in.
 */
export function InviteForm({
  onInvited
}: {
  onInvited: (email: string) => void;
}) {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [sending, setSending] = useState(false);
  const { message, fieldErrors, report, clear } = useFormFailure([
    'email',
    'role'
  ]);

  async function invite(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const values = new FormData(form);
    const email = textField(values, 'email');
    setSending(true);
    clear();
    try {
      await request<Invitation>('/invitations', {
        method: 'POST',
        body: { email, role: textField(values, 'role') }
      });
      // Reset before the parent reloads, so the next invitation starts from an empty
      // field rather than the address that has just been sent one.
      form.reset();
      onInvited(email);
    } catch (error) {
      report(error);
    }
    setSending(false);
  }

  return (
    <form className="invite-form" onSubmit={invite} noValidate>
      <h2>{t('members.invite.title')}</h2>
      <p className="lede">{t('members.invite.lede')}</p>

      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}

      <Field
        id="invite-email"
        name="email"
        type="email"
        label={t('auth.fields.email.label')}
        autoComplete="off"
        required
        maxLength={MAXIMUM_EMAIL_LENGTH}
        error={fieldErrors.email}
      />

      <p className="field">
        <label htmlFor="invite-role">{t('members.invite.roleLabel')}</label>
        <select id="invite-role" name="role" defaultValue="MEMBER">
          {ROLES.map((role) => (
            <option key={role} value={role}>
              {t(`roles.${role}`)}
            </option>
          ))}
        </select>
        {fieldErrors.role && (
          <span className="field-error">{fieldErrors.role}</span>
        )}
      </p>

      <button type="submit" className="primary" disabled={sending}>
        {sending ? t('members.invite.submitting') : t('members.invite.submit')}
      </button>
    </form>
  );
}
