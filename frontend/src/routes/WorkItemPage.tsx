import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { useLoaded } from '../api/useLoaded';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { DependencyForm } from '../projects/DependencyForm';
import type { DependencyValues } from '../projects/DependencyForm';
import { EstimateForm } from '../projects/EstimateForm';
import type { EstimateValues } from '../projects/EstimateForm';
import { ProgressForm } from '../projects/ProgressForm';
import type { ProgressValues } from '../projects/ProgressForm';
import { RequirementForm } from '../projects/RequirementForm';
import { WorkItemForm } from '../projects/WorkItemForm';
import { Help } from '../ui/Help';
import { byEnd, describeProgress, describeShape } from '../projects/work';
import type {
  CombinedEstimate,
  Dependency,
  Estimate,
  Project,
  WorkItem
} from '../projects/types';
import type { Requirement, Resource } from '../resource/types';

/** Which of the five things a task can be changed about is open, if any. */
type Mode = 'edit' | 'estimate' | 'progress' | 'needs' | 'order';

const ESTIMATE_FIELDS = ['p10Hours', 'p50Hours', 'p90Hours'];
const PROGRESS_FIELDS = ['status', 'actualEffortHours'];
const ORDER_FIELDS = ['successorItemId', 'lagHours'];

/**
 * One task: everything anybody has said about it, and every way to change it.
 *
 * **Every change to a piece of work happens here and nowhere else.** All five forms used to
 * open on a row of the plan's work list, which put five buttons on every one of up to five
 * hundred rows and gave each form the width of a list item to say itself in. The list is a
 * list again — what the work is, what it is expected to take, how far along it is and where
 * it sits — and each row links here. One place to change a task is the same argument the
 * rest of this codebase makes about one place to do anything: two would eventually be one
 * that was told about a new field and one that was not.
 *
 * **Addressed as `/app/items/{id}`, carrying no plan.** That is the routing half of what
 * `WorkItemController` already decided: an item is created and listed within a plan, and
 * once it exists it is addressed on its own. A URL naming both would be a second identifier
 * nothing checks against the first, and the plan comes back on the row anyway — which is
 * what the back link is built from.
 *
 * **The task arrives first and its plan's context second**, because the second read is keyed
 * on what the first one said. Only the ordering panel needs the plan — what else there is to
 * come before, and what those things are called — so the wait buys the one part of this page
 * that cannot be drawn from the task alone.
 *
 * Every action reloads from the server rather than editing what is on screen, exactly as
 * the list did: what the task ends up called is the server's, and so is which estimate is
 * the current one after a colleague recorded theirs a moment ago.
 */
export function WorkItemPage() {
  const { t, i18n } = useTranslation();
  const { itemId } = useParams();
  const { account, request } = useAuth();
  const [item, setItem] = useState<WorkItem | null>(null);
  const [project, setProject] = useState<Project | null>(null);
  /** The rest of the plan, which is what an arrow can be drawn to and named by. */
  const [siblings, setSiblings] = useState<WorkItem[]>([]);
  const [dependencies, setDependencies] = useState<Dependency[]>([]);
  const [estimates, setEstimates] = useState<Estimate[]>([]);
  const [needs, setNeeds] = useState<Requirement[]>([]);
  const [resources, setResources] = useState<Resource[]>([]);
  /**
   * The loop a refused arrow would have closed, already turned into titles. Kept here
   * rather than taken from `useFormFailure`, which knows about wording and per-field
   * complaints and deliberately nothing about the extra properties one refusal carries.
   */
  const [cycle, setCycle] = useState<string[] | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  const [open, setOpen] = useState<Mode | null>(null);
  const rewording = useFormFailure(['title', 'description']);
  const estimating = useFormFailure(ESTIMATE_FIELDS);
  const reporting = useFormFailure(PROGRESS_FIELDS);
  const ordering = useFormFailure(ORDER_FIELDS);
  /**
   * The one form here with no field of its own to complain about: a requirement is a pool
   * and a number, and both refusals it can meet — the same pool twice, a pool that is not
   * there — belong to the set rather than to a box. So it renders a banner and nothing else.
   */
  const needing = useFormFailure([]);

  const userId = account?.userId;
  const organisationId = account?.organisation.id;

  useEffect(() => {
    if (!organisationId || !itemId) {
      return undefined;
    }
    let cancelled = false;
    setItem(null);

    async function load() {
      // The task first, because everything else here is asked of the plan it names and
      // this page's address does not carry one.
      const found = await request<WorkItem>(`/items/${itemId}`);
      if (cancelled) {
        return;
      }
      const [plan, work, drawn, current, wanted, pools] = await Promise.all([
        request<Project>(`/projects/${found.projectId}`),
        request<WorkItem[]>(`/projects/${found.projectId}/items`),
        request<Dependency[]>(`/projects/${found.projectId}/dependencies`),
        request<Estimate[]>(`/projects/${found.projectId}/estimates`),
        request<Requirement[]>(`/items/${itemId}/requirements`),
        // The organisation's pools rather than the plan's, because that is what they are:
        // the same three backend engineers constrain every plan at once.
        request<Resource[]>('/resources')
      ]);
      if (!cancelled) {
        setItem(found);
        setProject(plan);
        setSiblings(work);
        setDependencies(drawn);
        setEstimates(current);
        setNeeds(wanted);
        setResources(pools);
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
  }, [request, organisationId, itemId, reloads, t]);

  /**
   * Every current estimate on this task, the reader's own first.
   *
   * The whole point of a page rather than a row: the list has space for a line saying who
   * else gave one, and this has space for what they said. Two people who disagree about a
   * task are saying something, and it is invisible anywhere a colleague is only named.
   */
  const ranges = useMemo(
    () =>
      estimates
        // The endpoint answers for the whole plan, because that is the request the plan
        // screen makes and a second per-item one would be a second way to read one thing.
        .filter((range) => range.itemId === itemId)
        .sort((one, other) =>
          one.estimatorId === userId ? -1 : other.estimatorId === userId ? 1 : 0
        ),
    [estimates, itemId, userId]
  );

  const own = ranges.find((estimate) => estimate.estimatorId === userId);

  /**
   * What the ranges say together, asked for only where there is more than one to combine —
   * one person's range already says everything a combination of it would — and only on
   * work still in the plan, since put-away work shows no estimates at all. Read again after
   * every write, because a colleague's new range changes it.
   */
  const combining =
    item !== null && item.archivedAt == null && ranges.length > 1;
  const { data: combined } = useLoaded<CombinedEstimate>(
    combining ? `/items/${itemId}/estimates/combined` : null,
    [itemId, reloads, combining]
  );

  const outgoing = useMemo(
    () => byEnd(dependencies, 'predecessorItemId'),
    [dependencies]
  );
  const incoming = useMemo(
    () => byEnd(dependencies, 'successorItemId'),
    [dependencies]
  );

  const titles = useMemo(
    () => new Map(siblings.map((work) => [work.id, work.title])),
    [siblings]
  );

  /**
   * Where this task sits in the plan, read from both ends. Not a hook: it depends on an
   * item that may not have arrived, and the empty list for that moment is the same empty
   * list a task with no arrows has.
   */
  const shape = item
    ? describeShape(t, item.id, outgoing, incoming, titles)
    : [];

  const reload = useCallback(() => setReloads((count) => count + 1), []);

  const reword = useCallback(
    async (values: { title: string; description: string | null }) => {
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
    [request, itemId, rewording, reload]
  );

  const estimate = useCallback(
    async (values: EstimateValues) => {
      setBusy(true);
      estimating.clear();
      try {
        // A revision is a new estimate rather than a change to the old one — there is no
        // route that would rewrite it, and what somebody said last month is the only thing
        // a track record can ever ask about.
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
    [request, itemId, estimating, reload]
  );

  const report = useCallback(
    async (values: ProgressValues) => {
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
    [request, itemId, reporting, reload]
  );

  /**
   * Says what this piece of work needs, in place of whatever it needed before.
   *
   * A whole set at a time, which is what the endpoint takes: what a task needs is the list,
   * and replacing it makes "it needs these two things now" one fact arriving once rather
   * than a sequence somebody has to reassemble from an add, a change and a delete.
   */
  const require = useCallback(
    async (wanted: { resourceId: string; units: number }[]) => {
      setBusy(true);
      needing.clear();
      try {
        await request<Requirement[]>(`/items/${itemId}/requirements`, {
          method: 'PUT',
          body: { needs: wanted }
        });
        setOpen(null);
        reload();
      } catch (error) {
        needing.report(error);
      }
      setBusy(false);
    },
    [request, itemId, needing, reload]
  );

  const block = useCallback(
    async (values: DependencyValues) => {
      setBusy(true);
      ordering.clear();
      setCycle(null);
      try {
        await request<Dependency>('/dependencies', {
          method: 'POST',
          body: { predecessorItemId: itemId, ...values }
        });
        // Left open, unlike every other form here, because ordering is plural where the
        // others are not: a task that must finish before one thing usually must finish
        // before two, and the list it just joined is right there.
        reload();
      } catch (error) {
        ordering.report(error);
        // The refusal carries the loop it would have closed, which is the whole reason
        // the server walks the graph rather than merely answering yes or no.
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
    [request, itemId, ordering, reload, titles, t]
  );

  const unblock = useCallback(
    async (dependency: Dependency) => {
      setBusy(true);
      ordering.clear();
      setCycle(null);
      try {
        await request<void>(`/dependencies/${dependency.id}`, {
          method: 'DELETE'
        });
        reload();
      } catch (error) {
        ordering.report(error);
      }
      setBusy(false);
    },
    [request, ordering, reload]
  );

  const setArchived = useCallback(
    async (archived: boolean) => {
      setBusy(true);
      try {
        await request<WorkItem>(
          `/items/${itemId}/${archived ? 'archive' : 'unarchive'}`,
          { method: 'POST' }
        );
        setOpen(null);
        reload();
      } catch (error) {
        setFailure(describeFailure(t, error));
      }
      setBusy(false);
    },
    [request, itemId, reload, t]
  );

  /** Opening one form clears whatever the last one was told, so it cannot follow along. */
  const show = useCallback(
    (mode: Mode | null) => {
      rewording.clear();
      estimating.clear();
      reporting.clear();
      ordering.clear();
      needing.clear();
      setCycle(null);
      setOpen(mode);
    },
    [rewording, estimating, reporting, ordering, needing]
  );

  if (!account) {
    return null;
  }

  const away = item?.archivedAt != null;

  return (
    <main className="projects work-item">
      {project && (
        <p className="back">
          <Link to={`/app/projects/${project.id}`}>
            {t('projects.item.back', { project: project.name })}
          </Link>
        </p>
      )}

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      {item === null ? (
        !failure && (
          <p className="loading" role="status">
            {t('projects.item.loading')}
          </p>
        )
      ) : (
        <>
          <h1>{item.title}</h1>
          {away && <p className="empty">{t('projects.item.archivedNotice')}</p>}
          {item.description && (
            <p className="description">{item.description}</p>
          )}

          {open === 'edit' ? (
            <WorkItemForm
              id="item"
              title={item.title}
              description={item.description}
              busy={busy}
              submit={t('projects.items.edit.submit')}
              submitting={t('projects.items.edit.submitting')}
              banner={rewording.message}
              fieldErrors={rewording.fieldErrors}
              onSubmit={(values) => void reword(values)}
              onCancel={() => show(null)}
            />
          ) : (
            <p className="reword">
              <button
                type="button"
                className="secondary"
                disabled={busy}
                onClick={() => show('edit')}
              >
                {t('projects.items.edit.open')}
              </button>
            </p>
          )}

          {/*
            Everything below changes something a forecast reads, and a task that has been
            put away is not in one. The plan's list hides the same four for the same
            reason; what stays is rewording it and bringing it back.
          */}
          {!away && (
            <>
              <section className="task-part">
                <h2>{t('projects.item.estimate.title')}</h2>
                {open === 'estimate' ? (
                  /*
                    Alone on this part of the page while it is open, which is not a layout
                    choice: a colleague's numbers must never be on screen while somebody is
                    answering, or the two of them anchored on each other and the band they
                    produce is confidently narrow for a reason nothing downstream can see.
                  */
                  <EstimateForm
                    id="estimate"
                    estimate={own}
                    busy={busy}
                    banner={estimating.message}
                    // The form asks one question at a time, so a refusal usually belongs to
                    // a box the visitor is not looking at. It needs the code to know which
                    // question to bring back.
                    code={estimating.code}
                    fieldErrors={estimating.fieldErrors}
                    onSubmit={(values) => void estimate(values)}
                    onCancel={() => show(null)}
                  />
                ) : (
                  <>
                    {ranges.length === 0 ? (
                      <p className="empty">
                        {t('projects.items.estimate.none')}
                      </p>
                    ) : (
                      <ul className="ranges">
                        {ranges.map((range) => (
                          <li key={range.id}>
                            {range.estimatorId === userId
                              ? t('projects.items.estimate.mine', {
                                  p10: range.p10Hours,
                                  p50: range.p50Hours,
                                  p90: range.p90Hours
                                })
                              : t('projects.item.estimate.theirs', {
                                  name: range.estimatorName,
                                  p10: range.p10Hours,
                                  p50: range.p50Hours,
                                  p90: range.p90Hours
                                })}
                          </li>
                        ))}
                      </ul>
                    )}
                    {/*
                      Beside the ranges and never instead of them: "2–8 and 10–40" says go
                      and talk, and the combination alone would hide that. Off the screen
                      with them while somebody answers, since it is made of their numbers.
                      The count is checked here as well as in the read, because a read that
                      is no longer asked for keeps its last answer.
                    */}
                    {combining && combined?.range && (
                      <div className="combined">
                        <p>
                          {t('projects.item.estimate.combined', {
                            p10: combined.range.p10Hours,
                            p50: combined.range.p50Hours,
                            p90: combined.range.p90Hours
                          })}{' '}
                          <Help
                            label={t('projects.item.estimate.combinedHelp')}
                          >
                            {t('projects.item.estimate.combinedHint')}
                          </Help>
                        </p>
                      </div>
                    )}
                    {item.status === 'NOT_STARTED' ? (
                      <p className="actions">
                        <button
                          type="button"
                          className="secondary"
                          disabled={busy}
                          onClick={() => show('estimate')}
                        >
                          {t('projects.items.estimate.open')}
                        </button>
                      </p>
                    ) : (
                      /*
                        Said in place of the control rather than left to the refusal. The
                        server refuses an estimate on work that has begun, and a screen
                        that still offered the form would be inviting somebody through
                        four questions to be turned away at the end — which is
                        `progress_not_applicable`'s rule seen from the same side.
                      */
                      <p className="hint">
                        {t('projects.item.estimate.closed')}
                      </p>
                    )}
                  </>
                )}
              </section>

              <section className="task-part">
                <h2>{t('projects.item.progress.title')}</h2>
                <p className="progress">
                  {describeProgress(t, i18n.language, item)}
                </p>
                {open === 'progress' ? (
                  <ProgressForm
                    id="progress"
                    item={item}
                    // Whether starting it is about to close something or about to close
                    // nothing, which is what the warning has to tell the two cases apart.
                    estimated={ranges.length > 0}
                    busy={busy}
                    banner={reporting.message}
                    fieldErrors={reporting.fieldErrors}
                    onSubmit={(values) => void report(values)}
                    onCancel={() => show(null)}
                  />
                ) : (
                  <p className="actions">
                    <button
                      type="button"
                      className="secondary"
                      disabled={busy}
                      onClick={() => show('progress')}
                    >
                      {t('projects.items.progress.open')}
                    </button>
                  </p>
                )}
              </section>

              <section className="task-part">
                <h2>{t('projects.item.needs.title')}</h2>
                {open === 'needs' ? (
                  <RequirementForm
                    id="needs"
                    resources={resources}
                    needs={needs}
                    busy={busy}
                    banner={needing.message}
                    onSubmit={(wanted) => void require(wanted)}
                    onCancel={() => show(null)}
                  />
                ) : (
                  <>
                    {needs.length === 0 ? (
                      <p className="empty">{t('projects.item.needs.none')}</p>
                    ) : (
                      <ul className="requirements">
                        {needs.map((need) => (
                          <li key={need.resourceId}>
                            {t(
                              need.resourceArchived
                                ? 'projects.item.needs.summaryPutAway'
                                : 'projects.item.needs.summary',
                              { name: need.resourceName, units: need.units }
                            )}
                          </li>
                        ))}
                      </ul>
                    )}
                    <p className="actions">
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => show('needs')}
                      >
                        {t('projects.items.needs.open')}
                      </button>
                    </p>
                  </>
                )}
              </section>

              <section className="task-part">
                <h2>{t('projects.item.order.title')}</h2>
                {open === 'order' ? (
                  <DependencyForm
                    // Remounted once an arrow has landed, which is what empties the boxes
                    // for the next one — this panel deliberately stays open, so it has
                    // nothing to be unmounted by. A refusal does not reload, so it leaves
                    // what was typed exactly where it is.
                    key={reloads}
                    id="order"
                    item={item}
                    candidates={siblings}
                    drawn={outgoing.get(item.id) ?? []}
                    titles={titles}
                    cycle={cycle}
                    busy={busy}
                    banner={ordering.message}
                    fieldErrors={ordering.fieldErrors}
                    onSubmit={(values) => void block(values)}
                    onRemove={(edge) => void unblock(edge)}
                    onCancel={() => show(null)}
                  />
                ) : (
                  <>
                    {shape.length === 0 ? (
                      <p className="empty">{t('projects.item.order.none')}</p>
                    ) : (
                      <ul className="shape">
                        {shape.map((line) => (
                          <li key={line}>{line}</li>
                        ))}
                      </ul>
                    )}
                    <p className="actions">
                      <button
                        type="button"
                        className="secondary"
                        disabled={busy}
                        onClick={() => show('order')}
                      >
                        {t('projects.items.blocks.open')}
                      </button>
                    </p>
                  </>
                )}
              </section>
            </>
          )}

          <p className="archive">
            <button
              type="button"
              className={away ? 'secondary' : 'danger'}
              disabled={busy}
              onClick={() => void setArchived(!away)}
            >
              {away
                ? t('projects.items.unarchive')
                : t('projects.items.archive')}
            </button>
            <span className="hint">{t('projects.item.archiveHint')}</span>
          </p>
        </>
      )}
    </main>
  );
}
