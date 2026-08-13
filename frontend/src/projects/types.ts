/**
 * One plan the organisation holds.
 *
 * Names no organisation, because there is only one it could name: the session is scoped to
 * it, and the server took it from the token rather than from anything on screen.
 */
export type Project = {
  id: string;
  name: string;
  /** Null when nothing has been said about it, which is every project on its first day. */
  description: string | null;
  createdAt: string;
  /** Null while it is in use. Nothing is ever deleted, so this is how one leaves a list. */
  archivedAt: string | null;
  /** Work in the plan, archived items excluded. */
  itemCount: number;
  /**
   * How much of that carries an estimate from anybody. Reported rather than enforced: a
   * plan that is only half estimated is every real plan on its first day, and what matters
   * is that nobody is left to guess how much of it a forecast covered.
   */
  estimatedItemCount: number;
};

/**
 * How far along one piece of work is. Three states and no more: this is not a workflow —
 * day-to-day movement belongs in whatever tracker the team already uses — and what a
 * forecast needs to know is only whether an item is still ahead of it.
 */
export type WorkItemStatus = 'NOT_STARTED' | 'IN_PROGRESS' | 'DONE';

/**
 * One piece of work inside a plan, and the thing that will carry an estimate.
 *
 * Names its project, unlike a project naming its organisation: `PATCH /api/items/{id}`
 * puts no project in the path, so this is what says which plan was just changed.
 */
export type WorkItem = {
  id: string;
  projectId: string;
  title: string;
  description: string | null;
  createdAt: string;
  archivedAt: string | null;
  status: WorkItemStatus;
  /**
   * Days rather than moments — `2026-08-14`, not an instant. What somebody reports is the
   * day it happened, and storing a time of day would invent the one part of the claim they
   * never made.
   */
  startedOn: string | null;
  completedOn: string | null;
  /** What it actually took, in the same hours an estimate is given in. Rarely filled in. */
  actualEffortHours: number | null;
};

/**
 * One person's current three-point range for one item, in hours of effort.
 *
 * Several may be current on one item at once — one per estimator — and that is the point
 * rather than a state to resolve on screen: two people who disagree about a task are
 * saying something, and it is the engine that decides what.
 *
 * There is no way to change one. A revision is a new estimate, and the old one stays
 * exactly as it was said, because that is the only form of it a calibration report can ask
 * about later.
 */
export type Estimate = {
  id: string;
  itemId: string;
  estimatorId: string;
  estimatorName: string;
  p10Hours: number;
  p50Hours: number;
  p90Hours: number;
  createdAt: string;
};
