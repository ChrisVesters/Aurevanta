import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { numberField } from './fields';
import type { Estimate } from './types';

export type EstimateValues = {
  p10Hours: number | null;
  p50Hours: number | null;
  p90Hours: number | null;
};

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
 * Three boxes labelled P10, P50 and P90.
 *
 * **This form is deliberately not good, and that is worth saying where somebody will read
 * it before improving it.** `product-concept.md` is blunt that three boxes with those
 * labels produce 3/5/8 without anybody thinking, which is worse than no tool at all
 * because the garbage now carries a probability. Making it good is M5 — surprise framing,
 * betting framing, comparison against work the team has already done — and that is a
 * milestone rather than a coat of paint. Styling this one would only make the numbers it
 * collects look more considered than they are.
 *
 * What it does owe the visitor now is honesty about the unit: hours of effort, which is
 * not a date and not a duration.
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

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    onSubmit({
      p10Hours: numberField(values, 'p10Hours'),
      p50Hours: numberField(values, 'p50Hours'),
      p90Hours: numberField(values, 'p90Hours')
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

      <span className="points">
        {(['p10Hours', 'p50Hours', 'p90Hours'] as const).map((point) => (
          <span className="field" key={point}>
            <label htmlFor={`${id}-${point}`}>
              {t(`projects.items.estimate.fields.${point}`)}
            </label>
            <input
              id={`${id}-${point}`}
              name={point}
              type="number"
              inputMode="decimal"
              min="0"
              step="0.25"
              defaultValue={estimate?.[point] ?? ''}
              aria-invalid={fieldErrors[point] ? true : undefined}
            />
            {fieldErrors[point] && (
              <span className="field-error">{fieldErrors[point]}</span>
            )}
          </span>
        ))}
      </span>

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
