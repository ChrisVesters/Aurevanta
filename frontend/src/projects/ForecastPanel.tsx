import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import type { TFunction } from 'i18next';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { numberField, optionalField } from './fields';
import { formatDay, todayHere } from './dates';
import type { Forecast, ForecastLimitation } from './types';

/**
 * Every box on this form that the server wants as a number, which is all of them but one.
 * A field added here is a field sent.
 */
const NUMBERS = [
  'capacity',
  'teamFactorWorseByPercent',
  'scopeGrowthP10Percent',
  'scopeGrowthP90Percent',
  'workingHoursPerDay',
  'sampleCount'
];

/**
 * Every box on this form, which is what `useFormFailure` needs: suppressing the banner is
 * only safe for complaints about fields the visitor can actually see.
 *
 * **`startsOn` is the one that is not a number**, which is exactly the case the previous
 * version of this list anticipated — the two duties were one list only while everything on
 * the form was a number, and a date is not. So the body is built from {@link NUMBERS} plus
 * that one field rather than from this, and the names the visitor can see stay complete.
 */
const ASKED_FOR = [...NUMBERS, 'startsOn'];

/**
 * The three confidences worth committing at, and the percentile each reads.
 *
 * **A view over one run and never a re-run.** All five percentiles are already in the
 * response, so moving between these changes a date on screen without a request going out —
 * which is not an optimisation but the feature. The trade this milestone exists to make
 * visible only works if it is immediate: somebody asks whether the plan can go faster, and
 * the answer is a control moving from 95 to 80 and a date moving with it, on one screen,
 * from one forecast. A round trip would make two readings of one run look like two
 * different forecasts.
 *
 * It also means the confidence is not stored on the run: there is no such thing as the
 * confidence a forecast was *made* at, only the one somebody is reading it at.
 */
const CONFIDENCES = [50, 80, 95] as const;

type Confidence = (typeof CONFIDENCES)[number];

/** Which percentile each of them reads. The other two dates have no control and no need. */
const DATE_AT: Record<Confidence, 'p50Date' | 'p80Date' | 'p95Date'> = {
  50: 'p50Date',
  80: 'p80Date',
  95: 'p95Date'
};

/**
 * Eight tenths of the probability, which is what the band sentence beneath already states.
 * A view has to start somewhere and this is the reading the rest of the screen agrees with
 * — unlike the assumptions above, which have no right answer and so are left empty.
 */
const USUAL_CONFIDENCE: Confidence = 80;

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
 * **Three things are therefore never optional here.** The assumptions are asked for and no
 * *claim* is pre-filled, because they move the answer more than anything else on this
 * screen and a box already filled in is a box nobody reads. The coverage is stated in
 * words, because a forecast that quietly covers less than the plan is the failure this
 * product exists to prevent. And the assumptions and limitations alike are printed next to
 * the number rather than behind a disclosure, because a number seen without its caveats is
 * already in somebody's slide.
 *
 * **The start date is the one exception and it proves the rule rather than bending it.**
 * Today's date is not a claim about this team — it is a fact this browser holds and the
 * server cannot reach, since an instant is not a date without a timezone. Pre-filling it
 * tells somebody what day it is, not what their week is worth, and it stays editable
 * because a plan that starts in January is one edit away.
 *
 * **Only the sample count sits behind the disclosure, and that is the test of where the
 * line is.** It is a statement about sampling error rather than about this team, and it
 * has a right answer for everybody who has not already decided otherwise. Nothing that is
 * required may go in there: a required box inside a collapsed section is a refusal about a
 * field the visitor cannot see.
 *
 * **The date is the headline and the hours stay.** The band is what the model produced; the
 * date is that number with a working day on top, and that is exactly the kind of assumption
 * that gets forgotten. Removing the hours would leave nothing on this screen that came out
 * of the engine, and would make the working day invisible in the way this panel's whole
 * design exists to prevent: **a date is the first thing this product emits that looks like
 * a fact.** An hours band advertises that it came out of a model; "14 November" does not.
 */
export function ForecastPanel({ projectId }: { projectId: string }) {
  const { t, i18n } = useTranslation();
  const { request } = useAuth();
  const [runs, setRuns] = useState<Forecast[] | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  const [confidence, setConfidence] = useState<Confidence>(USUAL_CONFIDENCE);
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
    async (asked: Record<string, number | string | null>) => {
      setBusy(true);
      asking.clear();
      try {
        await request<Forecast>(`/projects/${projectId}/forecasts`, {
          method: 'POST',
          // Every box goes as null when it is empty rather than being left out. The
          // server reads an absent sample count as "the ordinary ten thousand", and the
          // other six have no reading at all — which is the whole point of them being
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
    void run({
      ...Object.fromEntries(
        NUMBERS.map((field) => [field, numberField(values, field)])
      ),
      // A day rather than a number, and it goes as null when empty like every other box:
      // the server has no reading for a missing start date, which is the whole point of
      // its being required, and a screen that quietly sent today would undo that.
      startsOn: optionalField(values, 'startsOn')
    });
  }

  const latest = runs?.[0];
  const earlier = runs?.slice(1) ?? [];
  const calendar = latest ? calendarOf(latest, i18n.language) : null;
  // All five are absent together, so the one being read answers "has this run any dates at
  // all" — which is what decides whether there is anything for the control to choose
  // between, and is a different question from whether a calendar was stated.
  const chosenDate = latest ? latest[DATE_AT[confidence]] : null;

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
          The two the calendar needs, and the only place on this form where a box arrives
          already answered. Today's date is a fact this browser holds and the server cannot
          reach; the working day is a claim about a team, and so is left empty for the
          reason capacity and the two percentages are. Both are in the open, because a
          required box behind a disclosure is a refusal about a field nobody can see.
        */}
        <span className="field">
          <label htmlFor="forecast-starts-on">
            {t('projects.forecast.fields.startsOn.label')}
          </label>
          <input
            id="forecast-starts-on"
            name="startsOn"
            type="date"
            defaultValue={todayHere()}
            aria-invalid={asking.fieldErrors.startsOn ? true : undefined}
          />
          <span className="hint">
            {t('projects.forecast.fields.startsOn.hint')}
          </span>
          {asking.fieldErrors.startsOn && (
            <span className="field-error">{asking.fieldErrors.startsOn}</span>
          )}
        </span>

        <span className="field">
          <label htmlFor="forecast-working-day">
            {t('projects.forecast.fields.workingDay.label')}
          </label>
          <input
            id="forecast-working-day"
            name="workingHoursPerDay"
            type="number"
            inputMode="decimal"
            min="0.5"
            max="24"
            step="0.5"
            aria-invalid={
              asking.fieldErrors.workingHoursPerDay ? true : undefined
            }
          />
          <span className="hint">
            {t('projects.forecast.fields.workingDay.hint')}
          </span>
          {asking.fieldErrors.workingHoursPerDay && (
            <span className="field-error">
              {asking.fieldErrors.workingHoursPerDay}
            </span>
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
          {/*
            The number people actually asked for, and the control that makes the trade
            visible: lower confidence, earlier date, same plan, no request. Two dates that
            come out the same are short plans rounding to one day rather than a control
            that has stopped working — the band is in hours and the calendar is in days.

            It is absent, rather than disabled, on a run that has no dates to choose
            between: three buttons that visibly change nothing read as a broken screen,
            where the line beneath says exactly what is missing and why.
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
            {describeDate(t, latest, confidence, i18n.language)}
          </p>

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
            The sixth assumption, in its own sentence rather than woven into the five above
            — because a run that has none would otherwise need the whole paragraph written
            twice, and a duplicated sentence is two wordings waiting to disagree. It sits
            beside the date it produced and never behind a disclosure, which is the rule
            the other five already follow.
          */}
          {calendar && (
            <p className="assumptions">
              {t('projects.forecast.calendar', calendar)}
            </p>
          )}

          {/*
            Beside the number, never behind a link. Every one of these is a property of the
            plan rather than of the engine — the two that described the engine were retired
            by M3b, which removed their cause rather than their wording — and a band
            reported without them is narrower than the truth by an amount nobody on this
            screen could guess.
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
                {earlier.map((run) => {
                  const readUnder = calendarOf(run, i18n.language);
                  return (
                    <li key={run.id}>
                      {t('projects.forecast.earlier.entry', {
                        middle: hours(run.p50Hours, i18n.language),
                        high: hours(run.p90Hours, i18n.language),
                        capacity: run.capacity,
                        who: run.requestedByName,
                        ...stretchAndGrowth(run, i18n.language)
                      })}
                      {/*
                        Its own calendar, for the reason it carries its own assumptions:
                        two runs read under different working days are two readings rather
                        than a date moving, which is the mistake M10 exists to avoid.
                      */}
                      {readUnder && (
                        <>
                          {' '}
                          {t('projects.forecast.earlier.calendar', readUnder)}
                        </>
                      )}
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </>
      )}
    </section>
  );
}

/**
 * The date somebody would commit to at one confidence, or why there is not one.
 *
 * **Which of the two absences it is matters, and the server is what versions ahead.** A run
 * with no working day was made before a calendar was something anybody stated, and saying
 * so is the point of not having backfilled one. A run that *has* a working day and still no
 * date was read under a calendar this browser has never heard of — the same direction
 * {@link describeLimitation} handles, and the same answer: say what is true rather than
 * show a blank or, far worse, a date worked out under the wrong rule.
 */
function describeDate(
  t: TFunction,
  run: Forecast,
  confidence: Confidence,
  locale: string
): string {
  const day = run[DATE_AT[confidence]];
  if (day !== null) {
    return t('projects.forecast.confidence.date', {
      confidence,
      date: formatDay(day, locale)
    });
  }
  return run.workingHoursPerDay === null
    ? t('projects.forecast.confidence.noCalendar')
    : t('projects.forecast.confidence.unreadableCalendar');
}

/**
 * What a run's dates were read through, or nothing where it has none.
 *
 * Both halves or neither: they are stored together and cleared together, and a sentence
 * naming one of them would be a sentence with a hole in it.
 */
function calendarOf(
  run: Forecast,
  locale: string
): { start: string; day: string } | null {
  if (run.startsOn === null || run.workingHoursPerDay === null) {
    return null;
  }
  return {
    start: formatDay(run.startsOn, locale),
    // To the hundredth the column keeps, like the percentages and for the same reason:
    // this is a number somebody typed rather than one this application worked out, so
    // rounding it would show them something other than what they said.
    day: decimal(run.workingHoursPerDay, locale, 2)
  };
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
