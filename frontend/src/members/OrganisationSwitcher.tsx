import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import type { Membership } from '../auth/types';
import { describeFailure } from '../i18n/problems';

/**
 * Moves the session from one organisation to another, using the exchange endpoint.
 *
 * Loads the caller's own list rather than reading it off the session, because the session
 * does not carry one: an access token names a single organisation, and the list a person
 * belongs to changes underneath it — accepting an invitation adds to it, being removed
 * takes from it.
 *
 * Renders nothing but the organisation's name until there is somewhere to switch *to*.
 * A control offering one choice is a control that reads as broken, and until an
 * invitation has been accepted one choice is what almost everybody has.
 */
export function OrganisationSwitcher({
  organisation
}: {
  organisation: { id: string; name: string };
}) {
  const { t } = useTranslation();
  const { request, selectOrganisation } = useAuth();
  const [held, setHeld] = useState<Membership[]>([]);
  const [switching, setSwitching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    request<Membership[]>('/memberships')
      .then((memberships) => {
        if (!cancelled) {
          setHeld(memberships);
        }
      })
      .catch(() => {
        // A switcher that cannot load its options is a switcher that is not offered.
        // Failing here must not take the page it sits on down with it.
      });
    return () => {
      cancelled = true;
    };
    // Reloaded on every switch, so a membership gained or lost since is reflected.
  }, [request, organisation.id]);

  async function switchTo(organisationId: string) {
    setSwitching(true);
    setMessage(null);
    try {
      await selectOrganisation(organisationId);
    } catch (error) {
      setMessage(describeFailure(t, error));
    }
    setSwitching(false);
  }

  if (held.length < 2) {
    return <span className="organisation">{organisation.name}</span>;
  }

  return (
    <>
      <label className="visually-hidden" htmlFor="organisation-switcher">
        {t('app.switcher.label')}
      </label>
      <select
        id="organisation-switcher"
        className="organisation"
        value={organisation.id}
        disabled={switching}
        onChange={(event) => void switchTo(event.target.value)}
      >
        {held.map((membership) => (
          <option key={membership.id} value={membership.organisation.id}>
            {membership.organisation.name}
          </option>
        ))}
      </select>
      {message && (
        <span className="field-error" role="alert">
          {message}
        </span>
      )}
    </>
  );
}
