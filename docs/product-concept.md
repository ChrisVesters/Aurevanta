# Aurevanta — Concepts and Planned Features

> **Status: design intent, and now partly built.** As of 2026-08-14 the schema this
> document implies exists: M0–M1a built tenancy, identity and teams, and **M2 built the
> estimation schema** — projects, work items, immutable P10/P50/P90 estimates with an
> estimator, progress and actuals, and a precedence graph, with the plainest possible UI to
> fill them. `roadmap.md` sequences the rest and is newer than this document wherever the
> two disagree.
>
> **Everything downstream of the schema is still design intent**, which is most of what
> follows: there is no distribution fitting, no sampling, no forecast of any kind. The
> product's own argument applies to itself here — what exists today is a table of numbers,
> and M3 is what makes it worth having. Two of the *Open questions* at the end were
> answered by M2 and are marked as such; the rest stand.

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

> **Built, 2026-08-14.** `V9__estimates.sql` has no `updated_at` and there is no endpoint
> that updates one; a revision is a new row and the current estimate is the newest per
> (item, estimator). The estimator is a **user** rather than a membership, so it survives
> that person leaving the organisation — a membership is deletable and this evidence is not.

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

> **Superseded, 2026-08-06.** `roadmap.md` reverses the first half of this. Measurement
> showed that summing a flat list is not a neutral simplification — it silently assumes one
> worker doing everything in sequence, and the same ten items forecast at 51 or 86 days
> depending only on structure. Precedence dependencies therefore moved into the schema and
> the engine; capacity modelling remains later, as M11.

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

- ~~**Unit of estimation** — task, story, or epic?~~ *Answered by M2: **task**.* Coarser
  units hide scope growth inside the estimate, which M3's scope-uncertainty model would
  then count a second time. The quantity is **effort in hours**, never duration — duration
  is effort divided by what is assigned to it, and that division is M11's.
- ~~**Multi-estimator support**~~ *Answered by M2: **schema now, UI later**.* Several
  estimates may sit on one item, one current per estimator, and they are read back
  together — so two people disagreeing is stored rather than refused. What is *done* with
  the disagreement is M3's, and the session UI that makes it a group activity is in the
  icebox. It was a schema decision, as this bullet said, and the schema was built for it.
- **Communicating to stakeholders** — output must reach people who do not know what P90
  means. Plain-language sentences ("85% likely to finish between 12 October and
  20 November") and a burn-up with a confidence cone, showing the band narrowing as work
  completes. A forecast history also enables a sliding-date detector: an early warning
  when successive re-forecasts keep moving out rather than converging.
