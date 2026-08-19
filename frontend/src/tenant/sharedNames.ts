import type { Membership, Organisation } from '../auth/types';

/**
 * Which of these organisations share their name with another in the same list.
 *
 * Since chosen handles a name is not unique, so a person who belongs to two organisations called
 * Acme sees the same word twice. It only matters where a *choice* is being made between
 * them — the chooser and the switcher — and only for the ones that actually repeat:
 * putting a handle under every name would be noise for the overwhelming majority who have
 * no collision at all.
 *
 * Compared without regard to case, because case alone is not something anybody reliably
 * notices in a list. "Acme" beside "acme" is two things a person cannot tell apart at a
 * glance, which is the whole test this applies.
 */
export function sharedNames(
  memberships: Membership[]
): (organisation: Organisation) => boolean {
  const seen = new Set<string>();
  const repeated = new Set<string>();
  for (const membership of memberships) {
    const name = key(membership.organisation);
    if (seen.has(name)) {
      repeated.add(name);
    }
    seen.add(name);
  }
  return (organisation) => repeated.has(key(organisation));
}

function key(organisation: Organisation): string {
  return organisation.name.toLowerCase();
}
