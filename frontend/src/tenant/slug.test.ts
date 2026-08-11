import { describe, expect, it } from 'vitest';
import { MAXIMUM_SLUG_LENGTH, proposeSlug } from './slug';

/**
 * Ported from the server, where deriving a handle from a name used to be the rule. It is
 * a proposal now, and these are the cases that made it worth having at all.
 */
describe('proposeSlug', () => {
  it('lowercases and joins words with hyphens', () => {
    expect(proposeSlug('Acme Planning Co')).toBe('acme-planning-co');
  });

  it('folds accents rather than dropping the letters under them', () => {
    expect(proposeSlug('Sonetas Europé')).toBe('sonetas-europe');
  });

  it('collapses runs of punctuation into one hyphen', () => {
    expect(proposeSlug('Acme  --  Ltd.')).toBe('acme-ltd');
  });

  it('leaves no hyphen hanging off either end', () => {
    expect(proposeSlug('  ...Acme...  ')).toBe('acme');
  });

  /**
   * The person types their own, which is the honest outcome: an empty required field
   * asks a question, where a plausible default invites somebody to accept it unread.
   */
  it('proposes nothing for a name with nothing usable in it', () => {
    expect(proposeSlug('!!! ???')).toBe('');
  });

  it('fits the column, and not by leaving a hyphen at the end', () => {
    const proposed = proposeSlug(`${'a'.repeat(MAXIMUM_SLUG_LENGTH)} overflow`);

    expect(proposed).toHaveLength(MAXIMUM_SLUG_LENGTH);
    expect(proposed).not.toMatch(/-$/);
  });
});
