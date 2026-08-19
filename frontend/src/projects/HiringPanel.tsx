import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { describeFailure } from '../i18n/problems';
import { formatDay } from './dates';
import { textField } from '../auth/formValues';
import { numberField } from './fields';
import type { Forecast, HireOptions } from './types';

/** Every confidence M4's control offers, which one answer covers. */
type Confidence = 50 | 80 | 95;

/**
 * What hiring into one pool would be worth, weighed against one forecast.
 *
 * **It weighs and never decides**, which is `TargetDate`'s arrangement and for the same
 * reason: what a person costs, whether one can be found, and how long they take to be useful
 * are judgements this application holds none of. The answer is a number of days.
 *
 * **The model has no ramp-up and the screen says so.** A unit added here is at full rate
 * from the first hour, which no new joiner is — so every answer carries that caveat rather
 * than leaving somebody to remember it. It is the one place this product's own model is
 * optimistic in a way the number cannot show.
 *
 * **Only pools the run was actually scheduled against are offered.** A pool declared since
 * is not in the forecast and the server refuses to weigh one, so a screen that invited
 * somebody to pick it would be a trap rather than a check — the same rule the target-date
 * form keeps about work written down since.
 *
 * **The rows are cumulative and are never a column to add up.** Each says what *that many*
 * would buy, measured; the difference between two rows is what the next person adds, and it
 * shrinks. That shrinking is the answer to "should we hire", and it is the reason the rows
 * are simulated rather than the first one being multiplied.
 */
export function HiringPanel({
  run,
  confidence
}: {
  run: Forecast;
  confidence: Confidence;
}) {
  const { t, i18n } = useTranslation();
  const { request } = useAuth();
  const [answer, setAnswer] = useState<HireOptions | null>(null);
  const [failure, setFailure] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (run.resources.length === 0) {
    return null;
  }

  async function weigh(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setBusy(true);
    try {
      const weighed = await request<HireOptions>(`/forecasts/${run.id}/hires`, {
        method: 'POST',
        body: {
          // Through the same reader every other form uses rather than a fallback of its
          // own: the select always has a value — the panel is not rendered without one —
          // so a guard here would be a branch nothing could reach.
          resourceId: textField(values, 'resourceId'),
          units: numberField(values, 'units')
        }
      });
      setAnswer(weighed);
      setFailure(null);
    } catch (error) {
      setAnswer(null);
      setFailure(describeFailure(t, error));
    }
    setBusy(false);
  }

  const reading = answer?.at.find((at) => at.confidence === confidence);

  return (
    <div className="hiring">
      <h3>{t('projects.forecast.hiring.title')}</h3>
      <p className="hint">{t('projects.forecast.hiring.lede')}</p>

      <form onSubmit={(event) => void weigh(event)} noValidate>
        <p className="field">
          <label htmlFor="hire-resource">
            {t('projects.forecast.hiring.which')}
          </label>
          <select id="hire-resource" name="resourceId">
            {run.resources.map((pool) => (
              <option key={pool.resourceId} value={pool.resourceId}>
                {pool.name ?? t('projects.forecast.hiring.unnamed')}
              </option>
            ))}
          </select>
        </p>
        <p className="field">
          <label htmlFor="hire-units">
            {t('projects.forecast.hiring.howMany')}
          </label>
          <input
            id="hire-units"
            name="units"
            type="number"
            inputMode="numeric"
            min="1"
            max="10"
            step="1"
            defaultValue={2}
          />
        </p>
        <p className="actions">
          <button type="submit" className="secondary" disabled={busy}>
            {busy
              ? t('projects.forecast.hiring.weighing')
              : t('projects.forecast.hiring.weigh')}
          </button>
        </p>
      </form>

      {failure && <p className="empty">{failure}</p>}

      {reading && (
        <>
          <p className="hint">
            {t('projects.forecast.hiring.stands', {
              confidence,
              date: formatDay(reading.stands, i18n.language)
            })}
          </p>
          <ul className="hires">
            {reading.hires.map((step) => (
              <li key={step.units}>
                <span className="what">
                  {t('projects.forecast.hiring.step', { count: step.units })}
                </span>
                <span className="days">
                  {/*
                    Three sentences rather than one, because the number has three signs.
                    A negative is a real answer — a list scheduler over a precedence graph
                    can finish later with more units, and each date is rounded up on its
                    own — and rendering it through the "sooner" wording would read as a
                    bug in this page rather than as a fact about the plan.
                  */}
                  {step.daysEarlier === 0 &&
                    t('projects.forecast.hiring.buysNothing')}
                  {step.daysEarlier > 0 &&
                    t('projects.forecast.hiring.buys', {
                      count: step.daysEarlier,
                      date: formatDay(step.by, i18n.language)
                    })}
                  {step.daysEarlier < 0 &&
                    t('projects.forecast.hiring.buysLater', {
                      count: -step.daysEarlier,
                      date: formatDay(step.by, i18n.language)
                    })}
                </span>
              </li>
            ))}
          </ul>
          {/*
            Beside the answer and never behind a disclosure, which is the rule the
            assumptions and the limitations already follow: this model has no ramp-up at
            all, so every row above is the best case by a margin nothing here can measure.
          */}
          <p className="caveat">{t('projects.forecast.hiring.rampUp')}</p>
        </>
      )}

      {/*
        The price belongs to the answer rather than to one confidence, so it sits outside
        the reading — which is also what keeps it from needing a fallback for a number that
        is always there once there is an answer at all.
      */}
      {answer !== null && (
        <p className="caveat">
          {t('projects.forecast.hiring.cost', {
            simulations: answer.simulations
          })}
        </p>
      )}
    </div>
  );
}
