import { useCallback, useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import type { TFunction } from 'i18next';
import { useAuth } from '../auth/AuthContext';
import type { Calibration } from '../calibration/types';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { numberField, optionalField } from './fields';
import { formatDay, todayHere } from './dates';
import { describeWork } from './work';
import { TargetDate } from './TargetDate';
import type {
  BurnUp,
  Contribution,
  Drift,
  Forecast,
  ForecastHistory,
  ForecastLimitation,
  Movement,
  MovementAt,
  MovementTerm,
  Throughput
} from './types';

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

/**
 * Which percentile each of them reads. The other two dates have no control and no need.
 *
 * **Both forecasts, because a throughput answer carries the same five percentiles under the
 * same names.** That is what keeps the trade immediate on both sides: moving from 95 to 80
 * changes two dates and sends no request. A second constant holding the same three fields
 * would be two names for one thing and a reader having to check whether they agree.
 */
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

/**
 * The three ways to have no second date, in the order they are looked for.
 *
 * Each is a different thing to say, and a code this version has never heard of gets a sentence
 * saying so rather than nothing — the rule `describeLimitation` follows, because the server is
 * what versions ahead here.
 */
const NO_SECOND_OPINION = [
  'throughput_nothing_left',
  'throughput_history_too_short',
  'throughput_beyond_horizon'
] as const;

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
export function ForecastPanel({
  projectId,
  projectName
}: {
  projectId: string;
  /**
   * What the plan is called, and it is here for one line only.
   *
   * The headline sentence is the thing somebody copies into an email, and a sentence that
   * names a confidence and a day but not *what* is being forecast only makes sense on the
   * screen it is already on. Nothing else in this panel needs it — which is why it is a name
   * rather than the project, since a component handed the whole row would soon read more of
   * it than it should.
   */
  projectName: string;
}) {
  const { t, i18n } = useTranslation();
  const { request } = useAuth();
  const [runs, setRuns] = useState<Forecast[] | null>(null);
  // The newest run's identifier, which is the one an account of a movement is asked *of* —
  // named here rather than below because the effect that asks needs it.
  const latestId = runs?.[0]?.id;
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [reloads, setReloads] = useState(0);
  // Null until somebody asks. Working this out replays the whole run — about half a second
  // at five hundred items — which is not a cost to put on opening a page, and most readers
  // never scroll this far.
  const [spread, setSpread] = useState<Contribution[] | null>(null);
  const [spreadFailure, setSpreadFailure] = useState<string | null>(null);
  const [breakingDown, setBreakingDown] = useState(false);
  /** Which run somebody has asked about, which is what starts the work below. */
  const [explaining, setExplaining] = useState<string | null>(null);
  const [confidence, setConfidence] = useState<Confidence>(USUAL_CONFIDENCE);
  /**
   * What the ranges feeding this band have historically been worth.
   *
   * A caveat about the *inputs* and never a correction to the number: folding a calibration
   * factor into a forecast would close a loop on its own evidence, so the record converges
   * on 80% while nothing about the estimating changes. This says what the estimates have
   * been worth and leaves the band exactly as the engine produced it.
   */
  const [track, setTrack] = useState<Calibration | null>(null);
  /**
   * What this plan's own history says, which is the second half of the only comparison this
   * screen makes. Its own request and its own failure: a forecast is not less true because
   * the history beside it could not be read, so losing this leaves the band alone.
   */
  const [throughput, setThroughput] = useState<Throughput | null>(null);
  /**
   * Why there is no history to show, when there is a reason worth passing on.
   *
   * **Losing the read must leave the band alone; it must not leave the reader guessing.** The
   * first version set the state to null and rendered nothing, so a plan holding one task
   * marked finished next week — which this product's own progress form will accept — lost the
   * entire comparison with no sentence anywhere saying why. A refusal that names something
   * fixable in somebody's own plan is exactly the one to pass on.
   */
  const [throughputFailure, setThroughputFailure] = useState<string | null>(
    null
  );
  /**
   * Whether this plan's date keeps moving out, which arrives with the history above.
   *
   * Rendered only when the server says it is worth saying: a plan that is merely churning
   * must hear nothing, or the warning becomes one nobody reads. Which of those it is is the
   * server's to decide — the browser holds no threshold, as it holds none of
   * `EstimateQuality`'s.
   */
  const [drift, setDrift] = useState<Drift | null>(null);
  /**
   * Which earlier forecast somebody is asking about, and the account that came back.
   *
   * Its own request, like the breakdown above and for the same reason: an account of a
   * movement costs six whole simulations, which is cheap for somebody who asked and rude to
   * charge everybody who opened the page.
   */
  const [explainingMove, setExplainingMove] = useState<string | null>(null);
  const [movement, setMovement] = useState<Movement | null>(null);
  const [movementFailure, setMovementFailure] = useState<string | null>(null);
  const asking = useFormFailure(ASKED_FOR);

  useEffect(() => {
    let cancelled = false;

    // The history and the verdict on it arrive together: whether the date keeps moving
    // out is a property of the sequence rather than of any run in it, so there is nowhere
    // on a run to read it from and no second request to make.
    request<ForecastHistory>(`/projects/${projectId}/forecasts`)
      .then((loaded) => {
        if (!cancelled) {
          setRuns(loaded.runs);
          setDrift(loaded.drift);
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
        // A new run is a different plan's spread, so whatever was on screen about the
        // last one stops being true the moment this lands. An account of a movement is
        // the same: it was measured against a run that is no longer the newest.
        setSpread(null);
        setSpreadFailure(null);
        setExplaining(null);
        setMovement(null);
        setMovementFailure(null);
        setExplainingMove(null);
      } catch (error) {
        asking.report(error);
      }
      setBusy(false);
    },
    [request, projectId, asking]
  );

  // Its own read, and one this panel must survive losing. A forecast is not less true
  // because the record beside it could not be loaded, so a failure here leaves the line off
  // and touches nothing else — which is why it has no failure state of its own to render.
  useEffect(() => {
    let cancelled = false;
    request<Calibration>('/calibration')
      .then((record) => {
        if (!cancelled) {
          setTrack(record);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setTrack(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request]);

  // Asked about *today*, stated from here rather than left to the server: what day it is
  // where somebody is sitting is a fact only this end holds, which is the argument `todayHere`
  // exists to make. Keyed on the plan alone — the answer moves when work is finished, not when
  // a forecast is run, so it does not reload with the rest of the panel.
  useEffect(() => {
    let cancelled = false;
    request<Throughput>(`/projects/${projectId}/throughput?asOf=${todayHere()}`)
      .then((history) => {
        if (!cancelled) {
          setThroughput(history);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setThroughputFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, projectId, t]);

  // The same shape as the breakdown below, and for the same reason: six simulations is long
  // enough that somebody can navigate away while it is in flight, and nothing arriving
  // afterwards may touch a panel that has gone.
  useEffect(() => {
    if (explainingMove === null || latestId === undefined) {
      return undefined;
    }
    let cancelled = false;
    request<Movement>(`/forecasts/${latestId}/movement?since=${explainingMove}`)
      .then((account) => {
        if (!cancelled) {
          setMovement(account);
          setMovementFailure(null);
        }
      })
      .catch((error: unknown) => {
        // Including this endpoint's own refusal: two runs made by different versions of
        // the model are not a rougher comparison, they are an exact account of a movement
        // that never happened.
        if (!cancelled) {
          setMovement(null);
          setMovementFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [explainingMove, latestId, request, t]);

  // An effect rather than a handler, and the reason is the half second it takes: this is by
  // far the slowest request this panel makes, so it is by far the likeliest to be still in
  // flight when somebody navigates away. Nothing arriving afterwards may touch a panel that
  // has gone — the rule every other request here already follows.
  useEffect(() => {
    if (explaining === null) {
      return undefined;
    }
    let cancelled = false;
    setBreakingDown(true);
    request<Contribution[]>(`/forecasts/${explaining}/contributions`)
      .then((ranked) => {
        if (!cancelled) {
          setSpread(ranked);
          setBreakingDown(false);
        }
      })
      .catch((error: unknown) => {
        // Including the one refusal this endpoint has of its own: a run the engine no
        // longer reproduces is not explained at all, because a ranking from a different
        // model is an exact ranking of a plan nobody forecast.
        if (!cancelled) {
          setSpreadFailure(describeFailure(t, error));
          setBreakingDown(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [explaining, request, t]);

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
  // The drift at whichever confidence is being read, and only when it has dates to name.
  // Both are the server's answers rather than this end's: the window it was measured over,
  // and whether the distance is worth saying at all.
  const drifting = readingOf(drift, confidence);
  const movedBy = readingOf(movement, confidence);

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
            {describeDate(t, latest, projectName, confidence, i18n.language)}
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

          {/*
            Beside the date rather than down beside the history it was measured from, because
            it is a caveat about *this* number: a plan whose date keeps moving out is one
            whose latest date is worth less than it looks. Absent unless the server says it is
            worth saying — a plan that is merely churning hears nothing, which is the whole of
            decision 5 and the reason it is not a line that reads "no drift" the rest of the
            time.
          */}
          {drifting !== null && drifting.movingOut && (
            <p className="caveat">
              {t('projects.forecast.drift', {
                confidence,
                from: formatDay(drifting.fromDate, i18n.language),
                to: formatDay(drifting.toDate, i18n.language),
                out: t('projects.forecast.driftOut', {
                  count: drifting.days
                }),
                band: t('projects.forecast.driftBand', {
                  count: drifting.bandDays
                })
              })}
            </p>
          )}

          {/*
            The gap, which is the whole of what M9 is for — and deliberately not a number
            called "the gap". Two of the four differences named underneath make the engine
            look slow and two make it look fast, so a subtraction of the two dates is not
            interpretable on its own. Nothing here averages them or picks one: two forecasts
            that disagree are the output, because "six weeks against eleven" starts a
            conversation and one number in the middle ends it.
          */}
          {(throughput !== null || throughputFailure !== null) && (
            <ThroughputComparison
              history={throughput}
              failure={throughputFailure}
              run={latest}
              confidence={confidence}
            />
          )}

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
            What the band is made of, and the only thing on this panel that is not loaded
            with it: working it out replays the whole run, about half a second at the five
            hundred items a plan may hold. That is cheap for somebody who asked and rude to
            charge everybody who opened the page.
          */}
          <div className="spread">
            {spread === null && spreadFailure === null ? (
              <p className="actions">
                <button
                  type="button"
                  className="secondary"
                  disabled={breakingDown}
                  onClick={() => setExplaining(latest.id)}
                >
                  {breakingDown
                    ? t('projects.forecast.contributions.loading')
                    : t('projects.forecast.contributions.open')}
                </button>
              </p>
            ) : spreadFailure !== null ? (
              <p className="empty">{spreadFailure}</p>
            ) : (
              <>
                <h3>{t('projects.forecast.contributions.title')}</h3>
                <p className="hint">
                  {t('projects.forecast.contributions.lede')}
                </p>
                <ul className="ranking">
                  {spread?.map((source) => (
                    <li key={source.itemId ?? source.kind}>
                      <span className="what">{describeSource(t, source)}</span>
                      {/*
                        A bar rather than a number, deliberately. These are shares of the
                        spread and they overlap, so a percentage beside each would invite
                        somebody to add them up and find their plan accounts for three
                        hundred percent of its own uncertainty.
                      */}
                      <span
                        className="bar"
                        style={{
                          width: `${Math.min(100, source.shareOfSpread * 100)}%`
                        }}
                      />
                    </li>
                  ))}
                </ul>
                {/*
                  Beside the ranking and not behind a disclosure, for the reason the
                  assumptions and the limitations are: a number seen without its caveat is
                  already in somebody's slide.
                */}
                <p className="caveat">
                  {t('projects.forecast.contributions.caveat')}
                </p>
              </>
            )}
          </div>

          {/*
            Beside the number, never behind a link. Every one of these is a property of the
            plan rather than of the engine — the two that described the engine were retired
            by M3b, which removed their cause rather than their wording — and a band
            reported without them is narrower than the truth by an amount nobody on this
            screen could guess.
          */}
          {/*
            Beside the band for the reason the limitations are beside it: a band's own track
            record is the most useful thing that can sit next to it, and a number pasted into
            a plan without it is this product's failure mode with a chart on. Absent when
            nothing has been scored, because "no estimate here has ever been checked" on
            every forecast anybody runs is noise rather than a caveat — the track record page
            is where that belongs, and it says it properly.
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

          <div className="limitations">
            <h3>{t('projects.forecast.limitations.title')}</h3>
            <ul>
              {latest.limitations.map((limitation) => (
                <li key={limitation}>{describeLimitation(t, limitation)}</li>
              ))}
            </ul>
          </div>

          {/*
            The other question somebody opens this screen with, and the only one on it that
            proposes an action. It sits *after* the limitations rather than beside the
            spread, because everything it says is derived from the band above and inherits
            every caveat printed there — a list of work to drop is the most quotable thing
            this product emits, and the caveats have to have been passed on the way down.

            Keyed on the run, so asking for a new forecast starts the question again rather
            than leaving a list of work to drop that was measured against the previous one.
            That is the same rule the breakdown above keeps by clearing itself, arrived at
            the other way: here the whole panel — the date, the ticks and the answer — was
            about a run that is no longer on screen.
          */}
          <TargetDate key={latest.id} run={latest} />

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
                      {/*
                        **Asked of a pair rather than of a run**, which is why it is here
                        and not beside the date: the question is what happened between this
                        forecast and the newest one.

                        Offered only where both ends have a date to have moved. A run made
                        before there was a calendar has hours and no date, and "why did the
                        date move" is not a question about it — the same reason the
                        confidence control is absent rather than disabled on such a run.
                      */}
                      {chosenDate !== null &&
                        run[DATE_AT[confidence]] !== null && (
                          <MovementAsked
                            asking={explainingMove === run.id}
                            account={movedBy}
                            simulations={movement?.simulations ?? 0}
                            failure={movementFailure}
                            onAsk={() => {
                              // Cleared here rather than left to arrive: the account on
                              // screen is about a different pair the moment somebody asks
                              // about another one.
                              setMovement(null);
                              setMovementFailure(null);
                              setExplainingMove(run.id);
                            }}
                          />
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
function ThroughputComparison({
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

  if (history === null) {
    return (
      <div className="throughput">
        <h3>{t('projects.forecast.throughput.title')}</h3>
        <p className="empty">{failure}</p>
      </div>
    );
  }

  const theirs = history.projection?.[DATE_AT[confidence]] ?? null;
  const ours = run[DATE_AT[confidence]];

  return (
    <div className="throughput">
      <h3>{t('projects.forecast.throughput.title')}</h3>

      {history.window !== null && (
        <p className="hint">
          {t('projects.forecast.throughput.window', {
            weeks: history.window.weeks,
            completed: history.window.completed,
            best: history.window.best,
            worst: history.window.worst
          })}
        </p>
      )}
      <p className="hint">
        {t('projects.forecast.throughput.remaining', {
          count: history.remaining
        })}
      </p>

      {theirs === null ? (
        <p className="empty">{describeNoSecondOpinion(t, history)}</p>
      ) : (
        <>
          <p className="date">
            {t('projects.forecast.throughput.date', {
              confidence,
              date: formatDay(theirs, i18n.language)
            })}
          </p>
          {/*
            The one limitation that qualifies an answer rather than replacing it, and the
            window alone does not carry it: a reader has to be told that a short window is
            *why* the worst week above may be missing. Whether it fires is the server's to
            decide, as `EstimateQuality`'s thresholds are — this end renders a flag it was
            sent.
          */}
          {history.limitations.includes('throughput_window_is_short') && (
            <p className="caveat">{t('projects.forecast.throughput.short')}</p>
          )}
          {ours !== null && (
            <>
              <p className="hint">
                {t('projects.forecast.throughput.against', {
                  date: formatDay(ours, i18n.language)
                })}
              </p>
              <p className="caveat">{describeDifference(t, ours, theirs)}</p>
            </>
          )}
        </>
      )}

      {/*
        The picture, and the table it is a picture of. Both are the same numbers the date
        above came from — nothing here is a third forecast — and the table is built first
        because a cone that has to be seen to be understood is one this product cannot ship.
      */}
      {history.burnUp !== null && <BurnUpFigure burnUp={history.burnUp} />}

      {/*
        Named rather than subtracted into a figure. Two of these make the forecast look slow
        and two make it look fast, and the first two carry this run's own numbers because
        those are the ones somebody can act on.
      */}
      <h4>{t('projects.forecast.throughput.differences.title')}</h4>
      <ul>
        <li>{describeGrowth(t, run, i18n.language)}</li>
        <li>{describeEstimated(t, run)}</li>
        <li>{t('projects.forecast.throughput.differences.calendar')}</li>
        <li>{t('projects.forecast.throughput.differences.interruptions')}</li>
      </ul>
    </div>
  );
}

/**
 * What has been delivered and what is left: the numbers, and then a picture of them.
 *
 * **The table is the feature and the drawing is the enhancement, which is the opposite way
 * round from how a chart is usually built.** A cone described in words has to be understood
 * before it can be described, which is a better test of whether it is worth drawing than
 * drawing it is; and `roadmap.md` warns that charts built before the interface rework get
 * built twice, so the half that survives one is the half built first.
 *
 * **The drawing is `aria-hidden` and carries nothing the table does not.** A picture and its
 * equivalent saying the same thing twice to a screen reader is worse than either alone.
 */
function BurnUpFigure({ burnUp }: { burnUp: BurnUp }) {
  const { t, i18n } = useTranslation();
  const cone = burnUp.cone;
  // The first point of the cone is today with nothing yet delivered, which is the last row
  // of the past under a different name. It joins the two halves of the drawing and would be
  // a repeated line in the table, so it is in one and not the other.
  const ahead = cone === null ? [] : cone.slice(1);

  return (
    <div className="burnup">
      <h4>{t('projects.forecast.throughput.burnUp.title')}</h4>
      {/*
        **One sentence, and it does not name a second date.** The plan for this step proposed
        "the last is done between 12 October and 30 November", which is the two-sided form
        decision 2 exists to keep out — and the date it would have restated is already on
        screen one-sided three lines above. What is left is the half nothing else says.
      */}
      <p className="hint">
        {t('projects.forecast.throughput.burnUp.delivered', {
          delivered: burnUp.delivered,
          total: burnUp.total
        })}
      </p>

      <table className="weeks">
        <caption>
          {t('projects.forecast.throughput.burnUp.caption', {
            past: t('projects.forecast.throughput.burnUp.captionPast', {
              count: burnUp.past.length
            }),
            ahead: t('projects.forecast.throughput.burnUp.captionAhead', {
              count: ahead.length
            })
          })}
        </caption>
        <thead>
          <tr>
            <th scope="col">{t('projects.forecast.throughput.burnUp.week')}</th>
            <th scope="col">
              {t('projects.forecast.throughput.burnUp.count')}
            </th>
            <th scope="col">
              {t('projects.forecast.throughput.burnUp.range')}
            </th>
          </tr>
        </thead>
        <tbody>
          {burnUp.past.map((week) => (
            <tr key={week.week}>
              <th scope="row">{formatDay(week.week, i18n.language)}</th>
              <td>{week.delivered}</td>
              <td />
            </tr>
          ))}
        </tbody>
        {/*
          Its own group with its own heading, because the range column alone is a thin thing
          to hang "this one has not happened yet" on — and thinner still read out one cell at
          a time.
        */}
        {ahead.length > 0 && (
          <tbody>
            <tr>
              <th scope="rowgroup" colSpan={3}>
                {t('projects.forecast.throughput.burnUp.projected')}
              </th>
            </tr>
            {ahead.map((week) => (
              <tr key={week.week}>
                <th scope="row">{formatDay(week.week, i18n.language)}</th>
                <td>{week.p50}</td>
                <td>
                  {t('projects.forecast.throughput.burnUp.band', {
                    low: week.p10,
                    high: week.p90
                  })}
                </td>
              </tr>
            ))}
          </tbody>
        )}
      </table>

      <BurnUpDrawing burnUp={burnUp} />
    </div>
  );
}

/** How much room the drawing is given, in its own coordinates rather than in pixels. */
const CHART = { width: 640, height: 160, top: 10, bottom: 10 };

/**
 * The same series as the table above, drawn.
 *
 * **Inline SVG and no chart library**, following the bars M6 and M8 already render: a
 * dependency whose styling a rework would have to fight is exactly what `roadmap.md` warns
 * about. It reads the CSS variables the rest of the product is coloured from, so it follows
 * a theme rather than pinning one.
 *
 * **The band closes because the backlog is a ceiling, not because the uncertainty falls
 * away.** Every run stops when it covers the work, so they all arrive at the same number.
 * The line across the top is that ceiling, drawn so the narrowing reads as what it is.
 */
function BurnUpDrawing({ burnUp }: { burnUp: BurnUp }) {
  const cone = burnUp.cone ?? [];
  // One step per week across both halves, which they can share because both are weekly and
  // the cone begins in the week the past ends.
  const steps = Math.max(
    1,
    burnUp.past.length - 1 + Math.max(0, cone.length - 1)
  );
  const x = (week: number) => (week / steps) * CHART.width;
  const y = (delivered: number) =>
    CHART.height -
    CHART.bottom -
    (delivered / Math.max(1, burnUp.total)) *
      (CHART.height - CHART.top - CHART.bottom);
  const at = (week: number, delivered: number) => `${x(week)},${y(delivered)}`;
  const today = burnUp.past.length - 1;

  return (
    <svg
      className="drawing"
      viewBox={`0 0 ${CHART.width} ${CHART.height}`}
      aria-hidden="true"
      focusable="false"
    >
      {/* The total, which is what the cone closes onto. */}
      <line
        className="ceiling"
        x1={0}
        x2={CHART.width}
        y1={y(burnUp.total)}
        y2={y(burnUp.total)}
      />
      {cone.length > 1 && (
        <>
          <polygon
            className="cone"
            points={[
              ...cone.map((week, ahead) => at(today + ahead, week.p90)),
              ...cone
                .map((week, ahead) => at(today + ahead, week.p10))
                .reverse()
            ].join(' ')}
          />
          <polyline
            className="middle"
            points={cone
              .map((week, ahead) => at(today + ahead, week.p50))
              .join(' ')}
          />
        </>
      )}
      <polyline
        className="delivered"
        points={burnUp.past
          .map((week, index) => at(index, week.delivered))
          .join(' ')}
      />
    </svg>
  );
}

/**
 * The reading at one confidence, out of an answer that carries all three.
 *
 * **Shared by the drift and the decomposition because it is the same property**: both were
 * computed at every confidence M4's control offers, so moving the control changes what is on
 * screen and sends no request. That is the trade M4 built the control to make immediate,
 * inherited here rather than restated.
 */
function readingOf<T extends { confidence: number }>(
  answer: { at: T[] } | null,
  confidence: Confidence
): T | null {
  return (
    answer?.at.find((reading) => reading.confidence === confidence) ?? null
  );
}

/**
 * The question "why did the date move?", and its answer once somebody has asked it.
 *
 * **It costs six simulations and says so.** M7's rule: a number that is expensive to produce
 * should say what it cost rather than surprise somebody, and this is the second place in the
 * product where a read runs the engine.
 */
function MovementAsked({
  asking,
  account,
  simulations,
  failure,
  onAsk
}: {
  asking: boolean;
  account: MovementAt | null;
  simulations: number;
  failure: string | null;
  onAsk: () => void;
}) {
  const { t, i18n } = useTranslation();

  if (!asking) {
    return (
      <p className="actions">
        <button type="button" className="secondary" onClick={onAsk}>
          {t('projects.forecast.movement.open')}
        </button>
      </p>
    );
  }
  if (failure !== null) {
    return <p className="empty">{failure}</p>;
  }
  if (account === null) {
    return <p className="hint">{t('projects.forecast.movement.loading')}</p>;
  }
  return (
    <div className="movement">
      <p className="date">{describeMoved(t, account, i18n.language)}</p>
      {/*
        In the order the server attributed them and never re-sorted. The order *is* the rule:
        two defensible ones split the same eight days differently, so a list sorted by size
        here would be an account read under an order nobody stated.
      */}
      <ul className="terms">
        {account.terms.map((term) => (
          <li key={term.step}>
            <span className="what">
              {t(`projects.forecast.movement.steps.${term.step}`)}
            </span>
            <span className="days">{describeTerm(t, term)}</span>
          </li>
        ))}
      </ul>
      {/*
        Why they add up, and what that cost — beside the numbers rather than behind a link,
        which is the rule the assumptions and the limitations already follow.
      */}
      <p className="caveat">
        {t('projects.forecast.movement.cost', { simulations })}
      </p>
    </div>
  );
}

/**
 * The whole move in one sentence, or nothing when there is no date to have moved.
 *
 * The absence is a guard rather than a case: this question is only offered where both runs
 * resolve a date. The server versions ahead, though, and a headline missing its own dates
 * would otherwise render as a sentence with two blanks in it.
 */
function describeMoved(
  t: TFunction,
  account: MovementAt,
  locale: string
): string | null {
  if (account.fromDate === null || account.toDate === null) {
    return null;
  }
  const from = formatDay(account.fromDate, locale);
  const to = formatDay(account.toDate, locale);
  const days = Math.round(
    (Date.parse(account.toDate) - Date.parse(account.fromDate)) /
      (24 * 60 * 60 * 1000)
  );
  if (days === 0) {
    return t('projects.forecast.movement.movedNot', {
      confidence: account.confidence,
      date: to
    });
  }
  return days > 0
    ? t('projects.forecast.movement.movedOut', {
        confidence: account.confidence,
        days,
        from,
        to
      })
    : t('projects.forecast.movement.movedIn', {
        confidence: account.confidence,
        days: -days,
        from,
        to
      });
}

/** One term, in the direction it moved the date — and never as a bare signed number. */
function describeTerm(t: TFunction, term: MovementTerm): string {
  if (term.movedDays === null) {
    return t('projects.forecast.movement.termNoDays');
  }
  if (term.movedDays === 0) {
    return t('projects.forecast.movement.termNone');
  }
  return term.movedDays > 0
    ? t('projects.forecast.movement.termLater', { count: term.movedDays })
    : t('projects.forecast.movement.termEarlier', { count: -term.movedDays });
}

/** Which of the three reasons there is no second date, or that this version cannot say. */
function describeNoSecondOpinion(t: TFunction, history: Throughput): string {
  const reason = NO_SECOND_OPINION.find((code) =>
    history.limitations.includes(code)
  );
  return reason
    ? t(`projects.forecast.throughput.none.${reason}`)
    : t('projects.forecast.throughput.none.unknown');
}

/**
 * How far apart the two are, in days and in words.
 *
 * Later is the ordinary result and says so: the engine sums estimates of focused work and the
 * history contains every meeting, incident and holiday. A team seeing the two agree closely
 * should be more suspicious than one seeing them differ.
 */
function describeDifference(
  t: TFunction,
  ours: string,
  theirs: string
): string {
  const days = Math.round(
    (Date.parse(theirs) - Date.parse(ours)) / (24 * 60 * 60 * 1000)
  );
  if (days === 0) {
    return t('projects.forecast.throughput.differenceSame');
  }
  return days > 0
    ? t('projects.forecast.throughput.differenceLater', { days })
    : t('projects.forecast.throughput.differenceEarlier', { days: -days });
}

/** What this run assumed about work nobody has written down, which the history cannot see. */
function describeGrowth(t: TFunction, run: Forecast, locale: string): string {
  const grows = run.scopeGrowthP10Percent > 0 || run.scopeGrowthP90Percent > 0;
  return grows
    ? t('projects.forecast.throughput.differences.unlistedGrowth', {
        low: decimal(run.scopeGrowthP10Percent, locale, 0),
        high: decimal(run.scopeGrowthP90Percent, locale, 0)
      })
    : t('projects.forecast.throughput.differences.unlistedNone');
}

/** The one place the history is better informed than the engine — decision 7's second row. */
function describeEstimated(t: TFunction, run: Forecast): string {
  const unestimated = run.itemCount - run.estimatedItemCount;
  return unestimated > 0
    ? t('projects.forecast.throughput.differences.unestimated', {
        unestimated,
        total: run.itemCount
      })
    : t('projects.forecast.throughput.differences.estimated');
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
  plan: string,
  confidence: Confidence,
  locale: string
): string {
  const day = run[DATE_AT[confidence]];
  if (day !== null) {
    return t('projects.forecast.confidence.date', {
      plan,
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
 * What one source of a plan's spread is called.
 *
 * **Two of the three are not work**, and naming them in words is what keeps the ranking
 * honest: when the shared factor or the unlisted work tops the list, the answer to "what
 * should I spike" is that no estimate below it is the problem.
 *
 * What a piece of work is *called* is {@link describeWork}'s, shared with the cuts below it:
 * one run, two lists naming the same items, and a second copy of that rule would be a second
 * chance for one of them to start rendering a missing item as a blank.
 */
function describeSource(t: TFunction, source: Contribution): string {
  if (source.kind === 'item') {
    return describeWork(t, source.title, source.archived);
  }
  if (source.kind === 'discovered_work') {
    return t('projects.forecast.contributions.discoveredWork');
  }
  if (source.kind === 'team_factor') {
    return t('projects.forecast.contributions.teamFactor');
  }
  // A kind this version has never heard of, which the types say cannot happen and the
  // server is free to send: it is what versions ahead here. Falling through to the item
  // branch would have labelled it "work no longer in this plan", which is not merely
  // unhelpful but wrong — the same back door `describeLimitation` is closed against.
  return t('projects.forecast.contributions.unknownKind');
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
