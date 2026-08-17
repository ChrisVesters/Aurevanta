/**
 * A `yyyy-mm-dd` day from the API, shown in the reader's own locale.
 *
 * Built from its parts rather than handed to `new Date(iso)`, which reads a bare date as
 * **UTC midnight** — and so displays as the day before for every reader west of the
 * meridian. The server stores these as days precisely so that nobody has to reason about a
 * time of day that was never claimed; parsing one back into an instant here would throw
 * that away in the last step before it reaches a person.
 */
export function formatDay(iso: string, locale: string): string {
  const [year, month, day] = iso.split('-').map(Number);
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium' }).format(
    new Date(year, month - 1, day)
  );
}

/**
 * What day it is *here*, as the `yyyy-mm-dd` the server takes.
 *
 * The same bug as {@link formatDay} written the other way round: `toISOString()` is UTC, so
 * at ten in the evening in New York it reports tomorrow. This is why a start date is the
 * browser's to offer and the server's to be told — what day it is where somebody is sitting
 * is a fact only this end holds, and the server's own clock would answer a different
 * question in its own timezone.
 */
export function todayHere(): string {
  const now = new Date();
  return [
    String(now.getFullYear()).padStart(4, '0'),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0')
  ].join('-');
}
