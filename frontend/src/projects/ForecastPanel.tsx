import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import type { TFunction } from 'i18next';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { numberField } from './fields';
import type { Forecast, ForecastLimitation } from './types';

const ASKED_FOR = ['capacity', 'sampleCount'];

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
 * **Three things are therefore never optional here.** The capacity is asked for and not
 * pre-filled, because it moves the answer by more than half and a box already filled in is
 * a box nobody reads. The coverage is stated in words, because a forecast that quietly
 * covers less than the plan is the failure this product exists to prevent. And the
 * limitations are printed next to the number rather than behind a disclosure, because a
 * number seen without its caveats is already in somebody's slide.
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
    async (capacity: number | null, sampleCount: number | null) => {
      setBusy(true);
      asking.clear();
      try {
        await request<Forecast>(`/projects/${projectId}/forecasts`, {
          method: 'POST',
          // Sent as null rather than left out when nobody opened the disclosure: the
          // server reads an absent sample count as "the ordinary ten thousand", and the
          // capacity has no reading at all, which is the point of it being required.
          body: { capacity, sampleCount }
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
      numberField(values, 'capacity'),
      numberField(values, 'sampleCount')
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
              samples: latest.sampleCount
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
                      who: run.requestedByName
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
 * A quantity of effort, in the reader's own locale.
 *
 * To a tenth of an hour, which is six minutes: the engine answers to the hundredth and the
 * columns keep that, and putting either on screen would be claiming a precision the ranges
 * that produced it never had.
 */
function hours(value: number, locale: string): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits: 1 }).format(
    value
  );
}

/**
 * What a limitation means, in this application's own words.
 *
 * A code this version does not recognise still gets a line rather than being dropped. It
 * means the server is ahead of the browser — M3b adds one — and quietly showing nothing
 * would be the one failure decision 12 exists to prevent, arriving through the back door.
 */
function describeLimitation(
  t: TFunction,
  limitation: ForecastLimitation
): string {
  return LIMITATIONS.includes(limitation)
    ? t(`projects.forecast.limitations.${limitation}`)
    : t('projects.forecast.limitations.unknown');
}
