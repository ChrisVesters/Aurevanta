import { useState } from 'react';
import type { PointerEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { formatDay } from './dates';
import type { BurnUp, BurnUpConeWeek } from './forecastTypes';

/**
 * What has been delivered and what is left: the numbers, and then a picture of them.
 *
 * **The table is the feature and the drawing is the enhancement, which is the opposite way
 * round from how a chart is usually built.** A cone described in words has to be understood
 * before it can be described, which is a better test of whether it is worth drawing than
 * drawing it is; and `roadmap.md` warns that charts built before the interface rework get
 * built twice, so the half that survives one is the half built first.
 *
 * **There is no table any more**, by the product owner's decision in November 2026: folded
 * under the drawing it was a section nobody opened, and unfolded it was forty rows. The
 * drawing is an image to a screen reader, named by one sentence saying how many weeks were
 * delivered and how many are projected; the sentence above it says how many items.
 */
export function BurnUpFigure({ burnUp }: { burnUp: BurnUp }) {
  const { t } = useTranslation();
  const cone = burnUp.cone;
  // The first point of the cone is today with nothing yet delivered, which is the last week
  // of the past under a different name: it joins the two halves and is not a week ahead.
  const ahead = cone === null ? [] : cone.slice(1);

  return (
    <div className="burnup">
      <h3>{t('projects.forecast.throughput.burnUp.title')}</h3>
      {/*
        **One sentence, and it does not name a second date.** The plan for this step proposed
        "the last is done between 12 October and 30 November", which is the two-sided form
        decision 2 exists to keep out — and the date it would have restated is already on
        screen one-sided three lines above. What is left is the half nothing else says.
      */}
      <p className="hint">
        {t('projects.forecast.throughput.burnUp.delivered', {
          delivered: burnUp.delivered,
          total: burnUp.total
        })}
      </p>

      <BurnUpDrawing
        burnUp={burnUp}
        summary={t('projects.forecast.throughput.burnUp.caption', {
          past: t('projects.forecast.throughput.burnUp.captionPast', {
            count: burnUp.past.length
          }),
          ahead: t('projects.forecast.throughput.burnUp.captionAhead', {
            count: ahead.length
          })
        })}
      />
    </div>
  );
}

/**
 * How much room the drawing is given, in its own coordinates rather than in pixels. The top
 * leaves room for the label on the total line, which sits just above it.
 */
const CHART = { width: 640, height: 180, top: 24, bottom: 6 };

/** Where on the drawing a point falls, as a share of its width or height, for a label. */
const percent = (value: number, of: number) => `${(value / of) * 100}%`;

/**
 * Edge dates closer than this to "this week", as a share of the width, are left off rather
 * than printed over it.
 */
const CROWDED = 0.2;

/**
 * The same series as the table above, drawn.
 *
 * **Inline SVG and no chart library**, following the bars the contribution ranking and
 * calibration already render. It reads the CSS variables the rest of the product is coloured
 * from, so it follows a theme rather than pinning one.
 *
 * **Labelled in HTML laid over it, never in SVG text.** The drawing scales with its column,
 * and text inside it would scale too — to a few pixels on a phone. The labels are placed at
 * the same coordinates as a share of the drawing, so they stay where they belong at any width
 * and at the size of the text around them. A first version had no labels at all, on the
 * strength of the table beside it, and once the table was folded nobody could tell what the
 * shapes were.
 *
 * **The band closes because the backlog is a ceiling, not because the uncertainty falls
 * away.** Every run stops when it covers the work, so they all arrive at the same number.
 * The dashed line across the top is that ceiling, and is labelled as the plan's total.
 *
 * One image to a screen reader, labels and legend included, named by `summary`.
 */
function BurnUpDrawing({
  burnUp,
  summary
}: {
  burnUp: BurnUp;
  summary: string;
}) {
  const { t, i18n } = useTranslation();
  const cone = burnUp.cone ?? [];
  // One step per week across both halves, which they can share because both are weekly and
  // the cone begins in the week the past ends.
  const steps = Math.max(
    1,
    burnUp.past.length - 1 + Math.max(0, cone.length - 1)
  );
  const x = (week: number) => (week / steps) * CHART.width;
  const y = (delivered: number) =>
    CHART.height -
    CHART.bottom -
    (delivered / Math.max(1, burnUp.total)) *
      (CHART.height - CHART.top - CHART.bottom);
  const at = (week: number, delivered: number) => `${x(week)},${y(delivered)}`;
  const today = burnUp.past.length - 1;
  const now = today / steps;
  const projecting = cone.length > 1;
  const first = burnUp.past[0].week;
  const last = projecting ? cone[cone.length - 1].week : null;
  /** The week being pointed at, counted from the first; null while nothing is. */
  const [pointed, setPointed] = useState<number | null>(null);

  /** The week nearest the pointer, from where it is across the drawing. */
  function point(event: PointerEvent<HTMLDivElement>) {
    const box = event.currentTarget.getBoundingClientRect();
    const across = (event.clientX - box.left) / Math.max(1, box.width);
    setPointed(Math.round(Math.min(1, Math.max(0, across)) * steps));
  }

  return (
    <div className="chart" role="img" aria-label={summary}>
      {/*
        Pointing at the drawing names the week under the pointer and what it holds. A pointer
        and not a keyboard: to assistive technology the drawing is one image with one name.
      */}
      <div
        className="plot"
        onPointerMove={point}
        onPointerDown={point}
        onPointerLeave={() => setPointed(null)}
      >
        <svg
          className="drawing"
          viewBox={`0 0 ${CHART.width} ${CHART.height}`}
          focusable="false"
        >
          {/* The total, which is what the cone closes onto. */}
          <line
            className="ceiling"
            x1={0}
            x2={CHART.width}
            y1={y(burnUp.total)}
            y2={y(burnUp.total)}
          />
          {/* Where the past ends and the projection begins. */}
          {projecting && (
            <line
              className="now"
              x1={x(today)}
              x2={x(today)}
              y1={0}
              y2={CHART.height}
            />
          )}
          {projecting && (
            <>
              <polygon
                className="cone"
                points={[
                  ...cone.map((week, ahead) => at(today + ahead, week.p90)),
                  ...cone
                    .map((week, ahead) => at(today + ahead, week.p10))
                    .reverse()
                ].join(' ')}
              />
              <polyline
                className="middle"
                points={cone
                  .map((week, ahead) => at(today + ahead, week.p50))
                  .join(' ')}
              />
            </>
          )}
          <polyline
            className="delivered"
            points={burnUp.past
              .map((week, index) => at(index, week.delivered))
              .join(' ')}
          />
        </svg>
        <span
          className="label total"
          style={{ top: percent(y(burnUp.total), CHART.height) }}
        >
          {t('projects.forecast.throughput.burnUp.total', {
            count: burnUp.total
          })}
        </span>
        {pointed !== null && (
          <Pointed
            burnUp={burnUp}
            index={pointed}
            today={today}
            across={pointed / steps}
            down={(value) => y(value) / CHART.height}
          />
        )}
      </div>

      {/* Dates under the drawing: where it starts, this week, and where it stops. */}
      <div className="axis">
        {now >= CROWDED && (
          <span className="label start">{formatDay(first, i18n.language)}</span>
        )}
        {projecting && (
          <span className="label now" style={{ left: percent(now, 1) }}>
            {t('projects.forecast.throughput.burnUp.now')}
          </span>
        )}
        {(last === null || 1 - now >= CROWDED) && (
          <span className="label end">
            {formatDay(last ?? burnUp.past[today].week, i18n.language)}
          </span>
        )}
      </div>

      <ul className="legend">
        <li>
          <span className="swatch delivered" />
          {t('projects.forecast.throughput.burnUp.legend.delivered')}
        </li>
        {projecting && (
          <>
            <li>
              <span className="swatch middle" />
              {t('projects.forecast.throughput.burnUp.legend.likely')}
            </li>
            <li>
              <span className="swatch cone" />
              {t('projects.forecast.throughput.burnUp.legend.range')}
            </li>
          </>
        )}
      </ul>
    </div>
  );
}

/**
 * One week of the drawing, named: a guide down through it, a dot where the line is, and a
 * label with the week and its numbers. A week up to this one says what had been delivered; a
 * week after it says the most likely count and the range, which is what the wedge there is.
 *
 * The label turns to the left of the guide past the middle, so it never runs off the
 * drawing's right edge.
 *
 * @param across where the week is, as a share of the drawing's width
 * @param down where a count sits, as a share of the drawing's height
 */
function Pointed({
  burnUp,
  index,
  today,
  across,
  down
}: {
  burnUp: BurnUp;
  index: number;
  today: number;
  across: number;
  down: (value: number) => number;
}) {
  const { t, i18n } = useTranslation();
  const past = index <= today ? burnUp.past[index] : null;
  // Past the last week delivered, the drawing only goes on where there is a cone.
  const ahead =
    past === null ? (burnUp.cone as BurnUpConeWeek[])[index - today] : null;
  const value = past?.delivered ?? (ahead as BurnUpConeWeek).p50;
  const week = past?.week ?? (ahead as BurnUpConeWeek).week;

  return (
    <>
      <span className="guide" style={{ left: percent(across, 1) }} />
      <span
        className="dot"
        style={{ left: percent(across, 1), top: percent(down(value), 1) }}
      />
      <span
        className={across > 0.5 ? 'tip leftward' : 'tip'}
        style={{ left: percent(across, 1) }}
      >
        <strong>
          {t('projects.forecast.throughput.burnUp.tip.week', {
            date: formatDay(week, i18n.language)
          })}
        </strong>
        {past !== null
          ? t('projects.forecast.throughput.burnUp.tip.delivered', {
              count: past.delivered,
              total: burnUp.total
            })
          : t('projects.forecast.throughput.burnUp.tip.likely', {
              likely: (ahead as BurnUpConeWeek).p50,
              low: (ahead as BurnUpConeWeek).p10,
              high: (ahead as BurnUpConeWeek).p90,
              total: burnUp.total
            })}
      </span>
    </>
  );
}
