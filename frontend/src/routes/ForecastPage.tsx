import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams } from 'react-router';
import { useLoaded } from '../api/useLoaded';
import type { Calibration } from '../calibration/types';
import {
  CONFIDENCES,
  DATE_AT,
  HOURS_AT,
  USUAL_CONFIDENCE
} from '../projects/confidence';
import type { Confidence } from '../projects/confidence';
import { dayHere, formatMoment } from '../projects/dates';
import {
  calendarOf,
  describeDate,
  describeDrift,
  describeLimitation,
  describePool,
  hours,
  readingOf,
  stretchAndGrowth
} from '../projects/forecastText';
import type {
  Forecast,
  ForecastHistory,
  Movement,
  Throughput
} from '../projects/forecastTypes';
import { HiringPanel } from '../projects/HiringPanel';
import { MovementAsked } from '../projects/MovementAsked';
import { SpreadPanel } from '../projects/SpreadPanel';
import { TargetDate } from '../projects/TargetDate';
import { ThroughputComparison } from '../projects/ThroughputComparison';
import type { Project } from '../projects/types';

/** The five readings every forecast carries, top to bottom in the table. */
const READINGS = ['p10', 'p50', 'p80', 'p90', 'p95'] as const;

/**
 * One forecast, on the page that is only about it.
 *
 * **What belongs to the forecast is everything on this page, and nothing here makes a new
 * one.** That is the confusion it replaces: the form used to sit above the answer, and the
 * confidence control between them read as an input to the button. Making a forecast is the
 * Forecasts list's button; this page reads one that exists.
 *
 * **The confidence control is a way of reading it**, and everything with a number in it
 * follows: the headline date, the hours beneath it, the marked row of the five readings, the
 * history's date, what one more person buys and how far the date moved. Moving it sends no
 * request, because every reading is already in the answer. The band in hours stays on screen
 * at every confidence — remove it and nothing visible came out of the engine.
 *
 * **Then what it assumed and what it leaves out, under their own headings and never behind a
 * disclosure**, because a date read without them is this product's own failure mode.
 *
 * **Then the questions asked of it**, which work on any forecast and not only the newest:
 * each replays this run out of its own seed and writes nothing.
 *
 * **The second opinion is the history as of the day this forecast was made**, not as of
 * today — so an old forecast is compared with what was known when it was made, rather than
 * with a history that has had months to move since.
 *
 * Like a task, its address carries one identifier: the plan comes back on the run.
 */
export function ForecastPage() {
  const { runId } = useParams();
  const { t, i18n } = useTranslation();
  const [confidence, setConfidence] = useState<Confidence>(USUAL_CONFIDENCE);
  const [asking, setAsking] = useState(false);

  const { data: run, failure } = useLoaded<Forecast>(`/forecasts/${runId}`, [
    runId
  ]);
  const projectId = run?.projectId ?? null;
  const { data: project } = useLoaded<Project>(
    projectId === null ? null : `/projects/${projectId}`,
    [projectId]
  );
  const { data: history } = useLoaded<ForecastHistory>(
    projectId === null ? null : `/projects/${projectId}/forecasts`,
    [projectId]
  );
  const { data: track } = useLoaded<Calibration>('/calibration', []);
  const asOf = run ? dayHere(run.createdAt) : null;
  const { data: throughput, failure: throughputFailure } =
    useLoaded<Throughput>(
      projectId === null || asOf === null
        ? null
        : `/projects/${projectId}/throughput?asOf=${asOf}`,
      [projectId, asOf]
    );

  const runs = history?.runs ?? [];
  const at = run ? runs.findIndex((each) => each.id === run.id) : -1;
  const newest = at === 0;
  const before: Forecast | undefined = at >= 0 ? runs[at + 1] : undefined;

  const { data: movement, failure: movementFailure } = useLoaded<Movement>(
    asking && run && before
      ? `/forecasts/${run.id}/movement?since=${before.id}`
      : null,
    [asking, run?.id, before?.id]
  );

  // Inside the same page container as a plan and a task, so the space above the back link
  // and down the sides is the same on all three.
  if (failure) {
    return (
      <main className="projects">
        <p className="form-error" role="alert">
          {failure}
        </p>
      </main>
    );
  }
  if (!run) {
    return (
      <main className="projects">
        <p className="loading" role="status">
          {t('projects.forecast.page.loading')}
        </p>
      </main>
    );
  }

  const chosenDate = run[DATE_AT[confidence]];
  const calendar = calendarOf(run, i18n.language);
  const drifting = newest
    ? readingOf(history?.drift ?? null, confidence)
    : null;
  const madeOn = formatMoment(run.createdAt, i18n.language);

  return (
    <main className="projects">
      <section className="forecast-page forecast">
        {project && (
          <p className="back">
            <Link to={`/app/projects/${project.id}?tab=forecasts`}>
              {t('projects.forecast.page.back', { project: project.name })}
            </Link>
          </p>
        )}

        <h1>{t('projects.forecast.list.entry', { day: madeOn })}</h1>
        <p className="hint">
          {t('projects.forecast.page.by', { who: run.requestedByName })}{' '}
          {history &&
            (newest
              ? t('projects.forecast.page.newest')
              : t('projects.forecast.page.olderThan'))}
        </p>

        {/*
        Above the headline and nowhere else, because it is a way of reading what is
        below it. Absent rather than disabled on a run with no dates.
      */}
        {chosenDate !== null && (
          <fieldset className="confidence">
            <legend>{t('projects.forecast.confidence.legend')}</legend>
            <span className="choices">
              {CONFIDENCES.map((level) => (
                <label key={level} htmlFor={`forecast-confidence-${level}`}>
                  <input
                    id={`forecast-confidence-${level}`}
                    type="radio"
                    name="confidence"
                    checked={level === confidence}
                    onChange={() => setConfidence(level)}
                  />
                  {t('projects.forecast.confidence.option', { value: level })}
                </label>
              ))}
            </span>
          </fieldset>
        )}

        <p className="date">
          {describeDate(t, run, project?.name ?? '', confidence, i18n.language)}
        </p>

        <p className="band">
          {t('projects.forecast.page.hoursAt', {
            confidence,
            hours: hours(run[HOURS_AT[confidence]], i18n.language)
          })}
        </p>

        <p className="coverage">
          {t('projects.coverage', {
            estimated: run.estimatedItemCount,
            total: run.itemCount
          })}
        </p>

        {/*
        Beside the date of the newest forecast only: whether the date keeps moving out is
        a property of the sequence, and the newest is where the sequence ends.
      */}
        {drifting !== null && drifting.movingOut && (
          <p className="caveat">
            {describeDrift(t, drifting, confidence, i18n.language)}
          </p>
        )}

        {/* The five readings, with the one the control is on marked. */}
        <table className="percentiles">
          <tbody>
            {READINGS.map((point) => (
              <tr
                key={point}
                className={point === `p${confidence}` ? 'chosen' : undefined}
                aria-current={point === `p${confidence}` ? 'true' : undefined}
              >
                <th scope="row">
                  {t(`projects.forecast.percentiles.${point}`)}
                </th>
                <td>
                  {t('projects.forecast.hours', {
                    value: hours(run[`${point}Hours`], i18n.language)
                  })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>

        <h2>{t('projects.forecast.page.assumed')}</h2>
        <p className="assumptions">
          {t('projects.forecast.assumptions', {
            capacity: run.capacity,
            samples: run.sampleCount,
            ...stretchAndGrowth(run, i18n.language)
          })}
        </p>
        {run.resources.length > 0 && (
          <p className="assumptions">
            {t('projects.forecast.resources.scheduledAgainst', {
              team: run.resources
                .map((pool) => describePool(t, pool))
                .join(', ')
            })}
          </p>
        )}
        {calendar && (
          <p className="assumptions">
            {t('projects.forecast.calendar', calendar)}
          </p>
        )}
        {/*
        What the ranges feeding it have historically been worth: a caveat about the inputs,
        never a correction to the number. Absent when nothing has been scored.
      */}
        {track?.forecasts.rate != null && (
          <p className="caveat">
            {t('projects.forecast.trackRecord.line', {
              rate: Math.round(track.forecasts.rate.value * 100),
              scored: track.forecasts.scored
            })}{' '}
            <Link to="/app/calibration">
              {t('projects.forecast.trackRecord.link')}
            </Link>
          </p>
        )}

        <h2>{t('projects.forecast.page.leavesOut')}</h2>
        {run.limitations.length === 0 ? (
          <p className="empty">{t('projects.forecast.page.nothingLeftOut')}</p>
        ) : (
          <ul className="limitations">
            {run.limitations.map((limitation) => (
              <li key={limitation}>{describeLimitation(t, limitation)}</li>
            ))}
          </ul>
        )}

        {(throughput !== null || throughputFailure !== null) && (
          <>
            <h2>{t('projects.forecast.page.secondOpinion')}</h2>
            <p className="hint">
              {t('projects.forecast.page.secondOpinionLede')}
            </p>
            <ThroughputComparison
              history={throughput}
              failure={throughputFailure}
              run={run}
              confidence={confidence}
            />
          </>
        )}

        <h2>{t('projects.forecast.page.explore.title')}</h2>
        <p className="hint">{t('projects.forecast.page.explore.lede')}</p>
        <SpreadPanel key={run.id} runId={run.id} />
        <TargetDate key={`target-${run.id}`} run={run} />
        <HiringPanel
          key={`hiring-${run.id}`}
          run={run}
          confidence={confidence}
        />

        {history && (
          <>
            <h2>{t('projects.forecast.page.moved.title')}</h2>
            {before === undefined ? (
              <p className="empty">{t('projects.forecast.page.moved.first')}</p>
            ) : (
              <>
                <p className="hint">
                  {t('projects.forecast.page.moved.since', {
                    day: formatMoment(before.createdAt, i18n.language),
                    who: before.requestedByName
                  })}
                </p>
                {chosenDate !== null && before[DATE_AT[confidence]] !== null ? (
                  <MovementAsked
                    asking={asking}
                    account={readingOf(movement, confidence)}
                    simulations={movement?.simulations ?? 0}
                    failure={movementFailure}
                    onAsk={() => setAsking(true)}
                  />
                ) : (
                  <p className="empty">
                    {t('projects.forecast.page.moved.noDate')}
                  </p>
                )}
              </>
            )}
          </>
        )}
      </section>
    </main>
  );
}
