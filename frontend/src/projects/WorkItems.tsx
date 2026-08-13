import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { EstimateForm } from './EstimateForm';
import type { EstimateValues } from './EstimateForm';
import { WorkItemForm } from './WorkItemForm';
import type { Estimate, WorkItem } from './types';

type Values = { title: string; description: string | null };

/** Which row is open, and for which of the two things a row can be opened for. */
type OpenRow = { itemId: string; mode: 'edit' | 'estimate' };

const ESTIMATE_FIELDS = ['p10Hours', 'p50Hours', 'p90Hours'];

/**
 * The work inside one plan: what it is made of, what each person thinks it will take, and
 * how much of it has been estimated at all.
 *
 * Loads and writes on its own rather than through the page around it, because the project
 * and its contents are separate resources on the server and answering for one another's
 * failures would mean a plan that could not be renamed because its items would not load.
 *
 * Every action reloads from the server instead of editing what is on screen: the order is
 * the server's, so is what an item ends up called, and so is which estimate is the current
 * one after somebody else recorded theirs a moment earlier.
 */
export function WorkItems({ projectId }: { projectId: string }) {
  const { t } = useTranslation();
  const { account, request } = useAuth();
  const [items, setItems] = useState<WorkItem[] | null>(null);
  const [estimates, setEstimates] = useState<Estimate[]>([]);
  const [archived, setArchived] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  const [open, setOpen] = useState<OpenRow | null>(null);
  const adding = useFormFailure(['title', 'description']);
  const rewording = useFormFailure(['title', 'description']);
  const estimating = useFormFailure(ESTIMATE_FIELDS);

  const userId = account?.userId;

  useEffect(() => {
    let cancelled = false;
    setItems(null);

    // Asked for together rather than one after the other: neither answer depends on the
    // other, and a page that waits for the work before it starts asking what the work is
    // estimated at takes twice as long to draw for no reason.
    //
    // The estimates are only ever the current ones, and only for work still in the plan —
    // which is why nothing is asked about them while the archived listing is on screen.
    async function load() {
      const [loaded, current] = await Promise.all([
        request<WorkItem[]>(
          `/projects/${projectId}/items${archived ? '?archived=true' : ''}`
        ),
        archived
          ? Promise.resolve<Estimate[]>([])
          : request<Estimate[]>(`/projects/${projectId}/estimates`)
      ]);
      if (!cancelled) {
        setItems(loaded);
        setEstimates(current);
        setFailure(null);
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
  }, [request, projectId, archived, reloads, t]);

  /** What this reader last said about each item, which is what a form starts from. */
  const mine = useMemo(
    () =>
      new Map(
        estimates
          .filter((estimate) => estimate.estimatorId === userId)
          .map((estimate) => [estimate.itemId, estimate])
      ),
    [estimates, userId]
  );

  /** And what everybody else said, so a colleague's range is not invisible. */
  const theirs = useMemo(() => {
    const byItem = new Map<string, Estimate[]>();
    for (const estimate of estimates) {
      if (estimate.estimatorId !== userId) {
        byItem.set(estimate.itemId, [
          ...(byItem.get(estimate.itemId) ?? []),
          estimate
        ]);
      }
    }
    return byItem;
  }, [estimates, userId]);

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
        setOpen(null);
        reload();
      } catch (error) {
        rewording.report(error);
      }
      setBusy(false);
    },
    [request, rewording, reload]
  );

  const estimate = useCallback(
    async (itemId: string, values: EstimateValues) => {
      setBusy(true);
      estimating.clear();
      try {
        // A revision is a new estimate rather than a change to the old one — there is no
        // route that would rewrite it, and what somebody said last month is the only thing
        // a calibration report can ever ask about.
        await request<Estimate>(`/items/${itemId}/estimates`, {
          method: 'POST',
          body: values
        });
        setOpen(null);
        reload();
      } catch (error) {
        estimating.report(error);
      }
      setBusy(false);
    },
    [request, estimating, reload]
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

  /** Opening a row clears whatever the last one was told, so it cannot follow along. */
  const openRow = useCallback(
    (row: OpenRow | null) => {
      rewording.clear();
      estimating.clear();
      setOpen(row);
    },
    [rewording, estimating]
  );

  return (
    <section className="work">
      <h2>{t('projects.items.title')}</h2>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      {/*
        Coverage said in words rather than left for somebody to count, and only about the
        work actually in the plan — the archived listing is a different question.
      */}
      {!archived && items !== null && items.length > 0 && (
        <p className="coverage">
          {t('projects.coverage', {
            estimated: items.filter(
              (item) => mine.has(item.id) || theirs.has(item.id)
            ).length,
            total: items.length
          })}
        </p>
      )}

      <p className="listing-switch">
        <button
          type="button"
          className="link"
          onClick={() => {
            openRow(null);
            setArchived((showing) => !showing);
          }}
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
          {items.map((item) => (
            <li key={item.id}>
              {open?.itemId === item.id && open.mode === 'edit' ? (
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
                  onCancel={() => openRow(null)}
                />
              ) : (
                <>
                  <span className="who">
                    <span className="name">{item.title}</span>
                    {item.description && (
                      <span className="description">{item.description}</span>
                    )}
                    {!archived && (
                      <span className="estimate">{summary(item.id)}</span>
                    )}
                  </span>

                  {!archived && (
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      aria-label={t('projects.items.estimate.openNamed', {
                        title: item.title
                      })}
                      onClick={() =>
                        openRow({ itemId: item.id, mode: 'estimate' })
                      }
                    >
                      {t('projects.items.estimate.open')}
                    </button>
                  )}
                  <button
                    type="button"
                    className="secondary"
                    disabled={busy}
                    // Named for anybody reading through a screen reader, where a column of
                    // identical buttons says nothing about what each one would change.
                    aria-label={t('projects.items.edit.openNamed', {
                      title: item.title
                    })}
                    onClick={() => openRow({ itemId: item.id, mode: 'edit' })}
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
                </>
              )}

              {open?.itemId === item.id && open.mode === 'estimate' && (
                <EstimateForm
                  id={`estimate-${item.id}`}
                  estimate={mine.get(item.id)}
                  busy={busy}
                  banner={estimating.message}
                  fieldErrors={estimating.fieldErrors}
                  onSubmit={(values) => void estimate(item.id, values)}
                  onCancel={() => openRow(null)}
                />
              )}
            </li>
          ))}
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

  /**
   * What the row says about its estimate: the reader's own range if they gave one, and
   * otherwise whose ranges exist, so a colleague's work is never invisible.
   */
  function summary(itemId: string) {
    const own = mine.get(itemId);
    if (own) {
      return t('projects.items.estimate.mine', {
        p10: own.p10Hours,
        p50: own.p50Hours,
        p90: own.p90Hours
      });
    }
    const others = theirs.get(itemId);
    if (others) {
      return t('projects.items.estimate.others', {
        names: others.map((estimate) => estimate.estimatorName).join(', ')
      });
    }
    return t('projects.items.estimate.none');
  }
}
