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
  onInvited,
  onCancel
}: {
  onInvited: (email: string) => void;
  /** The way out that is not sending anything, since this is asked for rather than always on screen. */
  onCancel: () => void;
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
      // Nothing is reset here any more: the dialog holding this form renders its contents
      // only while it is open, so the next invitation starts from a form that has just been
      // built rather than from one somebody emptied.
      onInvited(email);
    } catch (error) {
      report(error);
    }
    setSending(false);
  }

  return (
    <form className="invite-form" onSubmit={invite} noValidate>
      {/*
        No heading of its own: the dialog around it carries the title, and the control that
        opened it says the same words. What stays is the line about what an invitation
        actually does, which is the part somebody needs while filling the form in.
      */}
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

      <p className="actions">
        <button type="submit" className="primary" disabled={sending}>
          {sending
            ? t('members.invite.submitting')
            : t('members.invite.submit')}
        </button>
        <button type="button" className="link" onClick={onCancel}>
          {t('members.invite.cancel')}
        </button>
      </p>
    </form>
  );
}
