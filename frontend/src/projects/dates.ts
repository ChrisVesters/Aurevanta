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
