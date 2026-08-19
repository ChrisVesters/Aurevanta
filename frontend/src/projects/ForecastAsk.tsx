import { useCallback, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import type { Resource } from '../resource/types';
import { todayHere } from './dates';
import { numberField, optionalField } from './fields';
import type { Forecast } from './forecastTypes';

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
 * The question, which is where every assumption in this product is actually made.
 *
 * **Its own component because it is its own form**, and a form in this codebase owns a
 * `useFormFailure` over the fields it renders: held by the panel, that list had to be kept
 * in step with markup two hundred lines away, and the banner's suppression rule is only safe
 * for complaints about fields the visitor can see.
 *
 * **Nothing here has a default and that is the whole design.** Capacity moves the P90 by
 * 70%; the two M3b assumptions have a neutral value — zero — and zero is a claim, that
 * nothing in this team's world has a common cause and that no unlisted work will ever
 * appear. A box already filled in is a box nobody reads.
 */
export function ForecastAsk({
  projectId,
  pools,
  onForecast
}: {
  projectId: string;
  /** Empty until an organisation describes a team, which is what decides the capacity box. */
  pools: Resource[];
  /** Called once a run has landed, so the panel above reloads its history. */
  onForecast: () => void;
}) {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [busy, setBusy] = useState(false);
  const asking = useFormFailure(ASKED_FOR);

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
        onForecast();
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

  return (
    <form onSubmit={ask} noValidate>
      {asking.message && (
        <p className="form-error" role="alert">
          {asking.message}
        </p>
      )}

      {/*
        **Absent rather than disabled once a team has been described**, which is the rule
        the confidence control already follows on a run with no dates: a box that visibly
        does nothing reads as a broken screen, where a sentence saying the resources
        answer this says what is true. The server refuses either way — a capacity named
        beside a declared team is `capacity_not_applicable`, refused rather than ignored,
        because silently dropping input is worse than refusing it.
      */}
      {pools.length === 0 ? (
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
      ) : (
        <p className="hint">
          {t('projects.forecast.fields.capacity.declared', {
            count: pools.length
          })}
        </p>
      )}

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
  );
}
