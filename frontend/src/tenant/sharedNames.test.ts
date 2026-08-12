import { describe, expect, it } from 'vitest';
import { sharedNames } from './sharedNames';
import type { Membership, Organisation } from '../auth/types';

function membership(name: string, slug: string): Membership {
  return {
    id: `membership-${slug}`,
    role: 'MEMBER',
    organisation: { id: `organisation-${slug}`, name, slug },
    lastAccessedAt: null
  };
}

function organisation(name: string, slug: string): Organisation {
  return membership(name, slug).organisation;
}

describe('sharedNames', () => {
  it('finds the name two organisations both answer to', () => {
    const shared = sharedNames([
      membership('Acme Consulting', 'acme-consulting'),
      membership('Acme Consulting', 'acme-consulting-2'),
      membership('Umbrella', 'umbrella')
    ]);

    expect(shared(organisation('Acme Consulting', 'acme-consulting'))).toBe(
      true
    );
    // Unambiguous, so nothing has to be said about it.
    expect(shared(organisation('Umbrella', 'umbrella'))).toBe(false);
  });

  it('finds nothing in a list where every name is its own', () => {
    const shared = sharedNames([
      membership('Acme Consulting', 'acme-consulting'),
      membership('Umbrella', 'umbrella')
    ]);

    expect(shared(organisation('Acme Consulting', 'acme-consulting'))).toBe(
      false
    );
  });

  /** Case alone is not something anybody reliably notices in a list. */
  it('counts names that differ only in case as the same name', () => {
    const shared = sharedNames([
      membership('Acme', 'acme'),
      membership('ACME', 'acme-2')
    ]);

    expect(shared(organisation('Acme', 'acme'))).toBe(true);
  });

  it('finds nothing at all in an empty list', () => {
    expect(sharedNames([])(organisation('Acme', 'acme'))).toBe(false);
  });
});
