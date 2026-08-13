import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { WorkItemForm } from './WorkItemForm';
import type { WorkItem } from './types';

type Values = { title: string; description: string | null };

/**
 * The work inside one plan: a list, an inline add, and a way to reword or put away any of
 * it.
 *
 * Loads and writes on its own rather than through the page around it, because the project
 * and its items are separate resources on the server and answering for one another's
 * failures would mean a plan that could not be renamed because its items would not load.
 *
 * Every action reloads from the server instead of editing the list in place: the order is
 * the server's, and so is what an item ends up called after somebody else's change landed
 * a moment earlier.
 */
export function WorkItems({ projectId }: { projectId: string }) {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [items, setItems] = useState<WorkItem[] | null>(null);
  const [archived, setArchived] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  /** Which row is open for rewording; at most one, so the page cannot fill with forms. */
  const [editing, setEditing] = useState<string | null>(null);
  const adding = useFormFailure(['title', 'description']);
  const rewording = useFormFailure(['title', 'description']);

  useEffect(() => {
    let cancelled = false;
    setItems(null);

    request<WorkItem[]>(
      `/projects/${projectId}/items${archived ? '?archived=true' : ''}`
    )
      .then((loaded) => {
        if (!cancelled) {
          setItems(loaded);
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
  }, [request, projectId, archived, reloads, t]);

  const reload = useCallback(() => setReloads((count) => count + 1), []);

  const add = useCallback(
    async (values: Values) => {
      setBusy(true);
      adding.clear();
      try {
        await request<WorkItem>(`/projects/${projectId}/items`, {
          method: 'POST',
          body: values
        });
        // Written down while looking at what was put away, which is a plausible thing to
        // do and a confusing thing to be shown nothing for.
        setArchived(false);
        reload();
      } catch (error) {
        adding.report(error);
      }
      setBusy(false);
    },
    [request, projectId, adding, reload]
  );

  const reword = useCallback(
    async (itemId: string, values: Values) => {
      setBusy(true);
      rewording.clear();
      try {
        await request<WorkItem>(`/items/${itemId}`, {
          method: 'PATCH',
          body: values
        });
        setEditing(null);
        reload();
      } catch (error) {
        rewording.report(error);
      }
      setBusy(false);
    },
    [request, rewording, reload]
  );

  const setItemArchived = useCallback(
    async (item: WorkItem, put: boolean) => {
      setBusy(true);
      try {
        await request<WorkItem>(
          `/items/${item.id}/${put ? 'archive' : 'unarchive'}`,
          { method: 'POST' }
        );
        reload();
      } catch (error) {
        setFailure(describeFailure(t, error));
      }
      setBusy(false);
    },
    [request, reload, t]
  );

  const open = useCallback(
    (itemId: string | null) => {
      rewording.clear();
      setEditing(itemId);
    },
    [rewording]
  );

  return (
    <section className="work">
      <h2>{t('projects.items.title')}</h2>

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
          {archived
            ? t('projects.items.showCurrent')
            : t('projects.items.showArchived')}
        </button>
      </p>

      {items === null ? (
        <p className="loading" role="status">
          {t('projects.items.loading')}
        </p>
      ) : items.length === 0 ? (
        <p className="empty">
          {archived
            ? t('projects.items.noneArchived')
            : t('projects.items.none')}
        </p>
      ) : (
        <ul className="work-item-list">
          {items.map((item) =>
            editing === item.id ? (
              <li key={item.id}>
                <WorkItemForm
                  id={`item-${item.id}`}
                  title={item.title}
                  description={item.description}
                  busy={busy}
                  submit={t('projects.items.edit.submit')}
                  submitting={t('projects.items.edit.submitting')}
                  banner={rewording.message}
                  fieldErrors={rewording.fieldErrors}
                  onSubmit={(values) => void reword(item.id, values)}
                  onCancel={() => open(null)}
                />
              </li>
            ) : (
              <li key={item.id}>
                <span className="who">
                  <span className="name">{item.title}</span>
                  {item.description && (
                    <span className="description">{item.description}</span>
                  )}
                </span>
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  // Named for anybody reading through a screen reader, where a column of
                  // identical buttons says nothing about what each one would change.
                  aria-label={t('projects.items.edit.openNamed', {
                    title: item.title
                  })}
                  onClick={() => open(item.id)}
                >
                  {t('projects.items.edit.open')}
                </button>
                <button
                  type="button"
                  className="secondary"
                  disabled={busy}
                  aria-label={t(
                    item.archivedAt
                      ? 'projects.items.unarchiveNamed'
                      : 'projects.items.archiveNamed',
                    { title: item.title }
                  )}
                  onClick={() => void setItemArchived(item, !item.archivedAt)}
                >
                  {item.archivedAt
                    ? t('projects.items.unarchive')
                    : t('projects.items.archive')}
                </button>
              </li>
            )
          )}
        </ul>
      )}

      <WorkItemForm
        // Remounted once something has been written down, which is what empties the boxes
        // for the next one. A refusal leaves them alone.
        key={reloads}
        id="new-item"
        busy={busy}
        submit={t('projects.items.add.submit')}
        submitting={t('projects.items.add.submitting')}
        banner={adding.message}
        fieldErrors={adding.fieldErrors}
        onSubmit={(values) => void add(values)}
      />
    </section>
  );
}
