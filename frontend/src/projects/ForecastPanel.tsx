import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import type { TFunction } from 'i18next';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { numberField } from './fields';
import type { Forecast, ForecastLimitation } from './types';

/**
 * Every box on this form, which is two duties in one list and they are the same list on
 * purpose: `useFormFailure` needs the names the visitor can actually see, and the request
 * body is exactly what was asked for. Every one of them is a number the server wants under
 * that name, so a field added here is sent — and anything that is *not* a number would have
 * to be built into the body some other way rather than added to this.
 */
const ASKED_FOR = [
  'capacity',
  'teamFactorWorseByPercent',
  'scopeGrowthP10Percent',
  'scopeGrowthP90Percent',
  'sampleCount'
];

const LIMITATIONS: ForecastLimitation[] = [
  'no_team_factor',
  'no_scope_uncertainty',
  'unestimated_items',
  'inconsistent_estimates',
  'dependencies_on_archived_work'
];

/**
 * What this plan is likely to take, and everything that number depends on.
 *
 * **The whole product is this panel**, and every screen before it existed to give it
 * something to read. It is also the first place where being wrong would not look like
 * being wrong: a forecast is plausible whether or not it is right, so what is on screen
 * beside the band matters as much as the band.
 *
 * **Three things are therefore never optional here.** The assumptions are asked for and
 * none of them pre-filled, because they move the answer more than anything else on this
 * screen and a box already filled in is a box nobody reads. The coverage is stated in
 * words, because a forecast that quietly covers less than the plan is the failure this
 * product exists to prevent. And the assumptions and limitations alike are printed next to
 * the number rather than behind a disclosure, because a number seen without its caveats is
 * already in somebody's slide.
 *
 * **Only the sample count sits behind the disclosure, and that is the test of where the
 * line is.** It is a statement about sampling error rather than about this team, and it
 * has a right answer for everybody who has not already decided otherwise. Nothing that is
 * required may go in there: a required box inside a collapsed section is a refusal about a
 * field the visitor cannot see.
 *
 * **No dates anywhere.** Hours of effort, until M4 makes a working day an assumption
 * somebody states rather than one this screen invents.
 */
export function ForecastPanel({ projectId }: { projectId: string }) {
  const { t, i18n } = useTranslation();
  const { request } = useAuth();
  const [runs, setRuns] = useState<Forecast[] | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  const asking = useFormFailure(ASKED_FOR);

  useEffect(() => {
    let cancelled = false;

    request<Forecast[]>(`/projects/${projectId}/forecasts`)
      .then((loaded) => {
        if (!cancelled) {
          setRuns(loaded);
          setFailure(null);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, projectId, reloads, t]);

  const run = useCallback(
    async (asked: Record<string, number | null>) => {
      setBusy(true);
      asking.clear();
      try {
        await request<Forecast>(`/projects/${projectId}/forecasts`, {
          method: 'POST',
          // Every box goes as null when it is empty rather than being left out. The
          // server reads an absent sample count as "the ordinary ten thousand", and the
          // other four have no reading at all — which is the whole point of them being
          // required, and would be quietly undone by a screen that filled one in.
          body: asked
        });
        setReloads((count) => count + 1);
      } catch (error) {
        asking.report(error);
      }
      setBusy(false);
    },
    [request, projectId, asking]
  );

  function ask(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    void run(
      Object.fromEntries(
        ASKED_FOR.map((field) => [field, numberField(values, field)])
      )
    );
  }

  const latest = runs?.[0];
  const earlier = runs?.slice(1) ?? [];

  return (
    <section className="forecast">
      <h2>{t('projects.forecast.title')}</h2>
      <p className="hint">{t('projects.forecast.lede')}</p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      <form onSubmit={ask} noValidate>
        {asking.message && (
          <p className="form-error" role="alert">
            {asking.message}
          </p>
        )}

        <span className="field">
          <label htmlFor="forecast-capacity">
            {t('projects.forecast.fields.capacity.label')}
          </label>
          <input
            id="forecast-capacity"
            name="capacity"
            type="number"
            inputMode="numeric"
            min="1"
            step="1"
            aria-invalid={asking.fieldErrors.capacity ? true : undefined}
          />
          <span className="hint">
            {t('projects.forecast.fields.capacity.hint')}
          </span>
          {asking.fieldErrors.capacity && (
            <span className="field-error">{asking.fieldErrors.capacity}</span>
          )}
        </span>

        {/*
          The two questions M3b exists to ask, in the only form anybody can answer them:
          a percentile of an outcome rather than a parameter of a distribution. Both are
          required and neither is pre-filled, because zero is a claim — that nothing goes
          wrong for everybody at once, and that nobody will discover anything — and a
          claim has to be made rather than inherited from a box somebody left alone.
        */}
        <span className="field">
          <label htmlFor="forecast-team-factor">
            {t('projects.forecast.fields.teamFactor.label')}
          </label>
          <input
            id="forecast-team-factor"
            name="teamFactorWorseByPercent"
            type="number"
            inputMode="decimal"
            min="0"
            max="200"
            step="5"
            aria-invalid={
              asking.fieldErrors.teamFactorWorseByPercent ? true : undefined
            }
          />
          <span className="hint">
            {t('projects.forecast.fields.teamFactor.hint')}
          </span>
          {asking.fieldErrors.teamFactorWorseByPercent && (
            <span className="field-error">
              {asking.fieldErrors.teamFactorWorseByPercent}
            </span>
          )}
        </span>

        {/*
          One question with two boxes, so it is one fieldset with one legend. The refusal
          for a range the wrong way round belongs to the pair rather than to either of
          them, and arrives in the banner above for exactly that reason.
        */}
        <fieldset className="range">
          <legend>{t('projects.forecast.fields.scopeGrowth.legend')}</legend>
          <span className="hint">
            {t('projects.forecast.fields.scopeGrowth.hint')}
          </span>
          <span className="pair">
            <span className="field">
              <label htmlFor="forecast-growth-low">
                {t('projects.forecast.fields.scopeGrowth.low')}
              </label>
              <input
                id="forecast-growth-low"
                name="scopeGrowthP10Percent"
                type="number"
                inputMode="decimal"
                min="0"
                max="200"
                step="5"
                aria-invalid={
                  asking.fieldErrors.scopeGrowthP10Percent ? true : undefined
                }
              />
              {asking.fieldErrors.scopeGrowthP10Percent && (
                <span className="field-error">
                  {asking.fieldErrors.scopeGrowthP10Percent}
                </span>
              )}
            </span>

            <span className="field">
              <label htmlFor="forecast-growth-high">
                {t('projects.forecast.fields.scopeGrowth.high')}
              </label>
              <input
                id="forecast-growth-high"
                name="scopeGrowthP90Percent"
                type="number"
                inputMode="decimal"
                min="0"
                max="200"
                step="5"
                aria-invalid={
                  asking.fieldErrors.scopeGrowthP90Percent ? true : undefined
                }
              />
              {asking.fieldErrors.scopeGrowthP90Percent && (
                <span className="field-error">
                  {asking.fieldErrors.scopeGrowthP90Percent}
                </span>
              )}
            </span>
          </span>
        </fieldset>

        {/*
          Behind a disclosure because it is a statement about sampling error rather than
          about this plan, and the ordinary answer is right for everybody who has not
          already decided otherwise.
        */}
        <details className="advanced">
          <summary>{t('projects.forecast.more')}</summary>
          <span className="field">
            <label htmlFor="forecast-samples">
              {t('projects.forecast.fields.sampleCount.label')}
            </label>
            <input
              id="forecast-samples"
              name="sampleCount"
              type="number"
              inputMode="numeric"
              min="1"
              step="1000"
              aria-invalid={asking.fieldErrors.sampleCount ? true : undefined}
            />
            <span className="hint">
              {t('projects.forecast.fields.sampleCount.hint')}
            </span>
            {asking.fieldErrors.sampleCount && (
              <span className="field-error">
                {asking.fieldErrors.sampleCount}
              </span>
            )}
          </span>
        </details>

        <p className="actions">
          <button type="submit" className="primary" disabled={busy}>
            {busy
              ? t('projects.forecast.submitting')
              : t('projects.forecast.submit')}
          </button>
        </p>
      </form>

      {runs === null ? (
        !failure && (
          <p className="loading" role="status">
            {t('projects.forecast.loading')}
          </p>
        )
      ) : latest === undefined ? (
        <p className="empty">{t('projects.forecast.none')}</p>
      ) : (
        <>
          <p className="band">
            {t('projects.forecast.band', {
              low: hours(latest.p10Hours, i18n.language),
              high: hours(latest.p90Hours, i18n.language)
            })}
          </p>

          <p className="coverage">
            {t('projects.coverage', {
              estimated: latest.estimatedItemCount,
              total: latest.itemCount
            })}
          </p>

          <table className="percentiles">
            <tbody>
              {(['p10', 'p50', 'p80', 'p90', 'p95'] as const).map((point) => (
                <tr key={point}>
                  <th scope="row">
                    {t(`projects.forecast.percentiles.${point}`)}
                  </th>
                  <td>
                    {t('projects.forecast.hours', {
                      value: hours(latest[`${point}Hours`], i18n.language)
                    })}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <p className="assumptions">
            {t('projects.forecast.assumptions', {
              capacity: latest.capacity,
              samples: latest.sampleCount,
              ...stretchAndGrowth(latest, i18n.language)
            })}
          </p>

          {/*
            Beside the number, never behind a link. Two of these are always here because
            they describe the engine rather than the plan, and a band reported without them
            is narrower than the truth by an amount nobody on this screen could guess.
          */}
          <div className="limitations">
            <h3>{t('projects.forecast.limitations.title')}</h3>
            <ul>
              {latest.limitations.map((limitation) => (
                <li key={limitation}>{describeLimitation(t, limitation)}</li>
              ))}
            </ul>
          </div>

          {earlier.length > 0 && (
            <div className="earlier">
              <h3>{t('projects.forecast.earlier.title')}</h3>
              <ul>
                {earlier.map((run) => (
                  <li key={run.id}>
                    {t('projects.forecast.earlier.entry', {
                      middle: hours(run.p50Hours, i18n.language),
                      high: hours(run.p90Hours, i18n.language),
                      capacity: run.capacity,
                      who: run.requestedByName,
                      ...stretchAndGrowth(run, i18n.language)
                    })}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/**
 * The two assumptions M3b added, as a run reports them — written once because the band and
 * the history below it have to describe a run the same way. A reader comparing two entries
 * is comparing exactly these numbers.
 */
function stretchAndGrowth(
  run: Forecast,
  locale: string
): { worseBy: string; growthLow: string; growthHigh: string } {
  return {
    worseBy: percent(run.teamFactorWorseByPercent, locale),
    growthLow: percent(run.scopeGrowthP10Percent, locale),
    growthHigh: percent(run.scopeGrowthP90Percent, locale)
  };
}

/**
 * A quantity of effort, in the reader's own locale.
 *
 * To a tenth of an hour, which is six minutes: the engine answers to the hundredth and the
 * columns keep that, and putting either on screen would be claiming a precision the ranges
 * that produced it never had.
 */
function hours(value: number, locale: string): string {
  return decimal(value, locale, 1);
}

/**
 * A percentage somebody typed, shown to the hundredth the column keeps — which is all of
 * it. Unlike an answer, an assumption is not a quantity this application worked out, so
 * rounding it would be showing somebody a number other than the one they gave.
 */
function percent(value: number, locale: string): string {
  return decimal(value, locale, 2);
}

function decimal(
  value: number,
  locale: string,
  maximumFractionDigits: number
): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits }).format(value);
}

/**
 * What a limitation means, in this application's own words.
 *
 * A code this version does not recognise still gets a line rather than being dropped. It
 * means the server is ahead of the browser, which is the direction this pair versions in,
 * and quietly showing nothing would be the one failure decision 12 exists to prevent
 * arriving through the back door. The two codes M3b retired go the other way and are still
 * described here, because a run made before it is still a run this screen lists.
 */
function describeLimitation(
  t: TFunction,
  limitation: ForecastLimitation
): string {
  return LIMITATIONS.includes(limitation)
    ? t(`projects.forecast.limitations.${limitation}`)
    : t('projects.forecast.limitations.unknown');
}
