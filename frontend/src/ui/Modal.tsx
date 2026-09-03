import { useEffect, useId, useRef } from 'react';
import type { ReactNode } from 'react';

/**
 * A form asked for, over the page it was asked from.
 *
 * **In `ui` rather than in the feature that needed it first**, because two of them use it —
 * writing a task down and inviting a colleague — and a shared component living inside one
 * feature is how `members` ends up importing from `projects` for a reason that has nothing
 * to do with either.
 *
 * **A native `<dialog>` and not a `div` wearing `role="dialog"`.** Focus into it, focus
 * trapped inside it, Escape, the page behind it made inert and the backdrop are all the
 * platform's — every one of them is a thing a hand-built modal gets subtly wrong, and being
 * wrong about them is only visible to somebody using a keyboard or a screen reader, which
 * is to say invisible in review.
 *
 * **Its contents exist only while it is open**, which is doing two jobs. It is what empties
 * the boxes for the next one, the way the add form used to be keyed on the reload count. And
 * it keeps a closed dialog out of the accessibility tree by not being there at all rather
 * than by the user-agent stylesheet — which matters because a closed `<dialog>` in jsdom is
 * a visible one, so a test asserting the form is not on screen yet would otherwise pass in a
 * browser and be meaningless here.
 *
 * **A press on the backdrop does not close it**, deliberately, though the platform would let
 * it. What is inside is a form somebody has typed into, and a stray press losing that is a
 * worse failure than the one shutting it costs — Escape and the form's own cancel are two
 * ways out, and both are ways somebody *meant*.
 *
 * **The title is an `h2` wherever this is used.** A dialog is a thing in its own right
 * rather than a subsection of whatever opened it, and the page it sits over has an `h1`; an
 * `h3` picked to look subordinate to the section behind it would skip a level for a reader
 * moving by headings, who cannot see what it is sitting over.
 */
export function Modal({
  title,
  open,
  onClose,
  children
}: {
  title: string;
  open: boolean;
  /**
   * Called for every way out there is, including the ones this component never hears about
   * directly: the native `close` event is what Escape and `close()` both raise, so the
   * caller's state cannot drift from what is on screen.
   */
  onClose: () => void;
  children: ReactNode;
}) {
  const dialog = useRef<HTMLDialogElement>(null);
  const heading = useId();

  useEffect(() => {
    /*
      Asserted rather than guarded, and deliberately: the element is rendered
      unconditionally and a ref is set before any effect reads it, so `if (!element)` would
      be a branch no test could ever reach — a hole in the coverage gate, which is what this
      codebase says about an unreachable `default` on a `switch` over an enum.
    */
    const element = dialog.current as HTMLDialogElement;
    // Asked only where the two disagree, so a re-render with nothing changed cannot reopen
    // a dialog the reader has just closed, and mounting one that is shut does nothing.
    if (open !== element.open) {
      if (open) {
        element.showModal();
      } else {
        element.close();
      }
    }
  }, [open]);

  return (
    <dialog
      className="modal"
      ref={dialog}
      aria-labelledby={heading}
      onClose={onClose}
    >
      {open && (
        <>
          <h2 id={heading}>{title}</h2>
          {children}
        </>
      )}
    </dialog>
  );
}
