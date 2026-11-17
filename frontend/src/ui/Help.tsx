import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState
} from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/** The space kept between the panel and the edge of the window, matching the page gutter. */
const GUTTER = 16;

/** The space between the "?" and the panel it opens. */
const GAP = 6;

type Place = { top: number; left: number };

/**
 * An explanation kept behind a "?" beside the thing it explains, shown on a press as a small
 * panel floating over the page.
 *
 * **Floating rather than inline**, so opening it moves nothing: an explanation that pushed
 * the boxes beneath it down the page would move the thing somebody was about to type into.
 *
 * **Placed against the window, never against whatever it sits in.** It is `position: fixed`,
 * at coordinates measured from the "?": under it where there is room and above it where there
 * is not, and kept inside the window's gutters. An absolutely placed panel was the first
 * version, and inside a dialog — which scrolls — any part of it past the dialog's edge widened
 * what the dialog had to scroll, and a horizontal scrollbar appeared under a form. A fixed
 * element is not part of any ancestor's scrollable area, so that cannot happen wherever this
 * is put. The price is following the "?" when anything scrolls or the window resizes, which
 * is what the listeners below do.
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
  const [place, setPlace] = useState<Place | null>(null);
  const text = useId();
  const root = useRef<HTMLSpanElement>(null);
  const toggle = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLSpanElement>(null);

  /**
   * Where the panel goes, from where the "?" is now and how big the panel is: under the "?"
   * unless that runs past the bottom of the window, then above it; and as far left as the
   * "?" unless that runs past the right edge, then pulled back inside it. Never past the top
   * or the left gutter either way.
   */
  const position = useCallback(() => {
    const anchor = (
      toggle.current as HTMLButtonElement
    ).getBoundingClientRect();
    const box = (panel.current as HTMLSpanElement).getBoundingClientRect();
    const below = anchor.bottom + GAP;
    const top =
      below + box.height > window.innerHeight - GUTTER
        ? Math.max(GUTTER, anchor.top - GAP - box.height)
        : below;
    const left = Math.max(
      GUTTER,
      Math.min(anchor.left, window.innerWidth - GUTTER - box.width)
    );
    setPlace({ top, left });
  }, []);

  // Measured once it is showing, before the browser paints it, so it never flashes in the
  // wrong place.
  useLayoutEffect(() => {
    if (open) {
      position();
    } else {
      setPlace(null);
    }
  }, [open, position]);

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
    // Captured, so a scroll inside a dialog or any other container is heard as well as the
    // page's own.
    document.addEventListener('scroll', position, true);
    window.addEventListener('resize', position);
    return () => {
      document.removeEventListener('pointerdown', away);
      document.removeEventListener('keydown', escape);
      document.removeEventListener('scroll', position, true);
      window.removeEventListener('resize', position);
    };
  }, [open, position]);

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
        style={place ?? undefined}
      >
        {children}
      </span>
    </span>
  );
}
