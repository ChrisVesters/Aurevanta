import { useCallback, useEffect, useRef, useState } from 'react';
import { Trans, useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Help } from '../ui/Help';
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
 * The two common-cause questions, which may be left empty and are then sent as zero.
 *
 * Zero is a claim — that nothing ever goes wrong for everybody at once, and that no work
 * nobody has listed will turn up — and it is only an acceptable thing to send for an empty
 * box because the forecast says so beside its date: a run that assumed zero for either
 * reports `no_team_factor` or `no_scope_uncertainty`. That is the same rule the dependency
 * form keeps for its wait, sending zero where its own hint says an empty box means none.
 */
const ZERO_WHEN_EMPTY = [
  'teamFactorWorseByPercent',
  'scopeGrowthP10Percent',
  'scopeGrowthP90Percent'
];

/**
 * The question, which is where every assumption in this product is actually made.
 *
 * **Its own component because it is its own form**, and a form in this codebase owns a
 * `useFormFailure` over the fields it renders: held by the panel, that list had to be kept
 * in step with markup two hundred lines away, and the banner's suppression rule is only safe
 * for complaints about fields the visitor can see.
 *
 * **Nothing here has a default and that is the whole design.** Capacity moves the P90 by
 * 70%; the two the common-cause model assumptions have a neutral value — zero — and zero is a claim, that
 * nothing in this team's world has a common cause and that no unlisted work will ever
 * appear. A box already filled in is a box nobody reads.
 */
export function ForecastAsk({
  projectId,
  pools,
  previous,
  onForecast,
  onCancel
}: {
  projectId: string;
  /** Empty until an organisation describes a team, which is what decides the capacity box. */
  pools: Resource[];
  /**
   * The newest forecast of this plan, whose answers the form opens with. Asking again is
   * usually asking the same question of a plan that has moved on, so the claims made last
   * time are the starting point — they are this team's own, said before, rather than a
   * default the server made up. The start date is not carried over: it is today's fact.
   */
  previous?: Forecast;
  /** Called with the forecast the server made, once it has made it. */
  onForecast: (run: Forecast) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [busy, setBusy] = useState(false);
  const asking = useFormFailure(ASKED_FOR);
  const advanced = useRef<HTMLDetailsElement>(null);
  // A capacity is carried over only where it still answers the question: a run scheduled
  // against a team stored that team's units as its capacity, and offering that number as
  // a capacity would be a claim nobody made.
  const priorCapacity =
    previous && previous.resources.length === 0 ? previous.capacity : undefined;
  // Under Advanced, so opened where it carries something other than zero — a claim the
  // form is about to repeat must be on screen, not folded away.
  const priorAssumptions =
    previous !== undefined &&
    (previous.teamFactorWorseByPercent > 0 ||
      previous.scopeGrowthP90Percent > 0);

  // A complaint about a box inside the disclosure opens it. `useFormFailure` keeps the banner
  // quiet because the field is one this form renders — which is only true of a field
  // somebody can see, so a closed disclosure would put the complaint nowhere at all.
  useEffect(() => {
    if (ZERO_WHEN_EMPTY.some((field) => asking.fieldErrors[field])) {
      (advanced.current as HTMLDetailsElement).open = true;
    }
  }, [asking.fieldErrors]);

  const run = useCallback(
    async (asked: Record<string, number | string | null>) => {
      setBusy(true);
      asking.clear();
      try {
        const made = await request<Forecast>(
          `/projects/${projectId}/forecasts`,
          {
            method: 'POST',
            // Every box goes as null when it is empty rather than being left out. The
            // server reads an absent sample count as "the ordinary ten thousand", and the
            // other six have no reading at all — which is the whole point of them being
            // required, and would be quietly undone by a screen that filled one in.
            body: asked
          }
        );
        onForecast(made);
      } catch (error) {
        asking.report(error);
      }
      setBusy(false);
    },
    [request, projectId, asking, onForecast]
  );

  function ask(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    void run({
      ...Object.fromEntries(
        NUMBERS.map((field) => [field, numberField(values, field)])
      ),
      ...Object.fromEntries(
        ZERO_WHEN_EMPTY.map((field) => [field, numberField(values, field) ?? 0])
      ),
      // A day rather than a number, and it goes as null when empty like every other box:
      // the server has no reading for a missing start date, which is the whole point of
      // its being required, and a screen that quietly sent today would undo that.
      startsOn: optionalField(values, 'startsOn')
    });
  }

  return (
    <form className="forecast-form" onSubmit={ask} noValidate>
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
            defaultValue={priorCapacity}
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
        /*
          The question's own name stays on screen, styled as the label it would have been,
          so the sentence under it is plainly the answer to *that* question — and says
          where the answer came from and where to change it.
        */
        <div className="field answered">
          <span className="label">
            {t('projects.forecast.fields.capacity.label')}
          </span>
          <p className="hint">
            <Trans
              i18nKey="projects.forecast.fields.capacity.declared"
              count={pools.length}
              components={{ resources: <Link to="/app/resources" /> }}
            />
          </p>
        </div>
      )}

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
          defaultValue={previous?.workingHoursPerDay ?? undefined}
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
        Behind a disclosure: what the common-cause model adds, which may be left empty and
        is then reported beside the date, and the number of runs, which is a statement about
        sampling error rather than about this plan.
      */}
      <details
        className="advanced"
        ref={advanced}
        open={priorAssumptions || undefined}
      >
        <summary>{t('projects.forecast.more')}</summary>
        {/*
          The two questions the common-cause model exists to ask, in the only form anybody
          can answer them: a percentile of an outcome rather than a parameter of a
          distribution. Optional, and empty is sent as zero — see `ZERO_WHEN_EMPTY` — so a
          plan can be forecast on its listed work alone, with the forecast saying so.
        */}
        <span className="field">
          <span className="label">
            <label htmlFor="forecast-team-factor">
              {t('projects.forecast.fields.teamFactor.label')}
            </label>{' '}
            <Help label={t('projects.forecast.fields.teamFactor.help')}>
              {t('projects.forecast.fields.teamFactor.hint')}
            </Help>
          </span>
          <input
            id="forecast-team-factor"
            name="teamFactorWorseByPercent"
            type="number"
            inputMode="decimal"
            min="0"
            max="200"
            step="5"
            defaultValue={previous?.teamFactorWorseByPercent}
            aria-invalid={
              asking.fieldErrors.teamFactorWorseByPercent ? true : undefined
            }
          />
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
        {/*
          Named by the legend's words alone: the "?" sits inside the legend, and a group
          named by all of it would carry the button's label in its name as well.
        */}
        <fieldset className="range" aria-labelledby="forecast-growth-legend">
          <legend>
            <span id="forecast-growth-legend">
              {t('projects.forecast.fields.scopeGrowth.legend')}
            </span>{' '}
            <Help label={t('projects.forecast.fields.scopeGrowth.help')}>
              {t('projects.forecast.fields.scopeGrowth.hint')}
            </Help>
          </legend>
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
                defaultValue={previous?.scopeGrowthP10Percent}
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
                defaultValue={previous?.scopeGrowthP90Percent}
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

        <span className="field">
          <span className="label">
            <label htmlFor="forecast-samples">
              {t('projects.forecast.fields.sampleCount.label')}
            </label>{' '}
            <Help label={t('projects.forecast.fields.sampleCount.help')}>
              {t('projects.forecast.fields.sampleCount.hint')}
            </Help>
          </span>
          <input
            id="forecast-samples"
            name="sampleCount"
            type="number"
            inputMode="numeric"
            min="1"
            step="1000"
            aria-invalid={asking.fieldErrors.sampleCount ? true : undefined}
          />
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
        <button type="button" className="link" onClick={onCancel}>
          {t('projects.forecast.new.cancel')}
        </button>
      </p>
    </form>
  );
}
