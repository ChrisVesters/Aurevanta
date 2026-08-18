import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { formatMoment } from '../projects/dates';
import type {
  Calibration,
  CalibrationRecord,
  MethodCalibration
} from './types';

/** The ways a range has been asked for that this version has wording for. */
const METHODS = ['three_point', 'surprise_framed'] as const;

/**
 * How often this organisation's ranges contained the truth.
 *
 * **Three things about this screen are decisions rather than layout.**
 *
 * **The rate never appears alone.** Its count and its interval are on the same line, because
 * four hits out of five is 80% and is consistent with a team at 51% and one at 94%. And the
 * band-width reading is always beside it, because a hit rate on its own is won by estimating
 * one to a thousand hours — which contains every outcome and predicts nothing. Publishing the
 * rate by itself would make the gameable half the easy half to show.
 *
 * **Nothing here names a percentile.** Where the truth typically lands inside somebody's own
 * range is shown as a *position* — a marker on a bar between the two ends the estimate form
 * actually asks for — rather than as a number that would have to be explained. That is the
 * same refusal `EstimateForm` makes when it declines to print "P90".
 *
 * **And no threshold lives in the browser.** Whether a record is good is not a judgement this
 * end may make: two rules about one estimate is what `EstimateQuality` exists to prevent. So
 * the page states what a well-judged set scores, shows what this one scored, and leaves the
 * subtraction to the reader.
 */
export function TrackRecord({ record }: { record: Calibration }) {
  const { t, i18n } = useTranslation();
  const forecasts = record.forecasts;
  // Pulled out once, because they are what decides whether each block exists — and because
  // reading them off the record inside the markup would put the same null check in four
  // places, which is three chances to render half of a pair.
  const rate = forecasts.rate;
  const corrections = forecasts.corrections;
  const anythingScored =
    forecasts.scored + record.reports.scored + record.unbounded.scored > 0;

  return (
    <div className="track-record">
      {rate !== null && (
        <section className="headline">
          <h2>{t('calibration.headline.title')}</h2>
          <p className="rate">
            {t('calibration.headline.rate', {
              rate: percent(rate.value, i18n.language)
            })}
          </p>
          {/*
            On the same line as the rate and never below it: a reader who has taken the
            number and moved on has taken the half of it that means nothing on its own.
          */}
          <p className="hint">
            {t('calibration.headline.confidence', {
              hits: forecasts.hits,
              scored: forecasts.scored,
              low: percent(rate.low, i18n.language),
              high: percent(rate.high, i18n.language)
            })}
          </p>
          <p className="hint">
            {t('calibration.headline.tails', {
              above: forecasts.aboveP90,
              below: forecasts.belowP10
            })}
          </p>
          <p className="caveat">{t('calibration.target')}</p>
        </section>
      )}

      {rate !== null &&
        (corrections === null ? (
          // A rate with one outcome behind it has no spread to report. Saying so is what
          // keeps the pairing honest: the rate is never left standing unqualified.
          <p className="empty">{t('calibration.headline.tooLittle')}</p>
        ) : (
          <section className="corrections">
            <h2>{t('calibration.corrections.title')}</h2>

            <h3>{t('calibration.corrections.biasTitle')}</h3>
            <p className="scale">
              <span className="end">{t('calibration.corrections.good')}</span>
              {/*
                A position rather than a number, so that nothing has to explain what a
                percentile is — and the two ends are the two questions somebody was
                actually asked. The tick at the middle is what a well-judged record looks
                like, which is how a reader sees the gap without this page judging it.
              */}
              <span className="track">
                <span className="middle" />
                <span
                  className="marker"
                  style={{
                    left: `${Math.round(corrections.medianPercentile * 100)}%`
                  }}
                />
              </span>
              <span className="end">{t('calibration.corrections.bad')}</span>
            </p>
            <p className="hint">{t('calibration.corrections.biasReading')}</p>

            <h3>{t('calibration.corrections.widthTitle')}</h3>
            <p className="hint">
              {t('calibration.corrections.widthReading', {
                multiplier: number(
                  corrections.bandWidthMultiplier,
                  i18n.language,
                  1
                )
              })}
            </p>

            {forecasts.pointEstimates > 0 && (
              <p className="caveat">
                {t('calibration.corrections.certain', {
                  count: forecasts.pointEstimates
                })}
              </p>
            )}
          </section>
        ))}

      {anythingScored && (
        <section className="buckets">
          <h2>{t('calibration.buckets.title')}</h2>
          <p className="hint">{t('calibration.buckets.lede')}</p>
          <ul>
            {(
              [
                ['forecasts', record.forecasts],
                ['reports', record.reports],
                ['unbounded', record.unbounded]
              ] as const
            ).map(([which, bucket]) => (
              <li key={which}>
                <span className="what">
                  {t(`calibration.buckets.${which}`)}
                  <span className="hint">
                    {t(`calibration.buckets.${which}Hint`)}
                  </span>
                </span>
                <span className="figure">{describeRate(t, i18n, bucket)}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {record.byMethod.length > 0 && (
        <section className="methods">
          <h2>{t('calibration.methods.title')}</h2>
          <p className="hint">{t('calibration.methods.lede')}</p>
          <ul>
            {record.byMethod.map((method) => (
              <li key={method.method}>
                <span className="what">{describeMethod(t, method)}</span>
                <span className="figure">
                  {describeRate(t, i18n, method.record)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {record.byEstimator.length > 0 && (
        <section className="estimators">
          <h2>{t('calibration.estimators.title')}</h2>
          <p className="hint">{t('calibration.estimators.lede')}</p>
          <ul>
            {record.byEstimator.map((estimator) => (
              <li key={estimator.estimatorId}>
                <span className="what">{estimator.estimatorName}</span>
                <span className="figure">
                  {describeRate(t, i18n, estimator.record)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="coverage">
        <h2>{t('calibration.coverage.title')}</h2>
        <ul>
          {record.coverage.completedItems === 0 ? (
            <li>{t('calibration.coverage.nothingFinished')}</li>
          ) : (
            <>
              {record.coverage.completedItems > record.coverage.withActual && (
                <li>
                  {t('calibration.coverage.noActual', {
                    count:
                      record.coverage.completedItems -
                      record.coverage.withActual
                  })}
                </li>
              )}
              {record.coverage.completedItems >
                record.coverage.withEstimate && (
                <li>
                  {t('calibration.coverage.noEstimate', {
                    count:
                      record.coverage.completedItems -
                      record.coverage.withEstimate
                  })}
                </li>
              )}
              <li>
                {t('calibration.coverage.scored', {
                  count: record.coverage.scoredItems
                })}
              </li>
            </>
          )}
          {record.coverage.movedByTheStartDay > 0 && (
            <li>
              {t('calibration.coverage.startDay', {
                count: record.coverage.movedByTheStartDay
              })}
            </li>
          )}
          {record.firstScored !== null && record.lastScored !== null && (
            <li>
              {t('calibration.coverage.span', {
                first: formatMoment(record.firstScored, i18n.language),
                last: formatMoment(record.lastScored, i18n.language)
              })}
            </li>
          )}
        </ul>
      </section>
    </div>
  );
}

/**
 * A bucket's rate with its count, or a plain "none yet".
 *
 * One function because the three buckets, the methods and the people are all the same
 * reading of the same shape, and three copies of "remember the count" is two chances to
 * forget it.
 */
function describeRate(
  t: TFunction,
  i18n: { language: string },
  record: CalibrationRecord
): string {
  if (record.rate === null) {
    return t('calibration.buckets.nothing');
  }
  return t('calibration.buckets.figure', {
    rate: percent(record.rate.value, i18n.language),
    count: record.scored
  });
}

/**
 * What a method is called here.
 *
 * A name this version has never heard of gets its own row and a phrase saying so, rather
 * than being dropped or having the server's own string printed — the rule
 * `describeLimitation` follows, and for the same reason: the server is what versions ahead
 * here, and silently showing nothing is that rule failing through the back door.
 */
function describeMethod(t: TFunction, method: MethodCalibration): string {
  return METHODS.includes(method.method as (typeof METHODS)[number])
    ? t(`calibration.methods.${method.method as (typeof METHODS)[number]}`)
    : t('calibration.methods.unknown');
}

function percent(value: number, locale: string): string {
  return number(value * 100, locale, 0);
}

function number(
  value: number,
  locale: string,
  maximumFractionDigits: number
): string {
  return new Intl.NumberFormat(locale, {
    maximumFractionDigits,
    minimumFractionDigits: maximumFractionDigits
  }).format(value);
}
