import { describe, expect, it } from 'vitest';
import { formatDay, formatMoment, todayHere } from './dates';

/**
 * **These three exist because of one off-by-one, and this suite runs in `America/New_York`
 * so that it fails rather than being invisible.** Every date this product shows is either a
 * *day* somebody reported or a *moment* the server observed, the two are converted in
 * opposite directions, and getting either backwards shows the wrong day to half the planet
 * while looking entirely reasonable in UTC.
 */
describe('dates', () => {
  /**
   * A bare `yyyy-mm-dd` has no time of day in it, so there is nothing to convert. Handing
   * one to `new Date(iso)` reads it as UTC midnight, which is the previous evening here.
   */
  it('shows a reported day as the day it says, west of the meridian', () => {
    expect(formatDay('2026-08-14', 'en')).toBe('Aug 14, 2026');
  });

  it('does not shift the first of a month back into the one before', () => {
    expect(formatDay('2026-01-01', 'en')).toBe('Jan 1, 2026');
  });

  /**
   * An instant is the other case and takes the other rule: it happened at one moment, and
   * which day that was depends on where the reader is sitting. Two in the morning UTC is
   * the previous evening in New York, and saying so is correct rather than an error.
   */
  it('shows a moment as the day it happened where the reader is', () => {
    expect(formatMoment('2026-08-15T02:00:00Z', 'en')).toBe('Aug 14, 2026');
  });

  it('leaves a moment already inside the reader’s day alone', () => {
    expect(formatMoment('2026-08-14T18:00:00Z', 'en')).toBe('Aug 14, 2026');
  });

  /**
   * <strong>The two are not interchangeable, and this is what says so.</strong> Slicing the
   * date part off an instant — the obvious way to reuse {@link formatDay} for one — reads
   * out its UTC day and reports tomorrow for a report filed this evening.
   */
  it('disagrees with taking the date part off an instant, and is right to', () => {
    const instant = '2026-08-15T02:00:00Z';
    expect(formatMoment(instant, 'en')).not.toBe(
      formatDay(instant.slice(0, 10), 'en')
    );
  });

  /** The same argument written the other way: `toISOString()` here reports tomorrow. */
  it('offers today as the day it is here rather than in UTC', () => {
    const now = new Date();
    expect(todayHere()).toBe(
      [
        String(now.getFullYear()).padStart(4, '0'),
        String(now.getMonth() + 1).padStart(2, '0'),
        String(now.getDate()).padStart(2, '0')
      ].join('-')
    );
  });
});
