import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { UserRole } from '../auth/types';
import type { Member } from './types';

const ROLES: readonly UserRole[] = ['OWNER', 'MEMBER'];

type MemberListProps = {
  members: Member[];
  /** Whether the caller may administer this organisation, which only an owner may. */
  canAdminister: boolean;
  /** So somebody can be shown which row is their own before they remove it. */
  currentUserId: string;
  busy: boolean;
  onChangeRole: (member: Member, role: UserRole) => void;
  onRemove: (member: Member) => void;
};

/**
 * Everybody in the organisation, and — for an owner — what can be done about them.
 *
 * The controls are hidden from a member rather than disabled: an interface that offers an
 * action and then refuses it teaches people to distrust it. The backend enforces the same
 * rule regardless, because hiding a button is a courtesy and not a permission.
 */
export function MemberList({
  members,
  canAdminister,
  currentUserId,
  busy,
  onChangeRole,
  onRemove
}: MemberListProps) {
  const { t } = useTranslation();
  // Which row has asked "are you sure": removal is the one action here that cannot be
  // undone from this screen, since re-adding somebody means inviting them again.
  const [confirming, setConfirming] = useState<string | null>(null);

  return (
    <ul className="member-list">
      {members.map((member) => (
        <li key={member.id}>
          <span className="who">
            <span className="name">
              {member.displayName}
              {/*
                Beside the name rather than on a line of its own: a row one line taller
                than its neighbours is a row whose controls sit at a different height
                from theirs.
              */}
              {member.userId === currentUserId && (
                <span className="you">{t('members.you')}</span>
              )}
            </span>
            <span className="email">{member.email}</span>
          </span>

          {canAdminister ? (
            <>
              <label
                className="visually-hidden"
                htmlFor={`member-role-${member.id}`}
              >
                {t('members.roleLabel', { name: member.displayName })}
              </label>
              <select
                id={`member-role-${member.id}`}
                value={member.role}
                disabled={busy}
                onChange={(event) =>
                  onChangeRole(member, event.target.value as UserRole)
                }
              >
                {ROLES.map((role) => (
                  <option key={role} value={role}>
                    {t(`roles.${role}`)}
                  </option>
                ))}
              </select>
            </>
          ) : (
            <span className="role">{t(`roles.${member.role}`)}</span>
          )}

          {canAdminister &&
            (confirming === member.id ? (
              <span className="confirm" role="group">
                <span>
                  {t('members.removeConfirm', { name: member.displayName })}
                </span>
                <button
                  type="button"
                  className="danger"
                  disabled={busy}
                  onClick={() => {
                    setConfirming(null);
                    onRemove(member);
                  }}
                >
                  {t('members.confirmRemove')}
                </button>
                <button
                  type="button"
                  className="link"
                  onClick={() => setConfirming(null)}
                >
                  {t('members.cancel')}
                </button>
              </span>
            ) : (
              <button
                type="button"
                className="secondary"
                disabled={busy}
                // Named for anybody reading the page through a screen reader, where a
                // column of identical "Remove" buttons says nothing about what each one
                // would remove. Kept out of the visible label so the buttons are all one
                // width and line up down the column.
                aria-label={t('members.removeNamed', {
                  name: member.displayName
                })}
                onClick={() => setConfirming(member.id)}
              >
                {t('members.remove')}
              </button>
            ))}
        </li>
      ))}
    </ul>
  );
}
