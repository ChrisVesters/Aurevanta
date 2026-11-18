import { describe, expect, it } from 'vitest';
import i18n from '../i18n/config';
import { describeWait, withWait } from './work';

const t = i18n.t.bind(i18n);

describe('describeWait', () => {
  it.each([
    [1, 'a 1-hour wait'],
    [16, 'a 16-hour wait'],
    [108, 'a 108-hour wait'],
    [0.5, 'a 0.5-hour wait'],
    [8, 'an 8-hour wait'],
    [8.5, 'an 8.5-hour wait'],
    [11, 'an 11-hour wait'],
    [18, 'an 18-hour wait'],
    [80, 'an 80-hour wait'],
    [88, 'an 88-hour wait'],
    [800, 'an 800-hour wait'],
    [8000, 'an 8000-hour wait']
  ])('says %s as "with %s"', (hours, phrase) => {
    expect(describeWait(t, hours)).toBe(`with ${phrase}`);
  });

  it('leaves a title alone when there is no wait', () => {
    expect(withWait(t, 'Build', 0)).toBe('Build');
    expect(withWait(t, 'Build', 8)).toBe('Build with an 8-hour wait');
  });
});
