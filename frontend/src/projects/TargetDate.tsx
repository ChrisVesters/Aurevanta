import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { useFormFailure } from '../auth/useFormFailure';
import { describeFailure } from '../i18n/problems';
import { numberField } from './fields';
import { describeWork } from './work';
import type { CutOptions, Forecast, WorkItem } from './types';

/**
 * The two boxes this form asks for, which is what `useFormFailure` needs. The candidates
 * are not among them and deliberately: they are ticked rather than typed, so a complaint
 * about them belongs in the banner where it can be read.
 */
const ASKED_FOR = ['by', 'confidence'];

/**
 * As many things as the server will weigh at once, because each one is a whole simulation.
 *
 * Repeated here to *stop* somebody reaching it rather than to decide anything: the server
 * refuses a thirteenth and this is what makes that refusal unreachable from the screen. A
 * form that let somebody tick fifteen boxes and then told them to untick three would be
 * asking them to guess which three mattered.
 */
const MOST_CANDIDATES = 12;

/**
 * A date somebody wants, and what it would take to get there.
 *
 * **The one screen in this product that proposes cutting work**, which is why almost
 * everything about it is arranged to stop it being read as an instruction. It proposes and
 * decides nothing: acting on the answer means archiving something on the plan screen, where
 * a reader can see what else it is connected to.
 *
 * **The candidates come from the person, never from the server.** Which work is negotiable
 * is a judgement about its value, and nothing in this product records any — a task worth
 * four weeks that a regulator requires is not a candidate, and a two-day nicety is. A list
 * this application proposed by itself would be recommending that somebody delete work
 * because it happened to sit on the deciding path.
 *
 * **The cumulative list and the singles are two different answers and are never in one
 * column.** Each single says what it buys *on its own*; they overlap, they sometimes cancel,
 * and adding two of them together gives a number no run of this plan ever produced. The list
 * that reaches the bar was searched for and measured at every step, and it is the one to act
 * on. A screen that put both in a table with plus signs down the side would be arithmetic
 * waiting to happen, which is decision 7 with the volume turned up.
 *
 * **Only work the run was actually about may be ticked.** An item written down since the
 * forecast is not in it, and the server refuses to weigh one — so it is not offered, because
 * a refusal about a box the screen has just invited somebody to tick is a trap rather than a
 * check. That is the same rule the progress form keeps when it offers only the boxes a
 * status has room for.
 */
export function TargetDate({ run }: { run: Forecast }) {
  const { t } = useTranslation();
  const { request } = useAuth();
  /** The plan's own work, which is where a candidate comes from. Null until it lands. */
  const [work, setWork] = useState<WorkItem[] | null>(null);
  const [workFailure, setWorkFailure] = useState<string | null>(null);
  // Held here rather than read out of the form on submission, because it is half of what
  // decides whether there is a question to ask at all.
  const [by, setBy] = useState('');
  const [ticked, setTicked] = useState<string[]>([]);
  const [answer, setAnswer] = useState<CutOptions | null>(null);
  const [busy, setBusy] = useState(false);
  const asking = useFormFailure(ASKED_FOR);

  useEffect(() => {
    let cancelled = false;

    request<WorkItem[]>(`/projects/${run.projectId}/items`)
      .then((loaded) => {
        if (!cancelled) {
          setWork(loaded);
        }
      })
      .catch((error: unknown) => {
        // Said rather than swallowed. An empty tick list and a tick list that failed to
        // load look identical, and the first of them reads as "there is nothing you could
        // drop" — which is an answer, and the wrong one.
        if (!cancelled) {
          setWorkFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, run.projectId, t]);

  // A run made before there was a calendar, or under one this version cannot read, has no
  // date to be asked about at all — so it says which of the two it is instead of showing a
  // form that could only ever be refused.
  if (run.workingHoursPerDay === null) {
    return (
      <div className="target">
        <h3>{t('projects.forecast.target.title')}</h3>
        <p className="empty">{t('projects.forecast.target.noCalendar')}</p>
      </div>
    );
  }
  if (run.p50Date === null) {
    return (
      <div className="target">
        <h3>{t('projects.forecast.target.title')}</h3>
        <p className="empty">
          {t('projects.forecast.target.unreadableCalendar')}
        </p>
      </div>
    );
  }

  const offered = (work ?? []).filter((item) => wasInTheRun(item, run));
  const atTheLimit = ticked.length >= MOST_CANDIDATES;

  function tick(itemId: string) {
    setTicked((chosen) =>
      chosen.includes(itemId)
        ? chosen.filter((each) => each !== itemId)
        : [...chosen, itemId]
    );
  }

  async function ask(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    setBusy(true);
    asking.clear();
    try {
      setAnswer(
        await request<CutOptions>(`/forecasts/${run.id}/cuts`, {
          method: 'POST',
          body: {
            by,
            // Null where the box is empty rather than zero, since `Number('')` is zero and
            // would ask for a date at no confidence at all — which the server refuses for
            // the wrong reason, telling somebody a number they never typed is too small.
            confidence: numberField(values, 'confidence'),
            candidates: ticked
          }
        })
      );
    } catch (error) {
      // Including the two this endpoint has of its own — a run the engine no longer
      // reproduces, and work the forecast was never about — both of which arrive as an
      // ordinary code and are worded by the catalogue like every other refusal.
      setAnswer(null);
      asking.report(error);
    }
    setBusy(false);
  }

  return (
    <div className="target">
      <h3>{t('projects.forecast.target.title')}</h3>
      <p className="hint">{t('projects.forecast.target.lede')}</p>

      <form onSubmit={(event) => void ask(event)} noValidate>
        {asking.message && (
          <p className="form-error" role="alert">
            {asking.message}
          </p>
        )}

        <span className="field">
          <label htmlFor="target-by">
            {t('projects.forecast.target.fields.by.label')}
          </label>
          <input
            id="target-by"
            name="by"
            type="date"
            value={by}
            onChange={(event) => setBy(event.target.value)}
            aria-invalid={asking.fieldErrors.by ? true : undefined}
          />
          <span className="hint">
            {t('projects.forecast.target.fields.by.hint')}
          </span>
          {asking.fieldErrors.by && (
            <span className="field-error">{asking.fieldErrors.by}</span>
          )}
        </span>

        {/*
          Empty like every other claim on this panel. There is no ordinary confidence to
          commit at — 80 is a reading and 95 is a promise — and a box arriving already
          answered would be answering the one question this screen exists to put.
        */}
        <span className="field">
          <label htmlFor="target-confidence">
            {t('projects.forecast.target.fields.confidence.label')}
          </label>
          <input
            id="target-confidence"
            name="confidence"
            type="number"
            inputMode="numeric"
            min="1"
            max="100"
            step="5"
            aria-invalid={asking.fieldErrors.confidence ? true : undefined}
          />
          <span className="hint">
            {t('projects.forecast.target.fields.confidence.hint')}
          </span>
          {asking.fieldErrors.confidence && (
            <span className="field-error">{asking.fieldErrors.confidence}</span>
          )}
        </span>

        <fieldset className="candidates">
          <legend>{t('projects.forecast.target.candidates.legend')}</legend>
          <span className="hint">
            {t('projects.forecast.target.candidates.hint')}
          </span>
          {workFailure !== null ? (
            <p className="empty">{workFailure}</p>
          ) : work === null ? (
            <p className="loading" role="status">
              {t('projects.items.loading')}
            </p>
          ) : offered.length === 0 ? (
            <p className="empty">
              {t('projects.forecast.target.candidates.none')}
            </p>
          ) : (
            <ul className="ticks">
              {offered.map((item) => (
                <li key={item.id}>
                  <label htmlFor={`target-candidate-${item.id}`}>
                    <input
                      id={`target-candidate-${item.id}`}
                      type="checkbox"
                      checked={ticked.includes(item.id)}
                      // Stopped at the limit rather than refused after the fact: the
                      // server weighs twelve, and being told to untick three would be
                      // being asked to guess which three mattered.
                      disabled={atTheLimit && !ticked.includes(item.id)}
                      onChange={() => tick(item.id)}
                    />
                    {item.title}
                  </label>
                </li>
              ))}
            </ul>
          )}
          {atTheLimit && (
            <span className="hint">
              {t('projects.forecast.target.candidates.limit', {
                most: MOST_CANDIDATES
              })}
            </span>
          )}
        </fieldset>

        <p className="actions">
          {/*
            Nothing goes out until there is a date and something to weigh. Both are the
            question rather than options on it: a target with no candidates asks only
            whether the plan already gets there, which the band above has already said.
          */}
          <button
            type="submit"
            className="secondary"
            disabled={busy || by === '' || ticked.length === 0}
          >
            {busy
              ? t('projects.forecast.target.submitting')
              : t('projects.forecast.target.submit')}
          </button>
        </p>
      </form>

      {answer && <Answer answer={answer} />}
    </div>
  );
}

/**
 * What came back, in the order somebody needs it.
 *
 * **Whether the bar is already met comes first**, because everything below it is advice
 * nobody needs when it is — and a screen that led with a list of work to drop would have
 * proposed a sacrifice before mentioning it was unnecessary.
 */
function Answer({ answer }: { answer: CutOptions }) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language;

  return (
    <div className="answer">
      <p className="standing">
        {answer.meets
          ? t('projects.forecast.target.answer.met', {
              confidence: number(answer.baselineConfidence, locale, 1)
            })
          : t('projects.forecast.target.answer.short', {
              confidence: number(answer.baselineConfidence, locale, 1)
            })}
      </p>

      {/*
        The hours the date came to, beside the answer it produced. A target date means
        nothing without a working day and a calendar, and this run carries both — the same
        rule that keeps the band's assumptions on screen rather than behind a disclosure.
      */}
      <p className="assumptions">
        {t('projects.forecast.target.answer.budget', {
          hours: number(answer.targetHours, locale, 1),
          simulations: answer.simulations
        })}
      </p>

      {!answer.meets && (
        <>
          <h4 id="target-together">
            {t('projects.forecast.target.together.title')}
          </h4>
          <p className="hint">{t('projects.forecast.target.together.lede')}</p>
          {answer.together.steps.length > 0 && (
            <ol className="steps" aria-labelledby="target-together">
              {answer.together.steps.map((step) => (
                <li key={step.itemId}>
                  {t('projects.forecast.target.together.step', {
                    what: describeWork(t, step.title, step.archived),
                    confidence: number(step.confidence, locale, 1)
                  })}
                </li>
              ))}
            </ol>
          )}
          {/*
            Why the search stopped, and never left to be inferred from the length of the
            list above. A set that reaches the bar, the best that could be found, and a
            search that ran out of the runs it was allowed are three different answers, and
            only the first of them is finished.
          */}
          <p className="ending">
            {t(`projects.forecast.target.endings.${answer.together.ending}`)}
          </p>
        </>
      )}

      {answer.cuts.length > 0 && (
        <>
          <h4 id="target-singles">
            {t('projects.forecast.target.singles.title')}
          </h4>
          {/*
            Beside the list and not behind a disclosure, for the reason the band's caveats
            are: these are what each buys *alone*, they overlap, and two of them added
            together give a number no run of this plan ever produced. It is said before the
            numbers rather than after them, because a reader who has already added two up
            has already been given the wrong answer.
          */}
          <p className="caveat">{t('projects.forecast.target.singles.lede')}</p>
          <ul className="singles" aria-labelledby="target-singles">
            {answer.cuts.map((cut) => (
              <li key={cut.itemId}>
                {t('projects.forecast.target.singles.entry', {
                  what: describeWork(t, cut.title, cut.archived),
                  confidence: number(cut.confidence, locale, 1),
                  buys: number(cut.buys, locale, 1)
                })}
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

/**
 * Whether a forecast was made after this work was written down, which is the only thing on
 * this side of the wire that can say whether the run holds it.
 *
 * **A run's snapshot is the work the plan held at the moment it was asked for**, and nothing
 * in the forecast response lists it — deliberately, since the snapshot keeps identifiers and
 * no titles so that M10 can diff two runs without a rename reading as movement. So this
 * compares the two moments, which errs in the safe direction: work added since is never
 * offered, and work put away since the run is in the snapshot but absent from the live
 * listing, so it is not offered either. Offering too few is an omission somebody can see;
 * offering too many would be a tick box the server refuses.
 */
function wasInTheRun(item: WorkItem, run: Forecast): boolean {
  return item.createdAt <= run.createdAt;
}

/** A quantity in the reader's own locale, to as many places as it is worth reading. */
function number(
  value: number,
  locale: string,
  maximumFractionDigits: number
): string {
  return new Intl.NumberFormat(locale, { maximumFractionDigits }).format(value);
}
