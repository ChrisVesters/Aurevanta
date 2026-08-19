import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useAuth } from '../auth/AuthContext';
import { describeFailure } from '../i18n/problems';

/**
 * One GET, held in state, dropped on the floor if the screen has gone.
 *
 * **Twenty copies of this effect were written by hand before it existed**, and the copies
 * were not the problem — the rule inside them was. Every one of them ends
 * `return () => { cancelled = true; }`, because a `setState` arriving after the component
 * has unmounted is a React warning at best and a write into a screen nobody is looking at
 * at worst. That is the sort of rule that holds in twenty places until it is nineteen, and
 * the nineteenth is found by a user rather than by a test.
 *
 * **A null path means "not yet", not "nothing".** Half the callers guard on something they
 * do not have yet — an organisation that is still being restored, a run nobody has asked
 * about — and returning early from an effect is exactly where a stale cleanup gets lost.
 * Passing null keeps the guard in one place and the hook order stable.
 *
 * **The failure is returned rather than shown.** Some screens must say why a read failed
 * and some must survive one silently — the pools behind a forecast, the colleagues behind
 * a resource — and that is a judgement for the screen. What is *not* a judgement is whether
 * the answer may still touch the page, and that is what this owns.
 *
 * **Read-only, and that is the boundary.** A screen that writes the loaded value back —
 * renaming a plan and holding the row the server answers with — keeps its own state, because
 * what it has then is no longer a read. Widening this to hand out a setter would make it the
 * state container for pages whose state is not a read at all, and the guarantee above is
 * about reads.
 *
 * @param path the API path to read, or null to load nothing at all
 * @param deps what a fresh read is keyed on. The organisation belongs in here on any screen
 * that shows its rows: switching to another one must ask again rather than leave the
 * previous organisation's data on screen.
 */
export function useLoaded<T>(
  path: string | null,
  deps: unknown[]
): { data: T | null; failure: string | null } {
  const { t } = useTranslation();
  const { request } = useAuth();
  const [data, setData] = useState<T | null>(null);
  const [failure, setFailure] = useState<string | null>(null);

  useEffect(() => {
    if (path === null) {
      return undefined;
    }
    let cancelled = false;
    // Both cleared as the read starts rather than when it lands, so switching between two
    // listings leaves neither the previous one's rows nor the previous one's complaint on
    // screen while this one is in flight.
    setData(null);
    setFailure(null);
    request<T>(path)
      .then((loaded) => {
        if (!cancelled) {
          setData(loaded);
          setFailure(null);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setFailure(describeFailure(t, error));
        }
      });
    return () => {
      cancelled = true;
    };
    // `path` is deliberately not a dependency: it is built from the deps the caller names,
    // and listing it as well would re-read on every render that rebuilt the same string.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [request, t, ...deps]);

  return { data, failure };
}
