import type { TFunction } from 'i18next';

/**
 * What one piece of work a run was about is called, on a screen reading that run.
 *
 * **Stated once because it is one fact answered three ways**, and two of the three are easy
 * to get subtly wrong. A run's snapshot never held a title — the reporting layer diffs those snapshots and a
 * rename is not a thing that moved — so every screen showing work a forecast was about
 * resolves the name from the plan as it stands now, and has to say what it found:
 *
 * - work still in the plan is simply named;
 * - work put away since is named **and marked**, because a top contributor or a proposed cut
 *   that had quietly been archived is exactly what a reader would go looking for;
 * - work the plan no longer holds at all says so, which is the shape of a bug rather than an
 *   ordinary state, since nothing in this product deletes an item.
 *
 * The backend stated the same three-way rule twice and its coverage gate found the second
 * copy untested; this is that lesson arriving on the other side of the wire, before the
 * second copy exists. A copy that started rendering the third case as a blank row would look
 * like an ordinary empty label rather than like a fault.
 */
export function describeWork(
  t: TFunction,
  title: string | null,
  archived: boolean
): string {
  if (title === null) {
    return t('projects.forecast.work.unknown');
  }
  return archived ? t('projects.forecast.work.archived', { title }) : title;
}
