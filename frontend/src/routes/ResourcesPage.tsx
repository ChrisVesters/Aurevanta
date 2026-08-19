import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { ResourceForm } from '../resource/ResourceForm';
import type { Resource } from '../resource/types';
import type { Member } from '../members/types';

const FIELDS = ['name', 'units'];

/**
 * What this organisation has to work with.
 *
 * **Organisation-wide rather than per plan**, because a team is not a property of one plan:
 * the same three backend engineers are the constraint on every plan at once, and declaring
 * them per plan would be the same claim written down as many times as there are places to
 * get it wrong.
 *
 * **Every member sees this and every member may act on it.** Roles govern administration,
 * and saying what a team is made of is planning — the same reason the projects page hides
 * nothing.
 *
 * **It is deliberately not a screen about people.** A pool may name somebody, and that is a
 * convenience for finding them: nothing here says what anybody is working on, and the moment
 * it ranked people by how busy they are this would be a different product.
 */
export function ResourcesPage() {
  const { t } = useTranslation();
  const { account, request } = useAuth();
  const [resources, setResources] = useState<Resource[] | null>(null);
  const [people, setPeople] = useState<Member[]>([]);
  const [archived, setArchived] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  const [editing, setEditing] = useState<string | null>(null);
  const declaring = useFormFailure(FIELDS);
  const changing = useFormFailure(FIELDS);

  const organisationId = account?.organisation.id;

  useEffect(() => {
    if (!organisationId) {
      return;
    }
    let cancelled = false;
    // Cleared here rather than when the answer lands, so switching listings does not leave
    // the empty state of the previous one on screen while this one loads.
    setResources(null);

    request<Resource[]>(`/resources${archived ? '?archived=true' : ''}`)
      .then((loaded) => {
        if (!cancelled) {
          setResources(loaded);
          setFailure(null);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, organisationId, archived, reloads, t]);

  // Its own read, and one this page survives losing: a team is still a team if the list of
  // colleagues could not be loaded, so what goes missing is the ability to name one of them.
  useEffect(() => {
    let cancelled = false;
    request<Member[]>('/members')
      .then((loaded) => {
        if (!cancelled) {
          setPeople(loaded);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setPeople([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, organisationId]);

  const declare = useCallback(
    async (values: {
      name: string;
      units: number | null;
      personId: string | null;
    }) => {
      setBusy(true);
      declaring.clear();
      try {
        await request<Resource>('/resources', { method: 'POST', body: values });
        setArchived(false);
        setReloads((count) => count + 1);
      } catch (error) {
        declaring.report(error);
      }
      setBusy(false);
    },
    [request, declaring]
  );

  const change = useCallback(
    async (
      id: string,
      values: { name: string; units: number | null; personId: string | null }
    ) => {
      setBusy(true);
      changing.clear();
      try {
        await request<Resource>(`/resources/${id}`, {
          method: 'PATCH',
          body: values
        });
        setEditing(null);
        setReloads((count) => count + 1);
      } catch (error) {
        changing.report(error);
      }
      setBusy(false);
    },
    [request, changing]
  );

  const setAway = useCallback(
    async (id: string, away: boolean) => {
      setBusy(true);
      try {
        await request<Resource>(
          `/resources/${id}/${away ? 'archive' : 'unarchive'}`,
          { method: 'POST' }
        );
        setReloads((count) => count + 1);
      } catch (error: unknown) {
        setFailure(describeFailure(t, error));
      }
      setBusy(false);
    },
    [request, t]
  );

  if (!account) {
    return null;
  }

  return (
    <main className="resources">
      <h1>{t('resources.title')}</h1>
      <p className="lede">{t('resources.lede')}</p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      <p className="listing-switch">
        <button
          type="button"
          className="link"
          onClick={() => setArchived((showing) => !showing)}
        >
          {archived ? t('resources.showCurrent') : t('resources.showArchived')}
        </button>
      </p>

      {resources === null ? (
        <p className="loading" role="status">
          {t('resources.loading')}
        </p>
      ) : resources.length === 0 ? (
        <p className="empty">
          {archived ? t('resources.noneArchived') : t('resources.none')}
        </p>
      ) : (
        <ul className="resource-list">
          {resources.map((resource) => (
            <li key={resource.id}>
              <span className="who">
                <span className="name">
                  {/*
                    `count` rather than `units`: these are the plurals i18next selects on,
                    and a key handed a differently named number resolves to nothing at all.
                  */}
                  {t('resources.entry', {
                    name: resource.name,
                    count: resource.units
                  })}
                </span>
                {resource.personName && (
                  <span className="description">
                    {t('resources.person', { name: resource.personName })}
                  </span>
                )}
              </span>
              <span className="actions">
                <button
                  type="button"
                  className="link"
                  onClick={() =>
                    setEditing((open) =>
                      open === resource.id ? null : resource.id
                    )
                  }
                >
                  {t('resources.change')}
                </button>
                <button
                  type="button"
                  className="link"
                  disabled={busy}
                  onClick={() =>
                    void setAway(resource.id, resource.archivedAt === null)
                  }
                >
                  {resource.archivedAt === null
                    ? t('resources.putAway')
                    : t('resources.bringBack')}
                </button>
              </span>
              {editing === resource.id && (
                <ResourceForm
                  id={`resource-${resource.id}`}
                  name={resource.name}
                  units={resource.units}
                  personId={resource.personId}
                  people={people}
                  busy={busy}
                  submit={t('resources.change')}
                  submitting={t('resources.changing')}
                  banner={changing.message}
                  fieldErrors={changing.fieldErrors}
                  onSubmit={(values) => void change(resource.id, values)}
                />
              )}
            </li>
          ))}
        </ul>
      )}

      <ResourceForm
        // Remounted once a pool has been declared, which is what empties the fields: the
        // next one starts from nothing rather than from the last one's name. A refusal does
        // not remount, so what somebody typed survives being told why it was refused.
        key={reloads}
        id="new-resource"
        people={people}
        busy={busy}
        submit={t('resources.new.submit')}
        submitting={t('resources.new.submitting')}
        banner={declaring.message}
        fieldErrors={declaring.fieldErrors}
        onSubmit={(values) => void declare(values)}
      >
        <h2>{t('resources.new.title')}</h2>
        <p className="lede">{t('resources.new.lede')}</p>
      </ResourceForm>
    </main>
  );
}
