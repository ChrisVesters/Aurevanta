import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { Help } from '../ui/Help';
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
 * told by the component that knows, rather than by the caller that merely posts. Three boxes
 * on one screen is `three_point`, which is also what the form before August 2026 was; the
 * track record's split by method is what can say whether either costs accuracy against the
 * one-question-at-a-time form that sat between them.
 */
const METHOD = 'three_point';

/**
 * The three boxes, top to bottom, and the field each fills.
 *
 * **The bad case first, because it is the only one of the three that is unbounded.** The
 * good case has a floor — the work obviously takes something — so an anchor above it
 * compresses it far less than an anchor below the bad case compresses that. With all three
 * on one screen nothing *forces* the order any more, which is the price of the screen; the
 * order is what is left of the defence, so the box somebody reaches first is the one with
 * nothing above it, and the middle — which the fit does not use — comes last.
 *
 * Reordering this array is not a cosmetic change: 10 / 50 / 90 top to bottom invites the
 * middle first, which is what 3/5/8 is.
 */
const FIELDS = [
  { field: 'p90Hours', wording: 'bad' },
  { field: 'p10Hours', wording: 'good' },
  { field: 'p50Hours', wording: 'typical' }
] as const;

type Field = (typeof FIELDS)[number]['field'];

/**
 * How long the answers sit still before the server is asked what is worth questioning about
 * them, so typing "12" is one question rather than two.
 */
const SETTLE_MS = 300;

type EstimateFormProps = {
  id: string;
  /** The caller's current estimate, so revising starts from what they last said. */
  estimate?: Estimate;
  busy: boolean;
  banner: string | null;
  fieldErrors: Record<string, string>;
  onSubmit: (values: EstimateValues) => void;
  onCancel: () => void;
};

/**
 * Three boxes on one screen — the bad case, the good case and what somebody expects — each
 * with a "?" saying what goes in it.
 *
 * **This replaced one question at a time, by the product owner's decision, because four
 * screens per estimate was too many presses.** What it gave up is recorded in
 * `docs/design/elicitation.md`: three boxes side by side invite the middle first, and the
 * result — a Fibonacci-shaped triple — is coherent enough that neither warning catches it.
 * What it keeps:
 *
 * - **The order**, bad case first. See {@link FIELDS}.
 * - **The plain words.** Each label names the case before its percentage, and the "?" holds
 *   the question the one-at-a-time form used to ask, so the surprise framing is one press away
 *   rather than gone.
 * - **The bet**, which arrives as soon as there is a bad case to bet about, and gates nothing.
 * - **The warnings**, asked of the server as the answers settle and rendered from what it
 *   says, so a threshold moves there and nowhere else.
 * - **A colleague's numbers are never on screen while somebody answers** — that rule lives on
 *   the task's page, which renders this form in place of the ranges.
 */
export function EstimateForm({
  id,
  estimate,
  busy,
  banner,
  fieldErrors,
  onSubmit,
  onCancel
}: EstimateFormProps) {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [quality, setQuality] = useState<EstimateQuality | null>(null);
  const [answers, setAnswers] = useState<Record<Field, string>>(() => ({
    p10Hours: typed(estimate?.p10Hours),
    p50Hours: typed(estimate?.p50Hours),
    p90Hours: typed(estimate?.p90Hours)
  }));

  const complete = FIELDS.every(({ field }) => answers[field] !== '');

  // Asked of the server rather than worked out here, once all three are given and have sat
  // still for a moment, and asked afresh whenever they change — so widening a band shows
  // what that band now says rather than what the old one did. A refusal (a range the wrong
  // way round) or a server that cannot be reached leaves no warning on screen and the save
  // to say what is wrong, which is better than a browser inventing a second opinion.
  useEffect(() => {
    setQuality(null);
    if (!complete) {
      return undefined;
    }
    let cancelled = false;
    const asking = setTimeout(() => {
      request<EstimateQuality>('/estimates/quality', {
        method: 'POST',
        body: values()
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
    }, SETTLE_MS);
    return () => {
      cancelled = true;
      clearTimeout(asking);
    };
    // `values` reads `answers`, which is listed; naming the function as well would re-ask
    // on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [answers, complete, request]);

  function values() {
    return {
      p10Hours: numberFrom(answers.p10Hours),
      p50Hours: numberFrom(answers.p50Hours),
      p90Hours: numberFrom(answers.p90Hours)
    };
  }

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    onSubmit({ ...values(), method: METHOD });
  }

  return (
    <form className="estimate-form" onSubmit={handle} noValidate>
      <p className="hint">{t('projects.items.estimate.hint')}</p>

      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}

      <span className="points">
        {FIELDS.map(({ field, wording }) => (
          <span className="field" key={field}>
            <span className="label">
              <label htmlFor={`${id}-${field}`}>
                {t(`projects.items.estimate.fields.${wording}.label`)}
              </label>{' '}
              <Help label={t(`projects.items.estimate.fields.${wording}.help`)}>
                {t(`projects.items.estimate.fields.${wording}.explain`)}
              </Help>
            </span>
            <input
              id={`${id}-${field}`}
              name={field}
              type="number"
              inputMode="decimal"
              min="0"
              step="0.25"
              value={answers[field]}
              onChange={(event) =>
                setAnswers({ ...answers, [field]: event.target.value })
              }
              aria-invalid={fieldErrors[field] ? true : undefined}
            />
            {fieldErrors[field] && (
              <span className="field-error">{fieldErrors[field]}</span>
            )}
          </span>
        ))}
      </span>

      {/*
        The betting frame, which makes a number typed cheaply feel expensive. It gates
        nothing: saying yes is pressing save, and saying no is changing the box above.
      */}
      {answers.p90Hours !== '' && (
        <p className="bet">
          {t('projects.items.estimate.bet', { hours: answers.p90Hours })}
        </p>
      )}

      {/*
        Advice, never a refusal. A tight band is sometimes exactly right, and a rule that
        blocked one would become a specification people learn to type. Rendered from what
        the server said, so moving a threshold there needs no change here.
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

      <p className="actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy
            ? t('projects.items.estimate.submitting')
            : t('projects.items.estimate.submit')}
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
