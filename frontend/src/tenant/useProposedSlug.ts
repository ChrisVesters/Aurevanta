import { useCallback, useState } from 'react';
import { proposeSlug } from './slug';

/**
 * A handle field that follows the name until somebody takes it over.
 *
 * Shared by the two forms that create an organisation, because the rule is easy to get
 * subtly wrong and invisible when you do: a field that kept following would silently undo
 * a handle its owner had just chosen, one keystroke into the name above it.
 *
 * Taking it over is one-way. Somebody who edits the handle and then goes back to fix a
 * typo in the name has still chosen a handle, and moving it under them at that point
 * would be the same bug arriving later.
 */
export function useProposedSlug() {
  const [slug, setSlug] = useState('');
  const [chosen, setChosen] = useState(false);

  return {
    slug,
    /** Called as the name is typed; does nothing once the handle has an owner. */
    followName: useCallback(
      (name: string) => {
        if (!chosen) {
          setSlug(proposeSlug(name));
        }
      },
      [chosen]
    ),
    /** Called when the handle itself is edited, or replaced by a refused one's remedy. */
    choose: useCallback((value: string) => {
      setChosen(true);
      setSlug(value);
    }, [])
  };
}
