import { useEffect, useState } from 'react';
import type { SubmitEvent } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link, useLocation, useParams } from 'react-router';
import { apiRequest } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Field } from '../auth/Field';
import {
  MAXIMUM_NAME_LENGTH,
  MAXIMUM_PASSWORD_LENGTH,
  MINIMUM_PASSWORD_LENGTH
} from '../auth/constants';
import { textField } from '../auth/formValues';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import type { InvitationPreview } from '../members/types';
import { AuthLayout } from './AuthLayout';

/** The address already has an account, so it has to be claimed by whoever holds it. */
const SIGN_IN_REQUIRED = 'sign_in_required';
/** Signed in, but as somebody the invitation was not addressed to. */
const WRONG_ADDRESS = 'invitation_for_another_address';

/**
 * Where the link in an invitation lands.
 *
 * Public, and has to be: the person reading it may have no account at all, and deciding
 * whether to make one is the whole reason they are shown the preview first. What they are
 * asked for depends on which of them they are, and the server decides that from the
 * invited address rather than from anything typed here.
 */
export function InvitePage() {
  const { t } = useTranslation();
  const location = useLocation();
  const { token } = useParams<{ token: string }>();
  const { status, account, acceptInvitation, logout } = useAuth();

  const [preview, setPreview] = useState<InvitationPreview | null>(null);
  const [unusable, setUnusable] = useState<string | null>(null);
  const [joined, setJoined] = useState<string | null>(null);
  const [accepting, setAccepting] = useState(false);
  const { message, code, fieldErrors, report, clear } = useFormFailure([
    'displayName',
    'password'
  ]);

  useEffect(() => {
    if (!token) {
      return;
    }
    let cancelled = false;
    apiRequest<InvitationPreview>(`/invitations/${token}`)
      .then((found) => {
        if (!cancelled) {
          setPreview(found);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setUnusable(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [token, t]);

  /**
   * `link` is passed in rather than read from the closure because the render paths below
   * have narrowed it to a string by the time they call this, and defaulting one here
   * would invent a token for the server to refuse.
   */
  async function join(
    link: string,
    event: SubmitEvent<HTMLFormElement> | null
  ) {
    event?.preventDefault();
    const values = event ? new FormData(event.currentTarget) : null;
    setAccepting(true);
    clear();
    try {
      setJoined(
        await acceptInvitation(
          link,
          // A visitor with no account chooses one here; somebody who already has one
          // signs in first and sends nothing, which is a different request entirely.
          values
            ? {
                displayName: textField(values, 'displayName'),
                password: textField(values, 'password')
              }
            : null
        )
      );
    } catch (error) {
      report(error);
      setAccepting(false);
    }
  }

  if (joined) {
    return (
      <AuthLayout>
        <h1>{t('invite.joined.title', { organisation: joined })}</h1>
        <p className="lede">{t('invite.joined.body')}</p>
        <p className="switch">
          <Trans
            i18nKey="invite.joined.open"
            components={{ app: <Link to="/app" /> }}
          />
        </p>
      </AuthLayout>
    );
  }

  // A link nobody recognises, one that ran out of time, one that was withdrawn. Each says
  // which, because what to do next differs: an expired invitation can be sent again and a
  // withdrawn one was somebody's decision.
  if (unusable) {
    return (
      <AuthLayout>
        <h1>{t('invite.unusable.title')}</h1>
        <p className="form-error" role="alert">
          {unusable}
        </p>
        <p className="switch">
          <Trans
            i18nKey="invite.unusable.signIn"
            components={{ signIn: <Link to="/login" /> }}
          />
        </p>
      </AuthLayout>
    );
  }

  // No token in the path and no preview yet look the same on screen, and neither has
  // asked the server anything. Narrowing here is also what lets everything below take the
  // token as the string it is, rather than defaulting one the server would only refuse.
  if (!token || !preview) {
    return (
      <AuthLayout>
        <p className="loading" role="status">
          {t('invite.loading')}
        </p>
      </AuthLayout>
    );
  }

  const invitation = (
    <>
      <h1>{t('invite.title', { organisation: preview.organisationName })}</h1>
      <p className="lede">
        {t('invite.lede', {
          inviter: preview.invitedBy,
          organisation: preview.organisationName,
          role: t(`roles.${preview.role}`)
        })}
      </p>
      {message && (
        <p className="form-error" role="alert">
          {message}
        </p>
      )}
      {/* Signed in as somebody else: a shared computer, or a forwarded message. */}
      {code === WRONG_ADDRESS && (
        <p className="switch">
          <button type="button" className="link" onClick={logout}>
            {t('invite.signOut')}
          </button>
        </p>
      )}
    </>
  );

  // Anyone holding a token is somebody the server can identify, so it decides whether
  // this invitation is theirs. Anyone else is offered the account they do not have yet.
  if (status !== 'anonymous') {
    return (
      <AuthLayout>
        {invitation}
        {account && (
          <p className="lede">
            {t('invite.accept.as', { email: account.email })}
          </p>
        )}
        <button
          type="button"
          className="primary"
          disabled={accepting}
          onClick={() => void join(token, null)}
        >
          {accepting
            ? t('invite.accept.submitting')
            : t('invite.accept.submit')}
        </button>
      </AuthLayout>
    );
  }

  /*
    Nobody is signed in and the address is already somebody's. Asking for a display name
    and a password would be asking for an account that exists, so they are not asked:
    the preview says which of the two ways through applies before anything is typed.
    `sign_in_required` is the same answer arriving late, for an account registered
    between fetching the preview and acting on it.

    Signing in from here comes back to this page rather than going to the dashboard, so
    the invitation is still in front of them when they return — otherwise accepting means
    finding the email again.
  */
  if (preview.claimed || code === SIGN_IN_REQUIRED) {
    return (
      <AuthLayout>
        {invitation}
        <p className="lede">{t('invite.claimed.lede')}</p>
        <p className="switch">
          <Trans
            i18nKey="invite.claimed.signIn"
            components={{
              signIn: <Link to="/login" state={{ from: location.pathname }} />
            }}
          />
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <form
        className="auth-form"
        onSubmit={(event) => void join(token, event)}
        noValidate
      >
        {invitation}
        <p className="lede">{t('invite.create.lede')}</p>

        <Field
          id="invite-display-name"
          name="displayName"
          label={t('auth.fields.displayName.label')}
          autoComplete="name"
          required
          maxLength={MAXIMUM_NAME_LENGTH}
          error={fieldErrors.displayName}
        />
        <Field
          id="invite-password"
          name="password"
          type="password"
          label={t('auth.fields.password.label')}
          autoComplete="new-password"
          required
          minLength={MINIMUM_PASSWORD_LENGTH}
          maxLength={MAXIMUM_PASSWORD_LENGTH}
          error={fieldErrors.password}
          hint={t('auth.fields.password.hint', {
            count: MINIMUM_PASSWORD_LENGTH
          })}
        />

        <button type="submit" className="primary" disabled={accepting}>
          {accepting
            ? t('invite.create.submitting')
            : t('invite.create.submit', {
                organisation: preview.organisationName
              })}
        </button>
      </form>
    </AuthLayout>
  );
}
