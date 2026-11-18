import { useMemo } from 'react';
import type { ChangeEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import { useLoaded } from '../api/useLoaded';
import { OrderBox, OrderDrawing } from './OrderDrawing';
import { arrange, chainThrough } from './order';
import type { OrderedTask } from './order';
import { FOCUS_PARAM } from './tabs';
import type { Dependency, Estimate, WorkItem } from './types';
import { byEnd, describeShape } from './work';

/**
 * What a plan's work waits on, read as a whole.
 *
 * **The drawing is the whole statement of the order.** It was built text-first, with a
 * step-by-step list under the drawing, and the product owner removed the list in November
 * 2026 as the burn-up's table was removed. Unlike the burn-up, nothing was lost to a screen
 * reader: the drawing's boxes are a list in step order, each carrying its step, its status,
 * whether it can start now, whether anybody has estimated it, and both of its directions.
 *
 * **There is no time in it.** No date is read, no effort figure is shown, and the engine is
 * never asked: a step is "after what comes before". Single-point dates are what this product
 * exists to replace, and a Gantt drawn from this data would be one.
 *
 * **It writes nothing.** A task's own page is where its arrows are drawn, so every title is a
 * link there, and the only control on this screen chooses what to look at.
 *
 * Three reads, all the plan screen already makes, each through `useLoaded`. Nothing is shown
 * until all three have answered, and if any fails the first complaint stands alone: half a
 * plan would be a picture of an order nobody set.
 */
export function OrderView({ projectId }: { projectId: string }) {
  const { t } = useTranslation();
  const { account } = useAuth();
  const [params, setParams] = useSearchParams();
  const deps = [projectId, account?.organisation.id];
  const work = useLoaded<WorkItem[]>(`/projects/${projectId}/items`, deps);
  const arrows = useLoaded<Dependency[]>(
    `/projects/${projectId}/dependencies`,
    deps
  );
  const estimates = useLoaded<Estimate[]>(
    `/projects/${projectId}/estimates`,
    deps
  );

  const arranged = useMemo(
    () =>
      work.data && arrows.data && estimates.data
        ? arrange(
            work.data,
            arrows.data,
            new Set(estimates.data.map((estimate) => estimate.itemId))
          )
        : null,
    [work.data, arrows.data, estimates.data]
  );

  const focusId = params.get(FOCUS_PARAM);
  const chain = useMemo(
    () =>
      arranged?.kind === 'ok' && focusId !== null
        ? chainThrough(arranged, focusId)
        : null,
    [arranged, focusId]
  );

  const failure = work.failure ?? arrows.failure ?? estimates.failure;
  if (failure) {
    return (
      <p className="form-error" role="alert">
        {failure}
      </p>
    );
  }
  if (arranged === null || !work.data || !arrows.data) {
    return (
      <p className="loading" role="status">
        {t('projects.order.loading')}
      </p>
    );
  }
  if (arranged.kind === 'cycle') {
    return (
      <p className="form-error" role="alert">
        {t('projects.order.cycle')}
      </p>
    );
  }
  if (work.data.length === 0) {
    return <p className="empty">{t('projects.order.nothing')}</p>;
  }

  const { steps, notOrdered, edges } = arranged;
  const titles = new Map(work.data.map((item) => [item.id, item.title]));
  const outgoing = byEnd(arrows.data, 'predecessorItemId');
  const incoming = byEnd(arrows.data, 'successorItemId');

  /** Written with `replace` like the tab, and absent for the whole plan. */
  const focusOn = (id: string | null) => {
    const updated = new URLSearchParams(params);
    if (id === null) {
      updated.delete(FOCUS_PARAM);
    } else {
      updated.set(FOCUS_PARAM, id);
    }
    setParams(updated, { replace: true });
  };

  const shape = (id: string) =>
    describeShape(t, id, outgoing, incoming, titles);

  if (steps.length === 0) {
    return (
      <section className="order">
        <p className="empty">{t('projects.order.noOrder')}</p>
        <NotOrdered tasks={notOrdered} shape={shape} />
      </section>
    );
  }

  const shown = chain ?? { steps, edges };
  const focused = chain !== null;
  const focusTitle = focusId === null ? '' : (titles.get(focusId) ?? '');

  return (
    <section className="order">
      <p className="hint">{t('projects.order.lede')}</p>

      <p className="order-focus">
        <label>
          {t('projects.order.focus.label')}
          <select
            value={focused ? (focusId as string) : ''}
            onChange={(event: ChangeEvent<HTMLSelectElement>) =>
              focusOn(event.target.value === '' ? null : event.target.value)
            }
          >
            <option value="">{t('projects.order.focus.whole')}</option>
            {work.data.map((item) => (
              <option key={item.id} value={item.id}>
                {item.title}
              </option>
            ))}
          </select>
        </label>
      </p>

      {focusId !== null && !focused && (
        <p className="notice" role="status">
          {t('projects.order.focus.unknown')}
        </p>
      )}

      {focused ? (
        <p className="order-focused" role="status">
          {t('projects.order.focus.showing', { title: focusTitle })}{' '}
          <button
            type="button"
            className="secondary"
            onClick={() => focusOn(null)}
          >
            {t('projects.order.focus.clear')}
          </button>
        </p>
      ) : (
        <p className="hint">
          {t('projects.order.summary', {
            count: steps.flat().length,
            steps: t('projects.order.steps', { count: steps.length })
          })}
        </p>
      )}

      <OrderDrawing steps={shown.steps} edges={shown.edges} shape={shape} />

      {!focused && <NotOrdered tasks={notOrdered} shape={shape} />}
    </section>
  );
}

/**
 * Work with no live arrow, collected apart so that it cannot bury the structure — drawn as
 * the same boxes as the order, flowing in a grid since there is no arrow to place them by.
 */
function NotOrdered({
  tasks,
  shape
}: {
  tasks: OrderedTask[];
  shape: (id: string) => string[];
}) {
  const { t } = useTranslation();
  if (tasks.length === 0) {
    return null;
  }
  return (
    <section className="order-unordered">
      <h3>{t('projects.order.notOrdered.title', { count: tasks.length })}</h3>
      <p className="hint">{t('projects.order.notOrdered.hint')}</p>
      <ul className="order-loose">
        {tasks.map((task) => (
          <OrderBox
            key={task.item.id}
            task={task}
            said={shape(task.item.id)}
            // Ordered by nothing live, though it had an arrow once: said, so a task that
            // looks isolated is not taken to have never been joined to anything.
            note={
              task.waitsOnPutAway || task.holdsUpPutAway
                ? t('projects.order.putAwayOnly')
                : undefined
            }
          />
        ))}
      </ul>
    </section>
  );
}
