import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { Modal } from '../ui/Modal';
import { ProgressForm } from './ProgressForm';
import type { ProgressValues } from './ProgressForm';
import { WorkItemForm } from './WorkItemForm';
import { byEnd, describeProgress, describeShape } from './work';
import type { Dependency, Estimate, WorkItem, WorkItemStatus } from './types';

type Values = { title: string; description: string | null };

/** The board's columns, in the order work moves through them. */
const COLUMNS: readonly WorkItemStatus[] = [
  'NOT_STARTED',
  'IN_PROGRESS',
  'DONE'
];

/** The progress form's fields, which a refusal from a drop may be about. */
const PROGRESS_FIELDS = ['status', 'actualEffortHours'];

/** A card dropped on a column, waiting for the dates the drop could not carry. */
type Move = { item: WorkItem; to: WorkItemStatus };

/**
 * The work inside one plan: what it is made of, what each person thinks it will take, how
 * far along it is, and where each piece sits among the others.
 *
 * **A list, and no longer five forms per row.** Every way of changing a task moved to the
 * task's own page — see `routes/WorkItemPage.tsx` — and each row's title is the link to it.
 * What is left here is the two things that are about the *list* rather than about one task:
 * writing a new one down, and putting one away. A plan holds up to five hundred rows, and
 * five buttons on each of them made the shape of the plan the hardest thing on the page to
 * read.
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
  const [archived, setArchived] = useState(false);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  /**
   * Whether somebody is writing a task down.
   *
   * The form used to sit under the list, which put every row in the plan between somebody
   * and the only way to add to it, and left an empty form on screen for everybody who was
   * not filling one in. It is asked for now — and asked for *over* the list rather than in
   * it, because a new task is not compared with anything: what it needs is two boxes, and
   * the rows underneath say nothing about how to fill them.
   */
  const [writing, setWriting] = useState(false);
  const adding = useFormFailure(['title', 'description']);
  /**
   * The card being dragged, and the column it is over. Held here rather than read back out
   * of the drag's `dataTransfer`, which a browser hides from `dragover` — and a column has
   * to know during the drag whether the card belongs in it.
   */
  const [dragging, setDragging] = useState<WorkItem | null>(null);
  const [over, setOver] = useState<WorkItemStatus | null>(null);
  /**
   * A drop waiting on its dates. **A drop is not a save**: work in progress needs the day
   * it started and finished work the day it finished, and the server refuses rather than
   * stamping its own clock. So a drop opens the progress form with the column's status
   * already chosen, and nothing is written until somebody presses save on it.
   */
  const [move, setMove] = useState<Move | null>(null);
  const reporting = useFormFailure(PROGRESS_FIELDS);

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

  /** What this reader last said about each item, which is what a row leads with. */
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
        // Closed only once the server has taken it. A refusal leaves the form up holding
        // what was typed, which is the whole reason this is state and not the dialog's own
        // idea of whether it is open: shutting on submit would ask somebody to type it all
        // again to read what was wrong with it.
        setWriting(false);
        reload();
      } catch (error) {
        adding.report(error);
      }
      setBusy(false);
    },
    [request, projectId, adding, reload]
  );

  const report = useCallback(
    async (item: WorkItem, values: ProgressValues) => {
      setBusy(true);
      reporting.clear();
      try {
        await request<WorkItem>(`/items/${item.id}/progress`, {
          method: 'PATCH',
          body: values
        });
        // Closed only once the server has taken it, for the add dialog's reason: a refusal
        // about a date belongs beside the box holding it, not on a closed dialog.
        setMove(null);
        reload();
      } catch (error) {
        reporting.report(error);
      }
      setBusy(false);
    },
    [request, reporting, reload]
  );

  /**
   * A card let go over a column. Over its own column it is nothing — the work is already
   * there — and anywhere else it opens the form that asks when it happened.
   */
  function drop(to: WorkItemStatus) {
    setOver(null);
    if (dragging !== null && dragging.status !== to) {
      reporting.clear();
      setMove({ item: dragging, to });
    }
    setDragging(null);
  }

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

  return (
    <section className="work">
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

      {/*
        Above the work rather than under it, because a plan holds five hundred items and the
        only way to add to one must not be a thing somebody scrolls to. Offered against the
        archived listing as well as the live one, which is the same decision `add` makes by
        switching back afterwards: writing something down while looking at what was put away
        is a plausible thing to do, and the way to serve it is to show the reader where their
        task went rather than to take the button away.
      */}
      <p className="add">
        <button
          type="button"
          className="primary"
          onClick={() => {
            // Cleared on the way in, so a refusal from the last attempt is not the first
            // thing on an empty form.
            adding.clear();
            setWriting(true);
          }}
        >
          {t('projects.items.add.open')}
        </button>
      </p>

      <Modal
        title={t('projects.items.add.title')}
        open={writing}
        onClose={() => setWriting(false)}
      >
        <WorkItemForm
          id="new-item"
          busy={busy}
          submit={t('projects.items.add.submit')}
          submitting={t('projects.items.add.submitting')}
          banner={adding.message}
          fieldErrors={adding.fieldErrors}
          onSubmit={(values) => void add(values)}
          onCancel={() => setWriting(false)}
        />
      </Modal>

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
      ) : archived ? (
        /*
          Put-away work is in no forecast and reports no progress, so it has no column to
          sit in. It stays a list.
        */
        <ul className="work-item-list">{items.map(card)}</ul>
      ) : (
        <>
          <p className="hint">{t('projects.items.board.hint')}</p>
          <div className="board">
            {COLUMNS.map((status) => {
              const column = items.filter((item) => item.status === status);
              const heading = `board-${status}`;
              return (
                <section
                  key={status}
                  aria-labelledby={heading}
                  className={over === status ? 'column over' : 'column'}
                  onDragOver={(event) => {
                    // Only a column the card could move to accepts it, which is what
                    // makes the browser show a drop is possible there and nowhere else.
                    if (dragging !== null && dragging.status !== status) {
                      event.preventDefault();
                      setOver(status);
                    }
                  }}
                  onDragLeave={() => setOver(null)}
                  onDrop={(event) => {
                    event.preventDefault();
                    drop(status);
                  }}
                >
                  <h3 id={heading}>
                    {t('projects.items.board.column', {
                      status: t(`projects.items.progress.status.${status}`),
                      count: column.length
                    })}
                  </h3>
                  {column.length === 0 ? (
                    <p className="empty">{t('projects.items.board.empty')}</p>
                  ) : (
                    <ul className="work-item-list">{column.map(card)}</ul>
                  )}
                </section>
              );
            })}
          </div>
        </>
      )}

      <Modal
        title={
          move === null
            ? ''
            : t('projects.items.board.move', { title: move.item.title })
        }
        open={move !== null}
        onClose={() => setMove(null)}
      >
        {move !== null && (
          <ProgressForm
            id="move"
            item={move.item}
            startAs={move.to}
            estimated={mine.has(move.item.id) || theirs.has(move.item.id)}
            busy={busy}
            banner={reporting.message}
            fieldErrors={reporting.fieldErrors}
            onSubmit={(values) => void report(move.item, values)}
            onCancel={() => setMove(null)}
          />
        )}
      </Modal>
    </section>
  );

  /**
   * One piece of work, as a card on the board or a row of the archived list. Only current
   * work can be dragged: a put-away task reports no progress.
   */
  function card(item: WorkItem) {
    return (
      <li
        key={item.id}
        draggable={!archived}
        className={dragging?.id === item.id ? 'dragging' : undefined}
        onDragStart={(event) => {
          // Firefox starts no drag without data, so the identifier goes in even though the
          // column reads the card from state.
          event.dataTransfer.setData('text/plain', item.id);
          event.dataTransfer.effectAllowed = 'move';
          setDragging(item);
        }}
        onDragEnd={() => {
          setDragging(null);
          setOver(null);
        }}
      >
        <span className="who">
          {/*
            The title is the way in, rather than a button beside it called "Open". A card is
            about one task and its name is what somebody is looking for. Marked as a link
            the way a plan's and a forecast's names are, so it reads as one.
          */}
          <span className="name">
            <Link to={`/app/items/${item.id}`}>{item.title}</Link>
          </span>
          {item.description && (
            <span className="description">{item.description}</span>
          )}
          {!archived && (
            <>
              <span className="estimate">{summary(item.id)}</span>
              {/*
                Not for work not yet begun: the column already says so, and it was the same
                two words on every card in it. Begun and finished work carry a date the
                column does not.
              */}
              {item.status !== 'NOT_STARTED' && (
                <span className="progress">
                  {describeProgress(t, i18n.language, item)}
                </span>
              )}
              {describeShape(t, item.id, outgoing, incoming, titles).map(
                (line) => (
                  <span className="shape" key={line}>
                    {line}
                  </span>
                )
              )}
            </>
          )}
        </span>

        {/*
          The one control left on a card, because it is about the plan rather than the task:
          putting something away is what takes it off the board.
        */}
        <button
          type="button"
          className="secondary"
          disabled={busy}
          // Named for anybody reading through a screen reader, where a column of identical
          // buttons says nothing about what each one would put away.
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
    );
  }

  /**
   * What the row says about its estimate: the reader's own range if they gave one, and
   * otherwise whose ranges exist, so a colleague's work is never invisible.
   *
   * Names rather than numbers, where the range is somebody else's. A row has one line for
   * this and a plan has five hundred rows; what everybody actually said is on the task's
   * own page, which has the room for it.
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
