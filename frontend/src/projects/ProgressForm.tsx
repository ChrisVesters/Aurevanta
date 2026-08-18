import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { SubmitEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { formatMoment } from './dates';
import { numberField, optionalField } from './fields';
import type { ProgressReport, WorkItem, WorkItemStatus } from './types';

export type ProgressValues = {
  status: WorkItemStatus;
  startedOn: string | null;
  completedOn: string | null;
  actualEffortHours: number | null;
};

const STATUSES: readonly WorkItemStatus[] = [
  'NOT_STARTED',
  'IN_PROGRESS',
  'DONE'
];

/** What each status has room for. Work that has not started has room for nothing. */
const RECORDS = {
  NOT_STARTED: { startedOn: false, completedOn: false, effort: false },
  IN_PROGRESS: { startedOn: true, completedOn: false, effort: true },
  DONE: { startedOn: true, completedOn: true, effort: true }
} as const;

/**
 * What has already happened to a piece of work: how far along it is, when that happened,
 * and — if anybody measured it — how long it took.
 *
 * The dates are asked for rather than taken from the clock, because work is marked finished
 * on the Monday after it finished at least as often as on the day. They are `<input
 * type="date">`, which hands back exactly the `yyyy-mm-dd` the server stores, so no
 * timezone is involved anywhere between the two.
 *
 * **A box only appears where the chosen status has room for it**, which is why the status
 * is the one controlled field here. This form first showed all four at once and let the
 * server keep whichever fitted — so somebody could type four hours against work marked not
 * started, watch the form close, and find the number gone with nothing said. The server
 * refuses that now rather than dropping it; this makes sure nobody is invited to write it
 * in the first place, because an interface that accepts something and then discards it
 * teaches people to distrust everything else it accepted.
 *
 * **It also says who last reported anything**, from a log the server keeps of every claim
 * ever made about this task. Two reasons, and the smaller one is that somebody about to
 * overwrite four fields can see whether the state in front of them is their own. The larger
 * one is that a record nothing ever reads is a record that quietly stops being written
 * correctly, and this one is what stops a calibration figure being improved after the fact
 * by editing the day work started.
 */
export function ProgressForm({
  id,
  item,
  busy,
  banner,
  fieldErrors,
  onSubmit,
  onCancel
}: {
  id: string;
  item: WorkItem;
  busy: boolean;
  banner: string | null;
  fieldErrors: Record<string, string>;
  onSubmit: (values: ProgressValues) => void;
  onCancel: () => void;
}) {
  const { t, i18n } = useTranslation();
  const { request } = useAuth();
  const [status, setStatus] = useState<WorkItemStatus>(item.status);
  const [last, setLast] = useState<ProgressReport | null>(null);
  const records = RECORDS[status];

  // Asked for on its own rather than carried on the item, because the plan listing draws
  // five hundred rows and this is wanted for the one that is open. A failure here leaves
  // the line off and the form working: the history is context, and nothing about recording
  // progress depends on being able to read it.
  useEffect(() => {
    let cancelled = false;
    request<ProgressReport[]>(`/items/${item.id}/progress`)
      .then((history) => {
        if (!cancelled) {
          setLast(history[0] ?? null);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setLast(null);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [request, item.id]);

  /**
   * Whether choosing this status throws away something the item already records. Said
   * before saving rather than discovered afterwards: the fields holding it have just
   * disappeared, and somebody who does not know why would reasonably think the values are
   * still there.
   *
   * Written as one question asked of each of the three rather than three questions joined
   * by `or`, which is not only shorter: an item can only carry an effort if it also
   * carries a date, so the effort clause of that version could never be the one that
   * decided the answer — a branch nothing could ever reach, quietly.
   */
  const discards = (
    [
      [records.startedOn, item.startedOn],
      [records.completedOn, item.completedOn],
      [records.effort, item.actualEffortHours]
    ] as const
  ).some(([keeps, recorded]) => !keeps && recorded !== null);

  function handle(event: SubmitEvent<HTMLFormElement>) {
    event.preventDefault();
    const values = new FormData(event.currentTarget);
    // A field the status has no room for is not on screen, so `FormData` has nothing for
    // it and the request carries a null — which is exactly the claim being made.
    onSubmit({
      status,
      startedOn: optionalField(values, 'startedOn'),
      completedOn: optionalField(values, 'completedOn'),
      actualEffortHours: numberField(values, 'actualEffortHours')
    });
  }

  return (
    <form className="progress-form" onSubmit={handle} noValidate>
      {banner && (
        <p className="form-error" role="alert">
          {banner}
        </p>
      )}

      <p className="field">
        <label htmlFor={`${id}-status`}>
          {t('projects.items.progress.statusLabel')}
        </label>
        <select
          id={`${id}-status`}
          name="status"
          value={status}
          onChange={(event) => setStatus(event.target.value as WorkItemStatus)}
        >
          {STATUSES.map((option) => (
            <option key={option} value={option}>
              {t(`projects.items.progress.status.${option}`)}
            </option>
          ))}
        </select>
        {fieldErrors.status && (
          <span className="field-error">{fieldErrors.status}</span>
        )}
      </p>

      {last && (
        <p className="hint">
          {t('projects.items.progress.lastReported', {
            name: last.reportedByName,
            // `formatMoment` and not `formatDay`, which is the one thing on this screen it
            // would be easy to get backwards: the two dates above are days somebody
            // reported and this is a moment the server observed, so this one converts to
            // where the reader is and those must not.
            day: formatMoment(last.reportedAt, i18n.language)
          })}
        </p>
      )}

      {discards && (
        <p className="hint">{t('projects.items.progress.clears')}</p>
      )}

      <span className="points">
        {records.startedOn && (
          <span className="field">
            <label htmlFor={`${id}-startedOn`}>
              {t('projects.items.progress.startedOn')}
            </label>
            <input
              id={`${id}-startedOn`}
              name="startedOn"
              type="date"
              defaultValue={item.startedOn ?? ''}
            />
          </span>
        )}
        {records.completedOn && (
          <span className="field">
            <label htmlFor={`${id}-completedOn`}>
              {t('projects.items.progress.completedOn')}
            </label>
            <input
              id={`${id}-completedOn`}
              name="completedOn"
              type="date"
              defaultValue={item.completedOn ?? ''}
            />
          </span>
        )}
        {records.effort && (
          <span className="field">
            <label htmlFor={`${id}-actualEffortHours`}>
              {t('projects.items.progress.actualEffortHours')}
            </label>
            <input
              id={`${id}-actualEffortHours`}
              name="actualEffortHours"
              type="number"
              inputMode="decimal"
              min="0"
              step="0.25"
              defaultValue={item.actualEffortHours ?? ''}
              aria-invalid={fieldErrors.actualEffortHours ? true : undefined}
            />
            {fieldErrors.actualEffortHours && (
              <span className="field-error">
                {fieldErrors.actualEffortHours}
              </span>
            )}
          </span>
        )}
      </span>

      <p className="actions">
        <button type="submit" className="primary" disabled={busy}>
          {busy
            ? t('projects.items.progress.submitting')
            : t('projects.items.progress.submit')}
        </button>
        <button type="button" className="link" onClick={onCancel}>
          {t('projects.items.progress.cancel')}
        </button>
      </p>
    </form>
  );
}
