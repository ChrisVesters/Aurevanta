/** Length and shape the server enforces; mirrored so the browser stops what it would refuse. */
export const MINIMUM_SLUG_LENGTH = 2;
export const MAXIMUM_SLUG_LENGTH = 80;

/**
 * Proposes a handle for an organisation from the name somebody is typing.
 *
 * A *proposal*, which is why it lives here rather than on the server: the handle is a
 * field its owner fills in, and offering a value while they type is what a form does. The
 * server enforces the shape and knows nothing about names — so a proposal it would refuse
 * is a bug in here, not a disagreement about the rule.
 *
 * Folds accents, lowercases, and reduces anything that is not a letter or digit to a
 * single hyphen. A name with nothing usable in it proposes nothing, and the person types
 * their own: an empty required field asks a question, where a plausible default invites
 * somebody to accept it without reading it.
 */
export function proposeSlug(name: string): string {
  const folded = name
    .normalize('NFD')
    .replace(/\p{M}+/gu, '')
    .toLowerCase();
  const hyphenated = folded.replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
  return hyphenated.length <= MAXIMUM_SLUG_LENGTH
    ? hyphenated
    : hyphenated.slice(0, MAXIMUM_SLUG_LENGTH).replace(/-+$/, '');
}
