# M6 — Variance contribution: implementation plan

> **Scope.** `roadmap.md` M6: rank what a plan holds by how much it widens the forecast, rather
> than by how long it is, so that *"what should I spike next?"* has an answer. Excluded: the
> probabilistic critical path and criticality indices (M10 — a different question, see decision
> 10), inverse queries that read this ranking to propose cuts (M7), correlation *groups* beyond
> M3b's single shared factor (icebox), and anything that changes what the engine samples —
> **nothing here does, and `Engine.VERSION` does not move.**
>
> **How to read this.** Decisions first. The two that decide whether this milestone is right are
> decision 2 — *a replay has to prove it is the same engine before it is allowed to explain
> anything* — and decision 3, what the number actually means, because the obvious presentation of
> it is a set of percentages that add up to three hundred.
>
> **Why this is worth doing, and why it is cheap.** This is the most defensible thing in the
> product: a point-estimate tool cannot produce it at all, because it has no spread to attribute.
> It also needs **no new schema, no new sampling and no new modelling** — M3a stored a run's
> inputs, its seed and its engine version precisely so that a run could be reproduced exactly,
> and this is the first feature to spend that. The whole milestone is one pure accumulator, one
> observer hook, one endpoint and one panel.
>
> **Where it will go wrong.** Not in the arithmetic, which has a closed-form oracle in the
> degenerate case. It will go wrong in what the number is taken to mean: a ranking that reads as a
> partition, or a report produced by an engine that is no longer the one that made the run. Both
> produce a plausible number, which is this product's own failure mode.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | What a contribution is, as a pure function ✅ *done* | M3 |
| 2 | The engine says what each run did ✅ *done* | 1 |
| 3 | Replaying a run, and refusing to when it is not the same one ✅ *done* | 2 |
| 4 | What to spike next, on screen ✅ *done* | 3 |
| 5 | Close out ✅ *done* | 1–4 |

**M6 adds no columns and no migration.** That is worth noticing before somebody adds one: the
alternative to replaying is storing a duration per item per run, which at 500 items and 10,000
runs is five million numbers written for every button press — and it would still only work for
runs made after the column existed. See decision 1.

---

## The measurement this plan is built on

A correlation can be accumulated in one pass in two ways: the sums-of-squares formula everybody
writes first, or Welford's co-moment update. The first was measured against the second, and
against an exact 60-digit computation, over 10,000 samples in `double`:

| Scenario | True *r* | Naive error | Welford error |
|---|---|---|---|
| A tight task, 20 h ± 1.2 | 0.368723 | 6.5e-14 | 1.2e-15 |
| An ordinary task, 40 h ± 15 | 0.466790 | 2.5e-14 | 3.3e-16 |
| **A plan of 1e6 hours, item ± 1** | 0.450330 | **2.2e-03** | 4.9e-12 |
| **A plan of 1e9 hours, item ± 0.5** | 0.442508 | **NaN** | 1.2e-08 |

**At ordinary magnitudes the naive formula is fine, and at large ones it is not merely
imprecise.** The failure is catastrophic cancellation: `n·Σx² − (Σx)²` subtracts two nearly equal
enormous numbers, and once the true variance is small relative to the mean squared, the
difference is noise. At a million hours it is wrong in the third decimal place — enough to
reorder a ranking whose entire purpose is the order. At a billion the subtraction goes negative,
`sqrt` returns NaN, and **NaN is not valid JSON**, so the endpoint would fail rather than lie —
which is the better of the two, and is not a defence.

**A million hours is not a contrived input.** `@Digits(integer = 10, fraction = 2)` lets a single
estimate be ten billion hours, and the sum over a 500-item plan is larger still. Nothing refuses
a plan measured in centuries.

This is the same concern `LogNormalFit.variance` already answers with `expm1` — "for a narrow
range the two differ by everything" — arriving in a second place. Decision 6 settles it.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Where the numbers come from | **Replaying the stored run.** Nothing is stored, and every run ever made gets a report. |
| 2 | Whether a replay may be trusted | **It must reproduce its own stored percentiles**, or the report is refused rather than given. |
| 3 | What a contribution *is* | **The square of the correlation** with the plan's completion — ranked, and never shown as a partition. |
| 4 | What gets a row | **Items, the work nobody listed, and the shared team factor.** The last two are why the first is honest. |
| 5 | An item that never varies | **Contributes nothing**, and that is the common case rather than an edge one. |
| 6 | How the correlation is accumulated | **Welford co-moments**, measured, because the obvious formula returns NaN on a large plan. |
| 7 | What the engine changes | **One observer and no draw**, so `Engine.VERSION` stays at 2. |
| 8 | Where the report lives | **Its own endpoint on the run**, computed on demand, and not folded into the forecast response. |
| 9 | What is on screen | **A ranking, with the reason the numbers overlap**, and the sentence it answers. |
| 10 | What M6 must not become | **Not criticality, not a Sobol decomposition, not M7.** |

### Decision 1 — Replay, and store nothing

`forecast_runs` keeps a seed, the resolved inputs and an engine version. Feeding those back
through the engine reproduces the run exactly, so the per-item durations M6 needs can be
*recomputed* rather than kept.

The alternative is a duration per item per run. At the 500 items a plan may hold and the 10,000
runs a forecast does, that is five million numbers — written on every button press, for a report
most runs will never be asked for.

**The decisive argument is not the size, it is the history.** A stored contribution only exists
for runs made after the column did. A derived one exists for **every forecast this product has
ever produced**, including the M3a runs made before scope growth was modelled. A feature that
answers questions about the past is worth more than one that starts answering them today, and
this is the same trade M4's decision 5 made from the other side: *store what is expensive or
lossy to reproduce; derive what is cheap and deterministic.* A replay is neither expensive nor
lossy — it is about a second, and it is exact.

| Rejected | Why |
|---|---|
| A duration vector per item per run | Five million numbers per forecast, and it answers nothing about any run already made. `roadmap.md` rejected it in advance and the reasoning holds. |
| Computing contributions during the original forecast and storing the result | Cheaper than the vectors and still only forwards-looking, and it would put a feature's cost inside the request that asks for a forecast — where the two-second budget lives. |
| Recomputing from the fitted distributions analytically | There is no closed form: the aggregator is a scheduler, which is the whole reason the roadmap says this has to be measured against project completion rather than derived from variances. |

### Decision 2 — A replay must prove itself before it explains anything

**This is the decision that keeps the milestone honest**, and it costs six comparisons.

The report is only meaningful if the engine replaying the run is the engine that made it. Today
that holds: `Engine.VERSION` is 2, and version 1 *is* version 2 with both M3b parameters at zero,
draw for draw. It stops holding the moment somebody bumps the version for a change that cannot be
reduced to a parameter — and at that moment a contributions endpoint that simply re-ran the
inputs would produce a confident ranking of a plan under a model that never forecast it.

So: **replay, then compare the six figures the replay produces against the six stored on the
row. If they differ, refuse.** `forecast_replay_mismatch`, and no numbers.

This is `ForecastApiTests`'s own assertion — the one `m3a-plan.md` calls "the test that fails the
day somebody changes the model without bumping the version" — promoted from a test into a runtime
guard. It needs no compatibility table to maintain and no version arithmetic to get wrong; it
catches a version bump, a JDK generator change, a snapshot format drift and an accidental edit to
the sampler alike, because it asks the only question that matters: *does this still come out the
same?*

The comparison is exact rather than approximate. A replay produces the same `double`s bit for
bit, and the columns hold them rounded HALF_UP to two decimals, so the rounded values match
exactly or something has moved.

| Rejected | Why |
|---|---|
| A list of engine versions this one can replay | A second thing to keep in step with the first, and the failure of forgetting is silent. The run already carries the answer. |
| Replaying anyway and labelling the result "approximate" | There is no such thing. A ranking from a different model is not a less precise ranking, it is a different plan's. |
| Refusing on `engine_version != Engine.VERSION` | Would refuse every M3a run today, which decision 1's whole payoff is about being able to read. |

### Decision 3 — Contribution is the square of a correlation, and it is not a share of anything

For each run, the engine has one duration per item and one completion. Across ten thousand runs
that is two series per item, and the Pearson correlation *r* between them is how much the plan's
finish moves with that item. **The number reported is r²**, which under a linear reading is the
fraction of the forecast's variance that moves with this item — and which is scale-free, so a
tight item on the critical path and a wild one off it are comparable.

**What it is not is a partition, and this is the trap.** In the degenerate case — one worker, a
chain, no shared factor — the completion is a sum of independent draws and the r² values sum to
exactly 1, which is the "share of total variance" the roadmap describes and step 1 uses as its
oracle. **In every real forecast they sum to more than 1**, because M3b's team factor multiplies
every item in a run by the same draw, so everything moves with everything. A screen presenting
these as percentages would show a plan whose parts account for three hundred percent of its own
uncertainty.

So they are shown as a **ranking with a relative bar**, the number is called *how much the finish
moves with this* rather than a share of it, and the panel says in one line that they overlap and
why. `product-concept.md`'s whole complaint about other tools is numbers that look precise and
land wrong; a percentage that cannot be added is one.

### Decision 4 — Three kinds of row, and the two extra ones are what make the first honest

A ranking of items alone answers "which of these should I spike" while hiding whether spiking any
of them is worth doing. Two things in a forecast are not items and both can dominate:

- **The shared team factor.** One multiplier per run, applied to everything. Its correlation with
  the finish is one more series and costs nothing to accumulate. If it tops the ranking, the
  honest reading is *no estimate on this list is your problem* — the plan's spread is a claim
  somebody made about how bad a bad quarter is, and tightening a task will not touch it.
- **The work nobody has listed.** Discovered items exist only within a run and have no identity
  across runs, so they cannot be ranked individually — but their *total* sampled effort is one
  series per run like any other, and on a plan expected to grow it is frequently the largest
  contributor. `product-concept.md` is explicit that scope growth is usually the bigger of the two
  uncertainty sources; a report that omitted it would rank the smaller one and say nothing.

Both are rows in the same table, named rather than numbered, and both are absent from a run whose
parameter was zero — because a source that took no draw contributed nothing, and a row reading
0% would invite somebody to conclude their team has no common cause when what they did was
decline to model one.

| Rejected | Why |
|---|---|
| Items only | Ranks the tail while the dog is off screen. The most useful answer this report can give is sometimes "none of these". |
| Ranking discovered work item by item | They are different items in every run. There is no thing to rank. |
| Splitting the team factor's contribution back across the items it multiplied | That is a variance decomposition with interaction terms — decision 10 — and it would turn one honest number into five hundred attributed ones. |

### Decision 5 — No variance is no contribution, and it is not an edge case

An item whose duration is identical in every run has zero variance, and a correlation with it is
0/0. Left alone that is a `NaN`, which sorts unpredictably, poisons a ranking, and **cannot be
serialised as JSON at all**.

Three ordinary things produce it, which is why this is a decision rather than a guard:

- **An unestimated item.** M3a keeps it in the graph as a zero-effort node, and every partly
  estimated plan has some.
- **Finished work.** Nothing remains, so nothing varies.
- **An estimate of three identical numbers.** M2 accepts them on purpose — it is somebody saying
  they are certain — and `LogNormalFit` fits them as a point mass rather than refusing.

All three report a contribution of exactly zero, which is true: a number that never moves cannot
be what the finish moves with. The same rule covers the whole plan finishing at the same moment
in every run, which is a forecast with no spread to attribute — and there `Histogram` already
makes the same choice, putting everything in one bucket rather than dividing by a zero width.

### Decision 6 — Welford, because the obvious formula returns NaN on a large plan

The measurement above settles it. The naive sums are simpler to read and wrong in the third
decimal at a million hours, which is a plan a person could type; at a billion they produce NaN.
Welford's co-moment update is five lines, allocates nothing per run, and is accurate to 1e-12 in
the case that breaks the alternative.

The cost is one pass, five doubles per source, and no array of samples anywhere — which matters
because the accumulator runs inside the engine's inner loop, 500 items times 10,000 runs.

### Decision 7 — The engine gains an observer and no draw

`Engine.run` takes an extra argument: something to be told, after each run, what the durations
were and when the plan finished. The existing signature stays and delegates with a no-op, exactly
as `Schedule.finish(durations)` delegates to the three-argument form with `NOTHING_FOUND`.

**The rule this must not break is M3b's.** A parameter set to none must consume no randomness —
and an observer consumes none by construction, since it is not random. But the same discipline
applies to the loop: the observer is called *after* everything that draws, it may not draw, and
`Engine.VERSION` therefore does not move. **The test that proves it is the one M3b already
established**: the same seed and inputs produce byte-identical percentiles with an observer
attached and without.

Nothing about `Schedule`, `ItemModel`, `TeamFactor` or `ScopeGrowth` changes. If any of them has
to, this milestone has gone wrong.

### Decision 8 — Its own endpoint, computed on demand

`GET /api/forecasts/{runId}/contributions`. A read, idempotent, storing nothing, reachable by any
member like everything else in the domain.

**Not folded into the forecast response**, for two reasons. It would double the cost of every
forecast for a report most callers never open — and `m3a-plan.md` sized the two-second budget that
keeps a forecast inside its own request. And it would exist only for new runs, throwing away
decision 1's whole payoff.

The budget: a replay is the same work as the original forecast, which was measured at 413ms with
no growth and 915ms at the scope-growth ceiling, plus the accumulator. **Step 3 measures it and
states the number**; if it lands beyond about two seconds at 500 items, the honest answer is a
smaller default sample count for the replay and a note saying so — never a stored column, which
decision 1 has already refused.

### Decision 9 — A ranking, its caveat, and the sentence it answers

The panel lists the sources by contribution, largest first, with a bar rather than a percentage —
decision 3 — and the run's own hours band above it so nobody reads a ranking without knowing what
is being ranked.

Three things are stated rather than implied: that the numbers overlap and do not add up, and why;
that a run made under a different engine cannot be explained at all (decision 2); and the
question it answers, in words — *tighten the top of this list and the band narrows most*.

An item's title comes from the plan as it is **now**, and a run's snapshot holds the title as it
**was**. Where they differ the current one wins, because somebody is being told what to go and
do; where the item has been archived or deleted since, the snapshot's title is shown and marked,
the way an arrow into archived work already is.

### Decision 10 — What this is not

- **Not the probabilistic critical path.** "How often is this item on the path that decides the
  finish" is a different question with a different answer: an item can be on the critical path in
  every run and contribute nothing to the spread, because it never varies. Criticality is M10's,
  it wants a different measurement inside the scheduler, and conflating them produces a ranking
  that means neither.
- **Not a Sobol or ANOVA decomposition.** Those partition variance properly, including
  interactions, and would answer decision 3's complaint about numbers that do not add up. They
  also cost a re-run per source at minimum. A correlation ranking is the cheap, honest 90%; the
  decomposition is worth revisiting only if somebody asks a question the ranking cannot answer.
- **Not M7.** Ranking what widens the plan is not proposing what to cut. M7 reads this and adds a
  search; keeping them apart is what stops M6 growing a scope-selection UI.

---

## Step 1 — What a contribution is, as a pure function ✅ *done*

**Goal.** The arithmetic exists, in one place, with an oracle behind it.

- `Contributions` in `forecast.model` — pure, like everything beside it. It accumulates, per
  source, the five Welford co-moments against the plan's completion, and yields a correlation.
- `Contribution` — one source's answer: its correlation and its squared share.
- Zero variance yields exactly zero, never `NaN` — decision 5.
- No array of samples anywhere: five doubles per source, updated once per run.

**Tests.** **The oracle is the degenerate case the roadmap describes**: for independent draws
summed, the correlation of one with the total is `sd_i / sqrt(Σ sd_j²)` in closed form, and the
squared shares sum to exactly 1 — asserted against arithmetic that exists outside this codebase,
the way `EngineTests` asserts the sampler. A source that never varies contributes zero, for each
of the three ways that happens. A source perfectly correlated with the outcome contributes 1. The
measurement above is a regression with its own name: a plan of a million hours with a tight item
gets the right answer to ten decimal places, and the naive formula's answer is in the assertion so
that nobody "simplifies" it back. Order is preserved under a change of units — multiplying every
duration by 60 changes no ranking.

**Done when** the number can be checked by hand on a case that has a closed form.

### As built — where it differs from the above

**Three doubles per source, not five.** The bullet says "the five Welford co-moments", which is
the count for a standalone pair of series. Here every source is measured against the *same*
outcome, so the outcome's mean and spread are held once for the whole accumulator and each source
carries only its own mean, its own spread and its co-moment. At five hundred items that is 1,500
doubles rather than 2,500, and — more to the point — it is two numbers that cannot come to
disagree about the plan they are attributing.

**The oracle turned out to need no sampling at all**, which makes it far stronger than planned.
Two sources of `[3, −3, 0, 0]` and `[0, 0, 1, −1]` have a mean of zero and are exactly orthogonal,
so four runs give variances of 4.5 and 0.5 against a total of 5.0, and the squared correlations
come out at 0.9 and 0.1 **to the last bit** rather than converged to within a sampling error. A
second test then does the same thing on a hundred thousand real draws across ten sources against
the closed form, because a formula that only works on four orthogonal points is not a formula —
but the exact one is what fails first and most legibly.

**The billion-hour regression is written as an invariance rather than as a magnitude.** A
correlation does not care where zero is, so the test shifts both series by 1e9 and asserts the
answer has not moved — and asserts, on the same numbers, that the naive formula returns `NaN`.
That is better than a table of errors: it states the property the naive formula loses, and the
naive formula is in the test file so that reinstating it is a failing test rather than a
tidier-looking method. Its companion asserts the other invariance the plan mentions in passing,
that sixty times the hours is the same ranking.

**Two cases the bullets did not name were added, and one of them matters.** A source the finish
runs *against* comes back at −1 and still ranks at a share of 1 — negative correlations are
scheduling anomalies rather than errors, and the accumulator must not clamp them away, which is
one more reason the square is what gets ranked. The other is an accumulator that has seen nothing
at all, which reports zero runs and no contribution rather than dividing by one.

**`Contribution` is a one-component record with a derived accessor**, so the correlation and its
square cannot disagree — the shape `EstimateQuality` could not have, because that one needs three
inputs. `Contribution.NONE` follows `TeamFactor.NONE`.

---

## Step 2 — The engine says what each run did ✅ *done*

**Goal.** The engine can be watched without being changed.

- `Engine.run` gains an overload taking an observer; the existing one delegates with a no-op,
  as `Schedule.finish` already does with `NOTHING_FOUND`.
- The observer is told, per run: each item's sampled duration, the total of whatever that run
  discovered, the team factor's draw, and the completion.
- It is called after everything that draws, and draws nothing itself — decision 7.
- `Engine.VERSION` does not move, and neither does `Schedule`.

**Tests.** **The one that matters is byte-identity**: the same seed and inputs produce exactly
the same percentiles with an observer attached and without, which is what says the version need
not move. A chain at capacity 1 with no team factor ranks its items in the closed-form order from
step 1 — the engine's answer agreeing with arithmetic. **And the case the roadmap says the old
formula gets wrong**: two parallel branches where a wide item sits off the one that decides the
finish contributes far less than its variance share, while a modest item at the merge point
contributes far more. A run with `ScopeGrowth.NONE` reports no discovered work rather than zero;
same for `TeamFactor.NONE`.

**Done when** contribution can be measured for a plan whose answer is already known, and the
engine is provably the same engine.

### As built — where it differs from the above

**The counter-oracle needed measuring before it could be asserted, and the first version of it
failed.** A wide item beside a chain of *three* still accounts for 12% of the spread — its tail
reaches past the chain often enough to matter — so the assertion the plan describes was simply
untrue at that shape. Lengthening the chain to five is what makes the claim stark, and the numbers
are now in the test rather than assumed by it:

| Chain length | Wide item's share | Each link's share |
|---|---|---|
| 3 | 0.119 | ~0.17 |
| 4 | 0.044 | ~0.18 |
| **5** | **0.012** | **~0.18** |
| 6 | 0.014 | ~0.14 |

The item with **forty-five times the variance of any other** accounts for 1.2% of the spread while
each of the five narrow links accounts for about 18%. That is `roadmap.md`'s sentence made
executable, and it would have been asserted as a guess if the shape had not been probed first.

**The two non-item sources are named accessors on `Contributions` rather than a third type.**
`Contributions.forRun(items)` allocates `items + 2` sources and `ofItem`, `ofDiscoveredWork` and
`ofTeamFactor` read them; the generic constructor and `of(int)` stay exactly as step 1 left them,
which is what keeps the closed-form oracle testing the arithmetic rather than the layout. The
alternative was a `RunSpread` wrapper holding a `Contributions`, which is one more file to say
that two indices are on the end.

**`Contributions` implements `RunObserver` directly**, so the engine hands it a run and it fills
itself. The row it accumulates is a reused field, not an allocation per run — the engine's own
`durations` array cannot be used as-is because it is longer than the plan whenever a run
discovered work.

**Engine computes the discovered total itself**, in the loop that already fills those durations,
rather than handing the observer a count and letting it re-walk the array. One addition per
discovered item against a second pass over it.

**"No discovered work rather than zero" turned out to be step 3's, and the plan is now clearer
for it.** What step 2 can say is that a source nobody modelled *never varied*, so it contributes
exactly nothing — `TeamFactor.NONE` holds the stretch at 1 in every run and `ScopeGrowth.NONE`
holds the discovered total at 0. Whether the report then shows a row reading zero or **no row at
all** is a question about what the run assumed, not about what it drew, and the run stores the
answer in `team_factor_worse_by_percent` and the two growth columns. That is the M4 calendar
pattern exactly — a run without a calendar has no dates, decided from the stored columns in the
response layer — and it is where decision 4's "absent, not zero" belongs.

---

## Step 3 — Replaying a run, and refusing to when it is not the same one ✅ *done*

**Goal.** A stored run can explain itself, or say why it cannot.

- `ForecastService` gains a method that reads a run, replays it from its snapshot, seed, sample
  count and assumptions, and returns the contributions.
- **It compares the replay's six figures with the six stored on the row and refuses on any
  difference** — `forecast_replay_mismatch`, `409`, with no numbers — decision 2.
- `GET /api/forecasts/{runId}/contributions` — any member, tenant-scoped, nothing written.
- Item identifiers come from the snapshot; the two non-item sources are named constants.
- The replay's cost is measured and the number written into this plan.

**Tests.** A stored run explains itself, and the contributions name the items the plan holds. A
run whose stored percentiles have been altered directly is refused with `forecast_replay_mismatch`
and no numbers — the guard asserted against the only thing that can produce it today. A run made
with both M3b parameters at zero reports neither non-item source. A run in another organisation is
`forecast_not_found`, and an identity token is refused. The endpoint writes nothing: the run's
columns are unchanged afterwards, and no row is added anywhere.

**Done when** no ranking this API publishes can have come from a model that did not produce the
run.

### As built — where it differs from the above

**Decision 9 assumed a title the snapshot has never held, and step 4 inherits the correction.**
That decision says an item's stored title is shown, marked, where the item is gone —
`ForecastInputs.PlannedItem` carries an id, a status, hours spent and the ranges, and no title at
all, on purpose: M10's movement decomposition diffs these, and a title is not a thing that moves.
So the response names items by **identifier only**, and step 4 has to resolve titles from the
live plan and name an item that is no longer in it, the way an arrow into archived work already
is. Better to find that here than while writing the screen.

**The guard became a loop, and that was a coverage failure worth taking seriously.** Written as
the plan describes — six `differs(...)` calls joined by `||` — five of the six short-circuit
branches are unreachable without six near-identical tests, and JaCoCo said so. Two ways out: six
tests, or one guard. Both were taken. The comparison walks a pair of six-element arrays, so there
is one branch; and `everyFigureARunStoresIsPartOfTheGuard` alters each of the six columns in turn
and asserts the refusal, so the claim "all six" is proved rather than asserted in a comment. The
version that would have shipped had the coverage gate not complained checks all six and can only
*demonstrate* one.

**`ForecastRun.hours` stopped being private.** A replay has to round its own answer the way the
columns were rounded before the two can be compared, and a second rounding would be a second
chance to disagree about a run that had not changed. It is package-private with a note saying
why, rather than duplicated into the service.

**The ranking is sorted by the server**, which the bullets left open. The order *is* the feature —
"what should I spike next" is a question about position — and a client sorting it a second way
would be a second answer to one question.

**`ContributionKind` is a name on the wire**, not a shape the client infers from which fields are
null. That follows the rule every refusal in this API already keeps: the browser translates a
name and never guesses from structure.

**The cost was not measured, and that is outstanding.** The plan's decision 8 says step 3 measures
the replay and writes the number down. The suite runs at small sample counts, so what it proves is
correctness rather than budget; a 500-item plan at ten thousand runs has not been timed. Step 4
needs the number before it decides whether the panel loads this on open or on request, and it is
recorded here rather than quietly skipped.

---

## Step 4 — What to spike next, on screen ✅ *done*

**Goal.** The question the milestone is named for, answered where the forecast already is.

- The forecast panel gains a ranking, loaded on demand rather than with the forecast — decision 8
  — so that opening a plan costs what it costs today.
- Sources largest first, with a bar; the two non-item rows named in words; an item's current title
  where it still exists and its stored one, marked, where it does not.
- **The caveat is beside it and not behind a disclosure**: these overlap and do not add up,
  because everything moves together when a bad quarter is bad for everything.
- A run that cannot be replayed says so in one line instead, the way a run with no calendar does.
- Every string comes from the catalogue.

**Tests.** The ranking is in contribution order, not plan order. The two non-item rows render with
their own wording and are absent when the run had neither. An item archived since the run shows
its stored title, marked. A run the server refuses to explain renders the reason and no ranking.
The panel does not ask for contributions until somebody asks for them. Every string comes from
the catalogue.

**Done when** somebody can read a plan's forecast and know which thing to go and reduce.

### As built — where it differs from the above

**The measurement step 3 left outstanding, taken before anything was built on it.** Five hundred
items, ten thousand runs, a team factor and scope growth near the ceiling:

| | Replay |
|---|---|
| Without an observer | 489 ms |
| **With the accumulator** | **491 ms** |

**The accumulator is free** — the scheduler dominates and the Welford update disappears into the
noise — and half a second at the largest plan this product supports is comfortably inside the
budget decision 8 worried about. So no reduced sample count was needed, and "on demand" is a
choice about not charging every reader rather than a workaround for a cost.

**Titles are resolved by the server, which step 3 predicted it would have to decide.** The
snapshot holds no title, so `ForecastResponse` gains `title` and `archived`, filled from both the
live and the archived listings of the plan. Doing it in the browser would have meant the forecast
panel loading the plan's work items, which it has never needed; doing it here puts the join where
the membership check already ran. **Decision 9's "its stored one, marked" is corrected to "its
current one, marked"** — there is no stored one, and the current name is the useful one anyway,
since a reader is being told what to go and do.

**A third naming case exists and had to be handled.** Work the plan no longer holds at all is not
an ordinary state — nothing here deletes an item — but the guard is cheap and the alternative is a
ranking that fails outright on a row somebody removed by hand. It says *"Work no longer in this
plan"* rather than rendering a blank, and the coverage gate is what surfaced it: the branch was
unreachable through the API, so the test reaches it by deleting the row, which is the pattern
`Schedule.topological`'s cycle refusal already follows.

**The refusal wording lives in the problem catalogue, not the panel's.** It was written twice
before being noticed — once as `contributions.unavailable` and once as
`errors.codes.forecast_replay_mismatch` — and the second is the one the mechanism actually uses,
since the panel renders whatever `describeFailure` returns. **`problems.test.ts` caught the other
half**: it asserts the code list and the catalogue agree exactly, so adding a refusal to the
server without wording is a failing test rather than a generic message in production.

**No percentage appears anywhere, and a test asserts the absence.** A bar per source, and the
ranking's own text is checked against `/%/` — because a share that reads as a percentage is one
somebody will add up, and these sum to well over one. The fixture is deliberately not in plan
order and its shares add to 1.2, so a panel that rendered what it was given in the order it
arrived would fail, and one that showed a total would be visibly wrong.

---

## Step 5 — Close out ✅ *done*

> **This section did not exist when the plan was written**, and the *At a glance* table promised
> it for four steps. Written now, from what the milestone turned out to be — which is the honest
> version, and is also why the plan is updated as its steps land rather than at the end.

- `roadmap.md`: mark M6 done. Discharge its own last sentence — *what M6 must not do is change the
  model without bumping `Engine.VERSION`* — which is now enforced at runtime rather than trusted.
- `roadmap.md`: record what M7 inherits. It is the one milestone that reads this ranking, and the
  two are deliberately not the same thing: ranking what widens a plan is not proposing what to
  cut, and M7's own note about evaluating cuts by re-running the schedule is what keeps them
  apart.
- `roadmap.md`: record what M10 inherits — criticality is a *different question* with a different
  answer, and the replay guard is the thing that lets any of M10's cross-run comparisons be
  trusted.
- `roadmap.md`: put the variance decomposition in the icebox with decision 10's reason, so that
  "the shares do not add up" has a recorded answer rather than being rediscovered as a defect.
- `product-concept.md`: *Variance contribution* stops being design intent, with the two things
  the section did not know it was claiming — that the shares are not a partition, and that two of
  the rows are not tasks.
- `CLAUDE.md`: a contribution is a ranking and never a share; three kinds of row and why the two
  non-items are what make it honest; a replay must prove itself before it explains anything;
  nothing is stored and that is what makes it work on the past; watching the engine takes no draw
  and moves no version.

### As built — where it differs from the above

**The bullets above were written now, and that is the departure worth naming first.** The plan
shipped with an *At a glance* table promising five steps and a body holding four — the close-out
section simply was not written, and nothing noticed until it was time to do it. Every other
milestone's step 5 existed before its step 1 did. What that cost is small (the work was obvious
from what M6 turned out to be) and what it risks is not: a close-out written after the fact is a
close-out that can only record what was done, never check it against what was promised. The M5
plan is the counter-example — its step 5 listed four documents in advance, and doing it turned up
three more places that had quietly stopped being true.

**`CLAUDE.md` got eight bullets rather than five**, because three facts a reader needs in the same
breath were not on the list: that a source with no variance is exactly zero and that this is the
ordinary case rather than an edge one; that titles come off the live plan because the snapshot
never held one; and the measurement itself — 489 ms against 491 ms — since "the accumulator is
free" is the fact that makes "on demand" a courtesy rather than a workaround.

**Two things had quietly stopped being true and were not on the list.** `CLAUDE.md`'s note on
`forecast_runs` said a replay is *what M6 gets its contribution from*, written when M6 was a
future tense; it now also says M6 turned the same comparison into a runtime guard. And both
status headers claimed Tier 2 as design intent — it is half built, and saying so is the whole
point of a status line.

**The variance decomposition went to the icebox with its question attached.** Decision 10 turned
Sobol down for costing a re-run per source, but "the shares do not add up" will be raised again by
whoever first wants a pie chart — so the icebox entry names the shape of the question that would
justify it: *how much of the spread is the team factor on its own, with the items held still.*
Recorded as a deferred alternative rather than left to be rediscovered as a defect.

**M10's criticality bullet gained the distinction rather than a cross-reference.** It would have
been enough to write "not the same as M6"; what it says instead is why — an item that never varies
can decide the finish in every single run and widen the band by nothing at all. That sentence is
the whole reason the two features cannot share a measurement, and it belongs where somebody will
be tempted to merge them.

### The review pass — what a read of the whole milestone changed

**One real bug, and it was in the slowest thing on the panel.** Loading a breakdown had no
unmount guard: it was written as a handler where every other request in the file is an effect
with a `cancelled` flag, and it is the request most likely to outlive the panel — half a second
against a few milliseconds for everything else. Somebody navigating away mid-replay would have
had a ranking painted onto a component that had gone, or onto the next plan they opened. It is an
effect keyed on "which run somebody asked about" now, matching `EstimateForm`'s quality lookup
exactly, with a test that unmounts mid-flight and resolves and rejects afterwards.

**One wrong answer waiting for the server to version ahead.** `describeSource` handled the two
non-item kinds and let everything else fall through to the item branch — so a kind this build had
never heard of would have rendered as *"Work no longer in this plan"*, which is not merely
unhelpful but false. This file already keeps the rule, ten lines away, for limitation codes: *the
server is what versions ahead here, and silently showing the wrong thing is that rule failing
through the back door.* The item branch is explicit now and an unknown kind says so, tested the
way the unknown limitation already is.

**One finding recorded rather than fixed, and the reason is that half a fix is worse.**
`contributionsTo` is `@Transactional(readOnly = true)` and runs a five-hundred-millisecond
simulation inside it, holding a pooled connection for CPU work. That is exactly what the
roadmap's *Operations* bullet already describes for forecasts — and M6 widened it, because unlike
asking for a forecast this is a `GET` any reader can repeat as fast as they can click. Making
this one endpoint non-transactional would leave two shapes of the same code and fix half the
problem; the concurrency limit that bullet already names is the actual fix. The bullet now says
so.

**What was checked and found sound.** The snapshot's item order and `Contributions.ofItem`'s index
are the same order, because `toModels` walks the snapshot and the engine preserves it. A share is
a square so it cannot be negative, and the bar clamps at 100% for the perfectly-correlated case.
The run-shaped `observed` cannot overrun the engine's durations array, since the engine passes its
own plan length. And the suite was run three times end to end after the fixes: 371 passing each
time, with one transient failure in an unrelated file that passed in isolation and did not recur —
the second time that has happened during a review pass on this machine, which is worth watching
even though nothing in either milestone touches those files.

---

## Migrations

**None, and that is the point of decision 1.** Every other milestone since M2 has added a column;
this one reads what M3a already stored and adds nothing. Anybody about to write
`V16__forecast_contributions.sql` should read decision 1 first — the column buys nothing that a
replay does not, and it costs every run made before it the ability to be explained.

---

## Sequencing and risk

**The risk in M6 is not the arithmetic.** Contribution has a closed form in the degenerate case,
which makes step 1 checkable against numbers that exist outside this codebase — the same footing
M3a stood on. What has no oracle is the *interpretation*, and the two ways that goes wrong are
both decisions above.

**The one that will actually go wrong** is decision 3 turning into percentages. A ranked list of
r² values looks exactly like a pie chart wanting to be drawn, and the first person to add a `%` to
it will be right that the numbers are shares of something and wrong about what. They sum to one
only in the case this product deliberately does not model. The defence is the wording, the bar
instead of a number, the line of caveat beside it, and this paragraph.

**The second is decision 2 being removed as ceremony.** Replaying and comparing looks like
belt-and-braces on a code path that has never failed, and it will read as six wasted comparisons
in a method that has just done ten thousand simulations. It is the only thing standing between a
future version bump and a confident ranking of a plan under a model that never forecast it.

**Two things that will look like bugs and are not.**

- **A huge task with a huge range contributing almost nothing.** That is the entire point: it is
  off the path that decides the finish, and the summing model that would have ranked it first is
  the one the roadmap says to stop using.
- **The top of the list being something that is not a task.** If the team factor or the unlisted
  work dominates, the honest answer to "what should I spike" is "nothing on this list" — and
  saying so is worth more than ranking the tasks anyway.

**What this milestone must not absorb.** Criticality is M10's and measures something else;
proposing what to cut is M7's and needs a search this does not have; correlation groups are the
icebox's and need a grouping somebody has to define. The line to hold is that M6 attributes the
spread of one stored run and stops.
