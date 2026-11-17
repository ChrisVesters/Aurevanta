import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useParams, useSearchParams } from 'react-router';
import { useLoaded } from '../api/useLoaded';
import type { Calibration } from '../calibration/types';
import {
  CONFIDENCES,
  DATE_AT,
  HOURS_AT,
  USUAL_CONFIDENCE
} from '../projects/confidence';
import type { Confidence } from '../projects/confidence';
import { dayHere, formatDay, formatMomentAt } from '../projects/dates';
import {
  calendarOf,
  describeDrift,
  describeLimitation,
  describePool,
  labelLimitation,
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
import { Help } from '../ui/Help';
import { Tabs } from '../ui/Tabs';

/**
 * The three parts of a forecast below its summary: what it assumed and leaves out, what it
 * is compared with, and the questions that can be asked of it.
 */
const FORECAST_VIEWS = ['details', 'compare', 'explore'] as const;

type ForecastView = (typeof FORECAST_VIEWS)[number];

const FIRST_VIEW: ForecastView = 'details';

/** The query parameter the part is kept in, so a link and a reload keep it. */
const VIEW_PARAM = 'view';

const VIEW_PANEL_ID = 'forecast-panel';

const viewId = (view: ForecastView) => `forecast-tab-${view}`;

/** The part a `?view=` names — or the first, for no value or one this page does not know. */
function viewFrom(value: string | null): ForecastView {
  return (FORECAST_VIEWS as readonly string[]).includes(value ?? '')
    ? (value as ForecastView)
    : FIRST_VIEW;
}

/**
 * One forecast, on the page that is only about it.
 *
 * **What belongs to the forecast is everything on this page, and nothing here makes a new
 * one.** That is the confusion it replaces: the form used to sit above the answer, and the
 * confidence control between them read as an input to the button. Making a forecast is the
 * Forecasts list's button; this page reads one that exists.
 *
 * **A summary, then three tabs.** The summary is the answer — the three readings worth
 * committing at, each with its date and each a way to choose it, the one sentence anybody
 * reads out and the hours it came from — and nothing below it is needed to read it. The
 * readings used to be a row of five beside a separate row of radio buttons, which said the
 * same thing twice; 10% and 90% went with it, being nothing anybody commits to. Until November 2026 the whole page was one column of
 * ten sections, and the product owner found it unreadable for the amount of prose in it.
 *
 * **The confidence control is a way of reading it**, and everything with a number in it
 * follows: the headline date, the hours beside it, the marked reading, the history's date,
 * what one more person buys and how far the date moved. Moving it sends no request, because
 * every reading is already in the answer. The hours stay on screen at every confidence —
 * remove them and nothing visible came out of the engine.
 *
 * **Details is the tab it opens on**, and holds what it assumed and what it leaves out. The
 * limitations are a few words each with the full sentence behind a "?": the caveat itself is
 * on screen, which is the rule, and only why it matters is a press away. **Compare** sets it
 * beside the plan's own history and the forecast before it; **Explore** holds the questions
 * asked of it, which work on any forecast and each replay this run out of its own seed. The
 * part is a `?view=` that replaces rather than pushes, for a plan's tabs' reason.
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
  const [params, setParams] = useSearchParams();

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
  const madeOn = formatMomentAt(run.createdAt, i18n.language);
  const view = viewFrom(params.get(VIEW_PARAM));
  const stretch = stretchAndGrowth(run, i18n.language);

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
          {t('projects.forecast.page.by', { who: run.requestedByName })}
          {history && (
            <span className={newest ? 'tag' : 'tag older'}>
              {newest
                ? t('projects.forecast.page.newest')
                : t('projects.forecast.page.olderThan')}
            </span>
          )}
        </p>

        {/*
          The answer, before anything else: a way of reading it, the one sentence anybody
          reads out, the hours it came from and the other readings beside it. Everything
          below the tabs is about this, and none of it is needed to read it.
        */}
        <div className="summary">
          {/*
            The readings and the way of choosing one are the same thing: each choice carries
            its own date, so the three are compared at a glance and pressing one reads the
            whole page at it. Hours stand in for a run with no dates, and the choice still
            moves them. The hours are on every choice rather than in a line of their own: they
            are what the engine produced, and the date is those hours with a calendar on top.
          */}
          <fieldset className="readings">
            <legend>
              {t('projects.forecast.confidence.legend')}
              <Help label={t('projects.forecast.page.readingsHelp')}>
                {t('projects.forecast.page.readingsExplain')}
              </Help>
            </legend>
            <span className="choices">
              {CONFIDENCES.map((level) => (
                <label
                  key={level}
                  htmlFor={`forecast-confidence-${level}`}
                  className={level === confidence ? 'chosen' : undefined}
                >
                  <input
                    id={`forecast-confidence-${level}`}
                    type="radio"
                    name="confidence"
                    checked={level === confidence}
                    aria-label={t('projects.forecast.confidence.option', {
                      value: level
                    })}
                    aria-describedby={`forecast-reading-${level}`}
                    onChange={() => setConfidence(level)}
                  />
                  <span className="level" aria-hidden="true">
                    {t('projects.forecast.confidence.option', {
                      value: level
                    })}
                  </span>
                  <span className="reading" id={`forecast-reading-${level}`}>
                    {run[DATE_AT[level]] !== null && (
                      <span className="day">
                        {formatDay(
                          run[DATE_AT[level]] as string,
                          i18n.language
                        )}
                      </span>
                    )}
                    <span className="hours">
                      {t('projects.forecast.hours', {
                        value: hours(run[HOURS_AT[level]], i18n.language)
                      })}
                    </span>
                  </span>
                </label>
              ))}
            </span>
          </fieldset>

          {/*
            No sentence repeating the chosen card's date — the card already says it. Only a
            run with no dates gets a line, saying why it has none.
          */}
          {chosenDate === null && (
            <p className="date">
              {run.workingHoursPerDay === null
                ? t('projects.forecast.confidence.noCalendar')
                : t('projects.forecast.confidence.unreadableCalendar')}
            </p>
          )}

          {/*
            Beside the date of the newest forecast only: whether the date keeps moving out is
            a property of the sequence, and the newest is where the sequence ends.
          */}
          {drifting !== null && drifting.movingOut && (
            <p className="caveat">
              {describeDrift(t, drifting, confidence, i18n.language)}
            </p>
          )}
        </div>

        <Tabs
          tabs={FORECAST_VIEWS}
          tab={view}
          label={t('projects.forecast.page.tabs.label')}
          nameOf={(name) => t(`projects.forecast.page.tabs.${name}`)}
          idOf={viewId}
          panelId={VIEW_PANEL_ID}
          onChange={show}
        />

        <div
          className="forecast-panel"
          id={VIEW_PANEL_ID}
          role="tabpanel"
          aria-labelledby={viewId(view)}
        >
          {view === 'details' && (
            <>
              <h2>{t('projects.forecast.page.assumed')}</h2>
              <ul className="facts">
                {/*
                  How much of the plan it could read, first: everything below is about the
                  work that carried an estimate.
                */}
                <li>
                  {t('projects.coverage', {
                    estimated: run.estimatedItemCount,
                    total: run.itemCount
                  })}
                </li>
                <li>
                  {run.resources.length > 0
                    ? t('projects.forecast.page.assume.team', {
                        team: run.resources
                          .map((pool) => describePool(t, pool))
                          .join(', ')
                      })
                    : t('projects.forecast.page.assume.capacity', {
                        capacity: run.capacity
                      })}
                </li>
                {/*
                  Not where the run left it out: "up to 0% longer" here and "No bad
                  stretches" under *What it leaves out* said one thing twice, the first time
                  as a number that read like a claim. The caveat is the one that stays.
                */}
                {!run.limitations.includes('no_team_factor') && (
                  <li>
                    {t('projects.forecast.page.assume.stretch', {
                      worseBy: stretch.worseBy
                    })}
                  </li>
                )}
                {!run.limitations.includes('no_scope_uncertainty') && (
                  <li>
                    {t('projects.forecast.page.assume.growth', {
                      growthLow: stretch.growthLow,
                      growthHigh: stretch.growthHigh
                    })}
                  </li>
                )}
                {calendar && (
                  <li>
                    {t('projects.forecast.page.assume.calendar', calendar)}
                  </li>
                )}
                <li>
                  {t('projects.forecast.page.assume.samples', {
                    samples: run.sampleCount
                  })}
                </li>
              </ul>

              <h2>{t('projects.forecast.page.leavesOut')}</h2>
              {run.limitations.length === 0 ? (
                <p className="empty">
                  {t('projects.forecast.page.nothingLeftOut')}
                </p>
              ) : (
                // A few words each, on screen; why each matters is behind its "?".
                <ul className="limitations">
                  {run.limitations.map((limitation) => {
                    const label = labelLimitation(t, limitation);
                    return (
                      <li key={limitation}>
                        {label}
                        <Help
                          label={t('projects.forecast.limitationHelp', {
                            label
                          })}
                        >
                          {describeLimitation(t, limitation)}
                        </Help>
                      </li>
                    );
                  })}
                </ul>
              )}

              {/*
                What the ranges feeding it have historically been worth: a caveat about the
                inputs, never a correction to the number. Absent when nothing is scored.
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
            </>
          )}

          {view === 'compare' && (
            <>
              {(throughput !== null || throughputFailure !== null) && (
                <ThroughputComparison
                  history={throughput}
                  failure={throughputFailure}
                  run={run}
                  confidence={confidence}
                />
              )}

              {history && (
                <div className="moved">
                  <h2>{t('projects.forecast.page.moved.title')}</h2>
                  {before === undefined ? (
                    <p className="empty">
                      {t('projects.forecast.page.moved.first')}
                    </p>
                  ) : (
                    <>
                      <p className="hint">
                        {t('projects.forecast.page.moved.since', {
                          day: formatMomentAt(before.createdAt, i18n.language),
                          who: before.requestedByName
                        })}
                      </p>
                      {chosenDate !== null &&
                      before[DATE_AT[confidence]] !== null ? (
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
                </div>
              )}
            </>
          )}

          {view === 'explore' && (
            <>
              <SpreadPanel key={run.id} runId={run.id} />
              <TargetDate key={`target-${run.id}`} run={run} />
              <HiringPanel
                key={`hiring-${run.id}`}
                run={run}
                confidence={confidence}
              />
            </>
          )}
        </div>
      </section>
    </main>
  );

  /** Moves to another part, replacing the address rather than pushing, like a plan's tabs. */
  function show(next: ForecastView) {
    const updated = new URLSearchParams(params);
    if (next === FIRST_VIEW) {
      updated.delete(VIEW_PARAM);
    } else {
      updated.set(VIEW_PARAM, next);
    }
    setParams(updated, { replace: true });
  }
}
