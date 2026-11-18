import { useMemo } from 'react';
import type { CSSProperties } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { BOX_HEIGHT, BOX_WIDTH, layout } from './order';
import type { Chain, OrderedTask } from './order';

type Props = Chain & {
  /** The `describeShape` lines for a task: what the curves into and out of it say. */
  shape: (id: string) => string[];
};

/**
 * A plan's order, drawn: a box per task and a curve per arrow, top to bottom.
 *
 * **It is the only statement of the order on screen**, since the step-by-step list under it
 * was removed (November 2026, by the product owner). So the boxes are a list in step order and
 * not a picture: each is a link in the tab order, and each carries, visually hidden, its step
 * and both directions of `describeShape` — which is what the curves say to somebody who can
 * see them. Only the curves and the wait labels are hidden from assistive technology, because
 * the hidden text already says both.
 *
 * **Vertical position means "after what is above it" and never a date.** Nothing here has
 * a scale, a duration or a figure of effort; the only number is an arrow's own wait.
 *
 * It is the burn-up's technique — no library, HTML laid over an SVG — at a fixed pixel size
 * inside a box that scrolls, since a long chain is wider than any screen and shrinking it
 * until it fits would shrink the words with it.
 */
export function OrderDrawing({ steps, edges, shape }: Props) {
  const { t } = useTranslation();
  const drawn = useMemo(() => layout(steps, edges), [steps, edges]);

  return (
    <div className="order-drawing">
      <div
        className="order-canvas"
        style={{ width: drawn.width, height: drawn.height }}
      >
        <svg width={drawn.width} height={drawn.height} aria-hidden="true">
          <defs>
            <marker
              id="order-arrowhead"
              markerWidth="8"
              markerHeight="8"
              refX="8"
              refY="4"
              orient="auto"
            >
              <path d="M0,0 L8,4 L0,8 z" className="head" />
            </marker>
          </defs>
          {drawn.curves.map(({ id, from, to }) => {
            const middle = (from.y + to.y) / 2;
            return (
              <path
                key={id}
                className="curve"
                d={`M${from.x},${from.y} C${from.x},${middle} ${to.x},${middle} ${to.x},${to.y}`}
                markerEnd="url(#order-arrowhead)"
              />
            );
          })}
        </svg>

        <ol className="order-boxes" aria-label={t('projects.order.stepsLabel')}>
          {steps.flatMap((step, at) =>
            step.map((task) => {
              const box = drawn.boxes.get(task.item.id) as {
                x: number;
                y: number;
              };
              return (
                <OrderBox
                  key={task.item.id}
                  task={task}
                  style={{ left: box.x, top: box.y }}
                  said={[
                    t('projects.order.step', { n: at + 1 }),
                    ...shape(task.item.id)
                  ]}
                />
              );
            })
          )}
        </ol>

        {drawn.curves
          .filter((curve) => curve.lagHours > 0)
          .map((curve) => (
            <span
              key={curve.id}
              className="order-lag"
              aria-hidden="true"
              style={{ left: curve.label.x, top: curve.label.y }}
            >
              {t('projects.order.drawing.lag', { count: curve.lagHours })}
            </span>
          ))}
      </div>
    </div>
  );
}

/**
 * One task as a box: its title as a link to its page, a line of what it is, and — read out
 * and not shown — `said`, which is what the arrows around it say to somebody who can see
 * them. The drawing places it; the list of work with no arrow lets it flow.
 */
export function OrderBox({
  task,
  said,
  note,
  style
}: {
  task: OrderedTask;
  said: string[];
  /** Shown after the status, on the same line. */
  note?: string;
  style?: CSSProperties;
}) {
  const { t } = useTranslation();
  return (
    <li
      className="order-box"
      data-status={task.item.status}
      style={{ ...style, width: BOX_WIDTH, height: BOX_HEIGHT }}
    >
      <Link to={`/app/items/${task.item.id}`}>{task.item.title}</Link>
      <span className="meta">
        {t(`projects.items.progress.status.${task.item.status}`)}
        {task.canStartNow && ` · ${t('projects.order.canStartNow')}`}
        {!task.estimated && ` · ${t('projects.order.notEstimated')}`}
        {note && ` · ${note}`}
      </span>
      {said.length > 0 && (
        <span className="visually-hidden">{said.join('. ')}</span>
      )}
    </li>
  );
}
