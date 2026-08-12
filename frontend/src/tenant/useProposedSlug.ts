import { useCallback, useState } from 'react';
import { ApiError } from '../api/client';
import { proposeSlug } from './slug';

/**
 * A handle field that follows the name until somebody takes it over.
 *
 * Shared by every form with a handle in it, because the rules are easy to get subtly wrong
 * and invisible when you do: a field that kept following would silently undo a handle its
 * owner had just chosen, one keystroke into the name above it.
 *
 * Taking it over is one-way. Somebody who edits the handle and then goes back to fix a
 * typo in the name has still chosen a handle, and moving it under them at that point
 * would be the same bug arriving later.
 *
 * @param held the handle an organisation already answers to, for the form that changes one
 * rather than creating it. Passing it starts the field owned, so nothing proposes over the
 * top of a handle somebody is already using.
 */
export function useProposedSlug(held?: string) {
  const [slug, setSlug] = useState(held ?? '');
  const [chosen, setChosen] = useState(held !== undefined);
  const [suggested, setSuggested] = useState(false);

  const choose = useCallback((value: string) => {
    setChosen(true);
    setSlug(value);
  }, []);

  return {
    slug,
    /**
     * Whether the last refusal arrived holding a free alternative, which decides what the
     * field says. The one that does not is the race — two people submitting the same
     * handle in the same instant — where the losing write meets the unique index with the
     * transaction already lost and nothing left to ask the database for.
     */
    suggested,
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
    choose,
    /**
     * Takes up the alternative a refusal carried, if it carried one.
     *
     * Doing it here rather than in each form is what keeps the field's message honest:
     * whether a suggestion arrived and whether the field was replaced with it are the same
     * fact, and two places to record it would be one place to forget. Taking it up also
     * marks the field as its owner's, so a later edit to the name above cannot quietly
     * undo it.
     */
    takeSuggestion: useCallback(
      (error: unknown) => {
        const alternative =
          error instanceof ApiError ? error.suggested : undefined;
        if (alternative) {
          choose(alternative);
        }
        setSuggested(Boolean(alternative));
      },
      [choose]
    )
  };
}
