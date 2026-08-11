import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import type { UserRole } from '../auth/types';
import { describeFailure } from '../i18n/problems';
import { InviteForm } from '../members/InviteForm';
import { MemberList } from '../members/MemberList';
import { PendingInvitations } from '../members/PendingInvitations';
import type { Invitation, Member } from '../members/types';

/**
 * Who is in this organisation, and — for an owner — everything that can be done about it.
 *
 * The owner-only half is hidden from a member rather than disabled, and the server
 * enforces the same rule either way: what is on screen is a courtesy, not the boundary.
 *
 * Every action reloads rather than editing the list in place. The server decides things
 * this page cannot — that a demotion would leave nobody able to administer the
 * organisation, that an invitation was already outstanding — so the answer to "what does
 * the list look like now" is one only it can give.
 */
export function MembersPage() {
  const { t } = useTranslation();
  const { account, request, refreshAccount, logout } = useAuth();
  const [members, setMembers] = useState<Member[] | null>(null);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [failure, setFailure] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);

  const canAdminister = account?.role === 'OWNER';
  const organisationId = account?.organisation.id;

  useEffect(() => {
    if (!organisationId) {
      return;
    }
    let cancelled = false;

    async function load() {
      const people = await request<Member[]>('/members');
      // Pending invitations are an owner's business; a member asking would be refused,
      // and asking in order to be refused is a request worth not making.
      const pending = canAdminister
        ? await request<Invitation[]>('/invitations')
        : [];
      if (!cancelled) {
        setMembers(people);
        setInvitations(pending);
      }
    }

    load().catch((error: unknown) => {
      if (!cancelled) {
        setFailure(describeFailure(t, error));
      }
    });
    return () => {
      cancelled = true;
    };
    // Keyed on the organisation, so switching to another one re-scopes the list rather
    // than leaving the previous organisation's people on screen.
  }, [request, canAdminister, organisationId, reloads, t]);

  const reload = useCallback(() => setReloads((count) => count + 1), []);

  /**
   * Every owner action is the same shape: do it, say what went wrong, then settle up.
   *
   * `settle` is normally a reload, and is not when the action was aimed at the caller
   * themselves — a session that has just had its own standing changed underneath it needs
   * more than a fresh list.
   */
  const perform = useCallback(
    async (
      action: Promise<unknown>,
      announcement: string | null,
      settle: () => void | Promise<void> = reload
    ) => {
      setBusy(true);
      setFailure(null);
      setNotice(null);
      try {
        await action;
        setNotice(announcement);
        await settle();
      } catch (error) {
        setFailure(describeFailure(t, error));
      }
      setBusy(false);
    },
    [reload, t]
  );

  /**
   * Demoting yourself changes what you may do here, and the token still says otherwise
   * for as long as twelve hours. Re-reading the account is what takes the controls away;
   * the list comes with it, because what may be shown depends on the answer.
   */
  const refreshThenReload = useCallback(async () => {
    await refreshAccount();
    reload();
  }, [refreshAccount, reload]);

  const changeRole = useCallback(
    (member: Member, role: UserRole) =>
      void perform(
        request(`/members/${member.id}`, { method: 'PATCH', body: { role } }),
        null,
        member.userId === account?.userId ? refreshThenReload : reload
      ),
    [perform, request, account?.userId, refreshThenReload, reload]
  );

  const remove = useCallback(
    (member: Member) =>
      void perform(
        request(`/members/${member.id}`, { method: 'DELETE' }),
        t('members.removed', { name: member.displayName }),
        // Removing yourself leaves a session scoped to an organisation you are no longer
        // in, and every request it could make from here is one the server will refuse —
        // starting with the reload. Ending it is the only coherent thing left, and the
        // guards take the visitor from there.
        member.userId === account?.userId ? logout : reload
      ),
    [perform, request, account?.userId, logout, reload, t]
  );

  const resend = useCallback(
    (invitation: Invitation) =>
      void perform(
        request(`/invitations/${invitation.id}/resend`, { method: 'POST' }),
        t('members.pending.resent', { email: invitation.email })
      ),
    [perform, request, t]
  );

  const revoke = useCallback(
    (invitation: Invitation) =>
      void perform(
        request(`/invitations/${invitation.id}`, { method: 'DELETE' }),
        t('members.pending.revoked', { email: invitation.email })
      ),
    [perform, request, t]
  );

  const invited = useCallback(
    (email: string) => {
      setNotice(t('members.invite.sent', { email }));
      setFailure(null);
      reload();
    },
    [reload, t]
  );

  if (!account) {
    return null;
  }

  return (
    <main className="members">
      <h1>{t('members.title')}</h1>
      <p className="lede">
        {t('members.lede', { organisation: account.organisation.name })}
      </p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}
      {notice && (
        <p className="notice" role="status">
          {notice}
        </p>
      )}

      {members === null ? (
        <p className="loading" role="status">
          {t('members.loading')}
        </p>
      ) : (
        <MemberList
          members={members}
          canAdminister={canAdminister}
          currentUserId={account.userId}
          busy={busy}
          onChangeRole={changeRole}
          onRemove={remove}
        />
      )}

      {canAdminister && (
        <>
          <PendingInvitations
            invitations={invitations}
            busy={busy}
            onResend={resend}
            onRevoke={revoke}
          />
          <InviteForm onInvited={invited} />
        </>
      )}
    </main>
  );
}
