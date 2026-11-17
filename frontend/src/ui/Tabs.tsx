import { useRef } from 'react';
import type { KeyboardEvent } from 'react';

/**
 * A strip of tabs over one panel: a plan's parts, and the parts of one forecast.
 *
 * **Buttons and not links, and the panel is one element rather than one per tab.** A tab is
 * a view of the page somebody is on, not somewhere else to go.
 *
 * **Focus moves with the arrow keys and the tab is taken by pressing it** — the manual half
 * of the ARIA pattern, deliberately. Selection that follows focus is right where a panel is
 * already in the browser, and several of these panels load something: arrowing past a tab
 * must not fire the request that tab would make. Roving `tabindex` is the other half, so the
 * strip is one stop on the way through the page rather than one per tab.
 *
 * @param idOf the id each tab's button carries, which its panel is labelled by
 * @param panelId the one panel every tab controls
 */
export function Tabs<T extends string>({
  tabs,
  tab,
  label,
  nameOf,
  idOf,
  panelId,
  onChange
}: {
  tabs: readonly T[];
  tab: T;
  label: string;
  nameOf: (tab: T) => string;
  idOf: (tab: T) => string;
  panelId: string;
  onChange: (tab: T) => void;
}) {
  const buttons = useRef(new Map<T, HTMLButtonElement | null>());

  const focus = (index: number) => {
    const wrapped = (index + tabs.length) % tabs.length;
    buttons.current.get(tabs[wrapped])?.focus();
  };

  const move = (event: KeyboardEvent<HTMLButtonElement>, from: number) => {
    const to: Record<string, number | undefined> = {
      ArrowRight: from + 1,
      ArrowLeft: from - 1,
      Home: 0,
      End: tabs.length - 1
    };
    const next = to[event.key];
    if (next !== undefined) {
      // Or the arrow keys scroll the page underneath the strip as well as moving through it.
      event.preventDefault();
      focus(next);
    }
  };

  return (
    <div className="plan-tabs" role="tablist" aria-label={label}>
      {tabs.map((name, index) => (
        <button
          key={name}
          type="button"
          role="tab"
          id={idOf(name)}
          aria-controls={panelId}
          aria-selected={name === tab}
          tabIndex={name === tab ? 0 : -1}
          ref={(element) => {
            buttons.current.set(name, element);
          }}
          onKeyDown={(event) => move(event, index)}
          onClick={() => onChange(name)}
        >
          {nameOf(name)}
        </button>
      ))}
    </div>
  );
}
