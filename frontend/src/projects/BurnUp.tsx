import { useTranslation } from 'react-i18next';
import { formatDay } from './dates';
import type { BurnUp } from './forecastTypes';

/**
 * What has been delivered and what is left: the numbers, and then a picture of them.
 *
 * **The table is the feature and the drawing is the enhancement, which is the opposite way
 * round from how a chart is usually built.** A cone described in words has to be understood
 * before it can be described, which is a better test of whether it is worth drawing than
 * drawing it is; and `roadmap.md` warns that charts built before the interface rework get
 * built twice, so the half that survives one is the half built first.
 *
 * **The drawing is `aria-hidden` and carries nothing the table does not.** A picture and its
 * equivalent saying the same thing twice to a screen reader is worse than either alone.
 */
export function BurnUpFigure({ burnUp }: { burnUp: BurnUp }) {
  const { t, i18n } = useTranslation();
  const cone = burnUp.cone;
  // The first point of the cone is today with nothing yet delivered, which is the last row
  // of the past under a different name. It joins the two halves of the drawing and would be
  // a repeated line in the table, so it is in one and not the other.
  const ahead = cone === null ? [] : cone.slice(1);

  return (
    <div className="burnup">
      <h4>{t('projects.forecast.throughput.burnUp.title')}</h4>
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

      <table className="weeks">
        <caption>
          {t('projects.forecast.throughput.burnUp.caption', {
            past: t('projects.forecast.throughput.burnUp.captionPast', {
              count: burnUp.past.length
            }),
            ahead: t('projects.forecast.throughput.burnUp.captionAhead', {
              count: ahead.length
            })
          })}
        </caption>
        <thead>
          <tr>
            <th scope="col">{t('projects.forecast.throughput.burnUp.week')}</th>
            <th scope="col">
              {t('projects.forecast.throughput.burnUp.count')}
            </th>
            <th scope="col">
              {t('projects.forecast.throughput.burnUp.range')}
            </th>
          </tr>
        </thead>
        <tbody>
          {burnUp.past.map((week) => (
            <tr key={week.week}>
              <th scope="row">{formatDay(week.week, i18n.language)}</th>
              <td>{week.delivered}</td>
              <td />
            </tr>
          ))}
        </tbody>
        {/*
          Its own group with its own heading, because the range column alone is a thin thing
          to hang "this one has not happened yet" on — and thinner still read out one cell at
          a time.
        */}
        {ahead.length > 0 && (
          <tbody>
            <tr>
              <th scope="rowgroup" colSpan={3}>
                {t('projects.forecast.throughput.burnUp.projected')}
              </th>
            </tr>
            {ahead.map((week) => (
              <tr key={week.week}>
                <th scope="row">{formatDay(week.week, i18n.language)}</th>
                <td>{week.p50}</td>
                <td>
                  {t('projects.forecast.throughput.burnUp.band', {
                    low: week.p10,
                    high: week.p90
                  })}
                </td>
              </tr>
            ))}
          </tbody>
        )}
      </table>

      <BurnUpDrawing burnUp={burnUp} />
    </div>
  );
}

/** How much room the drawing is given, in its own coordinates rather than in pixels. */
const CHART = { width: 640, height: 160, top: 10, bottom: 10 };

/**
 * The same series as the table above, drawn.
 *
 * **Inline SVG and no chart library**, following the bars M6 and M8 already render: a
 * dependency whose styling a rework would have to fight is exactly what `roadmap.md` warns
 * about. It reads the CSS variables the rest of the product is coloured from, so it follows
 * a theme rather than pinning one.
 *
 * **The band closes because the backlog is a ceiling, not because the uncertainty falls
 * away.** Every run stops when it covers the work, so they all arrive at the same number.
 * The line across the top is that ceiling, drawn so the narrowing reads as what it is.
 */
function BurnUpDrawing({ burnUp }: { burnUp: BurnUp }) {
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

  return (
    <svg
      className="drawing"
      viewBox={`0 0 ${CHART.width} ${CHART.height}`}
      aria-hidden="true"
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
      {cone.length > 1 && (
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
  );
}
