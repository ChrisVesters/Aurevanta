# M8 — Actuals and calibration feedback: implementation plan

> **Built, 2026-08-18.** All six steps are done and each carries its own *As built* section.
> One migration, no new problem code, and no change to anything the engine samples —
> `Engine.VERSION` is still 2. **This is the first milestone whose headline number this product
> does not control**, and on the day it shipped almost every organisation's answer to it is
> *nothing has been scored yet*.
>
> **Scope.** `roadmap.md` M8: record what happened, then measure the hit rate — of the items
> estimated, how many landed inside their P10–P90 band? It should be 80%; most teams score 30–50%.
> Report it per estimator, offer a correction, and **refuse to score an estimate written after the
> work began**, because somebody who can already see how a task is going is writing a report.
> Split the record by `elicitation_method`, which is the only thing that can ever say whether M5
> worked. Excluded and argued below: comparative framing (decision 10), proposing M3b's parameters
> from history (decision 9), throughput (M9), the burn-up and the movement decomposition (M10),
> and anything that feeds a correction back into a forecast (decision 8).
>
> **How to read this.** The decision that decides whether this milestone reports signal or
> flattery is **decision 1** — what counts as a forecast, and why a person's *day* cannot be
> compared exactly with a server's *instant*. The one that decides whether it is honest under
> pressure is **decision 6**: the hit rate on its own is trivially gamed by estimating 1–1000
> hours, and the pair of numbers is not, which is why neither ships without the other.
> **Decision 8** is the one somebody will want to undo first.
>
> **Why this milestone is different from every one before it.** M3 through M7 are all claims about
> the future, checked against arithmetic. This is the first that is checked against **what
> happened**, and it is therefore the first whose headline number this product does not control.
> Everything else here can be made to look good by building it well. This one can only be made to
> look good by *estimating* well, which is the point — and it is why every temptation in this plan
> is a temptation to make the number nicer.
>
> **And the honest caveat, stated at the top the way `m5-plan.md` states its own.** On the day this
> ships, almost every organisation's answer will be *nothing has been scored yet*, and that will
> stay true for months. `actual_effort_hours` is optional and most teams do not track it; a
> per-estimator reading needs tens of completed items before it can tell 45% from 80% (the table
> below). **The empty state is therefore the main screen of this milestone for its first year**,
> and it is designed as such in step 5 rather than as a fallback.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | The progress record stops being written over ✅ *done* | — |
| 2 | What one actual says about one range ✅ *done* | M2 |
| 3 | Which estimates were forecasts, and which were reports ✅ *done* | 1, 2 |
| 4 | The record, on an endpoint ✅ *done* | 3 |
| 5 | On screen, starting with the empty one ✅ *done* | 4 |
| 6 | Close out ✅ *done* | 1–5 |

**M8 adds one migration, and it creates a table rather than a column.** `V16__work_item_progress.sql` is an
append-only log beside the four progress columns, not instead of them — the one piece of evidence
in this milestone that cannot be reconstructed later, which is exactly the argument
`roadmap.md` already makes for it under *Cross-cutting*. The calibration record itself is
**derived**, M6's decision 1 spent a third time.

---

## The arithmetic this plan is built on

**Not a measurement — there is nothing to measure yet, and saying so is the point.** Every other
plan in this directory opens with numbers taken off a running system. This one opens with
arithmetic that can be checked with a calculator, because the thing M8 measures does not exist in
any database this product has ever written to. What *can* be settled in advance is how much
evidence a hit rate needs before it means anything, and the answer decides two screens.

A hit rate is a proportion. Its 80% Wilson interval — the same confidence as the band being
measured, see decision 7 — at the counts a real team will actually reach:

| Scored | Hits | Hit rate | 80% interval | What it can tell you |
|---|---|---|---|---|
| 5 | 4 | 80% | 51–94% | Nothing. Consistent with a team at 55% and one at 93%. |
| 10 | 8 | 80% | 60–91% | Nothing useful. Still consistent with badly overconfident. |
| 40 | 32 | 80% | 71–87% | Consistent with calibrated, and no longer with 50%. |
| 40 | 18 | 45% | 35–55% | **Clearly not 80%.** The first count that says something. |
| 100 | 45 | 45% | 39–51% | The same finding, now sharp enough to act on. |

**Three things follow, and they shape steps 4 and 5.**

**A per-estimator number below about forty scored estimates is decoration.** Four out of five is
80% and means nothing whatever. So the interval travels with the rate everywhere, never as a
footnote, and the count is beside both — and a row with too little behind it shows its interval
and no headline figure at all.

**The team aggregate is what most organisations will read for the first year**, because it is the
only bucket that reaches those counts. That is the reverse of the emphasis `roadmap.md` puts on
it ("reporting that per estimator"), and it is a matter of arithmetic rather than of preference.

**Detecting badness is cheap; confirming goodness is expensive.** Forty items settle whether a
team is at 45%, and do not settle whether they are at 80% — the interval at 32/40 still spans
sixteen points. That asymmetry is convenient: the finding this product exists to deliver is the
unflattering one, and it is the one that arrives first.

### The one number that is not a proportion

The hit rate throws away almost everything each completed item knows. An item that took twice its
P90 and one that took a minute over it are both one miss. So each scored estimate also yields
**where the truth landed on the estimator's own scale** — the percentile of the actual under the
log-normal their own two ends imply:

```
z = (ln actual − mu) / sigma        mu, sigma from LogNormalFit.from(p10, p90)
u = Normal.cdf(z)
```

`u` is the whole of the evidence in one number between 0 and 1. A calibrated estimator's `u`
values are spread evenly across the interval; a hit is `0.10 ≤ u ≤ 0.90` and nothing more. From
the `z` values, two corrections fall out with no further modelling:

| | Statistic | Reads as | Calibrated |
|---|---|---|---|
| Bias | median of `u` | Half your work lands above your own *n*th percentile | 0.50 |
| Spread | standard deviation of `z` about its mean | Your range should have been this many times wider | 1.00 |

An estimator at `u = 0.78` with a multiplier of `2.4` is systematically optimistic **and**
systematically overconfident, and those are two different things to fix. Somebody who has learned
to write 1–1000 scores a hit rate of 100% and a multiplier of `0.3`, which says *your bands are
three times wider than your errors* — that is decision 6, and it is why the pair is reported and
never the rate alone.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What counts as a forecast | **An estimate written before the reported start day**, with the ambiguous day excluded rather than split. |
| 2 | What happens to the rest | **Scored separately, into three named buckets** — forecasts, reports, unbounded. Nothing is dropped. |
| 3 | What is scored against what | **One (item, estimator) pair**, the estimator's last word before the boundary, against a finished item's recorded actual. |
| 4 | What an "actual" is | **`actual_effort_hours` on a `DONE` item, and nothing else.** Never elapsed days, never a partial. |
| 5 | Whether the hit rate goes through the fit | **No.** `p10 ≤ actual ≤ p90` is arithmetic on what somebody typed. Only the corrections use the log-normal. |
| 6 | Whether the rate may ship alone | **Never**, in either direction: the rate alone is gamed by widening, the multiplier alone hides bias. And there is no ranking of people. |
| 7 | How uncertain the number itself is | **A Wilson interval at 80%**, the same confidence as the band being measured, shown everywhere the rate is. |
| 8 | Whether a correction feeds the engine | **No, and this is the one to defend.** M8 reports; applying it would close a loop on its own evidence. |
| 9 | Whether history proposes M3b's parameters | **Not yet, and not from this number** — a per-estimate error is not a common cause, and folding one into the other counts the same spread twice. |
| 10 | What M8 must not become | **Not comparative framing, not backtesting, not M9, not commitment tracking.** |

### Decision 1 — A forecast is an estimate written before the work began

`roadmap.md` states the rule and M2 made it computable: an estimate carries an immutable
`created_at`, an item carries `started_on`, and an estimate on the wrong side of that is a report
by somebody who could already see how the task was going. Counting it flatters the one number in
this product whose entire value is that it is unflattering.

**The comparison is between two different kinds of thing, and there is no exact answer.**
`estimates.created_at` is a `timestamptz` the server observed. `work_items.started_on` is a
`date` a person reported, and `V10` is explicit that this is the one place in the schema where
that is deliberate — there is no time of day in "we started it on the twelfth", and inventing one
is the bug `formatDay` and `todayHere` exist to prevent. So no timezone makes the comparison
correct; the rule has to be one whose *error* runs in the safe direction.

**The boundary is the start of the reported start day, in UTC, and an estimate written on that
day is not a forecast.** Excluding the ambiguous day costs real forecasts — estimating at planning
and starting work that afternoon is an ordinary Monday — and that cost is paid deliberately,
because the alternative admits reports into the headline and reports are what this rule exists to
keep out. **The count of estimates excluded this way is published**, so if the ambiguous day turns
out to hold most of an organisation's estimates that arrives as a number on screen rather than as
a quietly better hit rate.

**The boundary is the *earliest* start ever reported for the item, not the current column value**,
which is the whole reason step 1 exists. As the schema stands, `started_on` is written over by
anybody with no trace that it ever said something else — so an estimate can be moved from *report*
to *forecast* by editing the date it is measured against. Two claims about when work began means
the earlier one wins, because moving a start later is the direction that flatters, and the tie-break
rule throughout this milestone is that the unflattering reading wins.

| Rejected | Why |
|---|---|
| Include the start day (`created_at < start + 1 day`) | Admits every estimate written while somebody watched the task run. |
| Compare in the organisation's timezone | There is no such column, and inventing one puts a setting between an estimator and their record. |
| Read the current `started_on` | The number can then be improved by editing it, which is the flattery the rule exists to prevent. |
| Split the ambiguous day into a fourth bucket | Three buckets already ask something of a reader; a fourth to hold a day is not worth its row. |

### Decision 2 — Three buckets, and nothing is thrown away

`roadmap.md` offers a choice — exclude late estimates, "or score them separately, which is
arguably the more interesting report of the two". Both, and a third the roadmap does not mention.

| Bucket | Contains | Why it is its own row |
|---|---|---|
| **Forecasts** | Estimate written before the boundary | The headline. The only bucket the breakdowns split. |
| **Reports** | Estimate written on or after the boundary, on an item with a reported start | The value of hindsight, as a number. Expect near 100%; anything lower is a finding. |
| **Unbounded** | Every estimate on a finished item that never had a start reported | Cannot be told apart from a report. Scored, named, and kept out of the headline. |

**The third bucket is what rescues the common case.** `DONE` does not require a start — CLAUDE.md
says so and `WorkItemService.requireConsistent` enforces it, because plenty of work is ticked off
by somebody who never marked it as begun. Under decision 1 alone every one of those items is
unscorable, which would silently discard most of the evidence in a typical organisation. Scoring
them into a bucket that says what it is keeps the evidence and refuses to launder it into the
headline, and it gives the product a true thing to ask for: **record a start and your estimates
start counting.**

**Reports scoring far better than forecasts is the expected shape and is worth reporting for its
own sake.** It is the size of the hindsight effect, measured on a team's own work, and it is the
strongest available argument for why the exclusion rule is not pedantry.

### Decision 3 — One (item, estimator) pair, the last word before the boundary

An item three people estimated yields three scored rows, not one. Calibration is a property of
people, and M2's schema kept several current estimates per item precisely so that disagreement
survives to be read.

**Which of an estimator's rows is scored: their last one before the boundary.** Not their newest
overall, which may be a report, and not their first, which they themselves superseded. A revision
written while the work was still ahead of them is a better forecast and is the one they stand
behind.

**Archived items are scored, and so are archived plans.** Coverage ignores archived items so that
the count and the screen agree; this is the opposite case — evidence is evidence, and excluding it
would make archiving a way to improve a hit rate. Nothing on screen invites that, and nothing
should make it work.

**Everything is scoped to one organisation**, and this corrects a comment in `V9`, which says M8
"calibrates per estimator across everything they ever estimated, in whichever organisation". It
does not. `estimates` carries a `tenant_id`, isolation is enforced in application code, and
reading somebody's estimates from another organisation would be a cross-tenant leak however true
it is that the same person made them. A consultant with two clients has two records, and the two
never meet.

**Somebody who has left still has a record.** The estimator is a `User` and not a `Membership` —
M2 spent a decision on it — so their rows survive their removal, and their name comes off `users`.
An organisation's history of what it estimated does not change because somebody moved on.

### Decision 4 — An actual is recorded effort on finished work, and nothing else

`actual_effort_hours` on a `DONE` item. Three near-misses, each of which would produce a plausible
number:

- **Elapsed time is not effort.** `completed_on − started_on` is duration, and duration is effort
  divided by what was assigned to it. That division is M11's, and `m4-plan.md`'s sharpest warning
  is about exactly this class of error: a quantity with capacity already inside it, divided again.
  A calibration built on elapsed days would report every estimator as wildly optimistic, by a
  factor nobody could see.
- **Effort recorded against `IN_PROGRESS` work is a partial.** `UpdateProgressRequest` accepts
  hours in that state deliberately — effort so far is a real thing to record — and scoring one as
  a final actual would put every unfinished item below its own P10. Only `DONE` is scored.
- **A missing actual is not a zero.** Most teams do not track effort, `@Positive` guarantees a
  recorded one is above zero, and an absent one means the item is not evidence. It is counted in
  what is missing, which is what step 5's empty state is made of.

### Decision 5 — The hit rate never goes through the fit

"Inside the band" is `p10 ≤ actual ≤ p90`, evaluated on the three numbers somebody typed. It does
not consult `LogNormalFit`, and it would give the same answer if this product replaced the
log-normal tomorrow.

That separation is worth having on purpose. The headline is then a fact about **what was claimed**
against **what happened**, with no model in the middle, which is what makes it the number to put
in front of a sceptic. The two corrections *do* need the fit, because "how far outside" has no
meaning without a distribution — and they are labelled as the modelled half. A future change to
the fit moves the corrections and cannot move the hit rate.

It also disposes of the degenerate case with no special handling. Three identical numbers make
`sigma` zero, so `z` is undefined and no correction can be computed from that row — but whether
`actual` fell between `p10` and `p90` is perfectly well defined, and the answer is almost always
no. Somebody who claimed certainty and was wrong scores a miss, which is correct. Those rows count
in the rate, are excluded from the corrections, and their number is published.

### Decision 6 — The rate and the multiplier ship together, and people are not ranked

**A hit rate on its own is gamed in one move.** Estimate everything 1–1000 hours and score 100%
forever. This is not a hypothetical failure of a metric; it is the obvious response to being shown
a number about yourself, and the wider bands would then be fed to M3, which would produce a band
so wide nobody reads it. The product would have taught the failure it exists to detect — the same
trap `EstimateQuality` names, arriving through the reporting layer instead of through a threshold.

**The spread multiplier closes it, because it moves the other way.** Bands three times wider than
the errors report `0.3`, which reads as *narrow these*. Bias and spread are two failures and
neither statistic sees the other, so both are always on screen and neither is ever a headline
alone. This is M3b's argument about the team factor and scope growth, arriving in a reporting
surface: two effects that load different failures are not substitutable, and collapsing them into
one "estimation score" would delete the only thing that makes either safe to show.

**And there is no leaderboard.** The per-estimator table is ordered **by name**, carries no rank,
no badge and no aggregate score, and shows the count and interval on every row so that a person
with six scored items is visibly not being compared with one who has ninety. This product ranks
work — M6 ranks sources of spread, M7 ranks candidate cuts — and it does not rank people. The
record exists so somebody can correct their own estimating, which is `roadmap.md`'s own phrase:
the tool improves its users rather than merely serving them.

### Decision 7 — A Wilson interval, at 80%, everywhere the rate appears

The table at the top of this plan is the argument: 4/5 is 80% and says nothing at all. A rate
without its interval invites a reader to act on five items.

**Wilson rather than the normal approximation**, which is wrong in exactly the cases this will be
read in — it produces intervals running past 100% at small counts and high rates, and a bound of
104% on a screen is the end of the number's credibility. Wilson stays inside [0, 1] by
construction and behaves at n = 5.

**At 80%, and that is a coherence decision rather than a statistical one.** Every interval this
product shows is a P10–P90 band. A 95% interval beside an 80% band is two confidence conventions
on one screen and a reader has to hold both. It also means the interval is built from
`Normal.P90_Z`, the same constant every estimate in the product is fitted through.

### Decision 8 — The correction is reported and never applied

Nothing computed here reaches `Engine`, `ItemModel` or a stored run. `Engine.VERSION` stays 2.

**The loop is the reason, and it closes quietly.** Apply an estimator's correction to their new
estimates and the next calibration measures the corrected model, not the person — so the record
converges on 80% while the estimating behind it does not change at all, and the one number in this
product whose value is that it is unflattering becomes a number about the correction. Nothing
would fail. The hit rate would simply improve, for the wrong reason, permanently.

**The second reason is reproducibility.** A forecast is currently a function of the ranges a team
typed and the assumptions they stated. Multiply those ranges by a factor derived from a history
that changes every time somebody finishes a task, and two runs of the same plan a week apart
differ for a reason not stored on either row — which is precisely the failure M10's sliding-date
detector exists to catch, arriving from inside the engine.

**And it is not a `ForecastLimitation`.** Limitations are stored in `outputs` because a limitation
is a property of the run frozen at the moment it was made — `m3a-plan.md` is explicit that
deriving them at read time would have made every M3a run claim a model it never had. Calibration
is the opposite kind of thing: it changes every week. Writing it onto a run would freeze a number
that must move, and deriving a limitation at read time would break the rule that keeps the retired
`no_team_factor` honest. It is a **caveat about the inputs**, read fresh, beside the band and
never inside it.

| Rejected | Why |
|---|---|
| Multiply the ranges before forecasting | Closes the loop on M8's own evidence; the record then measures the correction. |
| An opt-in "calibrated forecast" toggle | The same loop with a checkbox, plus two forecast kinds M10 has to tell apart. |
| Store the hit rate on the run | Freezes a number whose whole nature is that it moves. |
| Publish it as a `ForecastLimitation` | Limitations are frozen at run time; this is not, and the enum's contract is that it is. |

### Decision 9 — This does not propose M3b's parameters, and the reason is not "not yet"

`roadmap.md` records the obligation: both of M3b's parameters are "asked of a person now and
derivable from history later", under the rule **propose from history, never default**. M8 is named
as one of the two places that data comes from. It is not built here, and the reason is sharper
than a schedule.

**A per-estimate error is not a common cause.** The spread multiplier measures how wide one
person's range should have been around one task. The team factor is a single draw per run applied
to *every* remaining item, because the codebase fights back for everybody at once. The errors this
milestone measures contain both — the idiosyncratic part and the shared part, added together — and
handing the whole multiplier to `TeamFactor` would count the shared component twice: once inside
the widened estimates and once again in the factor. That is `m3b-plan.md` decision 3's rejection
of scope-as-a-multiplier, reappearing from the calibration side.

**The right route exists and is named rather than built**: decompose the residuals by *when* they
happened, so the component shared across items completed in the same period is the team factor and
what is left is the estimator's own error. That needs many completed plans, a period definition,
and its own oracle. It is a milestone, not a step, and it belongs after M9 gives the same quantity
from an independent direction.

**Scope growth is the easier half and still not here.** "How much did the last five plans grow in
item count" needs a per-plan history of when items were added, which `work_items.created_at` can
almost answer and `forecast_runs.inputs` can answer exactly — and it is M10's movement
decomposition wearing a different hat. It goes where that goes.

### Decision 10 — What M8 must not become

- **Comparative framing is deferred, and this is the milestone that makes it possible.**
  `m5-plan.md` moved it here because comparing an estimate with another *estimate* is a guess
  against a guess; "bigger or smaller than the auth migration?" is reference-class forecasting only
  once the auth migration's **actual** is known. It cannot be built in the same milestone that
  first makes actuals exist: on day one there is no reference class, and it would add a third
  `elicitation_method` splitting a record that the table above shows is already thin at forty rows.
  It is the next thing, and it needs M8 to have been running for a while.
- **Backtesting is not this** (icebox). Replaying a team's history to show what Aurevanta would
  have said is a different feature that happens to read the same rows.
- **Commitment tracking is not this** (icebox). What was promised, at what confidence, on what
  date, is organisational calibration one level up; M8 is per estimator.
- **M9 is not this.** Throughput is a second forecast with no estimation in it. The gap between
  the two is M9's deliverable and needs both to exist.
- **Estimate hygiene across a plan is not this** (icebox). Clustering on 3/5/8 is a pattern over
  estimates with no actuals involved.

The line to hold: **M8 scores completed work against what was said about it beforehand, and
stops.**

---

## Step 1 — The progress record stops being written over ✅ *done*

**Goal.** A progress report becomes the third kind of evidence in this schema rather than the one
kind that is not: append-only, attributed, and timed by the server.

`roadmap.md` carries this under *Cross-cutting* as **perishable** — every week it ships as it
stands is a week of history that cannot be backfilled — and M8 is its first reader. As things are,
`WorkItem.recordProgress` writes `status`, `started_on`, `completed_on` and `actual_effort_hours`
straight over whatever was there, `work_items` has no `updated_at`, and nothing records who said
it. Decision 1's boundary is measured against a column anybody can move afterwards.

- **`V16__work_item_progress.sql`** — a log beside the four columns, never instead of them. The
  item keeps its latest state for the screen and the scheduler; the log holds who claimed what,
  and when the server heard it. That is the estimates pattern exactly, and it is why an estimate
  costs nothing to reason about.
  - `id`, `tenant_id`, `work_item_id`, `reported_by_user_id`, `reported_at timestamptz not null`,
    then the four claimed values: `status`, `started_on`, `completed_on`, `actual_effort_hours`.
  - `reported_by_user_id` references `users` with **no cascade**, mirroring
    `estimates.estimator_user_id` — a report outlives the reporter's membership for the same
    reason an estimate outlives the estimator's.
  - `work_item_id` cascades, like `estimates.work_item_id`: without the item there is nothing the
    report describes, and nothing in the product deletes an item.
  - Index on `(work_item_id, reported_at)`, which is how both readers walk it, plus
    `ix_work_item_progress_tenant` for the tenant cascade.
  - **No unique constraint and no deduplication.** Two identical reports are two people saying so,
    or one person saying so twice, and both are true records of a claim being made.
- **Nothing is backfilled**, and this follows `V14` rather than `V13`. A row already holding
  `started_on = 2026-07-02` has no reporter, no report instant and no history — there is nothing
  honest to write, and inventing a report on behalf of somebody who never made one would corrupt
  the one table whose entire purpose is to say who said what. The migration file says so, at
  length, the way `V14` does.
- **`WorkItemProgress` entity in `item`**, immutable in the way `Estimate` is: no setter, no
  `updatedAt`, no endpoint that rewrites one. Written by `WorkItemService.recordProgress` in the
  same transaction as the column write, so the two cannot disagree.
- **`WorkItemProgressRepository.earliestReportedStarts(tenantId)`** — the earliest non-null
  `started_on` ever reported, per item, for decision 1's boundary; its own repository rather than a
  method on `WorkItemRepository`, because the log is a second table with a second lifetime and the
  item's own queries have no business in it. Items with no logged report at all fall back to the
  current column value, which is the only claim that exists for them.
- **The progress form gains one line**, not a screen: "last reported by Ada, 14 August". A log
  nothing reads is a log that quietly stops being written correctly.

**Tests.** The log is written on every progress call including one that changes nothing, and
carries the caller. Two reports produce two rows and the first is untouched. `earliestReportedStarts`
returns the earlier of two conflicting claims, and falls back to the column for an item with no
log. **`WorkItemProgressMigrationTests`, in the shape `EstimateElicitationMigrationTests`
established**: migrate to V15, insert work items carrying progress in the shape that version
produced, migrate across, and assert the table is **empty** — the backfill test that asserts
nothing was backfilled, because "we deliberately wrote nothing" and "the backfill silently missed
every row" are indistinguishable without it. Tenant isolation on the read.

**Done when** a start date that was moved cannot hide that it moved.

### As built — where it differs from the above

**The boundary is two queries joined in the service, and the join is a fallback rather than a
contest.** The bullet above says the log returns the earliest start "per item" and that items
with no report "fall back to the current column value", which reads as two rival claims to be
weighed. Written that way — take the earlier of the two — it has **a branch nothing can reach**:
`recordProgress` writes the column and appends the same claim in one transaction, so every value
the column has ever held is in the log, and the column can never sit below the log's floor. What
the column is actually for is the rows older than the table, and `putIfAbsent` says that where
`merge(..., earlier)` only looked as though it did. The comment on
`WorkItemService.earliestReportedStarts` records the rejected version, because the careful-looking
one is what somebody will reinstate.

That is the `ProjectService.lockForGraphChange` judgement again — a refusal removed for being
unreachable — rather than `LogNormalFit`'s, which keeps unreachable branches because a pure
function can be called directly by a test. This one is reachable only by writing the column
outside `recordProgress`, which nothing in the product does.

**Reading the log needed an endpoint, which the plan did not name.** `GET /api/items/{itemId}/progress`,
the natural sibling of the `PATCH` that appends to it. It answers the **whole** log rather than the
one line the form shows: the resource is the history, and an endpoint returning only the newest
would need a rule about how much of it is worth serving — which is the question this table exists
to stop anybody answering by accident.

**The one real mistake, and it is the one this codebase warns about by name.** The line was first
written as `formatDay(report.reportedAt.slice(0, 10), locale)`, which reads the **UTC** day off an
instant — so a report filed at nine in the evening in New York displays as tomorrow. That is
`dates.ts`'s own off-by-one arriving from the other side: a `yyyy-mm-dd` is a day somebody reported
and must not be converted, and `reported_at` is a moment the server observed and must be. It needed
a second function, `formatMoment`, and `dates.test.ts` now asserts the pair — including that the
two disagree on `2026-08-15T02:00:00Z` and that `formatMoment` is the one that is right. The suite
running in `America/New_York` is what makes that a failing test rather than a bug half the planet
sees; in UTC every one of those assertions passes against the broken version.

**The `WorkItems` test double had to learn about methods, not just URLs.** Reporting progress and
reading who reported it are the *same URL*, so a double keyed on the path alone handed the form a
list of work items to render as somebody's name — the "test double that answers every URL alike"
rule, met in a form the rule as written does not cover. It now branches on `init?.method` as well.

**The migration test rolls its one insert back.** `theSameClaimMayBeMadeTwice` and
`nothingIsInventedOnBehalfOfSomebodyWhoNeverMadeAReport` are assertions about the same table, and
the second is a row count of zero — so the first commits nothing rather than depending on the order
JUnit happens to run them in.

**Counts.** 23 new backend cases (19 API, 4 migration) and 9 new frontend ones; 828 backend tests and
399 frontend tests pass. `WorkItemProgress`, `WorkItemProgressRepository`, `ReportedStart`,
`ProgressReportResponse` and `WorkItemService` are all at zero missed branches and zero missed
instructions.

---

## Step 2 — What one actual says about one range ✅ *done*

**Goal.** The arithmetic exists in one place, pure, with an oracle behind every part of it.

Three types in `forecast.model`, beside `EstimateQuality` and for its reason: this package is
where arithmetic lives that must be checkable against numbers outside this codebase, it already
holds the fit and the normal distribution both of these need, and it is the only package in the
application separated by purity — a second one would make that sentence false.

- **`BandScore.of(p10, p50, p90, actual)`** — one estimate against one actual.
  - `inside`, `belowP10`, `aboveP90` computed from the four numbers directly. **No fit is
    consulted** (decision 5).
  - `z` and `percentile`, from `LogNormalFit.from(p10, p90)` and `Normal.cdf`. A `sigma` of zero
    yields no `z` at all — the record reports `modelled = false` rather than an infinity — which is
    the `Contribution.NONE` shape and the `TeamFactor.NONE` naming.
  - The stated `p50` is used for `inside` and by nothing else, which is the fit's own rule
    (`LogNormalFit` fits the two ends) arriving unchanged.
- **`Calibration`** — accumulates `BandScore`s and yields the record: counts, `Proportion hitRate()`,
  `medianPercentile()`, `bandWidthMultiplier()`.
  - The multiplier is the standard deviation of `z` **about its own mean**, not about zero, so that
    bias and spread are separated rather than mixed — a systematically optimistic estimator with
    perfectly wide bands must read as `0.5 / 1.0`, not as one inflated number.
  - It keeps the `z` values, which M6's accumulator could not. A median needs them all, and the
    population here is *completed work* — hundreds of rows — rather than ten thousand runs times
    five hundred items. **The naive two-pass mean and variance are used deliberately**, because the
    series is standardised and centred near zero, which is the exact opposite of M6's million-hour
    sums; `m6-plan.md` decision 6 explains why that difference matters and this is the case it
    does not apply to. The comment says so, so that the two do not look like an inconsistency.
- **`Proportion(hits, of)`** — the rate and its Wilson bounds at 80%, built on `Normal.P90_Z`.
  Zero out of zero is not a rate: it reports no value rather than `NaN`, which would sort
  unpredictably and fail to serialise, exactly as M6's zero-variance case does.

**Tests.** **The oracle is exactness, not convergence.** Ten actuals placed at the midpoints of
the deciles of a known log-normal — `u` at 0.05, 0.15, … 0.95 — give a hit rate of exactly 8/10,
which is the target itself, and a median percentile of exactly 0.5. No sampling anywhere. **The
multiplier on that set is 0.989 and not 1**, because ten stratified points under-represent the
tails, and writing 1.0 into that assertion is the mistake to avoid: it is a computable constant,
so it is computed and asserted, and the same case settles that the sample standard deviation
(`n − 1`) is the one used — the population form reads 0.938 on the same numbers. A second set with
every actual at its own P90 gives a median percentile of 0.9 and a hit rate of 1. Doubling every
actual against unchanged ranges moves the median percentile up and leaves the multiplier alone;
multiplying `sigma` by two leaves the median percentile alone and halves the multiplier — **the two
statistics are asserted to be independent**, which is decision 6's whole claim made into a test.
Wilson bounds are asserted against the five rows of the table at the top of this plan, and against
the property the normal approximation loses: at 5/5 the upper bound is **exactly** 1, which falls
out of the algebra rather than out of a clamp. Scale invariance,
as in `ContributionsTests` — hours to minutes changes nothing. Three identical numbers: `inside`
is decided, `modelled` is false, and the accumulator counts the row in the rate and not in the
corrections. An empty accumulator reports nothing rather than dividing by zero.

**Done when** every number this milestone publishes can be checked by hand on a case with a
closed form.

### As built — where it differs from the above

**`BandScore.of` takes three numbers, not four.** The bullet above says the stated middle "is used
for `inside` and by nothing else" — it is used by *nothing at all*. The band being scored is P10 to
P90, so `inside` needs the two ends; the fit needs the two ends; the percentile needs the fit.
There is no third place for the middle to go, and `EstimateQuality` already answers the only
question it can answer, which is whether it agrees with its own ends. Dropping it from the
signature is what makes that visible rather than leaving an argument nobody reads.

**The plan's claim about the Wilson endpoints is wrong, and the measurement is better than the
claim was.** It says the bound at five out of five "is **exactly** 1, which falls out of the
algebra rather than out of a clamp". It falls out of the algebra and not out of binary arithmetic:
swept over every count to 500, twenty out of twenty comes out at **1.0000000000000002** and five
out of five at **0.9999999999999999**. So there is a clamp, and the honest defence is the *size* of
what it corrects — one part in 10^16, against the three points by which the normal approximation
runs past certainty at four out of five. `theClampCorrectsOneUlpAndNotAWrongAnswer` asserts exactly
that, with the unclamped arithmetic kept in the test file beside the textbook form.

**And the clamp the plan did not foresee is the more interesting one.** Bounding into `[0, 1]` was
not enough: at five out of five the upper bound came out *below the rate it was bounding*, which is
incoherent in a way that a number outside `[0, 1]` is not — nothing renders "100% (75%–100%)" as
wrong, and an interval that excludes its own point estimate is wrong. Wilson always brackets the
observed proportion algebraically, so the bounds clamp into `[0, rate]` and `[rate, 1]` rather than
into `[0, 1]`, and `everyBoundIsAProbability` asserts it over every count to 200 instead of over a
handful of chosen ones.

**An even count splits its two middle observations in standardised units, not in percentiles.** The
plan did not say which, and percentile-space is the obvious reading — the median of the numbers
being published. It is wrong, and the oracle is what said so: doubling every outcome against a band
whose ends are a factor of four apart is exactly one 90th-percentile step, so the median must move
from 0.5 to 0.9 exactly, and averaging the two middle *percentiles* gives 0.8982. The percentile
scale is not linear, so an average taken on it means different things at different points — which
is `LogNormalFit`'s own argument for working in logarithms, arriving one layer up. For an odd count
the two agree, and `anOddNumberOfOutcomesTakesTheMiddleOne` covers the other half.

**Both corrections are withheld together, below two outcomes.** The bias needs one observation and
the spread needs two, so the gate could have been split. It is not, and the reason is decision 6:
they are published together because neither is safe alone, so withholding one and not the other
would hand a reader exactly the half that reads as a target.

**The constants came out as predicted.** The perfectly calibrated set of ten reads **0.9887069764763528**
under the sample form and **0.937969795249138** under the population form, and both are in the test —
one as the assertion and one as an `isNotCloseTo`, so choosing the wrong divisor fails rather than
drifting. The actuals themselves are written out rather than computed from `Normal.quantile`, since
a case built with the function it then checks is the homework-marking `Normal`'s own documentation
warns about; they were derived outside this codebase from `erf`.

**Counts.** 31 cases in `CalibrationTests`; 859 backend tests pass. `BandScore`, `Calibration` and
`Proportion` are at zero missed branches and zero missed instructions.

---

## Step 3 — Which estimates were forecasts, and which were reports ✅ *done*

**Goal.** The three buckets of decision 2 exist, filled by the boundary rule of decision 1.

- **`EstimateService.scorable(callerId, tenantId)`** → the estimates on finished work: every
  estimate whose item is `DONE` with a non-null `actual_effort_hours`, with the estimator, the
  three numbers, `elicitation_method`, `created_at` and the item's actual. All of them, not the
  current ones — the bucket an estimate lands in depends on when it was written, so
  `findCurrentInProject`'s "newest per pair" is the wrong shape here and a second query is the
  right answer rather than a parameter on the first.
  - Backed by a projection record `ScorableEstimate`, constructed in JPQL the way `ProjectCount`
    is, so the query returns columns rather than graphs.
  - Filtered by `tenant_id`, which is decision 3's correction to `V9`'s comment made concrete.
  - **Archived items are included**, unlike `findCurrentInProject` — the two queries differ on
    that line for the two reasons stated in decision 3, and both comments say which.
- **`CalibrationService` joins it with step 1's boundaries in memory.** Two reads rather than one
  join, so `estimate` does not have to learn that progress has a history: the arrow it already has
  is to `WorkItem`, not to the log beside it. At an organisation's worth of completed work this
  costs nothing, and it keeps the packages pointing one way.
- **The bucketing**, per (item, estimator):
  - No boundary for the item → every estimate is **unbounded**; score the newest.
  - A boundary, and at least one estimate strictly before it → **forecast**; score the last one
    before it.
  - A boundary and nothing before it → **report**; score the newest.
- **`MembershipService.requireMember` at the entry point**, once, because this service is the
  caller rather than a passenger — the two reads below it are tenant-scoped queries and not
  service lookups that would re-check.
- **The coverage counts come out of the same pass**, because the empty state is made of them:
  completed items, completed items with an actual, completed items with an estimate, and the
  number of estimates excluded by the ambiguous-day rule.

**Tests.** **The one that matters is the boundary changing the answer**: a fixture where the
estimates written after the start would score 90% and the ones written before score 40%, asserted
to report 40% in the headline and 90% in the reports bucket. An estimate written *on* the start day
is a report, and is counted in the published exclusion count. An item whose start date was later
moved forward still measures against the earlier claim. An item with no start ever reported lands
in the third bucket and not in the headline. Two estimators on one item produce two rows; one
estimator's three revisions produce one, and it is the last one before the boundary rather than
the newest. An item done with no actual is scored nowhere and counted in coverage. Cross-tenant:
the same person's estimates in another organisation are invisible. A former member keeps their
rows and their name.

**Done when** moving a start date, archiving an item, or writing an estimate late cannot improve
the headline.

### As built — where it differs from the above

**`ScorableEstimate` carries six columns and not nine.** The bullet lists the estimator, the three
numbers, the method, the timestamp and the actual. The stated middle is gone for step 2's reason —
the band is P10 to P90 and the fit takes the two ends, so there is nowhere for it to go. The
estimator's *name* and `elicitation_method` are gone because nothing in this step reads them: they
exist for step 4's two breakdowns, and a projection column nothing reads is a column fetched on
every row of an organisation's history for nobody. They go in when the thing that reads them does.

**`CalibrationService` has no membership check of its own, which contradicts the bullet above.**
The plan says one check "at the entry point ... because this service is the caller rather than a
passenger". Written that way it would need `EstimateService.scorable` to skip *its* check, and that
is the rule the four domain services own — no method touches a row before it has passed. So each
read enforces it and the caller adds nothing: `recordFor` makes four service calls and every one of
them re-reads the caller's standing. Four indexed lookups is the price of not having a fourth copy
of the rule to keep in step, which is the trade `MembershipService.requireMember` was extracted to
make.

**Three of the four coverage figures cannot come from "the same pass", and that is not a
shortcut.** Finished work nobody estimated and finished work nobody measured are precisely the rows
the scorable query excludes, so counting them means asking about them —
`WorkItemRepository.completedWork` for the first two and `EstimateRepository.countCompletedItemsEstimated`
for the third. Only `scoredItems` and `movedByTheStartDay` fall out of the pass. That is four reads
for the whole record, on a population bounded by what a team has actually finished.

**`movedByTheStartDay` needed a definition, and the plan's phrase does not supply one.** "The number
of estimates excluded by the ambiguous-day rule" could mean several things — every estimate written
that day, or the rows the rule moved. It is the second: a scored pair counts here when nothing was
written before the boundary *and* something was written before the next midnight, which is exactly
the set that would have been forecasts had the day been included. That is the number decision 1
promises to publish, because it is what the rule cost.

**The service returns a domain result rather than a response**, which is the opposite of what
`ForecastService.contributionsTo` and `cutsFor` do. The difference is that those are the last stop
and this is not: step 4 groups the same pass two more ways, so `OrganisationCalibration` and
`CalibrationCoverage` are the seam it groups over. `Calibration` itself is exposed on that record —
a mutable accumulator on a returned value, which is loose, and acceptable because this service is
its only writer and it has no setters worth the name.

**Counts.** 16 cases in `CalibrationServiceTests`; 875 backend tests pass. `CalibrationService`,
`CalibrationCoverage`, `OrganisationCalibration`, `ScorableEstimate`, `CompletedWork` and both
touched services are at zero missed branches and zero missed instructions.

---

## Step 4 — The record, on an endpoint ✅ *done*

**Goal.** `GET /api/calibration` answers what this organisation's ranges have been worth.

- **A new feature package `calibration`** — service, controller, responses — following `forecast`'s
  shape: it reads other features through their services and nothing points back at it. It is the
  second package to read across features and the first to read only.
- **One endpoint, organisation-scoped, any member.** Not per plan: a single plan holds too few
  completed items to reach the counts in the table above, and calibration is a property of people
  rather than of plans. Reachable by everybody for `/api/members`' reason — colleagues may see
  what their colleagues estimated, and they can already see the estimates themselves.
- **The response**, one shape used four times:
  - `forecasts`, `reports`, `unbounded` — each with `scored`, `hits`, `hitRate`, `hitRateLow`,
    `hitRateHigh`, `belowP10`, `aboveP90`, `medianPercentile`, `bandWidthMultiplier`,
    `pointEstimates`, and nulls rather than zeros where nothing was scored.
  - `byEstimator` — the same shape per person, **over the forecasts bucket only**, ordered by
    display name (decision 6).
  - `byMethod` — the same shape per `elicitation_method`, forecasts only. **This is what M5 shipped
    `V15` for**, and it is three lines of grouping here because that column exists.
  - `coverage` — what was not scored and why, which is step 5's main screen for a year.
  - `firstScored` / `lastScored` — the span the record covers, because a record with no dates on it
    is a record nobody can tell is stale.
- **No new problem code.** There is nothing here to refuse that `not_a_member` does not already
  answer, and no request body to get wrong. That is worth noticing rather than assuming: it is what
  a read-only derived endpoint looks like when the derivation stores nothing.
- **No window and no recency weighting.** The record is over everything, and it publishes its span
  so a reader can see how old it is. A twelve-month window is the obvious next thing and is
  deliberately not guessed at — nobody can choose that number well today, and a stale record is
  visible where a badly-chosen window is not.

**Tests.** `CalibrationApiTests`, driving the API: the three buckets, the two breakdowns, the
coverage counts, ordering by name, an organisation with nothing scored answering `200` with a
record full of nulls rather than a refusal. Tenant isolation with a two-organisation fixture, the
way `MembershipApiTests` does it. `not_a_member` for a caller whose membership has gone. The
`byMethod` split is asserted with `three_point` and `surprise_framed` rows scoring differently, so
the query that answers M5's question is exercised rather than merely present.

**Done when** the question "did changing how we ask change anything" has an endpoint that answers
it.

### As built — where it differs from the above

**`firstScored` and `lastScored` are when the estimates were *written*, not when the work
finished**, and the plan does not say which — "the span the record covers" admits both readings and
they answer different questions. A calibration record is a statement about how an organisation
estimates, so what makes it stale is the age of the estimating in it: a last-scored date eight
months old says that nothing predicted since has finished yet, which is what a reader needs before
acting on the number. They go out as instants, like step 1's `reported_at`, because that is what
they are — the browser converts them to where its reader is sitting.

**Computing that span is a fold and not a running minimum, and the reason is the coverage report
rather than taste.** Written as `earlier(held, arriving)` / `later(held, arriving)` it has one arm
per direction that a given fixture may never reach — and *which* arm is not fixed, because rows
arrive in item-identifier order and those identifiers are random per run. A test would have passed
with a branch uncovered, and a different run would have covered that one and missed the other.
`endOf(scoredAt, comparator)` has no arm at all. Order-independent in the answer is worth having;
order-independent in the coverage report is what made it necessary.

**The two breakdowns order by name *and then by identifier*, where the plan says only by name.**
That is `ProjectRepository`'s own rule about its listing arriving here — two people may share a
display name, and an order settled only by name is one that rearranges itself between requests.
`namesTheEstimatorsInNameOrderAndNotInRankOrder` also pins the negative: the fixture is built so
that the person who scores best sorts last, which is what makes it a test of the decision rather
than of the alphabet.

**`byMethod` groups the stored string and knows nothing about `Elicitation`.** A method this server
has never heard of comes back under its own name rather than as nothing, which is the property that
column is a `varchar` for. Grouping through the constants would have quietly dropped rows written by
a future version.

**Two things the plan predicted and it is worth confirming.** There is **no new problem code** — a
derived read that stores nothing has nothing to refuse beyond the `not_a_member` every tenant-scoped
endpoint already shares. And there is **no change to `SecurityConfiguration`**: `anyRequest()` is
already `hasAuthority(TENANT_SCOPED)`, so a new endpoint is access-token-only by default and would
have to be *deliberately* opened rather than accidentally left so.

**Counts.** 13 cases in `CalibrationApiTests`, beside step 3's 16; 888 backend tests pass. Every
type in `calibration`, plus `ScorableEstimate`, is at zero missed branches and zero missed
instructions.

---

## Step 5 — On screen, starting with the empty one ✅ *done*

**Goal.** A member can see what their organisation's ranges have been worth, and — for the first
year — exactly what is missing before that question has an answer.

- **`/app/calibration`, in the nav for every member.** The label in the catalogue is **"Track
  record"**, not "calibration": the route and the API keep the precise word, and the person reading
  it gets the plain one. Nothing on the page names a percentile, which is `EstimateForm`'s rule
  reappearing — "landed inside the range" says the same thing to more people than "P10–P90
  coverage".
- **The empty state is the designed one.** Most organisations will see it for months, and it says
  what is missing with the counts behind each line: work finished without an actual recorded, work
  finished with no estimate, work finished with no start date. Each is a sentence about what would
  start the record moving, not an apology. A page that says "no data" here is a page that guarantees
  there will continue to be none.
- **The headline** is one number with the target beside it — *of the estimates written before the
  work began, 45% landed inside their range; a well-calibrated team scores 80%* — with the count
  and the interval on the same line, never below it. Then the two tails, because misses that are
  all above P90 and misses on both sides are different problems: the first is optimism, the second
  is a band too tight.
- **The two corrections, always together** (decision 6), each with a plain reading: *half your work
  lands above your own 78th percentile* and *your ranges should have been 2.4 times wider*.
- **Then the three buckets as a table**, so the reader can see the hindsight effect for themselves,
  and the by-method table, and the by-estimator table ordered by name.
- **Bars rather than a chart**, as `ForecastPanel` renders M6's contributions — no chart library,
  and the first real chart in this product stays where `roadmap.md` puts it, after the interface
  rework.
- **`ProgressForm` asks for the actual at `DONE`, and says why.** Not required — that is settled in
  `V10` and in decision 4 — but a box with a sentence under it explaining that this is what makes
  the next forecast measurable. This is the highest-leverage change in the milestone, because the
  whole record is built on an optional column.
- **`ForecastPanel` carries one line beside the band**: what this organisation's estimates have
  historically been worth, with a link. A band with its own track record next to it is the most
  useful placement in the product, and it is a caveat about the inputs rather than a correction to
  the number (decision 8). It is a **separate read that must not be able to break the forecast** —
  if it fails or is empty, the line is absent and the band is unaffected.

**Tests.** The empty state renders every count and no rate. A record renders the interval and the
count beside the rate, and never the rate alone. The two corrections appear together. Rows are in
name order, and a row with too few scored shows its interval and no headline figure. `ProgressForm`
still submits without an actual. `ForecastPanel` renders the band unchanged when the calibration
read fails — mocked **by URL**, since this screen now makes two requests and a double that answers
both alike is the lying double CLAUDE.md names. Every string comes from the catalogue, which the
test setup enforces.

**Done when** somebody can find out that their team is at 45% without being told what a percentile
is.

### As built — where it differs from the above

**The response was reshaped, and the coverage report is what asked for it.** Step 4 published five
independently nullable scalars — `hitRate`, `hitRateLow`, `hitRateHigh`, `medianPercentile`,
`bandWidthMultiplier` — for two facts that are always absent or present together. Every one of them
then needed a `?? 0` or a redundant null check on this side, and each of those is a branch nothing
can reach, because the server never sends a rate without its bounds. Rather than write four pieces
of dead defence, the two facts became two objects: `rate: { value, low, high } | null` and
`corrections: { medianPercentile, bandWidthMultiplier } | null`. **That makes decision 6 structural
instead of conventional** — there is now no shape in which a client can hold the rate and not hold
the interval, or the bias and not the width — which is a better outcome than the wording that was
meant to enforce it. The uncoverable branch was the symptom; the loose modelling was the fault.

**The bias is drawn rather than printed, and that is what "names no percentile" cost.** A median
percentile of 0.78 has no plain-English rendering that does not ask a reader to think about tail
probability, which is exactly what `EstimateForm` refuses to do. So it is a marker on a bar running
between **Good case** and **Bad case** — the two questions somebody was actually asked — with a
tick at the middle showing where a well-judged record would sit. The reader sees the gap; the page
never names the number. M6's "a bar rather than a number, deliberately" arriving for a different
reason.

**And no threshold went into the browser, which took some care.** The obvious page classifies:
"you are optimistic", "your ranges are too tight". Every one of those needs a cut-off, and a
cut-off in the browser is a second rule about one estimate — the thing `EstimateQuality` exists to
prevent. So the page states what a well-judged set scores (8 in 10, 1.0× as wide, the marker in the
middle), shows what this one scored, and leaves the subtraction to the reader. No number on this
screen is judged by this screen.

**The empty state's three lines are two subtractions and a count, and the third one the plan asked
for does not exist.** "Work finished with no start date" is not in `coverage` — what the server
publishes about that is the `unbounded` bucket, which is a count of *estimates* rather than of
work. Rather than invent a backend count in a step called "on screen", the bucket table names it
and says why it is kept out of the headline.

**Two doubles had to learn about the new request, and one of them mattered.** `ForecastPanel` reads
the record on mount, so every one of its forty-odd cases hit a double that answered
`/api/calibration` with a forecast *list* — an array where the panel expects a record. Twelve
`mockImplementation` sites needed the branch. That is the "answers every URL alike" rule again, in
the shape this file was most exposed to: not one test misled, but a whole suite.

**Three after-unmount guards needed cases of their own**, including one from step 1 that had gone
uncovered — the progress form's history read. Each is the same property the contributions request
already had a test for: nothing arriving after somebody has navigated away may touch a screen that
has gone, on the success path *and* on the failure path. They are what took the frontend to 100% of
branches.

**It shipped looking like a different application, and the fix was to stop styling it.** The first
version gave the page its own frame — no centring, no page padding, its own heading sizes, its own
list treatment — so it rendered flush against the left edge at full width while every other
signed-in page sits in a 720px column. `.calibration` now joins the `.members, .projects` rule that
carries that frame, its sections are ruled off with the `border-top` and `padding-top` the members
page separates its own sections with, and the three tables use the bordered rows the plan and member
lists use. The page-specific CSS that remains is the bar and the marker, which is the only thing on
it that is not a list or a paragraph. **The lesson is narrower than "reuse the styles"**: every
override was a local decision that looked reasonable beside the component it was written for, and
nothing failed — a suite that renders in jsdom cannot see a layout at all, so this was invisible
until somebody looked at it.

**Two things that only a rendered page shows.** The row figures repeated the whole sentence — "45%
contained what the work actually took — 40 estimates" — which pushed every label into wrapping and
made three tidy rows read as a paragraph; they are now "45% of 40 estimates", since the heading
above has already said what the number means. And a blanket `p { margin: 0 }` made the title and
lede sit tighter than on every other page, so the reset is scoped to inside the record.

**Found while doing it, and unrelated:** `--muted` is used fifteen times in `App.css` and defined
nowhere, so every one of them is a declaration the browser discards. It is pre-existing, it affects
the forecast panel rather than this page, and it is left alone here rather than folded into a
milestone it has nothing to do with.

**Counts.** 28 new frontend cases; 423 frontend tests and 888 backend tests pass, with the frontend
at 100% of statements, branches, functions and lines.

---

## Step 6 — Close out ✅ *done*

**Goal.** The record of the milestone matches what was built.

- Each step's `### As built — where it differs from the above` is already written, in the change
  that built it. This step is the whole-milestone read, not the place those get filled in.
- `roadmap.md`: M8 marked done with its own *As built*, the *Progress is written over* section
  under *Cross-cutting* retired with a pointer to `V16`, and the two inheritances decision 9 and
  decision 10 defer written into M9/M10 and the icebox rather than left here to be rediscovered.
  The M5 section's "what M8 inherits" gains the answer: the split exists and here is the endpoint
  that serves it.
- `CLAUDE.md`: a section on calibration in the shape of the existing ones — the boundary rule, the
  three buckets, the rate-and-multiplier pairing, and the fact that nothing feeds the engine.
- `product-concept.md`: the comparative-framing note updated to say the reference class now exists.
- **The review pass**, as in M5, M6 and M7: read the milestone end to end and record what that read
  changed, under its own heading. Every one of the last three found something.

**Done when** the next reader can tell what M8 decided without reading its code.

### As built — where it differs from the above

`roadmap.md` marks M8 done and carries its own *As built*; the *Progress is written over* section
under *Cross-cutting* is struck through and points at `V16`; decisions 9 and 10 are written into
the M3b and M5 sections they were deferred from, and *What is next* now says M9. `CLAUDE.md` has a
calibration section in the shape of the others. `product-concept.md`'s elicitation note says the
split exists and the answer does not yet, and its scope-growth note says why M8 did not propose
M3b's parameters.

### The review pass — what a read of the whole milestone changed

**It found three things, and one of them was the milestone contradicting its own decision.**

**The rows published a rate with no interval.** Decision 6 says the per-estimator table "shows the
count and interval on every row so that a person with six scored items is visibly not being
compared with one who has ninety", and decision 7 says a Wilson interval goes "everywhere the rate
is". Step 5 shipped rows reading "45% of 40 estimates" — the count, and no interval anywhere but
the headline. The cause is visible in the diff: the long form pushed the labels into wrapping, so
the figure was shortened, and the interval was what got shortened out. Rows now carry two lines —
the rate, and `35–55% · 40 estimates` under it — and the three tables share one `Figure` component,
because three copies of "remember the interval" is what dropped it in the first place.

**The scorable query was ordered by a key that is not total.** `order by workItem.id, estimator.id,
createdAt` leaves two estimates by one person against one item at the same instant tied, and the
tie decides *which range gets scored*. That is `ProjectRepository`'s own rule — "a name alone is
not a total order" — arriving somewhere it changes an answer rather than a sequence, so `e.id` now
closes it. Unreachable through the API at microsecond precision and perfectly reachable through an
import or a fixed clock.

**And one number was described as something it is not.** `movedByTheStartDay` counts *pairs that
would have been forecasts under the looser rule*, and the screen said "N estimates counted as
written after the work began only because they were written on the start day itself" — which
describes the scored estimate as being on the start day when it is the newest one and may be months
later. It now says N finished tasks *would have counted as predictions* if a start-day estimate
counted as one, which is what the number actually measures.

**One thing the plan asked for and the build declined, recorded rather than quietly dropped.**
Step 5's tests paragraph asks that "a row with too few scored shows its interval and no headline
figure" — a threshold, and the browser is the one place this milestone decided a threshold may not
live. The interval on every row does that job without one: a row reading `26–48%` over 22 estimates
says how little is behind it more precisely than a cut-off could, and without this end deciding
what "too few" means.

**A second pass over the whole milestone for dead weight found seven things, all of the same
kind.** `BandScore.percentile()` and `Proportion.NOTHING` were public, documented, tested — and
called by nothing but their own tests: the application turns a standardised value into a
percentile inside `Calibration` and never one score at a time, and an empty `Proportion` arrives
from `new Proportion(0, 0)` rather than from a constant. Both are gone, and their assertions
survive in better form: the two that went through `percentile()` now pin `z` against
`Normal.P90_Z` directly, which says the same thing without round-tripping through the function
under test. Five catalogue entries went the same way — `buckets.scored`, superseded when the row
figure grew its interval, and three column headings for a table that is a list. That is M5's rule
applied rather than restated: it deleted the `P10`/`P50`/`P90` entries rather than leave them
unused, so the missing-translation failure is what stops one coming back.

**Two things were looked at and deliberately left.** The `number` formatter now exists in both
`TargetDate` and `TrackRecord`, and the copies differ where it matters — one pins a minimum
fraction digit so a multiplier reads `1.0` rather than `1`, the other does not. A shared wrapper
over `Intl.NumberFormat` taking both options would be indirection with no rule inside it, which is
not what "stated once" is for. And `--muted` is used fifteen times in `App.css` and defined
nowhere, so every one of those declarations is discarded — pre-existing, outside this milestone,
and any fix changes how existing screens look, so it is reported rather than folded in here.

**What the read did not find** is worth recording too, since the two hardest decisions were the
ones most likely to have drifted: nothing anywhere applies a correction to a forecast, and nothing
scores an estimate written on or after the boundary as a forecast. Decisions 1 and 8 are intact,
and each has a test whose failure would be the first thing anybody saw.

---

## Migrations

**One: `V16__work_item_progress.sql`.** M6 and M7 added none, and this milestone would have added
none either — the calibration record is derived, and a stored one would only ever explain estimates
written after it existed, which is `m6-plan.md` decision 1 for the third time.

The exception is not calibration. It is the **evidence calibration reads**, which today is written
over with no trace. `roadmap.md` puts it under *Cross-cutting* and calls it perishable, and M8 is
the first reader whose number can be flattered without it. It backfills nothing, deliberately, and
`WorkItemProgressMigrationTests` asserts that nothing is what it backfilled.

Anybody about to write `V17__calibration_records.sql` should read decision 8 first: a stored hit
rate is a number that must move, frozen.

---

## Sequencing and risk

**The risk in M8 is not the arithmetic.** Every statistic here has an exact case — deciles of a
known log-normal, a Wilson bound checkable on paper — so step 2 stands on the footing M3a and M6
stood on. What has no oracle is **whether anything gets recorded at all**, and that is the risk
the whole milestone carries: the record is built on an optional column that most teams do not fill
in, and no amount of correct arithmetic produces a number from an empty table. That is why step 5
designs the empty state first and why the progress form's prompt is not a nicety.

**The one that will actually go wrong** is decision 1 being relaxed. The exclusion rule will look
like pedantry the first time somebody sees three quarters of their estimates land in the *reports*
bucket, and including the start day, or reading the current `started_on`, will each look like a
small correction that recovers a lot of data. Both make the headline better by admitting hindsight,
and neither fails anything. The defences are the boundary fixture in step 3, the published
exclusion count, and this paragraph.

**The second is decision 8 being undone as an obvious improvement.** "We know Ada runs 40% long —
why not apply it?" is a reasonable question with a bad answer: the correction then measures itself,
and the record converges on 80% while nothing about the estimating changes. It is also the change
that would silently make two runs of one plan differ for a reason stored on neither.

**Two things that will look like bugs and are not.**

- **A hit rate of 100% that is bad news.** Wide bands score perfectly. The multiplier beside it is
  what says so, and that pairing is the reason neither number ships alone.
- **A team's record getting worse as they record more actuals.** The first few actuals a team
  records are the ones they remember, which are the tidy ones. The number falling as coverage rises
  is the sample becoming honest, and the coverage count beside it is what makes that legible.

**One thing that will look like a decision and is a limitation.** The record has no window, so a
team that improved two years ago carries their old rows forever. The span is published so this is
visible; fixing it properly means weighting by recency, which needs a number nobody can choose
today. It is named in decision 7's neighbourhood and not guessed at.

**What this milestone must not absorb.** Comparative framing is next and needs M8 to have run for a
while. Proposing M3b's parameters needs a decomposition M8 cannot do alone. Throughput is M9's, the
burn-up and the movement decomposition are M10's, and hygiene across a plan is the icebox's. The
line to hold is that M8 scores completed work against what was said about it beforehand, and stops.
