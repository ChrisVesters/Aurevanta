import { useState } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useLoaded } from '../api/useLoaded';
import type { Calibration } from '../calibration/types';
import type { Resource } from '../resource/types';
import { formatDay, todayHere } from './dates';
import { CONFIDENCES, DATE_AT, USUAL_CONFIDENCE } from './confidence';
import type { Confidence } from './confidence';
import { SpreadPanel } from './SpreadPanel';
import { ThroughputComparison } from './ThroughputComparison';
import {
  calendarOf,
  describeDate,
  describeLimitation,
  describePool,
  hours,
  readingOf,
  stretchAndGrowth
} from './forecastText';
import { EarlierRuns } from './EarlierRuns';
import { ForecastAsk } from './ForecastAsk';
import { HiringPanel } from './HiringPanel';
import { TargetDate } from './TargetDate';
import type { ForecastHistory, Throughput } from './forecastTypes';

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
  const [reloads, setReloads] = useState(0);
  const [confidence, setConfidence] = useState<Confidence>(USUAL_CONFIDENCE);
  /**
   * What the ranges feeding this band have historically been worth.
   *
   * A caveat about the *inputs* and never a correction to the number: folding a calibration
   * factor into a forecast would close a loop on its own evidence, so the record converges
   * on 80% while nothing about the estimating changes. This says what the estimates have
   * been worth and leaves the band exactly as the engine produced it.
   */
  /**
   * What this plan's own history says, which is the second half of the only comparison this
   * screen makes. Its own request and its own failure: a forecast is not less true because
   * the history beside it could not be read, so losing this leaves the band alone.
   */
  /**
   * Why there is no history to show, when there is a reason worth passing on.
   *
   * **Losing the read must leave the band alone; it must not leave the reader guessing.** The
   * first version set the state to null and rendered nothing, so a plan holding one task
   * marked finished next week — which this product's own progress form will accept — lost the
   * entire comparison with no sentence anywhere saying why. A refusal that names something
   * fixable in somebody's own plan is exactly the one to pass on.
   */
  /**
   * Whether this plan's date keeps moving out, which arrives with the history above.
   *
   * Rendered only when the server says it is worth saying: a plan that is merely churning
   * must hear nothing, or the warning becomes one nobody reads. Which of those it is is the
   * server's to decide — the browser holds no threshold, as it holds none of
   * `EstimateQuality`'s.
   */
  /**
   * The pools this organisation has declared, which decide whether a capacity is asked for
   * at all.
   *
   * Its own read and one this panel survives losing: a forecast is not less true because
   * the team could not be listed, so a failure here leaves the box where it was — which is
   * the state every organisation is in until it describes one.
   */
  const { data: declared } = useLoaded<Resource[]>('/resources', []);
  const pools = declared ?? [];

  // The history and the verdict on it arrive together: whether the date keeps moving out is
  // a property of the sequence rather than of any run in it, so there is nowhere on a run to
  // read it from and no second request to make.
  const { data: history, failure } = useLoaded<ForecastHistory>(
    `/projects/${projectId}/forecasts`,
    [projectId, reloads]
  );
  const runs = history?.runs ?? null;
  const drift = history?.drift ?? null;

  // Its own read, and one this panel must survive losing. A forecast is not less true
  // because the record beside it could not be loaded, so a failure here leaves the line off
  // and touches nothing else — which is why it has no failure state of its own to render.
  const { data: track } = useLoaded<Calibration>('/calibration', []);

  // Asked about *today*, stated from here rather than left to the server: what day it is
  // where somebody is sitting is a fact only this end holds, which is the argument
  // `todayHere` exists to make. Keyed on the plan alone — the answer moves when work is
  // finished, not when a forecast is run, so it does not reload with the rest of the panel.
  const { data: throughput, failure: throughputFailure } =
    useLoaded<Throughput>(
      `/projects/${projectId}/throughput?asOf=${todayHere()}`,
      [projectId]
    );
  const latest = runs?.[0];
  const earlier = runs?.slice(1) ?? [];
  const calendar = latest ? calendarOf(latest, i18n.language) : null;
  // All five are absent together, so the one being read answers "has this run any dates at
  // all" — which is what decides whether there is anything for the control to choose
  // between, and is a different question from whether a calendar was stated.
  const chosenDate = latest ? latest[DATE_AT[confidence]] : null;
  // The drift at whichever confidence is being read, and only when it has dates to name.
  // The server's answer rather than this end's, on both counts: the window it was measured
  // over, and whether the distance is worth saying at all.
  const drifting = readingOf(drift, confidence);

  return (
    <section className="forecast">
      <h2>{t('projects.forecast.title')}</h2>
      <p className="hint">{t('projects.forecast.lede')}</p>

      {failure && (
        <p className="form-error" role="alert">
          {failure}
        </p>
      )}

      {/*
        Both panels that read a run — the breakdown and the account of a movement — clear
        themselves by being keyed on the run they are about, so a new forecast needs nothing
        said to them here beyond reloading the history.
      */}
      <ForecastAsk
        projectId={projectId}
        pools={pools}
        onForecast={() => setReloads((count) => count + 1)}
      />

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
            **The team it was scheduled against, which is the run's own and not today's.**
            A pool that has grown since did not grow this forecast, so what is printed is
            what the run stored — and only the name comes off the organisation's list,
            because a rename is not a thing that moved. A pool put away since is marked
            rather than shown as ordinary, and one this organisation no longer holds at all
            says so rather than rendering as a blank.
          */}
          {latest.resources.length > 0 && (
            <p className="assumptions">
              {t('projects.forecast.resources.scheduledAgainst', {
                team: latest.resources
                  .map((pool) => describePool(t, pool))
                  .join(', ')
              })}
            </p>
          )}

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
          <SpreadPanel key={latest.id} runId={latest.id} />

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

          {/*
            The other question a team asks about a date, and the one M11 makes answerable:
            not *what do we drop* but *what if there were more of us*. Keyed on the run for
            `TargetDate`'s reason — an answer measured against a forecast that is no longer
            on screen is an answer about a plan nobody is looking at.
          */}
          <HiringPanel
            key={`hiring-${latest.id}`}
            run={latest}
            confidence={confidence}
          />

          {earlier.length > 0 && (
            <EarlierRuns
              latestId={latest.id}
              latestHasDate={chosenDate !== null}
              earlier={earlier}
              confidence={confidence}
            />
          )}
        </>
      )}
    </section>
  );
}
