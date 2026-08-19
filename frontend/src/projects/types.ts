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
 * reads; this exists because M8 refuses to score an estimate written after the work began,
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
   * numbers — and it is what lets M8 eventually ask whether changing the question changed
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

/**
 * One thing that could have moved a plan's finish, and how much it did.
 *
 * **`shareOfSpread` is not a share of anything, and nothing that renders it may add them
 * up.** They sum to 1 only for a chain at capacity one with no common cause — the summing
 * model this product deliberately stopped using. In a real forecast the shared team factor
 * multiplies every item in a run by the same draw, so everything moves with everything and
 * the shares overlap. A pie chart of these would show a plan whose parts account for three
 * hundred percent of its own uncertainty.
 *
 * **Two of the three kinds are not items**, and they are why the ranking is honest: when the
 * shared factor or the unlisted work tops the list, the true answer to "what should I spike"
 * is that no estimate on it is the problem.
 */
export type Contribution = {
  kind: 'item' | 'discovered_work' | 'team_factor';
  /** Null for the two sources that are not pieces of work. */
  itemId: string | null;
  /**
   * What that work is called *now*, from the plan rather than from the run — the snapshot a
   * run stores never held a title, because M10 diffs those and a rename is not a thing that
   * moved. Null for the two sources that are not work.
   */
  title: string | null;
  /** Whether the work has been put away since the run, which is said rather than hidden. */
  archived: boolean;
  correlation: number;
  shareOfSpread: number;
};

/**
 * One piece of work somebody could drop, and what dropping it would be worth.
 *
 * **`buys` is what this buys on its own, and it is never a term in a sum.** Two cuts on one
 * chain overlap, so cutting both is barely better than cutting one; two on separate branches
 * leave the later of them deciding, so one of the two buys nothing at all. A column of these
 * with plus signs in front of them reads as arithmetic waiting to happen, and it is not —
 * which is why "what do I cut" is answered by {@link CutPlan}, a list that was measured,
 * rather than by adding the top few of these together.
 */
export type Cut = {
  itemId: string;
  /** What the work is called now. Null for work the plan no longer holds at all. */
  title: string | null;
  archived: boolean;
  /** Where the plan would stand with only this cut, as a percentage. */
  confidence: number;
  /** The difference that makes, in percentage points. Zero for work off the path. */
  buys: number;
  /** Whether cutting this one thing clears the bar on its own. */
  meets: boolean;
};

/** One thing dropped, and where the plan stands once it and everything above it is gone. */
export type CutStep = {
  itemId: string;
  title: string | null;
  archived: boolean;
  /**
   * Measured with every earlier step already cut, never accumulated from the singles. That
   * is the whole reason this list exists beside {@link Cut} rather than being derived from
   * it.
   */
  confidence: number;
};

/**
 * Why the search for a set of cuts stopped, which is as much of the answer as the list is.
 *
 * A list that reaches the bar and the best anybody could find are different answers, and a
 * search that ran out of the simulations it was allowed is a third — one where the honest
 * reading is "this is as far as it looked", not "this is as far as it goes".
 */
export type CutSearchEnding = 'met' | 'nothing_left' | 'budget_spent';

/** A list of things to drop that gets to the date, in the order to drop them. */
export type CutPlan = {
  /** Empty when the plan already clears the bar, which is an answer rather than a gap. */
  steps: CutStep[];
  ending: CutSearchEnding;
};

/**
 * What it would take to hit a date, measured against one stored run.
 *
 * The hours the date came to travel with it, because a target date only means anything
 * under a working day and a calendar — M4's rule about a stated assumption arriving beside
 * the number it produced, in the one place where the number is a recommendation.
 */
export type CutOptions = {
  targetHours: number;
  /** The share of the run that already beat the date, as a percentage. */
  baselineConfidence: number;
  /** Whether the plan already clears the bar, in which case there is nothing to propose. */
  meets: boolean;
  /** How many times the plan was run to answer this, which is what the search bounds. */
  simulations: number;
  /** Each candidate and what it is worth **on its own**, largest first. */
  cuts: Cut[];
  together: CutPlan;
};

/**
 * What a forecast did not do.
 *
 * **These are not a footnote.** A number shown without them is the thing this product
 * exists to replace, so they are printed beside the answer rather than behind a link.
 *
 * **The first two are retired and still listed here.** M3b models a shared team factor and
 * scope growth, so nothing writes them any more — but every forecast made before it still
 * carries them, and this screen shows the runs a plan has accumulated rather than only its
 * newest. Dropping them from this union would describe a run made last month as saying
 * something this version cannot understand.
 */
export type ForecastLimitation =
  | 'no_team_factor'
  | 'no_scope_uncertainty'
  | 'unestimated_items'
  | 'inconsistent_estimates'
  | 'dependencies_on_archived_work'
  | 'unassigned_work'
  | 'requirements_on_archived_resources';

/** The shape of one forecast, coarse enough to draw and small enough to keep. */
export type Histogram = {
  fromHours: number;
  toHours: number;
  counts: number[];
};

/**
 * One answer the engine gave, on one day, from one set of assumptions.
 *
 * **Hours of effort, and dates derived from them.** The band is what the engine produced;
 * the five dates are five percentiles of it with a working day laid on top, and the three
 * calendar fields are that assumption travelling with them. Every one of the eight is null
 * together for a run made before there was a calendar to state, which is a true record
 * rather than a gap: that run assumed no working day, so it produced no date.
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
   * How much longer everything was assumed to take in a bad stretch, as a percentage.
   *
   * Zero is a claim rather than a blank: that nothing in this team's world has a common
   * cause. It travels with the answer because two runs of one plan made under different
   * assumptions are not a plan that moved.
   */
  teamFactorWorseByPercent: number;
  /** The two ends of how much more work the plan was assumed to turn out to hold. */
  scopeGrowthP10Percent: number;
  scopeGrowthP90Percent: number;
  /**
   * The day work was said to begin, as a day and not a moment. Null for a run made before
   * a calendar was something anybody stated.
   */
  startsOn: string | null;
  /**
   * What **one** person's working day was said to hold — never the team's total. How many
   * people there are is already inside the hours, so dividing by a team's daily total
   * counts capacity twice and produces a date wrong by exactly that factor.
   */
  workingHoursPerDay: number | null;
  /**
   * Which calendar the two above were read through. A name rather than a flag, because two
   * defensible calendars give two different dates from identical data — so a rule changing
   * must never be readable as a plan that moved.
   */
  calendarRule: string | null;
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
  /**
   * The same five percentiles as days, derived by the server so that no date this product
   * publishes is separable from the calendar that produced it. Null whenever the calendar
   * above is, and null as well for a rule the server could not resolve.
   */
  p10Date: string | null;
  p50Date: string | null;
  p80Date: string | null;
  p90Date: string | null;
  p95Date: string | null;
  /**
   * The team this run was scheduled against, in the order it was declared — empty for a run
   * made by an organisation that had described none, which is what `capacity` answers for.
   *
   * **The run's own team and not today's.** Hiring somebody must not rewrite what last
   * month's forecast assumed, which is the same reason the calendar is the run's own.
   */
  resources: ForecastResource[];
  limitations: ForecastLimitation[];
  histogram: Histogram;
};

/**
 * One pool a forecast was scheduled against.
 *
 * The units are the run's and the name is today's, which is the split a contribution
 * ranking already makes for the work it names: what a pool was called is not a thing that
 * moved, and what it held then is.
 */
export type ForecastResource = {
  resourceId: string;
  /** Null when this organisation no longer holds that pool at all. */
  name: string | null;
  /** Whether it has been put away since, which is said rather than hidden. */
  archived: boolean;
  units: number;
};

/**
 * How far one confidence's date has drifted, and whether that is worth saying.
 *
 * **The server sends the flag and keeps the number behind it**, the way it does for an
 * estimate worth questioning. A plan cannot be sliding on one screen and fine on another,
 * and a threshold repeated here is the second copy that eventually disagrees.
 */
export type DriftAt =
  | {
      confidence: number;
      /** Where the oldest run in the window put this percentile, and the newest. */
      fromDate: string | null;
      toDate: string | null;
      /** Positive when the plan has moved out. Null when either run has no calendar. */
      days: number | null;
      /**
       * What the forecast itself says the distance between the good and the bad case is —
       * the yardstick rather than a reading. Three days is nothing against a three-week
       * band and the whole of a plan against a two-day one.
       */
      bandDays: number | null;
      movingOut: false;
    }
  | {
      /**
       * **Two shapes, told apart by the flag**, because a plan cannot be drifting without
       * having somewhere to have drifted from: the server never says so without the days
       * and the band that decided it. Written as a union rather than as four checks a
       * reader would have to repeat, and rather than four nullable fields nobody could
       * render without them.
       */
      confidence: number;
      fromDate: string;
      toDate: string;
      days: number;
      bandDays: number;
      movingOut: true;
    };

/**
 * Whether this plan's date keeps moving out, read off its own history.
 *
 * **Never the direction of the last few runs.** A plan that is not slipping still moves out
 * one week and in the next, so a rule reading three increases in a row fires on 86% of plans
 * re-forecast weekly for six months with no slide in them at all. What is measured instead is
 * how far the date has moved since the oldest run that answered the *same question* — a
 * changed capacity or working day starts a new window rather than being compared across.
 */
export type Drift = {
  /** The oldest run this is measured from, which is in the same payload to be checked. */
  sinceRunId: string;
  /** How many forecasts the window holds. One means there is nothing behind it. */
  runs: number;
  at: DriftAt[];
};

/**
 * Every forecast of one plan, and what the sequence of them says.
 *
 * The verdict belongs to the history rather than to any run in it, so it arrives with the
 * list rather than from a second request — a screen drawing the history would otherwise have
 * to ask again for the one line about it worth reading out loud.
 */
export type ForecastHistory = {
  /** Newest first. */
  runs: Forecast[];
  /** Null only for a plan nobody has forecast yet. */
  drift: Drift | null;
};

/**
 * The weeks a throughput forecast was drawn from.
 *
 * **`worst` is the most useful number here.** A bootstrap cannot draw a week worse than the
 * worst one in its window, so a reader who knows their team stops for a week each quarter can
 * tell at a glance whether the window contains such a week — and if it does not, the forecast
 * beside it is early and confident for a reason no arithmetic reports.
 */
export type ThroughputWindow = {
  /** Empty weeks included: a week nobody finished anything in is a week the team had. */
  weeks: number;
  from: string;
  to: string;
  completed: number;
  /** The average, which is not what the projection uses — a mean cannot see a bad week. */
  perWeek: number;
  best: number;
  worst: number;
};

/** When the backlog runs out, in weeks and in the days those weeks land on. */
export type ThroughputProjection = {
  meanWeeks: number;
  p10Weeks: number;
  p50Weeks: number;
  p80Weeks: number;
  p90Weeks: number;
  p95Weeks: number;
  p10Date: string;
  p50Date: string;
  p80Date: string;
  p90Date: string;
  p95Date: string;
  /** A string, not a number: sixty-four bits do not survive a JSON number in a browser. */
  seed: string;
  sampleCount: number;
};

/**
 * One thing that moved a plan's date between two forecasts of it.
 *
 * The order is the server's and is a *rule* with a name, because two defensible orders split
 * the same eight days differently — so nothing here re-orders them, and they are rendered in
 * the order they arrive.
 */
export type MovementTerm = {
  step:
    | 'SAMPLING'
    | 'PROGRESS'
    | 'ESTIMATES'
    | 'SCOPE'
    | 'ASSUMPTIONS'
    | 'CALENDAR'
    | 'STARTS_ON';
  movedHours: number;
  /** Null when either run has no calendar to read a date through. */
  movedDays: number | null;
};

/** The whole account of a movement at one confidence, which the control chooses between. */
export type MovementAt = {
  confidence: number;
  fromDate: string | null;
  toDate: string | null;
  fromHours: number;
  toHours: number;
  /** They sum to the distance between the two dates exactly, which is the whole claim. */
  terms: MovementTerm[];
};

/**
 * Why the date moved, between two forecasts of one plan.
 *
 * **The terms account for the whole movement rather than most of it**, because each is
 * measured with every earlier one already applied and the last state *is* the newer run. That
 * is what makes it worth publishing a sentence like "out eight days: five new scope, four
 * re-estimates, one of progress" at all.
 */
export type Movement = {
  fromRunId: string;
  toRunId: string;
  /** Which order the terms were attributed in — a name, because two orders give two splits. */
  rule: string;
  /** What it cost, said rather than hidden: this is six whole simulations. */
  simulations: number;
  at: MovementAt[];
};

/** One week of what a plan has actually delivered, as a running total. */
export type BurnUpWeek = {
  /** The Monday that week began on, which is the bucket the history is cut into. */
  week: string;
  /** By the end of it, cumulatively — a flat stretch is a week nobody delivered in. */
  delivered: number;
};

/**
 * How much will have been delivered by one future week.
 *
 * **These percentiles read the other way round from the dates beside them.** Each is the
 * percentile of the thing it names, so `p90Date` is the *late* end of a finish while `p90`
 * here is the *good* end of a delivery count. The edge to plan against is `p10`.
 */
export type BurnUpConeWeek = {
  /** This many weeks after the day asked about, counted from then rather than from a Monday. */
  week: string;
  p10: number;
  p50: number;
  p90: number;
};

/**
 * What has been delivered and what is left, week by week.
 *
 * **One series in two halves, both already on the same scale.** The cone carries the past
 * total inside it rather than starting again from zero, so nothing on this side adds the two
 * together — which is the arithmetic that gets done wrong.
 *
 * **The cone is M9's bootstrap and never the engine's band.** A burn-up's future is how many
 * *items* are done by each week, and the engine has no notion of that: it forecasts effort.
 * Same history, same sampler and same seed as the date beside it, so the picture and the
 * number cannot disagree.
 */
export type BurnUp = {
  delivered: number;
  total: number;
  past: BurnUpWeek[];
  /** Null exactly when the projection is, and for the same three reasons. */
  cone: BurnUpConeWeek[] | null;
};

/**
 * What a plan's own history says about when it will be finished.
 *
 * **A second opinion and never a tiebreaker.** Nothing averages this with the engine's band or
 * resolves a disagreement between them: two forecasts that disagree are the output, because
 * "the team says six weeks and their own history says eleven" starts a conversation and one
 * number in the middle ends it.
 *
 * **The window and the projection are separately absent.** A plan nobody has finished anything
 * in has neither; a plan with a month of history has a window and no projection worth
 * publishing. There is no shape here that holds half of either.
 */
export type Throughput = {
  projectId: string;
  asOf: string;
  /** Which week the history was cut into — two definitions give two different histories. */
  rule: string;
  /** Work left, unestimated items included: what is counted is work rather than effort. */
  remaining: number;
  window: ThroughputWindow | null;
  projection: ThroughputProjection | null;
  /** The same numbers drawn rather than summarised. Null when nothing has been delivered. */
  burnUp: BurnUp | null;
  limitations: string[];
};
