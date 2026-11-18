/**
 * The parts of a plan, and the one place that says what they are.
 *
 * Four: the work, the order it must be done in, the forecasts made of it, and what the plan
 * is called. The order is the work read as a whole — what waits on what, with no time in it. Each forecast has a
 * page of its own (`/app/forecasts/{id}`), which is where it is read and where the questions
 * asked *of* it — what widens it, what would hit a date, what one more person buys, why the
 * date moved — are asked. Until November 2026 the forecast was three tabs here (forecast,
 * explore, history) about the newest run only; `tabFrom` still reads those names, so a link
 * saved then opens on the forecasts rather than on the work.
 *
 * The page, the strip and the URL all have to agree on these names, so they are stated once.
 */
export const PLAN_TABS = ['work', 'order', 'forecasts', 'settings'] as const;

export type PlanTab = (typeof PLAN_TABS)[number];

/** The panel the strip controls, named once so `aria-controls` and the panel agree. */
export const PLAN_PANEL_ID = 'plan-panel';

export const tabId = (tab: PlanTab) => `plan-tab-${tab}`;

/** The query parameter a plan's part is kept in. */
export const TAB_PARAM = 'tab';

/**
 * The task the order view is focused on, kept beside `?tab=order` so a link reopens the same
 * chain. It means nothing on any other part, which is why changing part drops it.
 */
export const FOCUS_PARAM = 'focus';

/** What a plan opens on, and what the plain plan URL means. */
export const FIRST_TAB: PlanTab = 'work';

/** The three names the forecast's parts had before they became one list of forecasts. */
const RETIRED_FORECAST_TABS = ['forecast', 'explore', 'history'];

/** The part a `?tab=` names — or the first, for no value or one this page does not know. */
export function tabFrom(value: string | null): PlanTab {
  if (RETIRED_FORECAST_TABS.includes(value ?? '')) {
    return 'forecasts';
  }
  return (PLAN_TABS as readonly string[]).includes(value ?? '')
    ? (value as PlanTab)
    : FIRST_TAB;
}
