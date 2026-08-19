/**
 * A pool of a thing a plan needs: a name, how many of it there are, and optionally who.
 *
 * **One concept where the roadmap describes two.** *Backend engineers × 3* is a type-level
 * requirement said directly, *Staging environment × 1* is the equipment case said
 * identically, and *Ada × 1* is a person — a pool of one, needing no second concept.
 *
 * **The person is a convenience and never a permission.** Nothing in this product reports
 * on how busy anybody is; a pool says a team has somebody in it and stops there.
 */
export type Resource = {
  id: string;
  name: string;
  /** Whole, because units are whole things. Half a person is availability, which is later. */
  units: number;
  personId: string | null;
  personName: string | null;
  createdAt: string;
  /** Null while the pool is in use, which is how the two states are told apart. */
  archivedAt: string | null;
};

/**
 * What one piece of work needs before it can be under way.
 *
 * **Units are occupancy and never speed.** Two units means the work ties up two, not that it
 * goes twice as fast — the estimate already says what the task takes, and dividing it by a
 * headcount would use one number twice.
 */
export type Requirement = {
  workItemId: string;
  resourceId: string;
  /** Today's name for the pool, which is not necessarily what it was called then. */
  resourceName: string;
  /** Whether the team has since put it away, which is said rather than hidden. */
  resourceArchived: boolean;
  units: number;
};
