/**
 * The parts of a plan, and the one place that says what they are.
 *
 * A plan holds four different kinds of thing — the work, the forecast made of it, the
 * questions asked *of* that forecast, and what the plan is called — and they were one
 * scrolling page until there was too much of it to read. Stated here rather than in the
 * component that draws the strip, because the page, the strip and the URL all have to agree
 * about the same five names, and three copies of a list is two chances to drift.
 */
export const PLAN_TABS = [
  'work',
  'forecast',
  'explore',
  'history',
  'settings'
] as const;

export type PlanTab = (typeof PLAN_TABS)[number];

/**
 * What the strip points at: one panel whose contents change, rather than five panels four
 * of which are empty. Named here beside the tabs themselves, because the page renders the
 * panel and the strip labels it, and an identifier those two disagreed about would be a
 * strip pointing at nothing with nothing on screen to show it.
 */
export const PLAN_PANEL_ID = 'plan-panel';

export const tabId = (tab: PlanTab) => `plan-tab-${tab}`;

/**
 * Which part is being read, in the URL rather than in the page's own state.
 *
 * A tab somebody can link to, reload on and come back to is worth the query parameter; a
 * tab that is only in memory sends everybody who follows a link back to the work list. It
 * is a *parameter* rather than a path segment because a plan is `/app/projects/{id}` and
 * nothing else addresses part of one — see the routing note in `CLAUDE.md`.
 */
export const TAB_PARAM = 'tab';

/**
 * The work, because that is what the page is for and what everything else here reads.
 *
 * It is also the tab with no parameter: the plain plan URL is the one people already hold,
 * and it must keep opening on the same thing it always did.
 */
export const FIRST_TAB: PlanTab = 'work';

/**
 * The three parts that are all about one forecast, which is why they share a component.
 *
 * They are one read and one confidence between them: the panel loads the history once and
 * stays mounted while any of the three is on screen, so moving between them costs no
 * request and does not quietly reset which confidence somebody is reading at.
 */
export const FORECAST_TABS = ['forecast', 'explore', 'history'] as const;

export type ForecastSection = (typeof FORECAST_TABS)[number];

export function isForecastTab(tab: PlanTab): tab is ForecastSection {
  return (FORECAST_TABS as readonly string[]).includes(tab);
}

/**
 * The tab a URL is asking for, and the work tab for anything else.
 *
 * A hand-edited or stale parameter names a tab this version does not have, and the answer
 * to that is the page somebody asked for showing its work rather than an empty frame
 * complaining about a query string.
 */
export function tabFrom(value: string | null): PlanTab {
  return (PLAN_TABS as readonly string[]).includes(value ?? '')
    ? (value as PlanTab)
    : FIRST_TAB;
}
