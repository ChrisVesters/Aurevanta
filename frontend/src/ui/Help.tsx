import { useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/** The space kept between the panel and the edge of the window, matching the page gutter. */
const GUTTER = 16;

/**
 * An explanation kept behind a "?" beside the thing it explains, shown on a press as a small
 * panel floating over the page.
 *
 * **Floating rather than inline**, so opening it moves nothing: an explanation that pushed
 * the boxes beneath it down the page would move the thing somebody was about to type into.
 * It is positioned under the "?" and nudged left where it would otherwise run off the edge
 * of a narrow window.
 *
 * **A press and not a hover**, because a hover has no equivalent on a touch screen and is
 * easy to get wrong from a keyboard: this is the disclosure pattern — a real button whose
 * `aria-expanded` says whether the text is showing, pointing at it with `aria-controls`.
 * The text is always in the document and `hidden` while closed, so the button never points
 * at something that is not there. Escape closes it and hands focus back to the "?", and so
 * does a press anywhere else — which is what a floating panel is expected to do, and what
 * stops one being left open over the form.
 *
 * **Only for explanation somebody can do without.** A caveat that changes how a number
 * should be read — a forecast's limitations and assumptions — stays on screen, which is a
 * rule the forecast panel argues for at length; this is for the sentence that says what a
 * term means.
 *
 * @param label what the button is called to a screen reader, which hears no "?" — so it
 * names what it explains rather than saying "help"
 */
export function Help({
  label,
  children
}: {
  label: string;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const [shift, setShift] = useState(0);
  const text = useId();
  const root = useRef<HTMLSpanElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLSpanElement>(null);

  // Measured once it is showing, before the browser paints it: the panel opens leftwards
  // only by as much as it would otherwise run past the window's right edge, and never so
  // far that it runs past the left one instead.
  useLayoutEffect(() => {
    if (!open) {
      setShift(0);
      return;
    }
    const box = (panel.current as HTMLSpanElement).getBoundingClientRect();
    const over = box.right - (window.innerWidth - GUTTER);
    setShift(over > 0 ? Math.min(over, box.left - GUTTER) : 0);
  }, [open]);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    function away(event: PointerEvent) {
      if (!(root.current as HTMLSpanElement).contains(event.target as Node)) {
        setOpen(false);
      }
    }
    function escape(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setOpen(false);
        (toggle.current as HTMLButtonElement).focus();
      }
    }
    document.addEventListener('pointerdown', away);
    document.addEventListener('keydown', escape);
    return () => {
      document.removeEventListener('pointerdown', away);
      document.removeEventListener('keydown', escape);
    };
  }, [open]);

  return (
    <span className="help" ref={root}>
      <button
        ref={toggle}
        type="button"
        className="help-toggle"
        aria-label={label}
        aria-expanded={open}
        aria-controls={text}
        onClick={() => setOpen((showing) => !showing)}
      >
        <span aria-hidden="true">{t('ui.help.icon')}</span>
      </button>
      <span
        ref={panel}
        id={text}
        className="help-text"
        hidden={!open}
        style={shift > 0 ? { left: -shift } : undefined}
      >
        {children}
      </span>
    </span>
  );
}
