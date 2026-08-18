# M10 — Communicating to people who do not know what P90 means: implementation plan

> **Proposal, 2026-08-18. Step 1 is built.** Six steps, **no migration expected**, and no change to anything the
> engine samples — `Engine.VERSION` does not move. Each step gains its
> `### As built — where it differs from the above` in the same change as its code, not at the end.
>
> **Scope, and it is smaller than `roadmap.md`'s bullets.** That section lists five things and
> **two of them are cut here** — the probabilistic critical path and merge-bias-as-a-number
> (decision 1). What is left is the milestone's own stated purpose: a plan's forecast said in a
> sentence anybody can read, a picture of it, and the two questions somebody asks second — *has
> this been getting worse?* and *why did the date move?* The last of those is the icebox's
> **forecast movement decomposition**, promoted into the milestone because it is the same
> machinery as the detector and because the icebox calls it "the feature I would most want as a
> user".
>
> **How to read this.** Decisions first. The one that decides whether the detector reports
> anything real is **decision 5**, and the measurement below is why: a rule about the *direction*
> a date moves fires on 86% of plans re-forecast weekly for six months with no slide at all. The
> one that decides whether the decomposition is honest is **decision 6**, which is M6's "these do
> not add up" arriving for the third time in a form where they finally must. **Decision 2
> corrects a sentence in `roadmap.md`.**
>
> **This is the first milestone whose output is prose and pictures rather than numbers**, and that
> changes what can go wrong. Every milestone so far could be wrong in a way a test catches. A
> sentence that is technically accurate and read as something else fails no test at all — and the
> roadmap's own example sentence is one of those, which is decision 2.
>
> **And it brings the first chart.** `roadmap.md` records under *Reworking the interface* that the
> rework is worth doing **before** M10 spends effort on charts, because "a confidence cone, a
> burn-up and a criticality heatmap are exactly the things that get built twice if the second
> build is a rework". That has not happened, and this plan does not wait for it — decision 9 says
> what is built so that a rework restyles rather than rebuilds, and what survives one either way.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | One sentence anybody can read ✅ *done* | M4 |
| 2 | When two forecasts may be compared | M3, M4 |
| 3 | Why the date moved | 2, M6's replay |
| 4 | Whether it keeps moving out | 2 |
| 5 | The burn-up, and the first chart in this product | M9 |
| 6 | Close out | 1–5 |

**No migration is expected.** Everything here is derived: `forecast_runs` already stores each run's
inputs, its seed, its calendar and its engine version, and `work_items.completed_on` already
carries the burn-up's past. That is M6's decision 1 spent a fourth time, and this is the milestone
where it pays best — a decomposition computed at read time explains every pair of runs this
product has ever stored, where a stored one would explain only the pairs made after it existed.

---

## The measurement this plan is built on

**Two runs, and together they invert the obvious design of the detector.**

### How far a date moves for no reason at all

The same plan, re-forecast with nothing changed but the seed. Twelve items in a chain — M3a's
oracle shape, so the finish is a sum of log-normals — and the 80th percentile taken across thirty
seeds:

| Samples | P80 | Its spread across seeds | In working days at six hours |
|---|---|---|---|
| 2,000 | 126.0 h | 1.6 h | 0.3 |
| **10,000** | **126.1 h** | **1.1 h** | **0.2** |
| 50,000 | 126.0 h | 0.6 h | 0.1 |

**At the sample count this product uses, the answer moves by a fifth of a working day** — and the
date is a whole day, arrived at by `ceil`. So an unchanged plan re-forecast twice gives *the same
date*, essentially always. **The detector therefore needs no noise floor**, which is the opposite
of what M7 found for cuts and is worth stating for that reason: there the noise was most of the
signal, and here there is none.

### And why the obvious rule is still useless

If the sampler contributes nothing, then a date that moves has moved for a real reason — a task
added, an estimate revised, a day of progress. **But real churn has no direction.** A plan that is
not slipping still moves out one week and in the next, and a rule about direction alone then reads
a coin:

| Re-forecasts | "Out three times running" fires | Four | Five |
|---|---|---|---|
| 8 (two months) | 37% | 16% | 6% |
| 13 (a quarter) | 58% | 30% | 14% |
| 26 (six months) | **86%** | 57% | 31% |
| 52 (a year) | **98%** | 83% | 56% |

**A team re-forecasting weekly for six months has an 86% chance of being told their plan is
sliding when it is not**, and no run length fixes it: five in a row still fires on more than half
of them within a year. **Direction cannot carry this feature at any threshold.** What can is
*magnitude*, and the only honest yardstick for magnitude is the plan's own band — three days of
drift on a three-week band is nothing, and three days on a two-day band is the whole plan. That is
decision 5.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What M10 is for | **The reader who does not know what P90 means.** Criticality and merge bias serve the one who does, and leave. |
| 2 | What the sentence says | **One-sided, not two.** `roadmap.md`'s own example is a two-sided interval and everything in this product is one-sided. |
| 3 | What makes two runs comparable | **The calendar, the engine version and the five assumptions.** Anything else compares two different questions. |
| 4 | What happens when they are not | **A term, not a refusal** — "six of those eight days were you halving the capacity" is the useful answer. |
| 5 | What the detector measures | **Cumulative drift against the band's own width**, never the direction of the last few runs. |
| 6 | Whether the terms add up | **Yes, by construction** — computed cumulatively, one change at a time, each re-run. |
| 7 | The order they are applied in | **A stated rule with a name**, because the order decides the attribution. |
| 8 | Where the progress term comes from | **The log `V16` added**, read rather than inferred from a snapshot diff. |
| 9 | The first chart | **The text equivalent is the feature and the drawing is the enhancement** — which is also what survives the interface rework. |
| 10 | Where the cone comes from | **M9's bootstrap, extended to a trajectory.** The engine forecasts effort and has no notion of items over time. |
| 11 | What is stored | **Nothing.** |
| 12 | What M10 must not become | **Not a report builder, not a dashboard, not a second forecast.** |

### Decision 1 — Criticality and merge bias leave this milestone

`roadmap.md` lists five bullets. Two of them are cut and put in the icebox: **the probabilistic
critical path** and **merge bias surfaced as a number**.

**The milestone's own title is the argument.** It is for the reader who does not know what P90
means. A criticality index — *this path drives completion in 40% of runs* — is strictly more
statistics than the band it sits beside, and merge bias as a figure is a correction to a number
that reader has not yet understood. Both are for somebody who already reads the band fluently, and
putting them here would make the milestone about two audiences at once.

**They are also not communication work at all.** Every other bullet is a presentation of something
already computed and stored; these two need `Schedule` to start reporting which items were on the
deciding path in each run, which is a change to what the engine observes, a new accumulator, and an
oracle of its own. That is modelling depth wearing a communication label, and it is the shape of
thing M3b was.

They go beside *a proper variance decomposition* in the icebox, which was turned down there for a
neighbouring reason. **The distinction `roadmap.md` draws between criticality and M6's ranking is
worth keeping verbatim when they move**, because it is the thing anybody building them will get
wrong: an item that never varies can decide the finish in every run and widen the band by nothing.

### Decision 2 — The sentence is one-sided, and the roadmap's example is not

`roadmap.md` offers *"85% likely to finish between 12 October and 20 November."* That is a
**two-sided interval**, and every date this product has ever published is one-sided: `p80Date` is
the day by which four runs in five had finished, and the confidence control reads exactly that.

**The two forms are different claims and the two-sided one is the one that gets misread.** "Between
12 October and 20 November" invites *so it will not be before the 12th?* — a question nobody
manages against, about the end of the distribution the model is worst at and the reader cares least
about. It also quietly halves the confidence a reader thinks they have at the far end. What
somebody commits to is a date they will not be past, so the sentence is:

> **There is an 85% chance this is finished by 20 November.**

with the confidence control the only thing that changes it. **The band in hours stays**, for M4's
reason — remove it and nothing on screen came out of the engine — but the sentence goes first and
in the largest type on the panel, because the whole milestone is that the sentence is what gets
pasted into somebody's email.

| Rejected | Why |
|---|---|
| "Between X and Y at 85%" | Two-sided, and read as a promise about the early end. |
| "Most likely 3 November" | A mode with no confidence attached is the single-point estimate this product exists to replace. |
| "3 November ± 2 weeks" | Symmetric, and the distribution is not — the right tail is the part worth managing. |

### Decision 3 — Two runs are comparable only if four things match

A pair of runs may be compared when they share: the **engine version**, the **calendar rule**, the
**working day**, and the **five assumptions** (capacity, team factor, both scope-growth ends, and
the start date). Anything else and the two answered different questions.

This is `roadmap.md`'s own warning made into a rule — "a comparison across runs made under
different calendars, or across an `Engine.VERSION` bump, is the way this feature reports a slide
that never happened" — and M4 is what made it checkable, since every run stores the calendar it was
read under. It is also the same instinct as M6's `forecast_replay_mismatch`, which refuses to
explain a run the engine no longer reproduces: **a comparison between incomparable things is not a
rougher comparison, it is an exact comparison of something nobody asked about.**

The start date is in that list and is the one that will look wrong. A run started on 1 September and
one started on 1 October are not the same question even with identical work: the second finishes a
month later for a reason that is not the plan. That is the *time simply passing* term, and
decision 4 is where it goes.

### Decision 4 — An assumption that changed is a term, not a refusal

M6 refuses to explain a run it cannot reproduce. This does the opposite: where two runs differ in
their assumptions, the difference is **measured and reported as its own line** rather than
disqualifying the pair.

**Because a refusal would fire constantly and teach nothing.** People adjust capacity, they push the
start date, they raise the growth range after a bad month — and the single most useful thing this
feature can say is *six of those eight days were you halving the capacity*. Refusing that pair
would leave somebody staring at two dates a fortnight apart with no account of either.

The line between this and M6's refusal is worth stating, because they look contradictory. M6
refuses when **the model** cannot reproduce the run — there is nothing to compare *with*. This
reports when **the question** changed, which is exactly the thing worth reporting. An
`Engine.VERSION` difference is the first kind and still refuses; everything else is the second.

### Decision 5 — Cumulative drift against the band's own width

The detector does not look at the direction of the last few runs. It asks how far the chosen
percentile's date has moved **since the oldest comparable run in a window**, and compares that
against the width of the current band in days.

The measurement above is the whole argument. Sampling contributes nothing, so every movement is
real — and real movement in a plan that is not slipping is directionless, so "out three times
running" fires on 86% of plans re-forecast weekly for half a year. **No run length rescues it**;
five in a row still fires on most plans within a year.

Magnitude needs a yardstick and the plan supplies its own: the distance between the P10 and P90
dates. Three days of drift on a three-week band is inside the noise the forecast already admits to;
three days on a two-day band is the plan coming apart. A fraction of that width, **stated once
beside the arithmetic** in the way `EstimateQuality.TIGHT_BAND` is, is the threshold — and the
browser is told the flag rather than the number.

| Rejected | Why |
|---|---|
| *k* successive increases | Measured: 86% false positives over six months, and no *k* fixes it. |
| A fixed number of days | Means opposite things on a two-week plan and a two-year one. |
| A trend line through the dates | More machinery, same question, and it still needs a threshold to fire on. |

### Decision 6 — The terms add up, because they are computed cumulatively

*"Out 8 days: +5 new scope, +4 re-estimates, −1 progress."* That sentence claims its terms sum to
its total, and **the obvious implementation cannot deliver it**: re-running the plan with one change
at a time and reporting each difference separately gives terms that do not add, because a
simulation is not linear in its inputs. Two changes that both load the same bottleneck overlap
exactly as M7's two cuts on one chain do.

M6 and M7 both met this and both answered *do not add them* — a ranking, and a measured cumulative
list. **Here that answer is not available**, because the sentence is the feature: an account of a
movement that does not account for the movement is not an account.

So the terms are computed the way M7's cut *plan* is: **applied one at a time, in a stated order,
re-running after each, each term being the difference that step made with every earlier step
already applied.** They then sum to the total by construction, and the last step lands exactly on
the newer run's own date — which is the assertion that says the whole thing is honest.

**The cost is that the order matters**, and that is decision 7 rather than something to hide.

### Decision 7 — The order is a stated rule with a name

`Movement.RULE`, a name beside the arithmetic, for `Schedule.PRIORITY_RULE`'s reason: two defensible
orders attribute the same eight days differently, so a decomposition read under one must never be
silently compared with one read under another.

The order, and why:

1. **Progress** — what already happened. It is not a decision anybody took between the two runs, so
   it is the baseline the rest is measured against rather than a change competing with them.
2. **Estimates revised** — a second opinion about work that was already listed.
3. **Scope added and removed** — work that was not there before.
4. **Assumptions** — capacity, the team factor, the growth range: what somebody changed about the
   question rather than about the plan.
5. **The start date moving** — time simply passing, last because it is the one term that is nobody's
   doing and shifts everything already counted.

**Reordering this is not a refactor.** A reader who is told scope cost them five days and estimates
four will act on it, and swapping steps 2 and 3 can move days between those two lines.

### Decision 8 — The progress term is read, not inferred

`roadmap.md` records under *Progress is written over* that M10's decomposition "carries a '−1
progress' term it can currently only *infer* by diffing two forecast snapshots, rather than reading
what somebody reported and when". **`V16` fixed that and nothing has read it since.** This is the
reader it was built for.

The difference is not academic. A snapshot diff says an item's status changed between two runs; the
log says *who* said so and *when the server heard it*, so a decomposition can attribute progress to
the week it was reported rather than to the gap between two forecasts. It is also the only way to
tell "three tasks finished" from "three tasks were finished a month ago and somebody only recorded
them on Tuesday" — and those move a date for entirely different reasons.

### Decision 9 — The text equivalent is the feature; the drawing is the enhancement

This milestone brings the first chart in the product, and *Cross-cutting* says the accessibility bar
set by the auth forms must survive it: "a confidence cone needs a non-visual equivalent".

**Inverted, deliberately.** The equivalent is not a fallback bolted to a picture — it is built
first, it is what the tests assert, and the drawing is added over it. Three reasons, and only the
first is about accessibility:

- A cone described in words has to be *understood* before it can be described, which is a better
  filter on whether the picture is worth drawing than drawing it is.
- `roadmap.md` warns that charts built before the interface rework get built twice. **A text
  equivalent is not restyled by a rework**, so the half that survives is the half built first.
- It is the same rule the rest of this product already keeps: the percentile table stays as the text
  equivalent of any curve, and a band never appears without its caveats.

The drawing itself is **inline SVG with no chart library**, following the bars M6 and M8 already
render — a dependency whose styling a rework would have to fight is exactly what that warning is
about.

### Decision 10 — The cone is M9's bootstrap, not the engine's band

A burn-up is items completed against time, and its cone is *how many items will be done by each
future week*. **The engine cannot answer that.** It forecasts effort and reports when a plan
finishes; it has no notion of an item count over time, and inventing one from the finish
distribution would mean assuming a shape for the trajectory that nothing measured.

M9's bootstrap already draws a week at a time and accumulates until the backlog is covered — the
trajectory is *the thing it walks over* and is currently thrown away in favour of the week it
stopped on. Recording the running total per week gives every percentile of the cone from the same
sampler, the same history and the same seed as the date beside it.

**That is why M9 had to come first**, and it is worth noting that neither milestone's plan
anticipated it: M9 was written as a cross-check and turns out to be the only thing in the product
that can draw the future half of a burn-up.

### Decision 11 — Nothing is stored

No table, no column, no cached decomposition. `forecast_runs` holds every run's inputs, seed,
calendar and version; `work_items.completed_on` and `work_item_progress` hold the rest. Everything
here is a read.

M6's decision 1, a fourth time — and the strongest instance yet: a decomposition computed at read
time explains **every pair of runs this product has ever stored**, including the ones made a year
before this milestone existed. A stored one would explain only the pairs made after it, which is the
same trade M8's calibration took and the same one M7 took for cuts.

### Decision 12 — What M10 must not become

- **Not a report builder.** One sentence, one chart, one history, one decomposition. A page where
  somebody chooses what to show is a different product.
- **Not a dashboard.** Every one of these belongs beside the plan it is about; a screen that
  aggregates plans is a portfolio feature and needs M11's ideas about resources first.
- **Not a third forecast.** The burn-up draws M9's answer and the sentence says M3's. Nothing here
  computes a new number about the future.
- **Not the export.** A shareable read-only link and a PNG for slides are both in the icebox, both
  are what makes this milestone reach people, and both are separately arguable. The line to hold is
  that M10 makes the answer legible and does not distribute it.

---

## Step 1 — One sentence anybody can read ✅ *done*

**Goal.** The forecast leads with a sentence, and the sentence is one-sided.

- **Frontend only.** Every number it needs is already in the response: the five dates, the
  confidence the reader has chosen, and the calendar that produced them.
- **`There is an 85% chance this is finished by 20 November.`** In the largest type on the panel,
  above the band rather than under it, because the sentence is what gets pasted into an email and
  the hours are what justify it.
- **The confidence control keeps its place and its meaning** — it already reads percentiles
  already in the response and sends no request, and now it rewrites the sentence as well as the
  date. That is M4's property extended rather than a second control.
- **A run with no calendar says so in the same voice.** `describeDate` already distinguishes "no
  calendar" from "a rule this version cannot resolve"; both become sentences rather than
  explanations of a missing field.
- **Nothing else on the panel moves.** The band, the coverage line, the assumptions and the
  limitations stay exactly where they are — a sentence that replaced them would be the
  single-number output this product exists to replace, wearing better clothes.

**Tests.** The sentence renders at each of the three confidences and says a different date at
each. It is one-sided: no case anywhere produces "between … and …", asserted as the absence of a
second date in that sentence. A run made before M4 gets the no-calendar sentence and no invented
day. The band and the limitations are still on screen beside it, because the failure mode of this
step is quietly becoming the only thing shown.

**Done when** somebody can copy one line into an email without editing it.

### As built — where it differs from the above

**Most of this step turned out to be built already, and saying so is more useful than pretending
otherwise.** M4 shipped the headline sentence one-sided (`80% likely to be finished by 25 August`),
above the band, at 22px against the band's 18 — so three of the five bullets describe the screen as
it stood. What was left was the wording and one thing the bullets did not name.

**The step's own *Done when* is what found the real gap.** "Somebody can copy one line into an
email without editing it" — and they could not, because the sentence never said *what* was being
forecast. A confidence and a day describe nothing away from the screen they are already on. So
`ForecastPanel` now takes the plan's name, and the line reads **"There is a 80% chance that Q3
platform work will be finished by 25 August."** That is the only structural change in the step, and
it is a prop rather than the project: a component handed the whole row would soon read more of it
than one sentence needs.

**The rewording is smaller than it looks and is worth stating honestly.** "80% likely to be
finished by X" was already plain and already one-sided. What changed is that it no longer opens
with a bare percentage — which reads as a statistic before it reads as a sentence — and that it now
has a subject. Anybody expecting decision 2 to have overturned something will find it did not:
**the two-sided form `roadmap.md` proposes was never what this product shipped**, and the decision's
work is to stop it being introduced rather than to remove it.

**Two properties were asserted rather than built.** `saysOneDateAndNeverAWindow` pins the absence —
no "between", exactly one year in the sentence at every confidence — because that is the thing
decision 2 exists to prevent and nothing else would fail if it crept in. And the plan's name is
tested through a *different* plan from the fixture, since a hard-coded name would have passed
against the usual one.

**Nothing else moved**, which the bullets ask for and the existing tests already guard: the band at
every confidence, both no-calendar sentences, and the limitations heading all had cases already.
The type size was left alone — 22px is already the largest content on the panel, and changing it to
satisfy a bullet that is already true is churn.

**Counts.** 2 new cases and 5 reworded; 440 frontend tests pass, at 100% of statements, branches,
functions and lines. No backend change.

---

## Step 2 — When two forecasts may be compared

**Goal.** The rule both of the next two steps rest on, in one place.

- **`Comparable` in `forecast.model`** — pure: given two runs' identifying facts, are they
  answering the same question, and if not, which of the four things differ?
  - The **engine version** differing is not a difference to report but a **refusal**: there is
    nothing to compare with, which is M6's `forecast_replay_mismatch` argument rather than
    decision 4's.
  - The **calendar rule** and the **working day** differing means the dates are not on one scale.
    Reported, and the comparison is made in *hours* rather than days so it still says something.
  - The **five assumptions** differing is decision 4's term, carried through to steps 3 and 4.
- **`ForecastService.comparable(callerId, tenantId, projectId)`** — the plan's runs in order, each
  paired with what it shares with the one before. One query; `findAllInProject` already returns
  them newest first.

**Tests.** Two runs identical in all four are comparable and report nothing. Each of the four
differing on its own is reported on its own, and the assertions are separate, because one
`equals` over a bag of fields would pass whichever of them was actually being checked. An engine
version bump refuses where a capacity change does not — the pair, since the difference between
those two answers is the whole decision. A plan with one run has nothing to compare and says so
rather than dividing by zero.

**Done when** a run made under a different working day cannot be silently subtracted from one made
under this one.

---

## Step 3 — Why the date moved

**Goal.** "Out 8 days: +5 new scope, +4 re-estimates, −1 progress" — and the terms sum to eight.

- **`Movement` in `forecast.model`** — the ordered steps of decision 7, as a named rule
  (`Movement.RULE`) and a list of what each step changes.
- **`ForecastService.movementBetween(callerId, tenantId, olderRunId, newerRunId)`** — replays the
  older run's stored inputs, then applies one class of change at a time, re-running after each and
  reading the chosen percentile's date. Each term is the difference that step made **with every
  earlier step already applied**, so they sum by construction (decision 6).
  - The replay is M6's, through the same method, because two ways of re-running one stored forecast
    would eventually be one right way and one that had drifted.
  - **The last step must land exactly on the newer run's own stored date.** That is the assertion
    that says the decomposition is a decomposition and not five plausible numbers.
  - The **progress** term reads `work_item_progress` (decision 8) rather than inferring from the
    status in the two snapshots.
- **Cost.** One re-run per step, five steps, on top of the two runs themselves — about the shape of
  M7's cut search and well inside its budget. Stated in the response the way M7 states its
  simulation count, so the price is visible rather than surprising.

**Tests.** **The oracle is that they add up**: for any pair, the terms sum to the difference between
the two stored dates, asserted to the day. A pair differing in exactly one way attributes everything
to that one term and zero to the rest — five cases, one per step. Reordering the steps changes the
attribution and not the total, asserted, because that is what decision 7 is warning about and it
should be visible rather than argued. A pair whose engine versions differ refuses; one whose
capacity differs reports an assumptions term. The progress term reads the log: an item finished
weeks ago and only *recorded* between the two runs is attributed to progress, which a snapshot diff
alone cannot distinguish from work that happened in between.

**Done when** the five numbers explain the whole of the movement and not most of it.

---

## Step 4 — Whether it keeps moving out

**Goal.** A plan that is sliding says so, and a plan that is merely churning does not.

- **`Drift` in `forecast.model`** — pure: given a plan's comparable runs in order and the band's
  current width in days, how far has the chosen percentile drifted, and is that worth saying?
  - **Cumulative against the oldest comparable run in the window**, never the direction of the last
    few (decision 5, and the measurement).
  - **Scaled by the band's own width**, so the threshold means the same thing on a two-week plan and
    a two-year one.
  - `Drift.WORTH_SAYING`, stated once beside the arithmetic, with the measurement in its javadoc —
    `EstimateQuality.TIGHT_BAND`'s shape, and the browser is told the flag rather than the number.
  - **Runs that are not comparable break the window rather than being skipped.** A plan re-forecast
    under a new capacity starts a new window, because drift measured across that boundary is
    decision 3's slide that never happened.
- Reported by the same endpoint that lists a plan's forecasts, so a screen showing the history has
  the verdict without a second request.

**Tests.** The measurement, made into cases: a plan whose date walks up and down and ends where it
started does **not** fire however many consecutive increases it contains — with a fixture built
from the run-of-three pattern that a direction rule would flag. A plan drifting steadily out by a
day a week fires once the drift passes the bar and not before. The same absolute drift fires on a
narrow band and not on a wide one, which is the whole of decision 5. A capacity change mid-history
starts a new window rather than contributing drift. One run, and two runs, each answer rather than
dividing by zero.

**Done when** the 86% false positive rate is a test rather than a paragraph.

---

## Step 5 — The burn-up, and the first chart in this product

**Goal.** What has been delivered and what is left, with the future drawn as a cone — and readable
without seeing it.

- **`Throughput.project` gains a trajectory** (decision 10): the running total per week across
  every run, from which each percentile of the cone is read. Same sampler, same history, same seed
  as the date M9 already publishes, so the picture and the number cannot disagree.
  - It changes no draw and takes no extra randomness — the accumulation is over numbers the loop
    already produces, which is M6's `RunObserver` argument in a smaller place.
- **The past comes from M9's history**, which already reads `completed_on` per week.
- **The text equivalent first** (decision 9): a short table of what was delivered by week and what
  the cone says for a handful of future weeks, and a sentence naming the shape — *"delivered 104 of
  144; on this history the last is done between 12 October and 30 November"*. It is what the tests
  assert.
- **Then the drawing**, inline SVG, no library, following M6's and M8's bars. A line for what has
  been delivered and a band behind it for the cone, `aria-hidden` with the table as its label,
  because a picture and its equivalent saying the same thing twice to a screen reader is worse than
  either.

**Tests.** The trajectory sums correctly: at the final week of a constant-throughput history the
cone's every percentile is the whole backlog, and at week zero it is nothing. The cone narrows —
its width at a late week is smaller than at an early one, which is the property the picture claims
and the only one a reader takes from its shape. The table renders every week it says it does. The
SVG is absent from the accessibility tree and the table is not. A plan with too little history draws
its past and no cone, saying which — M9's three states arriving here unchanged.

**Done when** somebody who cannot see the chart is told the same thing.

---

## Step 6 — Close out

**Goal.** The record matches what was built.

- Each step's `### As built` is written in the change that built it. This step is the whole-milestone
  read.
- `roadmap.md`: M10 marked done with its own *As built*; **the two cut bullets moved into the
  icebox with their reasoning intact** (decision 1), and the two-sided example sentence corrected in
  place (decision 2) rather than only here — the wrong version is the quotable one, which is the
  lesson M9's close-out learnt. *What is next* moves to M11.
- `CLAUDE.md`: a section on the sentence, the comparability rule, the ordered decomposition and the
  chart's text-first contract.
- `product-concept.md`: whatever it says about communicating a forecast, answered.
- **The review pass**, as in M5 through M9: read the milestone end to end and record what that read
  changed. Every one of the last five found something, and three of them found the milestone
  contradicting one of its own decisions.

**Done when** the next reader can tell what M10 decided without reading its code.

---

## Migrations

**None expected**, and this is the fourth milestone in a row without one. Every input is already
stored and already dated: `forecast_runs` keeps each run's inputs, seed, calendar, engine version
and assumptions; `work_items.completed_on` carries the burn-up's past; `work_item_progress` carries
the progress term. Anybody about to write `V17__forecast_movement.sql` should read decision 11
first — a stored decomposition explains only the pairs made after it exists, where a derived one
explains every pair this product has ever held.

---

## Sequencing and risk

**The risk in M10 is that nothing it produces can be wrong in a way a test catches.** Every
milestone so far had an oracle: a closed form, a byte-identical replay, an exact decile set, a
history anybody can add up. A sentence that is accurate and read as something else fails nothing —
and `roadmap.md`'s own example sentence is one of those, which is why decision 2 is the first
substantive decision in the plan rather than a wording note near the end.

**The one that will actually go wrong** is the decomposition's terms drifting apart from their
total. The cumulative construction makes them sum *today*; the tempting simplification is to compute
each term independently, which parallelises, reads more cleanly, and produces five numbers that no
longer account for the movement they claim to explain. The defence is the last-step assertion in
step 3 — the final term must land exactly on the newer run's stored date — plus decision 6 and this
paragraph.

**The second is the detector being simplified back into a direction rule**, because "three in a row"
is one line and the cumulative-drift version is not. The measurement is the answer and it is in the
plan: 86% of plans re-forecast weekly for six months, with no slide at all.

**The third is the chart arriving before the interface rework and being built twice.** That is a
real cost and it is accepted rather than argued away — what decision 9 buys is that the half worth
keeping, the text equivalent, is not what a rework touches.

**Two things that will look like bugs and are not.**

- **A decomposition where the assumptions term is the largest.** Somebody halved the capacity and
  the plan moved a fortnight; that is the honest account, and it is the single most useful line this
  feature produces.
- **A plan that is visibly slipping and is not flagged.** A wide band admits to a lot of movement
  already, and a plan whose P10 and P90 dates are two months apart has not been surprised by a week.
  The flag is about drift the plan did not already say was possible.

**What this milestone must not absorb.** Criticality and merge bias have left it (decision 1).
Resources are M11's. The shareable link, the PNG export and the scheduled digest are the icebox's and
are what *distribute* this rather than what makes it legible. The line to hold is that M10 says what
the product already knows, in a form somebody outside the team can read, and stops.
