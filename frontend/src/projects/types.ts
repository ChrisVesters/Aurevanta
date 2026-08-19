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
 * One claim somebody made about how far along a piece of work is.
 *
 * The four fields a `WorkItem` carries, plus who said them and when the server heard it —
 * and unlike those four, nothing ever writes over one. A second report is a second row.
 *
 * The item keeps its latest state because that is what a screen draws and what a forecast
 * reads; this exists because calibration refuses to score an estimate written after the work began,
 * and the day work began has to be a thing somebody said rather than a thing anybody can
 * quietly move afterwards.
 */
export type ProgressReport = {
  id: string;
  itemId: string;
  reportedById: string;
  reportedByName: string;
  /** A moment, unlike the two days below — the only field here the server observed. */
  reportedAt: string;
  status: WorkItemStatus;
  startedOn: string | null;
  completedOn: string | null;
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
  /**
   * How far the stated middle sits from the one the two ends imply, as a ratio. A
   * diagnostic rather than something to print — it is what makes {@link inconsistent}
   * explicable — and derived by the server so that the browser cannot come to a different
   * conclusion about the same estimate.
   */
  consistency: number;
  /** Whether that ratio is far enough from 1 to be worth mentioning. */
  inconsistent: boolean;
  /**
   * Whether the band is too tight to have been thought about. Both of these are advice and
   * never a refusal: a tight range is sometimes exactly right, and a rule that blocked one
   * would become a specification people learn to type.
   */
  overconfident: boolean;
  /**
   * How the three numbers were asked for. The only thing about an estimate the server
   * stores rather than derives, because it is the only one that leaves no trace in the
   * numbers — and it is what lets calibration eventually ask whether changing the question changed
   * how often a band contained the truth.
   */
  method: string;
  createdAt: string;
};

/**
 * What is worth questioning about a range, as the server sees it.
 *
 * The same three fields an `Estimate` carries, answered about numbers nobody has committed
 * to yet — because the warning has to arrive while somebody is still answering, and an
 * estimate is written once and never rewritten, so a form that saved first would make "that
 * is not what I meant" cost a second row. Asked for rather than worked out here: two rules
 * about one estimate would eventually disagree, and the one on this side is the one nobody
 * would notice had drifted.
 */
export type EstimateQuality = {
  consistency: number;
  inconsistent: boolean;
  overconfident: boolean;
};
