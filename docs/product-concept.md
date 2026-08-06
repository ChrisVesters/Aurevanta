# Aurevanta — Concepts and Planned Features

> **Status: design intent, not implemented.** As of 2026-08-05 the repository contains
> only the backend and frontend scaffolds. Nothing described below exists in code yet;
> `V1__baseline.sql` is an empty Flyway baseline and the domain schema has not been
> designed. Treat this document as the product direction, not a description of behaviour.

## Purpose

Aurevanta lets people plan work using **certainty interval estimations** — P10/P50/P90
ranges — instead of single-point estimates, and then aggregates those ranges correctly
into project-level forecasts.

## Core principle: percentiles do not add

This is the reason the product exists. Given ten tasks that each have a P90 of 5 days,
the project P90 is **not** 50 days — that figure assumes everything goes wrong
simultaneously, which is wildly pessimistic. Summing P50s is the opposite error: it
ignores that variance accumulates, and that the optimistic tail is bounded while the
pessimistic tail is not.

The only honest rollup is to fit a distribution per task, sample it many times, and read
the percentiles off the resulting project distribution. Every feature below is built on
that engine. Without it the application is a table of numbers that users will paste into
a spreadsheet and add up incorrectly.

### Distribution fitting

Two candidate approaches:

- **Log-normal** (preferred default) — a two-parameter fit from P10 and P90, treating P50
  as a consistency check. Durations are positive and right-skewed, so this matches
  reality, and it does not pretend a worst case exists.
- **PERT-beta** — honours all three supplied points, but requires a bounded maximum.

Recommendation: log-normal, with the P50 discrepancy surfaced to the user as a signal
that their three points are mutually inconsistent.

## Planned features

### Tier 1 — the minimum that beats a spreadsheet

**Monte Carlo rollup.** Fit → sample → aggregate. Produces a full project distribution
rather than a single number.

**Ship date at a confidence level.** Nobody asks for a distribution; they ask what date
they can commit to. A single confidence control (50% / 80% / 95%) resolving to a calendar
date. This also reframes stakeholder negotiation: "can we go faster" is answered by
"we can commit at lower confidence," which is the honest trade.

### Tier 2 — analysis over the same engine, no new schema

**Variance contribution.** Rank tasks by their contribution to the *spread* of the
project outcome, not by duration. A 20-day task estimated 18–22 is nearly risk-free; a
5-day task estimated 2–30 is what wrecks the forecast. Directly answers "what should I
spike next to tighten the plan," and is the most defensible feature in the product —
point-estimate tools cannot produce it.

**Inverse queries.** Run the question backwards: not "when will this finish" but "what do
I cut to hit 1 November at 85% confidence?", ranking candidate scope removals by the
confidence each one buys. This changes the usage pattern from reporting surface to
something opened during planning.

### Tier 3 — requires accumulated history

**Calibration feedback.** Record actuals, then measure the hit rate: of the tasks
estimated, how many landed inside their P10–P90 band? It should be 80%. In practice most
teams score 30–50%, because supplied ranges are far too narrow. Reporting that number per
estimator, and offering a correction factor, makes the tool improve its users rather than
merely serve them.

*Schema implication:* estimates must be stored immutably with a timestamp and an
estimator, never overwritten in place. This constraint should be honoured in the first
migration even though the feature comes later.

**Throughput cross-check.** A second, independent forecast derived from historical
throughput — items completed per week, sampled from the team's own past — with no
estimation involved. It implicitly includes interruptions, holidays, scope growth, and
the fact that nobody works eight focused hours.

Presented alongside the bottom-up estimate, the *gap* is the valuable artifact: when the
team says six weeks and their own history says eleven, that is much harder to dismiss
than any single forecast, because both numbers came from the team.

### Deferred

**Dependencies and parallelism**, and **capacity modelling** (people, allocation,
holidays, working days — mapping an effort distribution onto calendar dates). Both
multiply schema complexity considerably. A flat list of tasks already demonstrates the
core value.

## Modelling concerns to design around

### The input problem is harder than the maths

The simulation is straightforward to build; eliciting honest ranges from humans is the
actual product. Presented with three boxes labelled P10/P50/P90, people will enter 3/5/8
without thinking — producing a machine that emits confident garbage, which is strictly
worse than no tool because the garbage now carries a probability.

Nobody thinks in percentiles, so the elicitation should be indirect:

- **Surprise framing** — "How long if it goes well?" for the low end; for the high end,
  "What number would make you genuinely surprised to have exceeded it?" People recognise
  surprise far better than they reason about tail probability.
- **Betting framing** — "Would you take 9-to-1 odds that this lands under X?" Makes
  overconfidence feel expensive, which is the point.
- **Comparative framing** — "Is this bigger or smaller than the auth migration in March?"
  Reference-class forecasting beats absolute estimation, and improves as history
  accumulates.

Elicitation deserves a real design as a first-class feature, not a form. It is also where
a P90 only 1.5× the P50 can be flagged — that pattern almost always means the estimator
has not thought about what could go wrong.

### Unknown unknowns dominate the error

Projects rarely overrun because known tasks exceeded their P90. They overrun because of
work nobody listed. Every ticket can be estimated well and the project can still be 100%
late, because the ticket list itself grows.

An honest model therefore has two uncertainty sources: duration uncertainty on known
work, and **scope uncertainty** about how much unknown work appears. The second is
usually the larger, and it is estimable from history — if the last five projects grew
40–90% in ticket count, that is a distribution to sample from and multiply through.
Ignoring it is why other tools produce forecasts that look precise and land wrong.

### Independence is a lie

Naive Monte Carlo samples each task independently, so good and bad luck cancel out and
the project band comes out implausibly tight. Reality has common causes: if the team is
short-staffed or the codebase fights back, everything runs long together.

Sampling a single shared **team factor** per simulation run and applying it across all
tasks captures most of this at very little cost, and widens the band to something
believable.

### Merge bias

Where parallel branches must both complete, expected finish is later than either branch
alone, and this compounds at every join. Simulation captures it for free; spreadsheets
get it wrong universally. Worth surfacing as an explicit insight rather than burying it
inside a number.

## Open questions

- **Unit of estimation** — task, story, or epic? This changes how much scope-growth
  modelling is needed: coarser units absorb some unknown work implicitly.
- **Multi-estimator support** — do several people estimate the same item independently
  and then reconcile (wideband Delphi / planning poker)? If so, disagreement between
  estimators is itself a signal of real uncertainty or of an ambiguous requirement, and
  the app can flag large divergences. This is a schema decision, not a UI one.
- **Communicating to stakeholders** — output must reach people who do not know what P90
  means. Plain-language sentences ("85% likely to finish between 12 October and
  20 November") and a burn-up with a confidence cone, showing the band narrowing as work
  completes. A forecast history also enables a sliding-date detector: an early warning
  when successive re-forecasts keep moving out rather than converging.
