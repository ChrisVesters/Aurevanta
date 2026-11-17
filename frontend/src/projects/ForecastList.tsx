import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { useLoaded } from '../api/useLoaded';
import type { Resource } from '../resource/types';
import { Modal } from '../ui/Modal';
import { DATE_AT, USUAL_CONFIDENCE } from './confidence';
import { formatDay, formatMomentAt } from './dates';
import { ForecastAsk } from './ForecastAsk';
import {
  describeDrift,
  hours,
  readingOf,
  stretchAndGrowth
} from './forecastText';
import type { Forecast, ForecastHistory } from './forecastTypes';

/**
 * Every forecast made of a plan, newest first, and the one way to make another.
 *
 * **A list like every other list in this product**: a row per forecast with the day it was
 * made as the way in, and a button above it that opens the form in a `ui/Modal`. Each row
 * opens the forecast's own page, which is where it is read at a chosen confidence and where
 * the questions asked of it live. Until November 2026 this was three tabs about the newest
 * forecast only, with the form above the answer — which made the confidence control look
 * like an input to the button beside it.
 *
 * **Read at the usual confidence, and saying so.** A list has no room for a control, so each
 * row is the 80% reading with its percentage in the words; the page is where it moves.
 *
 * **Drift is said here**, above the list, because whether the date keeps moving out is a
 * property of the sequence rather than of any one forecast in it.
 */
export function ForecastList({ projectId }: { projectId: string }) {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [asking, setAsking] = useState(false);
  const { data: declared } = useLoaded<Resource[]>('/resources', []);
  const { data: history, failure } = useLoaded<ForecastHistory>(
    `/projects/${projectId}/forecasts`,
    [projectId]
  );
  const runs = history?.runs ?? null;
  const drifting = readingOf(history?.drift ?? null, USUAL_CONFIDENCE);

  return (
    <section className="forecasts">
      <p className="hint">{t('projects.forecast.list.lede')}</p>

      {/*
        Above the list, like the other three ways of adding to a list, because a plan's
        forecasts only accumulate.
      */}
      <p className="add">
        <button
          type="button"
          className="primary"
          onClick={() => setAsking(true)}
        >
          {t('projects.forecast.new.open')}
        </button>
      </p>

      <Modal
        title={t('projects.forecast.new.title')}
        open={asking}
        onClose={() => setAsking(false)}
      >
        {runs?.[0] && <p className="lede">{t('projects.forecast.new.lede')}</p>}
        <ForecastAsk
          projectId={projectId}
          pools={declared ?? []}
          previous={runs?.[0]}
          // Straight to what was just made: somebody who asked for a forecast wants to read
          // it, and its page is where it is read.
          onForecast={(made) => navigate(`/app/forecasts/${made.id}`)}
          onCancel={() => setAsking(false)}
        />
      </Modal>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      {drifting !== null && drifting.movingOut && (
        <p className="caveat">
          {describeDrift(t, drifting, USUAL_CONFIDENCE, i18n.language)}
        </p>
      )}

      {runs === null ? (
        !failure && (
          <p className="loading" role="status">
            {t('projects.forecast.list.loading')}
          </p>
        )
      ) : runs.length === 0 ? (
        <p className="empty">{t('projects.forecast.list.none')}</p>
      ) : (
        <ul className="forecast-list">
          {runs.map((run, index) => (
            <li key={run.id}>
              <span className="who">
                <span className="name">
                  <Link to={`/app/forecasts/${run.id}`}>
                    {t('projects.forecast.list.entry', {
                      day: formatMomentAt(run.createdAt, i18n.language)
                    })}
                  </Link>
                  {index === 0 && (
                    <span className="tag">
                      {t('projects.forecast.list.newest')}
                    </span>
                  )}
                </span>
                <span className="reading">{reading(run)}</span>
                <span className="description">
                  {t('projects.forecast.list.by', { who: run.requestedByName })}
                  {' · '}
                  {assumed(run)}
                </span>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );

  /**
   * What a run assumed, in one line. A question the run was not asked is named as left
   * out rather than printed as "up to 0%", which read as a claim about the team.
   */
  function assumed(run: Forecast) {
    const { worseBy, growthLow, growthHigh } = stretchAndGrowth(
      run,
      i18n.language
    );
    return t('projects.forecast.list.assumed', {
      capacity: run.capacity,
      stretch: run.limitations.includes('no_team_factor')
        ? t('projects.forecast.list.noStretch')
        : t('projects.forecast.list.stretch', { worseBy }),
      growth: run.limitations.includes('no_scope_uncertainty')
        ? t('projects.forecast.list.noGrowth')
        : t('projects.forecast.list.growth', { growthLow, growthHigh })
    });
  }

  /** The usual reading of one run: a day where it has one, its hours where it does not. */
  function reading(run: Forecast) {
    const day = run[DATE_AT[USUAL_CONFIDENCE]];
    return day === null
      ? t('projects.forecast.list.atHours', {
          confidence: USUAL_CONFIDENCE,
          hours: hours(run.p80Hours, i18n.language)
        })
      : t('projects.forecast.list.at', {
          confidence: USUAL_CONFIDENCE,
          date: formatDay(day, i18n.language)
        });
  }
}
