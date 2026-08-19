import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useLoaded } from '../api/useLoaded';
import { useAuth } from '../auth/AuthContext';
import type { Membership, Organisation } from '../auth/types';
import { describeFailure } from '../i18n/problems';
import { sharedNames } from '../tenant/sharedNames';

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
  organisation: Organisation;
}) {
  const { t } = useTranslation();
  const { selectOrganisation } = useAuth();
  const [switching, setSwitching] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Reloaded on every switch, so a membership gained or lost since is reflected — and on a
  // rename, which changes neither the id nor the membership but does change what this list
  // has to say. Since M1a a name is not unique, so a stale list can offer two options that
  // read alike: the rename may have been the thing telling them apart.
  //
  // The failure is dropped on purpose. A switcher that cannot load its options is a switcher
  // that is not offered, and failing here must not take the page it sits on down with it.
  const { data: loaded } = useLoaded<Membership[]>('/memberships', [
    organisation.id,
    organisation.name,
    organisation.slug
  ]);
  const held = loaded ?? [];

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

  // Since M1a a name is not unique. An <option> has nowhere to put a second line, so the
  // handle goes inline — and only for the names that actually repeat.
  const sharesName = sharedNames(held);

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
            {sharesName(membership.organisation)
              ? t('app.switcher.named', {
                  name: membership.organisation.name,
                  handle: membership.organisation.slug
                })
              : membership.organisation.name}
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
