import { useRef } from 'react';
import type { KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { PLAN_PANEL_ID, PLAN_TABS, tabId, type PlanTab } from './tabs';

/**
 * Which part of a plan is being read.
 *
 * **Buttons and not links, and the panel is one element rather than five.** A tab is a view
 * of one plan; it is not somewhere else to go, and the thing it shows is loaded by the page
 * already holding the plan.
 *
 * **Focus moves with the arrow keys and the tab is taken by pressing it** — the manual half
 * of the ARIA pattern, deliberately. Selection that follows focus is right where a panel is
 * already in the browser, and moving to the forecast reloads it: arrowing past a tab must
 * not fire the request that tab would make. Roving `tabindex` is the other half, so the
 * strip is one stop on the way through the page rather than five.
 */
export function PlanTabs({
  tab,
  onChange
}: {
  tab: PlanTab;
  onChange: (tab: PlanTab) => void;
}) {
  const { t } = useTranslation();
  const buttons = useRef(new Map<PlanTab, HTMLButtonElement | null>());

  const focus = (index: number) => {
    const wrapped = (index + PLAN_TABS.length) % PLAN_TABS.length;
    buttons.current.get(PLAN_TABS[wrapped])?.focus();
  };

  const move = (event: KeyboardEvent<HTMLButtonElement>, from: number) => {
    const to: Record<string, number | undefined> = {
      ArrowRight: from + 1,
      ArrowLeft: from - 1,
      Home: 0,
      End: PLAN_TABS.length - 1
    };
    const next = to[event.key];
    if (next !== undefined) {
      // Or the arrow keys scroll the page underneath the strip as well as moving through it.
      event.preventDefault();
      focus(next);
    }
  };

  return (
    <div
      className="plan-tabs"
      role="tablist"
      aria-label={t('projects.tabs.label')}
    >
      {PLAN_TABS.map((name, index) => (
        <button
          key={name}
          type="button"
          role="tab"
          id={tabId(name)}
          aria-controls={PLAN_PANEL_ID}
          aria-selected={name === tab}
          tabIndex={name === tab ? 0 : -1}
          ref={(element) => {
            buttons.current.set(name, element);
          }}
          onKeyDown={(event) => move(event, index)}
          onClick={() => onChange(name)}
        >
          {t(`projects.tabs.${name}`)}
        </button>
      ))}
    </div>
  );
}
