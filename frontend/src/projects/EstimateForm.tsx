import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { numberFrom } from './fields';
import type { Estimate, EstimateQuality } from './types';

export type EstimateValues = {
  p10Hours: number | null;
  p50Hours: number | null;
  p90Hours: number | null;
  method: string;
};

/**
 * How this form asks, said by the form that asks it.
 *
 * The server cannot observe how a browser put a question, so it has to be told — and it is
 * told by the component that knows, rather than by the caller that merely posts. That is
 * what makes the answer true: the name changed here in the same edit as the questions it
 * describes.
 */
const METHOD = 'surprise_framed';

/**
 * The three questions, in the order that stops them anchoring, and the field each answers.
 *
 * **The bad case first, because it is the only one of the three that is unbounded.** The
 * good case has a floor — the work obviously takes something, and nobody's optimistic
 * answer is zero — so an anchor above it compresses it far less than an anchor below the
 * bad case compresses that. The bad case has nothing above it: it can always be worse,
 * which is the property this product chose a log-normal for, and it is the number every
 * team gets wrong in the same direction. Asking it before any number exists on screen is
 * the only moment it can be answered cold.
 *
 * **The middle last, because the fit does not use it.** It is the consistency signal, and a
 * signal arrived at by arithmetic on the two ends in somebody's head is not a signal. Asked
 * last, it is the one number that may be anchored and the one that can afford to be.
 *
 * Reordering this array is not a cosmetic change. It is the whole of what separates this
 * form from three boxes that produce 3/5/8.
 */
const STEPS = [
  { field: 'p90Hours', wording: 'bad' },
  { field: 'p10Hours', wording: 'good' },
  { field: 'p50Hours', wording: 'typical' }
] as const;

type Field = (typeof STEPS)[number]['field'];

/**
 * The screen after the last question, and the first moment the three are seen together.
 *
 * That is deliberate rather than incidental. Seeing them together while any of them is
 * still being answered is the anchoring the order exists to prevent; seeing them together
 * once all three exist is the only way to notice that the bad week is barely worse than the
 * ordinary one. It is also where the bet is asked and where the warnings arrive, both of
 * which need a number that has been given.
 */
const REVIEW = STEPS.length;

/**
 * The one refusal this form has an *action* for rather than only a message.
 *
 * A range the wrong way round belongs to no single box, so it arrives in the banner — and
 * the banner is useless here, because the numbers it is about are each on a screen the
 * visitor is not looking at. So it sends them back to the first question to read their own
 * answers in order. `useFormFailure` hands back the code for exactly this case.
 */
const OUT_OF_ORDER = 'estimate_out_of_order';

type EstimateFormProps = {
  id: string;
  /** The caller's current estimate, so revising starts from what they last said. */
  estimate?: Estimate;
  busy: boolean;
  banner: string | null;
  code?: string;
  fieldErrors: Record<string, string>;
  onSubmit: (values: EstimateValues) => void;
  onCancel: () => void;
};

/**
 * One question at a time, and never the middle one first.
 *
 * **This form used to be three boxes labelled P10, P50 and P90, and replacing it is the
 * milestone.** `product-concept.md` is blunt that those produce 3/5/8 without anybody
 * thinking, and the measurement in `m5-plan.md` says why no check can catch it: every
 * Fibonacci triple agrees with itself to within a few percent and clears the overconfidence
 * rule. The garbage is *coherent*. The fault is not in the numbers, it is that they were
 * never separately thought about — which is a property of how they were asked for, and
 * leaves no trace in what is stored.
 *
 * **So one question is on screen at a time and no earlier answer is visible while the next
 * is asked.** The order buys nothing if the first answer can be seen while the second is
 * typed, because the anchor is *seeing* it rather than typing it. All three appear together
 * at the review, which is where they are meant to be seen together.
 *
 * **Nothing on screen says P90 while a question is being answered.** The percentile names
 * are what invite somebody to reason about tail probability, which nobody can do; surprise
 * is a thing people recognise. The labels are the questions.
 *
 * **There is no fast path back to three boxes**, deliberately. It would be used by
 * everybody, because it is quicker and because the people most confident they do not need
 * the framing are the people it is for. Revising is the case that objection is strongest
 * for, and it is answered by pre-filling every step from the current estimate: changing 8
 * to 10 is three confirmations, and revising is exactly when the tails are worth another
 * look.
 */
export function EstimateForm({
  id,
  estimate,
  busy,
  banner,
  code,
  fieldErrors,
  onSubmit,
  onCancel
}: EstimateFormProps) {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [at, setAt] = useState(0);
  const [quality, setQuality] = useState<EstimateQuality | null>(null);
  const [answers, setAnswers] = useState<Record<Field, string>>(() => ({
    p10Hours: typed(estimate?.p10Hours),
    p50Hours: typed(estimate?.p50Hours),
    p90Hours: typed(estimate?.p90Hours)
  }));

  // A refusal has to arrive somewhere the visitor can act on it, and on this form the box
  // it belongs to is usually not the one they are looking at. So a complaint about a field
  // brings its own question back; a range the wrong way round belongs to all three and
  // starts again from the first, since reading them in order is the only way to see it.
  useEffect(() => {
    const refused = STEPS.findIndex(
      (step) => fieldErrors[step.field] !== undefined
    );
    if (refused >= 0) {
      setAt(refused);
    } else if (code === OUT_OF_ORDER) {
      setAt(0);
    }
  }, [code, fieldErrors]);

  // Asked of the server rather than worked out here, and asked afresh whenever the answers
  // change, so that stepping back to widen a band and returning shows what that band now
  // says rather than what the old one did. A refusal — a range the wrong way round, a
  // question left blank — leaves the review with no warnings on it and the server to say so
  // on submit, which is better than a browser inventing a second opinion.
  useEffect(() => {
    if (at !== REVIEW) {
      setQuality(null);
      return undefined;
    }
    let cancelled = false;
    request<EstimateQuality>('/estimates/quality', {
      method: 'POST',
      body: {
        p10Hours: numberFrom(answers.p10Hours),
        p50Hours: numberFrom(answers.p50Hours),
        p90Hours: numberFrom(answers.p90Hours)
      }
    })
      .then((graded) => {
        if (!cancelled) {
          setQuality(graded);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setQuality(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [at, answers, request]);

  const step = STEPS[at];
  const reviewing = at === REVIEW;

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    // Every step submits, so Enter moves the visitor on rather than sending three
    // questions' worth of half an answer.
    if (!reviewing) {
      setAt(at + 1);
      return;
    }
    onSubmit({
      p10Hours: numberFrom(answers.p10Hours),
      p50Hours: numberFrom(answers.p50Hours),
      p90Hours: numberFrom(answers.p90Hours),
      method: METHOD
    });
  }

  return (
    <form className="estimate-form" onSubmit={handle} noValidate>
      <p className="hint">{t('projects.items.estimate.hint')}</p>

      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}

      {reviewing ? (
        <div className="review">
          <h4>{t('projects.items.estimate.review.title')}</h4>
          <ul>
            {STEPS.map((answered) => (
              <li key={answered.field}>
                {t(`projects.items.estimate.review.${answered.wording}`, {
                  hours:
                    answers[answered.field] === ''
                      ? t('projects.items.estimate.review.unanswered')
                      : answers[answered.field]
                })}
              </li>
            ))}
          </ul>

          {/*
            The betting frame, which makes a number typed cheaply feel expensive. It gates
            nothing: saying yes is pressing save, and the only control is the way out —
            because a bet somebody would not take is a P90 they have not really given.
          */}
          {answers.p90Hours !== '' && (
            <p className="bet">
              {t('projects.items.estimate.bet.question', {
                hours: answers.p90Hours
              })}{' '}
              <button type="button" className="link" onClick={() => setAt(0)}>
                {t('projects.items.estimate.bet.decline')}
              </button>
            </p>
          )}

          {/*
            Advice, never a refusal — decision 5. A tight band is sometimes exactly right,
            and a rule that blocked one would become a specification people learn to type,
            which is 3/5/8 with an extra step. Rendered from what the server said, so
            moving a threshold there needs no change here.
          */}
          {quality?.overconfident && (
            <p className="warning">
              {t('projects.items.estimate.warnings.overconfident')}
            </p>
          )}
          {quality?.inconsistent && (
            <p className="warning">
              {t('projects.items.estimate.warnings.inconsistent')}
            </p>
          )}
        </div>
      ) : (
        <span className="field">
          <label htmlFor={`${id}-${step.field}`}>
            {t(`projects.items.estimate.steps.${step.wording}.question`)}
          </label>
          <input
            id={`${id}-${step.field}`}
            name={step.field}
            type="number"
            inputMode="decimal"
            min="0"
            step="0.25"
            value={answers[step.field]}
            onChange={(event) =>
              setAnswers({ ...answers, [step.field]: event.target.value })
            }
            aria-invalid={fieldErrors[step.field] ? true : undefined}
          />
          <span className="hint">
            {t(`projects.items.estimate.steps.${step.wording}.hint`)}
          </span>
          {fieldErrors[step.field] && (
            <span className="field-error">{fieldErrors[step.field]}</span>
          )}
        </span>
      )}

      <p className="actions">
        <span className="progress">
          {reviewing
            ? t('projects.items.estimate.review.progress')
            : t('projects.items.estimate.progress', {
                step: at + 1,
                total: STEPS.length
              })}
        </span>
        {at > 0 && (
          <button type="button" className="link" onClick={() => setAt(at - 1)}>
            {t('projects.items.estimate.back')}
          </button>
        )}
        <button type="submit" className="primary" disabled={busy}>
          {reviewing
            ? busy
              ? t('projects.items.estimate.submitting')
              : t('projects.items.estimate.submit')
            : t('projects.items.estimate.next')}
        </button>
        <button type="button" className="link" onClick={onCancel}>
          {t('projects.items.estimate.cancel')}
        </button>
      </p>
    </form>
  );
}

/** What a number already given looks like in a box, and an absence looks like nothing. */
function typed(hours: number | undefined): string {
  return hours === undefined ? '' : String(hours);
}
