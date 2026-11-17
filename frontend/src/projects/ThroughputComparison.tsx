import { useTranslation } from 'react-i18next';
import { Help } from '../ui/Help';
import { BurnUpFigure } from './BurnUp';
import { DATE_AT } from './confidence';
import type { Confidence } from './confidence';
import { formatDay } from './dates';
import {
  describeDifference,
  describeEstimated,
  describeGrowth,
  describeNoSecondOpinion
} from './forecastText';
import type { Forecast, Throughput } from './forecastTypes';

/**
 * What the plan's own history says, beside what its estimates say.
 *
 * **Both dates or neither, and the window either way.** A plan with too little history, one
 * with nothing left, and one whose rate would not clear the backlog in ten years each say
 * which they are rather than showing a gap — and each still shows the weeks behind the
 * answer, because the window is the half a reader can judge for themselves. It is also the
 * only thing on screen that reports what a bootstrap cannot see: it can never draw a week
 * worse than the worst one in the window, so somebody who knows their team stops for a week
 * each quarter can tell whether that week is in there.
 */
export function ThroughputComparison({
  history,
  failure,
  run,
  confidence
}: {
  history: Throughput | null;
  failure: string | null;
  run: Forecast;
  confidence: Confidence;
}) {
  const { t, i18n } = useTranslation();

  // Where the second date comes from, behind a "?" beside the heading rather than as a
  // paragraph under it.
  const heading = (
    <div className="titled">
      <h2>{t('projects.forecast.throughput.title')}</h2>
      <Help label={t('projects.forecast.throughput.help')}>
        {t('projects.forecast.throughput.lede')}
      </Help>
    </div>
  );

  if (history === null) {
    return (
      <div className="throughput">
        {heading}
        <p className="empty">{failure}</p>
      </div>
    );
  }

  const theirs = history.projection?.[DATE_AT[confidence]] ?? null;
  const ours = run[DATE_AT[confidence]];

  return (
    <div className="throughput">
      {heading}

      {theirs === null ? (
        <p className="empty">{describeNoSecondOpinion(t, history)}</p>
      ) : (
        <>
          {/*
            The two dates as two labelled rows, at the confidence the page is read at, so the
            comparison is the first thing seen rather than something assembled from sentences.
          */}
          <dl className="versus">
            {ours !== null && (
              <div>
                <dt>{t('projects.forecast.throughput.fromEstimates')}</dt>
                <dd>
                  {t('projects.forecast.list.at', {
                    confidence,
                    date: formatDay(ours, i18n.language)
                  })}
                </dd>
              </div>
            )}
            <div>
              <dt>{t('projects.forecast.throughput.fromHistory')}</dt>
              <dd>
                {t('projects.forecast.list.at', {
                  confidence,
                  date: formatDay(theirs, i18n.language)
                })}
              </dd>
            </div>
          </dl>
          <p className="caveat">
            {ours !== null && `${describeDifference(t, ours, theirs)} `}
            {/*
              Named rather than subtracted into a figure. Two of these make the forecast look
              slow and two make it look fast, and the first two carry this run's own numbers
              because those are the ones somebody can act on. Behind a "?" because they
              explain the gap rather than qualify either date.
            */}
            <Help label={t('projects.forecast.throughput.differences.help')}>
              <span className="line">
                {describeGrowth(t, run, i18n.language)}
              </span>
              <span className="line">{describeEstimated(t, run)}</span>
              <span className="line">
                {t('projects.forecast.throughput.differences.calendar')}
              </span>
              <span className="line">
                {t('projects.forecast.throughput.differences.interruptions')}
              </span>
            </Help>
          </p>
          {/*
            The one limitation that qualifies an answer rather than replacing it: a reader has
            to be told that a short window is *why* the worst week may be missing. Whether it
            fires is the server's to decide — this end renders a flag it was sent.
          */}
          {history.limitations.includes('throughput_window_is_short') && (
            <p className="caveat">{t('projects.forecast.throughput.short')}</p>
          )}
        </>
      )}

      {/* What the history is made of, under both dates and whichever way it went. */}
      <p className="hint">
        {history.window !== null &&
          `${t('projects.forecast.throughput.window', {
            weeks: history.window.weeks,
            completed: history.window.completed,
            best: history.window.best,
            worst: history.window.worst
          })} `}
        {t('projects.forecast.throughput.remaining', {
          count: history.remaining
        })}
      </p>

      {/*
        The picture, and the table it is a picture of. Both are the same numbers the date
        above came from — nothing here is a third forecast — and the table is built first
        because a cone that has to be seen to be understood is one this product cannot ship.
      */}
      {history.burnUp !== null && <BurnUpFigure burnUp={history.burnUp} />}
    </div>
  );
}
