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
 * One arrow in a plan: the predecessor has to finish before the successor begins.
 *
 * Finish-to-start with a lag is the only kind there is, so nothing here says which kind it
 * is — a field with one possible value is a field somebody has to read to learn nothing.
 *
 * Names no project. An edge is only ever listed by plan and only ever addressed by its own
 * identifier, and its two ends already say which plan it is in.
 */
export type Dependency = {
  id: string;
  predecessorItemId: string;
  successorItemId: string;
  /** How long after the predecessor finishes the successor may begin. Usually none. */
  lagHours: number;
  createdAt: string;
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

/**
 * What a forecast did not do, and every one of them carries at least two.
 *
 * **These are not a footnote.** M3a samples every item independently and forecasts only
 * the work somebody wrote down, and both of those make the band narrower than the truth —
 * so a number shown without them is the thing this product exists to replace. They are
 * printed beside the answer rather than behind a link, and M3b removes the first two by
 * building what they name.
 */
export type ForecastLimitation =
  | 'no_team_factor'
  | 'no_scope_uncertainty'
  | 'unestimated_items'
  | 'inconsistent_estimates'
  | 'dependencies_on_archived_work';

/** The shape of one forecast, coarse enough to draw and small enough to keep. */
export type Histogram = {
  fromHours: number;
  toHours: number;
  counts: number[];
};

/**
 * One answer the engine gave, on one day, from one set of assumptions.
 *
 * **Hours of effort, never dates.** Turning one into the other needs an assumption about
 * what a working day is worth, and M4 is where that gets made somewhere a person can see
 * it. Anything here that reads like a date is the moment somebody pressed the button.
 *
 * Nothing changes a run and nothing deletes one, so this list only ever grows: the
 * question worth asking of a forecast is usually how it compares with the last one.
 */
export type Forecast = {
  id: string;
  projectId: string;
  createdAt: string;
  requestedById: string;
  requestedByName: string;
  /** How many items the engine was told could be under way at once. */
  capacity: number;
  sampleCount: number;
  /**
   * A string rather than a number, and deliberately: it is sixty-four bits, and a JSON
   * number is a double here, so as a number it would arrive rounded to something that
   * reproduces nothing.
   */
  seed: string;
  engineVersion: number;
  priorityRule: string;
  /** Coverage as it was when the run happened, which need not be as it is now. */
  itemCount: number;
  estimatedItemCount: number;
  meanHours: number;
  p10Hours: number;
  p50Hours: number;
  p80Hours: number;
  p90Hours: number;
  p95Hours: number;
  limitations: ForecastLimitation[];
  histogram: Histogram;
};
