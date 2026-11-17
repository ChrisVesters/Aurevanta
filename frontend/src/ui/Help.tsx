import { useId, useState } from 'react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

/**
 * An explanation kept behind a "?" beside the thing it explains, shown on a press.
 *
 * **A press and not a hover**, because a hover has no equivalent on a touch screen and is
 * easy to get wrong from a keyboard: this is the disclosure pattern — a real button whose
 * `aria-expanded` says whether the text is showing, pointing at it with `aria-controls`.
 * The text is always in the document and `hidden` while closed, so the button never points
 * at something that is not there.
 *
 * It renders as inline content, so it can sit inside the sentence it explains; the text
 * itself is laid out as a block underneath.
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
  const text = useId();

  return (
    <span className="help">
      <button
        type="button"
        className="help-toggle"
        aria-label={label}
        aria-expanded={open}
        aria-controls={text}
        onClick={() => setOpen((showing) => !showing)}
      >
        <span aria-hidden="true">{t('ui.help.icon')}</span>
      </button>
      <span id={text} className="help-text" hidden={!open}>
        {children}
      </span>
    </span>
  );
}
