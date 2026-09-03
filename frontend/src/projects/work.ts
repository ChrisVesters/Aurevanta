import type { TFunction } from 'i18next';
import { formatDay } from './dates';
import type { Dependency, WorkItem } from './types';

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

/**
 * How far along one piece of work is, said in the same breath as when.
 *
 * **Stated once because two screens say it and they must not drift.** The plan's list says
 * it per row and a task's own page says it above the form that changes it, and a status
 * with no date beside it reads as a claim nobody has to stand behind — which is the
 * opposite of what either is for. The locale is passed in rather than read here, because
 * `formatDay` builds a date from its parts and this file is a set of functions rather than
 * a component.
 */
export function describeProgress(
  t: TFunction,
  locale: string,
  item: WorkItem
): string {
  if (item.status === 'DONE' && item.completedOn) {
    const completed = formatDay(item.completedOn, locale);
    return item.actualEffortHours === null
      ? t('projects.items.progress.summary.done', { completed })
      : t('projects.items.progress.summary.doneWithEffort', {
          completed,
          hours: item.actualEffortHours
        });
  }
  if (item.status === 'IN_PROGRESS' && item.startedOn) {
    return t('projects.items.progress.summary.inProgress', {
      started: formatDay(item.startedOn, locale)
    });
  }
  return t('projects.items.progress.summary.notStarted');
}

/**
 * The plan's arrows grouped by one of their ends.
 *
 * Both directions are the same grouping with the ends swapped, so there is one of these
 * rather than two — and it is here rather than beside either screen because the list and a
 * task's own page both read the plan's edges from both ends.
 */
export function byEnd(
  dependencies: Dependency[],
  end: 'predecessorItemId' | 'successorItemId'
): Map<string, Dependency[]> {
  const grouped = new Map<string, Dependency[]>();
  for (const dependency of dependencies) {
    grouped.set(dependency[end], [
      ...(grouped.get(dependency[end]) ?? []),
      dependency
    ]);
  }
  return grouped;
}

/**
 * Where one piece of work sits in its plan, read from both ends.
 *
 * **Both directions, and neither is redundant**: an item that must finish before three
 * others is what a delay to it would hold up, and an item waiting on two is why it has not
 * started. Saying only one of them answers half the question somebody opened the plan
 * with, and the other half would then only be visible from another task.
 *
 * An arrow whose far end has been put away since is named as such rather than shown as a
 * blank, which is {@link describeWork}'s middle case reaching the plan screens: the
 * archived listing is a different screen, so its titles are not in the map.
 */
export function describeShape(
  t: TFunction,
  itemId: string,
  outgoing: Map<string, Dependency[]>,
  incoming: Map<string, Dependency[]>,
  titles: Map<string, string>
): string[] {
  const lines: string[] = [];
  const before = outgoing.get(itemId) ?? [];
  const after = incoming.get(itemId) ?? [];
  if (before.length > 0) {
    lines.push(
      t('projects.items.blocks.summary.before', {
        titles: named(t, before, 'successorItemId', titles)
      })
    );
  }
  if (after.length > 0) {
    lines.push(
      t('projects.items.blocks.summary.after', {
        titles: named(t, after, 'predecessorItemId', titles)
      })
    );
  }
  return lines;
}

/** The far ends of some arrows, named — a plan is read by its titles, not its keys. */
function named(
  t: TFunction,
  edges: Dependency[],
  end: 'predecessorItemId' | 'successorItemId',
  titles: Map<string, string>
): string {
  return edges
    .map((edge) => titles.get(edge[end]) ?? t('projects.items.blocks.putAway'))
    .join(', ');
}
