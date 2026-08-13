import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { textField } from '../auth/formValues';
import { numberField } from './fields';
import type { Dependency, WorkItem } from './types';

export type DependencyValues = {
  successorItemId: string;
  lagHours: number;
};

type DependencyFormProps = {
  id: string;
  /** The item this is opened on, which is always the one that finishes first. */
  item: WorkItem;
  /** What it could be made to come before: everything else still in the plan. */
  candidates: WorkItem[];
  /** What it already comes before, so the same arrow is not offered twice. */
  drawn: Dependency[];
  /** Every item in the plan by identifier, for naming the far end of an arrow. */
  titles: Map<string, string>;
  /**
   * The loop a refusal said this arrow would close, already named — shown beneath the
   * banner, because "that would create a cycle" without saying which one is something
   * somebody then has to go and find across a plan of up to five hundred items.
   */
  cycle: string[] | null;
  busy: boolean;
  banner: string | null;
  fieldErrors: Record<string, string>;
  onSubmit: (values: DependencyValues) => void;
  onRemove: (dependency: Dependency) => void;
  onCancel: () => void;
};

/**
 * "Must finish before…", picking another task in the same plan.
 *
 * Asked from one end rather than both: the item the form was opened on always finishes
 * first, so there is no box for choosing which way round the arrow points and no way to
 * draw one backwards by misreading a label. The other direction is reached by opening the
 * form on the other task, which is the same arrow read the other way.
 *
 * **The list offers only what could be picked.** The task itself is left out, as is
 * anything it already comes before — both are refusals the server would give, and a form
 * that offers a choice it knows will be refused is a form that wastes somebody's time to
 * tell them something it already knew. What it cannot rule out is a cycle: that is a
 * property of the whole plan, decided under a lock, and guessing at it here would mean
 * hiding options that are perfectly legal by the time the request arrives.
 */
export function DependencyForm({
  id,
  item,
  candidates,
  drawn,
  titles,
  cycle,
  busy,
  banner,
  fieldErrors,
  onSubmit,
  onRemove,
  onCancel
}: DependencyFormProps) {
  const { t } = useTranslation();
  const blocked = new Set(drawn.map((edge) => edge.successorItemId));
  const choices = candidates.filter(
    (candidate) => candidate.id !== item.id && !blocked.has(candidate.id)
  );

  /**
   * The far end of an arrow, named. An arrow may point at work that has since been put
   * away, and the archived listing is a different screen — so its title is not here to
   * look up, and saying so beats showing a row with a blank in it.
   */
  function name(edge: Dependency) {
    return (
      titles.get(edge.successorItemId) ?? t('projects.items.blocks.putAway')
    );
  }

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    onSubmit({
      successorItemId: textField(values, 'successorItemId'),
      // The hint says an empty box means no wait, so the form answers that question
      // rather than passing an unanswered one to a server that will not guess at it.
      lagHours: numberField(values, 'lagHours') ?? 0
    });
  }

  return (
    <form className="dependency-form" onSubmit={handle} noValidate>
      <p className="hint">{t('projects.items.blocks.hint')}</p>

      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}

      {cycle && (
        <p className="cycle">
          {t('projects.items.blocks.cycle', {
            // The loop closes on the item it starts with, which the server does not
            // repeat and a reader needs to see — otherwise the last arrow is the one
            // step of the route nobody is shown.
            path: [...cycle, cycle[0]].join(' → ')
          })}
        </p>
      )}

      {drawn.length > 0 && (
        <ul className="dependency-list">
          {drawn.map((edge) => (
            <li key={edge.id}>
              <span className="who">
                {edge.lagHours > 0
                  ? t('projects.items.blocks.drawnWithLag', {
                      title: name(edge),
                      hours: edge.lagHours
                    })
                  : t('projects.items.blocks.drawn', { title: name(edge) })}
              </span>
              <button
                type="button"
                className="secondary"
                disabled={busy}
                // Named for anybody reading through a screen reader, where a column of
                // identical buttons says nothing about which arrow each would rub out.
                aria-label={t('projects.items.blocks.removeNamed', {
                  title: name(edge)
                })}
                onClick={() => onRemove(edge)}
              >
                {t('projects.items.blocks.remove')}
              </button>
            </li>
          ))}
        </ul>
      )}

      {choices.length === 0 ? (
        <p className="empty">{t('projects.items.blocks.nothingLeft')}</p>
      ) : (
        <>
          <span className="field">
            <label htmlFor={`${id}-successorItemId`}>
              {t('projects.items.blocks.fields.successorItemId')}
            </label>
            <select
              id={`${id}-successorItemId`}
              name="successorItemId"
              defaultValue=""
              aria-invalid={fieldErrors.successorItemId ? true : undefined}
            >
              {/* Nothing chosen to begin with, so the first task in the plan is not
                  quietly the answer for anybody who opens this and presses save. */}
              <option value="">
                {t('projects.items.blocks.fields.choose')}
              </option>
              {choices.map((choice) => (
                <option key={choice.id} value={choice.id}>
                  {choice.title}
                </option>
              ))}
            </select>
            {fieldErrors.successorItemId && (
              <span className="field-error">{fieldErrors.successorItemId}</span>
            )}
          </span>

          <span className="field">
            <label htmlFor={`${id}-lagHours`}>
              {t('projects.items.blocks.fields.lagHours')}
            </label>
            <input
              id={`${id}-lagHours`}
              name="lagHours"
              type="number"
              inputMode="decimal"
              min="0"
              step="0.25"
              defaultValue=""
              aria-invalid={fieldErrors.lagHours ? true : undefined}
            />
            {fieldErrors.lagHours && (
              <span className="field-error">{fieldErrors.lagHours}</span>
            )}
          </span>
        </>
      )}

      {/*
        One set of actions rather than a pair kept in step, and the way out is always
        offered: a plan with nothing left to pick still has arrows to rub out above, and a
        form with no way to close it would strand somebody who opened it to look.
      */}
      <p className="actions">
        {choices.length > 0 && (
          <button type="submit" className="primary" disabled={busy}>
            {busy
              ? t('projects.items.blocks.submitting')
              : t('projects.items.blocks.submit')}
          </button>
        )}
        <button type="button" className="link" onClick={onCancel}>
          {t('projects.items.blocks.cancel')}
        </button>
      </p>
    </form>
  );
}
