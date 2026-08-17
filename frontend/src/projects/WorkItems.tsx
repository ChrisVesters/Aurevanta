import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { DependencyForm } from './DependencyForm';
import type { DependencyValues } from './DependencyForm';
import { EstimateForm } from './EstimateForm';
import type { EstimateValues } from './EstimateForm';
import { ProgressForm } from './ProgressForm';
import type { ProgressValues } from './ProgressForm';
import { WorkItemForm } from './WorkItemForm';
import { formatDay } from './dates';
import type { Dependency, Estimate, WorkItem } from './types';

type Values = { title: string; description: string | null };

/** Which row is open, and for which of the four things a row can be opened for. */
type OpenRow = {
  itemId: string;
  mode: 'edit' | 'estimate' | 'progress' | 'blocks';
};

const ESTIMATE_FIELDS = ['p10Hours', 'p50Hours', 'p90Hours'];
const PROGRESS_FIELDS = ['status', 'actualEffortHours'];
const BLOCK_FIELDS = ['successorItemId', 'lagHours'];

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
  const { t, i18n } = useTranslation();
  const { account, request } = useAuth();
  const [items, setItems] = useState<WorkItem[] | null>(null);
  const [estimates, setEstimates] = useState<Estimate[]>([]);
  const [dependencies, setDependencies] = useState<Dependency[]>([]);
  /**
   * The loop a refused arrow would have closed, already turned into titles. Kept here
   * rather than taken from `useFormFailure`, which knows about wording and per-field
   * complaints and deliberately nothing about the extra properties one refusal carries.
   */
  const [cycle, setCycle] = useState<string[] | null>(null);
  const [archived, setArchived] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  const [open, setOpen] = useState<OpenRow | null>(null);
  const adding = useFormFailure(['title', 'description']);
  const rewording = useFormFailure(['title', 'description']);
  const estimating = useFormFailure(ESTIMATE_FIELDS);
  const reporting = useFormFailure(PROGRESS_FIELDS);
  const blocking = useFormFailure(BLOCK_FIELDS);

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
      const [loaded, current, drawn] = await Promise.all([
        request<WorkItem[]>(
          `/projects/${projectId}/items${archived ? '?archived=true' : ''}`
        ),
        archived
          ? Promise.resolve<Estimate[]>([])
          : request<Estimate[]>(`/projects/${projectId}/estimates`),
        archived
          ? Promise.resolve<Dependency[]>([])
          : request<Dependency[]>(`/projects/${projectId}/dependencies`)
      ]);
      if (!cancelled) {
        setItems(loaded);
        setEstimates(current);
        setDependencies(drawn);
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

  /**
   * What each item must finish before, and what each item is waiting on — the same edges
   * read from both ends, because a row has to say both to be worth reading at all.
   */
  const outgoing = useMemo(
    () => byEnd(dependencies, 'predecessorItemId'),
    [dependencies]
  );
  const incoming = useMemo(
    () => byEnd(dependencies, 'successorItemId'),
    [dependencies]
  );

  /**
   * Every item in the plan by identifier. An arrow names the far end by identifier only,
   * and an identifier is not something to show anybody.
   */
  const titles = useMemo(
    () => new Map((items ?? []).map((item) => [item.id, item.title])),
    [items]
  );

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

  const report = useCallback(
    async (itemId: string, values: ProgressValues) => {
      setBusy(true);
      reporting.clear();
      try {
        await request<WorkItem>(`/items/${itemId}/progress`, {
          method: 'PATCH',
          body: values
        });
        setOpen(null);
        reload();
      } catch (error) {
        reporting.report(error);
      }
      setBusy(false);
    },
    [request, reporting, reload]
  );

  const block = useCallback(
    async (itemId: string, values: DependencyValues) => {
      setBusy(true);
      blocking.clear();
      setCycle(null);
      try {
        await request<Dependency>('/dependencies', {
          method: 'POST',
          body: { predecessorItemId: itemId, ...values }
        });
        // Left open, unlike every other form here, because ordering is plural where the
        // others are not: a task that must finish before one thing usually must finish
        // before two, and the list it just joined is right there. Closing after each one
        // would make drawing three arrows three trips through the same button. Rubbing
        // one out already leaves the panel open, so this is also what makes the two
        // halves of it behave alike.
        reload();
      } catch (error) {
        blocking.report(error);
        // The refusal carries the loop it would have closed, which is the whole reason
        // the server walks the graph rather than merely answering yes or no. Turned into
        // titles here, where the plan is already on screen, and left alone where the
        // refusal was something else.
        if (error instanceof ApiError && error.path) {
          setCycle(
            error.path.map(
              (id) => titles.get(id) ?? t('projects.items.blocks.putAway')
            )
          );
        }
      }
      setBusy(false);
    },
    [request, blocking, reload, titles, t]
  );

  const unblock = useCallback(
    async (dependency: Dependency) => {
      setBusy(true);
      blocking.clear();
      setCycle(null);
      try {
        await request<void>(`/dependencies/${dependency.id}`, {
          method: 'DELETE'
        });
        reload();
      } catch (error) {
        blocking.report(error);
      }
      setBusy(false);
    },
    [request, blocking, reload]
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
      reporting.clear();
      blocking.clear();
      setCycle(null);
      setOpen(row);
    },
    [rewording, estimating, reporting, blocking]
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
                      <>
                        <span className="estimate">{summary(item.id)}</span>
                        <span className="progress">{progress(item)}</span>
                        {shape(item.id).map((line) => (
                          <span className="shape" key={line}>
                            {line}
                          </span>
                        ))}
                      </>
                    )}
                  </span>

                  {!archived && (
                    <>
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
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        aria-label={t('projects.items.progress.openNamed', {
                          title: item.title
                        })}
                        onClick={() =>
                          openRow({ itemId: item.id, mode: 'progress' })
                        }
                      >
                        {t('projects.items.progress.open')}
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        aria-label={t('projects.items.blocks.openNamed', {
                          title: item.title
                        })}
                        onClick={() =>
                          openRow({ itemId: item.id, mode: 'blocks' })
                        }
                      >
                        {t('projects.items.blocks.open')}
                      </button>
                    </>
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
                  // The form asks one question at a time, so a refusal usually belongs to
                  // a box on a screen the visitor is not looking at. It needs the code to
                  // know which question to bring back.
                  code={estimating.code}
                  fieldErrors={estimating.fieldErrors}
                  onSubmit={(values) => void estimate(item.id, values)}
                  onCancel={() => openRow(null)}
                />
              )}

              {open?.itemId === item.id && open.mode === 'blocks' && (
                <DependencyForm
                  // Remounted once an arrow has landed, which is what empties the boxes
                  // for the next one — the same trick the add form uses, and it works
                  // here for the same reason: a refusal does not reload, so it leaves
                  // what was typed exactly where it is.
                  key={reloads}
                  id={`blocks-${item.id}`}
                  item={item}
                  candidates={items}
                  drawn={outgoing.get(item.id) ?? []}
                  titles={titles}
                  cycle={cycle}
                  busy={busy}
                  banner={blocking.message}
                  fieldErrors={blocking.fieldErrors}
                  onSubmit={(values) => void block(item.id, values)}
                  onRemove={(edge) => void unblock(edge)}
                  onCancel={() => openRow(null)}
                />
              )}

              {open?.itemId === item.id && open.mode === 'progress' && (
                <ProgressForm
                  id={`progress-${item.id}`}
                  item={item}
                  busy={busy}
                  banner={reporting.message}
                  fieldErrors={reporting.fieldErrors}
                  onSubmit={(values) => void report(item.id, values)}
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

  /**
   * What the row says about how far along the work is, said in the same breath as when.
   *
   * A status with no date beside it reads as a claim nobody has to stand behind, which is
   * the opposite of what these rows are for — so where a date exists it is shown, in the
   * reader's own locale rather than as the day the server spells it.
   */
  function progress(item: WorkItem) {
    if (item.status === 'DONE' && item.completedOn) {
      const completed = formatDay(item.completedOn, i18n.language);
      return item.actualEffortHours === null
        ? t('projects.items.progress.summary.done', { completed })
        : t('projects.items.progress.summary.doneWithEffort', {
            completed,
            hours: item.actualEffortHours
          });
    }
    if (item.status === 'IN_PROGRESS' && item.startedOn) {
      return t('projects.items.progress.summary.inProgress', {
        started: formatDay(item.startedOn, i18n.language)
      });
    }
    return t('projects.items.progress.summary.notStarted');
  }

  /**
   * What the row says about where it sits in the plan, read from both ends.
   *
   * Both directions, and neither is redundant: an item that must finish before three
   * others is what a delay to it would hold up, and an item waiting on two is why it has
   * not started. A row that said only one of them would answer half the question somebody
   * opened the plan with — and the other half would only be visible from another row.
   */
  function shape(itemId: string) {
    const lines: string[] = [];
    const before = outgoing.get(itemId) ?? [];
    const after = incoming.get(itemId) ?? [];
    if (before.length > 0) {
      lines.push(
        t('projects.items.blocks.summary.before', {
          titles: named(before, 'successorItemId')
        })
      );
    }
    if (after.length > 0) {
      lines.push(
        t('projects.items.blocks.summary.after', {
          titles: named(after, 'predecessorItemId')
        })
      );
    }
    return lines;
  }

  /** The far ends of some arrows, named — a plan is read by its titles, not its keys. */
  function named(
    edges: Dependency[],
    end: 'predecessorItemId' | 'successorItemId'
  ) {
    return edges
      .map(
        (edge) => titles.get(edge[end]) ?? t('projects.items.blocks.putAway')
      )
      .join(', ');
  }
}

/**
 * The plan's arrows grouped by one of their ends.
 *
 * Outside the component because it is a fact about a list rather than about anything on
 * screen, and because both directions are the same grouping with the ends swapped — two
 * copies of it would be two chances for one to drift.
 */
function byEnd(
  dependencies: Dependency[],
  end: 'predecessorItemId' | 'successorItemId'
) {
  const grouped = new Map<string, Dependency[]>();
  for (const dependency of dependencies) {
    grouped.set(dependency[end], [
      ...(grouped.get(dependency[end]) ?? []),
      dependency
    ]);
  }
  return grouped;
}
