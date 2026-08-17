# M7 — Inverse queries: implementation plan

> **Built, 2026-08-17.** All six steps are done and each carries its own *As built* section.
> `Engine.VERSION` is still 2, no migration was added, and nothing a cuts request does is written
> down. With this, **Tier 2 is complete.**
>
> **Scope.** `roadmap.md` M7: run the question backwards. Not *when will this finish* but **what
> do I cut to hit 1 November at 85%**, with each candidate ranked by the confidence it actually
> buys. Excluded: adding people rather than removing work (M11 — same machinery, a different
> lever), the plain-language sentences and the burn-up (M10), anything that *performs* a cut
> (this proposes; somebody else decides), and any change to what the engine samples — **nothing
> here changes it, and `Engine.VERSION` does not move.**
>
> **How to read this.** Decisions first. The one that decides whether this milestone reports
> signal or noise is decision 2 — *a cut is a draw discarded, not an item removed* — and the
> measurement below is why. The one that decides whether it is honest is decision 7, which is
> M6's "these do not add up" arriving again in a form that is far more tempting to add up.
>
> **Why this is the last of Tier 2, and what it changes.** Everything so far answers a question
> somebody asks *about* a plan. This is the first that answers a question asked *of* one — it
> turns a reporting surface into something opened during planning, which is `product-concept.md`'s
> own phrase for it. It is also the first feature whose output is a **recommendation**, and that
> is a different kind of thing to be wrong about: a band that is too tight is a bad forecast, and
> a cut list that is wrong sends somebody to delete work for nothing.
>
> **What it costs, and why that shapes everything.** Every candidate is a whole simulation. There
> is no closed form, no shortcut, and no way to evaluate a cut except to run the plan without it —
> which is precisely why `roadmap.md` says naive "rank by size" suggestions will be wrong. The
> budget is the design constraint, and decisions 1, 9 and 10 are all about spending it honestly.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | Reading a date backwards, and counting the runs that beat it ✅ *done* | M4, M6 |
| 2 | A cut that moves no other draw ✅ *done* | M3 |
| 3 | What one cut buys ✅ *done* | 1, 2 |
| 4 | The shortest list that gets there ✅ *done* | 3 |
| 5 | On screen ✅ *done* | 4 |
| 6 | Close out ✅ *done* | 1–5 |

**M7 adds no columns and no migration**, for M6's reason: everything is evaluated against a
stored run, replayed from its own seed. It also adds no engine behaviour — one flag on
`ItemModel`, which the engine already reads, and M6's `RunObserver`, which already exists.

---

## The measurement this plan is built on

The obvious implementation forecasts the plan, forecasts it again without an item, and reports the
difference. That was measured against the thing it has to beat: **how much does the answer move
when nothing changes at all?** A twelve-item plan in three chains, capacity 2, with a team factor
and scope growth, against a fixed hours budget — thirty seeds:

| | 2,000 runs | 10,000 runs |
|---|---|---|
| Baseline confidence | 87.17% | 87.20% |
| **…its spread across seeds** | **3.60 pp** | **1.23 pp** |
| One cut, **paired** — same seed both sides | +4.63 pp *(spread 1.90)* | +4.67 pp *(spread 0.75)* |
| One cut, **unpaired** — different seeds | +4.75 pp *(spread 4.20)* | +4.62 pp *(spread 1.84)* |

**Three things follow, and they are the spine of this plan.**

**A cut worth having buys about five points, and re-running the same plan moves the answer by
three.** At the sample count somebody would reach for to make this affordable, the noise is most
of the signal. Reporting "this takes you from 87% to 92%" when asking again would have said 85%
is reporting a coin flip as a recommendation.

**Pairing the two sides halves it, and the ranking is what it rescues.** Same seed both sides:
1.84 pp of spread becomes 0.75 pp at ten thousand runs. Two candidates whose true effects differ
by a point and a half can be ordered when the comparison is paired and cannot be when it is not —
and the *order* is the entire output of this milestone.

**Even paired, three quarters of a point is not nothing.** Two candidates within about a point of
each other are not really ordered, and nothing on screen may imply otherwise. That is decision 8's
job.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Where candidates come from | **The caller names them.** What is droppable is a judgement about value, which the server has none of. |
| 2 | What a "cut" is | **A draw taken and discarded**, so every other item gets the number it got in the baseline. |
| 3 | What else must not move | **The priority order.** A cut changes what an item costs, never where the scheduler ranks it. |
| 4 | What it is all measured against | **One stored run, replayed**, with M6's guard — the same plan, seed, capacity and assumptions. |
| 5 | Where the target date becomes hours | **The run's own calendar**, so a run made before M4 cannot answer at all. |
| 6 | Is M6's ranking the shortlist? | **No, and `roadmap.md` is wrong about this.** Contribution measures the spread; a cut buys a date. |
| 7 | Whether the numbers may be added | **Never**, and this is the milestone's own version of M6's trap. |
| 8 | The cumulative answer | **Computed, greedily, one re-evaluation at a time** — never inferred from the singles. |
| 9 | What bounds the work | **A stated number of simulations**, reported in the answer rather than hidden. |
| 10 | What M7 must not become | **Not an editor, not an optimiser, not M11.** |

### Decision 1 — The caller names the candidates

The request carries the items somebody is willing to drop. The server evaluates each and ranks
them; it proposes nothing of its own.

**Which work is droppable is a judgement about value, and the server holds none of it.** A task
worth four weeks that the regulator requires is not a candidate; a two-day nicety is. Nothing in
this schema records that, and inventing a ranking over work whose importance is unknown would be
a tool recommending that somebody delete something because it happened to be on the critical path.
`roadmap.md`'s own line is *it proposes, and somebody else decides* — this is what that means in
practice: **the person says what is negotiable, and the tool says what each is worth.**

It also bounds the cost honestly. Every candidate is a full simulation, so an implementation that
scored the whole plan would either be a heuristic shortlist (decision 6 says why that misleads) or
a request that takes four minutes.

| Rejected | Why |
|---|---|
| The server proposes candidates from the whole plan | Four minutes at 500 items, and it recommends deleting work whose value it cannot see. |
| Shortlist by M6's contribution, then evaluate | The heuristic is wrong in a specific and systematic direction — decision 6. |
| Shortlist by size | The failure `roadmap.md` names by name: the biggest item off the deciding path buys nothing. |

### Decision 2 — A cut is a draw taken and discarded

**This is the decision the measurement above exists to justify.** For the comparison to be paired
— the same random numbers on both sides — every draw the engine takes must be identical between
the baseline and the counterfactual, and only the cut item's contribution may differ.

That rules out the two obvious implementations:

- **Removing the item from the plan** shortens the loop, so every item after it gets a different
  draw. It also renumbers every edge, which is how the first attempt at the measurement above
  failed outright.
- **Emptying its estimates** looks safer and is worse, because it is silent: `ItemModel.sample`
  begins `if (weighsNothing()) return 0.0`, and that return happens **before** `random.nextInt`.
  An item made weightless takes no draws at all, so the generator runs ahead by two per run and
  every later item is sampled from a different place in the stream. Nothing would fail; the
  numbers would simply stop being comparable.

So a cut item **keeps its estimates and takes its draws**, and what it draws is worth nothing.
One flag on `ItemModel`, read at the end of `sample`. The engine is not touched: it already calls
`sample`, and a run with nothing cut behaves exactly as it does today — which is the property
step 2 asserts byte for byte, and the reason `Engine.VERSION` does not move.

### Decision 3 — The priority order does not move either

`Schedule.of` ranks items by the work waiting behind them, using `typicalEffortHours` — a property
of the *plan*, settled once, deliberately not of any draw. `Schedule`'s own doc says why: "a key
that moved with a draw would leave two forecasts of one plan ordered differently and unable to be
compared."

A cut must therefore leave `typicalEffortHours` alone. If a cut item dropped to zero typical
effort, the priority ranking would change, and the counterfactual would differ from the baseline
in **two** ways — the item's cost and the order everything else is picked up in — with no way to
tell which produced the difference. It would still be a plausible number.

The cost of holding the order fixed is that a cut item keeps a place in the queue it no longer
deserves. That costs nothing: it takes zero time, so it finishes the instant it starts.

### Decision 4 — Everything is measured against one stored run

The baseline is not a fresh forecast. It is a run that already exists, replayed from its seed with
its own capacity, sample count, calendar and both M3b parameters — which is what makes the
counterfactual a counterfactual rather than a different question.

**And the replay proves itself first**, exactly as M6's does: the six figures it produces are
compared against the six on the row, and any difference refuses the whole request with
`forecast_replay_mismatch`. A recommendation derived from a model that did not produce the run is
worse than a ranking derived from one, because somebody acts on it.

This is also what makes the answer reproducible and quotable: "at 85% by 1 November, cutting these
two gets you there" is a statement about a *named run*, and the run is still there to check.

### Decision 5 — The target date becomes hours through the run's own calendar

M4 stored `starts_on`, `working_hours_per_day` and `calendar_rule` on every run precisely so that
a date could be read back under the calendar it was made with. The inverse query needs the
conversion in the other direction: **how many hours fit between the start and the target**, which
is the count of working days times the working day.

`WorkingCalendar` gains `hoursBy(startsOn, target, hoursPerDay)`, and it must agree with
`finishOn` exactly — step 1's oracle is that a plan finishing on day *d* uses no more hours than
`hoursBy(start, d)`, and one hour more finishes later. Two functions that disagree about a
boundary would put a plan on the wrong side of a date it just met.

**A run made before there was a calendar cannot answer**, and says so rather than assuming a
working day. Same rule, same reason, same shape as the dates themselves.

### Decision 6 — M6's ranking is not the shortlist, and the roadmap is wrong about this

`roadmap.md` says: *"M6's ranking says which items the finish actually moves with, which is the
shortlist worth re-running rather than the whole plan."* **That is wrong in a specific and
systematic direction, and it is worth correcting here rather than discovering later.**

M6 ranks by contribution to the **spread** — the correlation between an item's duration and the
finish. A task that always takes exactly forty hours has a correlation of zero: it never varies,
so nothing moves with it. M6 reports it at the bottom of the list, correctly.

Cutting it removes forty hours from every single run. It is frequently the **best** thing to cut.

The two questions are different: contribution asks *what makes this uncertain*, and an inverse
query asks *what makes this long*. A shortlist drawn from the first would systematically hide the
certain-but-large work, which is exactly the work a team can most confidently plan to drop.

Since decision 1 has the caller name the candidates, no shortlist heuristic is needed at all —
which is the second reason for decision 1 and the better one.

### Decision 7 — The numbers may never be added, and here it is worse than M6

M6's shares overlapped and a reader might have summed them. Here the temptation is far stronger,
because every figure is a percentage of confidence with a plus sign in front of it, and three of
them in a list reads as arithmetic waiting to happen.

**Two cuts do not buy the sum of what each buys.** On one chain they overlap: cutting either
shortens the same path, and cutting both buys barely more than cutting one. On different chains
they may buy *less* than either alone appears to promise, because the finish is the maximum of the
branches and shortening one leaves the other deciding. Occasionally they buy more.

So: each single is labelled as **what this buys on its own**, the cumulative answer is a separate
thing that was actually computed (decision 8), and nothing on screen puts the two in a column
where they could be added. This is M6's caveat with the volume turned up.

### Decision 8 — The cumulative answer is computed, not inferred

"What do I cut to hit 1 November at 85%" is a question about a *set*, and decision 7 says the set
cannot be inferred from the singles. So it is searched for: take the best single, re-evaluate every
remaining candidate **with that one already cut**, take the best of those, and stop when the bar is
met or the candidates run out.

**Greedy, and not optimal, and that is stated rather than hidden.** The optimal subset needs
2ⁿ evaluations; greedy needs at most n + (n−1) + … and usually far fewer, because it stops at the
bar. It can miss a pair that only works together — two halves of one feature, neither of which
shortens the path alone. The answer is labelled as *a* list that gets there, not *the* shortest,
and a reader who wants a different combination names a different candidate set.

**Every step is measured, never extrapolated.** The confidence reported after cutting two things
is the confidence of a run with both cut, not the sum of two singles.

### Decision 9 — A stated simulation budget, reported in the answer

Every candidate is a whole forecast: measured at **490 ms** for five hundred items at ten thousand
runs, and single-digit milliseconds for a plan of twelve. The cost is the plan's size times the
number of evaluations, and the second is what this milestone controls.

So the request is bounded — at most **twelve candidates**, and a greedy search that stops at the
bar — and **the answer says how many simulations it did** and whether it stopped for want of
budget rather than for want of candidates. A number that was cut short must say so; the failure
mode of a search is silently reporting the best thing it happened to look at.

The sample count is the run's own, not a reduced one. The measurement above shows why: at two
thousand runs the baseline moves 3.6 points seed to seed, and a search whose steps are that noisy
would rank by luck. If the budget ever has to give, it gives on the *number of candidates*, which
is visible, rather than on the sample count, which is not.

### Decision 10 — What this is not

- **Not an editor.** Nothing here archives an item. The output is a recommendation, and acting on
  it is a separate, deliberate gesture on the plan screen — where the work is, where somebody can
  see what else it is connected to, and where archiving is already possible.
- **Not an optimiser.** Decision 8 is greedy on purpose. A tool that claimed the optimal cut set
  would be claiming to know what "optimal" means, which needs the value of the work, which
  decision 1 says the server does not have.
- **Not M11.** "What if we add a person" uses the same machinery — replay a stored run with one
  parameter changed — and it changes `capacity`, which is not a cut. When M11 lands, this becomes
  one lever of two rather than growing a second feature inside it.

---

## Step 1 — Reading a date backwards, and counting the runs that beat it ✅ *done*

**Goal.** A target date becomes a number of hours, and a forecast can say what share of its runs
came in under one.

- `WorkingCalendar.hoursBy(startsOn, target, hoursPerDay)` — the inverse of `finishOn`: working
  days from the start to the target inclusive, times the working day.
- A target before the start is zero hours rather than a negative number; a target on a weekend
  counts to the last working day before it.
- `ConfidenceBy` in `forecast.model` — a `RunObserver` that counts completions within a budget and
  reports the share. **No engine change**: M6 built this seam.

**Tests.** **The oracle is that the two functions agree**: for a spread of hour figures, the plan
finishing on `finishOn(start, h, d)` has `h <= hoursBy(start, that day, d)`, and `h` one hundredth
higher than the budget lands on a later day. Both directions, since a boundary that disagrees puts
a plan on the wrong side of a date it just met. A target equal to the start is one working day's
budget. A target before the start is zero. A weekend target counts the Friday. A run where every
completion beats the budget is 100% and one where none does is 0%, since a share is the one number
whose ends are worth pinning.

**Done when** "will this plan hit 1 November?" is a question about hours that the engine can
already answer.

### As built — where it differs from the above

**The oracle turned out to be an exact round trip rather than a pair of inequalities.** The
bullets describe checking `h <= hoursBy(...)` and that one hundredth more lands later; what is
actually true is stronger and simpler — **`finishOn(start, hoursBy(start, d), d)` is `d`
exactly**, for every working day, because `hoursBy` returns whole days' worth and `finishOn`
divides by the same day with a ceiling that has nothing to round. Both tests walk a fortnight
rather than a single case, and the second still asserts that a hundredth of an hour more misses.
An exact identity is a much better thing to have than a bound, and it was there for the taking.

**One case the bullets did not name, and it is the one that would have been wrong.** The working
days between two dates are counted rather than walked — five per whole week, the way `finishOn`
already counts — and that arithmetic divides the epoch day by seven. **Epoch day zero was a
Thursday and `/` truncates towards zero**, so a date before 1970 lands a whole working week out.
`Math.floorDiv` is the fix and `theCountHoldsForDatesBeforeTheEpoch` is the test. Nobody will type
1969 into this product; the point is that the failure is a week, is silent, and costs one word to
prevent.

**`ConfidenceBy` counts rather than reading a percentile**, which the bullets left implicit and
which is worth stating: a forecast keeps five percentiles, and answering "what share beat this
budget" from those would mean interpolating between two of them — precision the sampling does not
have, and the reason `Engine.at` takes the nearest rank in the first place.

**Its oracle is the engine's own percentiles**, which the step did not think to ask for. The P80
*is* the hours eight runs in ten came in under, so counting them has to land on eight in ten —
two ways of asking one question meeting on one number, checked at the P10, P50, P80 and P95 to
within a thousandth. That is a stronger footing than the hand-built cases beside it, and it is
what says the counter and the sampler agree about what a percentile means.

**A budget of exactly nothing is a real question and is accepted**; a negative one is refused.
"Can this be done before it starts?" is what a target date before the start becomes, and the
answer is no rather than an error — which is also what `hoursBy` returns for it, so the two agree
without either knowing about the other.

**`finishOn` lost four lines to a shared `requireWorkingDay`.** Both functions bound the working
day the same way, and two copies of a bound are two chances to move one.

---

## Step 2 — A cut that moves no other draw ✅ *done*

**Goal.** An item can be made worthless without moving a single number anywhere else.

- `ItemModel` gains a `cut` flag and a `cut()` that returns a copy with it set; `sample` takes its
  draws exactly as before and returns zero when it is set.
- `typicalEffortHours` is **not** affected — decision 3.
- `sampleAsNewWork` is not affected either: a cut item is still an example of what this team's
  work looks like, and changing that would move the reference class and every draw taken from it.
- Nothing in `Engine` or `Schedule` changes.

**Tests.** **The property this step exists for**: a plan with one item cut and the same seed
produces, for every *other* item, exactly the duration it produced in the baseline — asserted
through `RunObserver`, draw for draw, because "the same random numbers" is the whole claim.
Cutting nothing reproduces the baseline `Forecast` byte for byte. A cut item's own duration is
exactly zero in every run. A plan with everything cut finishes at zero hours. The two paths that
must *not* change are asserted directly: the priority order is identical with and without a cut,
and so is the number of items discovered by scope growth.

**Done when** a counterfactual differs from its baseline in exactly one respect.

### As built — where it differs from the above

**The factory is `asCut()`, not `cut()`, and the compiler decided that.** A record component
named `cut` owns the accessor `cut()`, so a method of that name returning an `ItemModel` will not
compile. Given the choice of which reading to keep, the predicate won: `item.cut()` reads as a
command and is a question, while `item.asCut()` reads as what it is — the same item, as a cut.
A five-minute detour, recorded because the naming collision is the kind of thing that gets
resolved by renaming the *component* to something vaguer, and the component's name is the one that
appears in the constructor everywhere.

**The paired-draw test walks every item, not one.** The bullets ask for "a plan with one item
cut"; what is asserted is every item in turn, over two thousand runs, against a fixture built to
exercise **every path `sample` can take** — a plain estimate, two estimators disagreeing, work
under way with hours against it, finished work, and work nobody costed. That matters because the
paths draw *different numbers of times*: the conditional draw for work under way takes a
`nextDouble` the ordinary path does not, and the two-estimator case takes a `nextInt` over a
larger range. A cut that preserved one path and not another would be caught by exactly one of
those five columns.

**The shared stretch and the discovered count are checked as well**, which the bullets mention
only for scope growth. Both come off the generator *before* the plan does, so a cut that disturbed
either would have disturbed everything after it — they are the cheapest possible canary and they
cost one field each on the recorded run.

**Two properties were added that the bullets did not name.** A cut item still answers
`sampleAsNewWork` identically, because it remains part of the reference class scope growth draws
from — dropping it would move every draw taken from that class, and would be untrue besides, since
imagining a task away says nothing about the size of the ones nobody has thought of. And cutting
work that already weighed nothing — finished, or never costed — is a no-op down to the last bit,
which is what says the flag invents no draws where there were none.

**`Engine` and `Schedule` are untouched**, as the step required, and the coverage report confirms
both are still fully exercised by the tests that were already there. The whole of the mechanism is
four lines in `ItemModel.sample`: take the draw, then throw it away.

---

## Step 3 — What one cut buys ✅ *done*

**Goal.** For a named set of candidates, what each is worth on its own.

- `POST /api/forecasts/{runId}/cuts` `{by, confidence, candidates}` — a POST because the body
  carries a list of identifiers, and the one that writes nothing, exactly as
  `/api/estimates/quality` is.
- Replays the run with M6's guard, converts the target through the run's own calendar, and
  evaluates the baseline plus one counterfactual per candidate.
- Answers: the hours the target became, the baseline confidence, and each candidate with the
  confidence it reaches and what that buys — ranked by the confidence bought, not by the order
  asked.
- Refuses: a run with no calendar, a candidate that is not in the run, more candidates than the
  budget allows.

**Tests.** Cutting an item that is never on the deciding path buys nothing measurable, and cutting
one that always is buys a great deal — the same shape M6's counter-oracle takes, in the currency
of a date. **A candidate that never varies still buys time**, which is decision 6's correction
made executable: an item fitted to a point mass has no contribution to the spread at all and moves
the date when it goes. The hours the target became are on the response, and match
`WorkingCalendar.hoursBy`. A run made before M4 is refused and named. A candidate the run never
held is refused. Thirteen candidates are refused with the budget in the message. The endpoint
writes nothing, and a run in another organisation is `forecast_not_found`.

**Done when** "what is this worth dropping?" has a measured answer per candidate.

### As built — where it differs from the above

**Decision 9's budget was measured, and at the scale ceiling it is six seconds.** Baseline plus
twelve cuts, five hundred items, ten thousand runs: **6.3 s**. That is the honest number and it
is over any comfortable request budget — a typical fifty-item plan is nearer half a second, so the
cost is the plan's size times the evaluations, exactly as decision 9 says.

The cap stays at twelve, and the reasoning is the one decision 9 already set out: **if the budget
gives, it gives on the number of candidates, which is visible, and never on the sample count,
which is not.** Halving the runs would halve the cost and put the paired spread back to 1.9 points
— most of what a cut is worth — so the search would rank by luck while looking exactly as
confident. What actually fixes six seconds is the per-tenant concurrency limit `roadmap.md`
already records as missing, and eventually doing this out of band. **Step 5 inherits a consequence
of it**: a screen that fires this off and shows nothing for six seconds looks broken, so the
waiting has to be visible.

**One refusal became three, and each is a different thing to be told.** The bullets name them
together; they are `forecast_has_no_calendar` (422 — the run cannot be asked about a date at all,
the same shape `nothing_to_forecast` takes), `candidate_not_in_forecast` (400 — a fact about what
the request names), and `too_many_candidates` (400, carrying the number). The last is a
document-level refusal rather than `@Size(max = 12)` on the list, which would have produced the
`max_size` code — whose wording is *"use no more than 12 characters"*, because that constraint has
only ever bounded strings.

**`ForecastRun.hasReadableCalendar()` now states a rule that was written twice.** M4's response
tests `WorkingCalendar.RULE.equals(getCalendarRule())` to decide whether to publish dates, and an
inverse query has to ask the same question before turning a date into hours. Two copies would be
two chances for one to start reading a run through a calendar it was not made with.

**The three-way naming was extracted, and the coverage gate is what found it.** `contributionsTo`
and `cutsFor` both turn "the item, or nothing" into a title and an archived flag, and the second
copy had no test for either edge — the ranking's tests cover them and the cuts' did not. Rather
than write two more near-identical cases, the logic is `titleOf` and `isArchived`, stated once and
covered once. Three-way logic written twice is two chances for one copy to start rendering a
missing item as a blank.

**One replay does two jobs.** The baseline evaluation *is* the replay whose six figures are
checked against the row, so proving the engine still reproduces the run costs nothing beyond the
work already being done. It also means the guard fires before any candidate is simulated, so a run
that cannot be explained cannot be advised on either.

**The wire is in percentages throughout**, converted once at the service boundary. The model deals
in shares from 0 to 1 the way `Contribution.shareOfSpread` does; nobody asks for a date at 0.85
confidence, and a request in percent with an answer in fractions would be two units in one
feature.

---

## Step 4 — The shortest list that gets there ✅ *done*

**Goal.** The question the milestone is named for: what do I cut to hit the date.

- Greedy accumulation over the same candidates: best single, then best of the rest **with it
  already cut**, until the bar is met or nothing is left.
- Every step is a fresh evaluation with all the chosen cuts applied — never a sum of singles.
- The answer carries the ordered list, the confidence after each step, whether the bar was met,
  and how many simulations it took.
- Stops honestly: reaching the bar, running out of candidates, and running out of budget are three
  different endings and the response says which.

**Tests.** Two cuts on the same chain buy less together than the sum of what each buys alone —
which is decision 7 asserted rather than merely warned about. A plan already past the bar returns
an empty list and says the bar is met, rather than proposing a cut that buys nothing. A bar that
cannot be reached with every candidate cut says so and reports the best it could do. The reported
confidence after two cuts equals a direct evaluation with both cut. The simulation count is
reported and matches what was done.

**Done when** somebody can be told which things to drop, and the number is one that was measured.

### As built — where it differs from the above

**Round one of the search *is* the singles.** Weighing every candidate on its own and taking the
best of them are the same set of simulations, so they are run once and read twice: the map that
answers "what is each worth alone" is handed straight to the search as its opening round. Running
them twice would have doubled the cost of the cheapest request in the feature and — worse — left
two numbers for one fact, free to drift the day somebody changed one loop and not the other. The
test asserts they are the *same* number, not two that agree.

**The simulation budget got a number, and it is `MOST_SIMULATIONS = 40`.** Decision 9 bounded the
candidates and said the answer must report what it spent, but never said where the search stops.
Twelve candidates that never reach the bar are 12 + 11 + … + 1 evaluations plus the baseline —
**79**, six times the thirteen step 3 measured at 6.3 s, so around forty seconds at the scale
ceiling. Forty is three times the cost of weighing the candidates once, which is still nineteen
seconds in that worst case and is a bound on a search rather than a promise about latency; what
actually fixes that is step 3's answer, out of band and with the waiting visible.

It is a bound on the request rather than on each round, so a narrow shortlist searches deeper
than a wide one — **five candidates never reach it at all**, the whole search being sixteen runs,
while twelve stop after three steps at thirty-four. That is the right way round: somebody who
named twelve things is asking a wider question and gets a shallower answer, and is told so.

**So `BUDGET_SPENT` is a real ending rather than a defensive one**, and that is why the three
endings are an enum on the wire rather than a boolean `met`. `MET` and `NOTHING_LEFT` differ in
what to do next — accept the list, or renegotiate what is droppable — and `BUDGET_SPENT` differs
from both by saying the answer is *as far as it looked*. A search reporting the best thing it
happened to look at, without saying that is what it did, is the failure mode of every heuristic
that returns a result instead of a result and a reason.

**Decision 7 is asserted with an oracle that needs no engine.** A chain with everything on it cut
takes no time at all, so the confidence after the last step is exactly 100 — a number that can be
checked by reading rather than by trusting the sampler. Against it, the two singles sum to
ninety-odd points on a plan that only had seventy-odd to give, which is the decision made
executable: *the numbers may never be added.*

**The coverage gate found that the search was never asked to disagree with the order it was
given.** Every test named its best candidate first, so the branch where a later candidate wins had
never run — a search that simply took whatever was offered first would have passed the whole suite,
and its answer would have looked exactly like an answer. The candidates are now named smallest
first, and the test asserts the search picks the second one.

**`Search` is a small stateful class rather than a loop in the service method.** Four things move
together across a round — what is left, what has been chosen, what each remaining candidate now
reaches, and how many simulations have been spent — and the invariant that matters is that the
third goes stale the moment the second changes. As a field set to null in `take`, that is one line
stating it; threaded through a loop as locals it would be four variables and a comment.

---

## Step 5 — On screen ✅ *done*

**Goal.** A date somebody wants, and what it would take.

- The forecast panel gains a target: a date and a confidence, beside the date it already shows.
- The candidates are chosen from the plan's own work — decision 1 — as a list somebody ticks.
- The answer leads with **whether the bar is already met**, then the cumulative list, then the
  singles as *what each buys on its own*.
- **The singles carry the warning next to them, not behind a disclosure**: these are what each
  buys alone, and they do not add up. Decision 7.
- A run that cannot be replayed, or one with no calendar, says so in a line the way the
  contributions panel already does.
- Every string comes from the catalogue.

**Tests.** No request goes out until a target and at least one candidate exist. The cumulative
list and the singles are visibly different sections, and the singles say what they are. A plan
already past the bar shows that and proposes nothing. Choosing a different date changes the
answer, and choosing a different confidence changes it too. A run with no calendar shows the
reason rather than a form. Every string comes from the catalogue.

**Done when** somebody can ask for a date and be told, in the same screen, what it would cost.

### As built — where it differs from the above

**It is its own component, `TargetDate`, not more of `ForecastPanel`.** The panel was already
eight hundred lines and this adds a form, a tick list and two result sections — but the deciding
reason is that it needs *the plan's work*, which nothing else in that panel has ever loaded. A
component that fetches something no other part of its host wants is a component.

**Which work the run was about is worked out from two timestamps, because nothing on the wire
says.** `ForecastResponse` carries no item list — the snapshot keeps identifiers and no titles
on purpose, so that M10 can diff two runs without a rename reading as movement — so the tick
list offers live work whose `createdAt` is no later than the run's. That errs in the safe
direction in both of its two ways of being wrong: work written down since is never offered, and
work put away since is in the snapshot but absent from the live listing, so it is not offered
either. **Offering too few is an omission a reader can see; offering too many would be a tick box
the server refuses** — which is the trap the progress form's rule about only showing the boxes a
status has room for exists to avoid.

**`describeWork` is shared with the contributions ranking**, which is step 3's lesson arriving on
the other side of the wire before the second copy existed. The backend's coverage gate caught
`contributionsTo` and `cutsFor` naming work two ways; here the two lists are on one screen, so
the catalogue entries moved out of `contributions` into `projects.forecast.work` and both read
them. A copy that started rendering "work the plan no longer holds" as a blank row would look
like an ordinary empty label rather than like a fault.

**The twelve-candidate limit is enforced on screen, and the refusal is still translated.** Once
twelve are ticked the rest are disabled with a line saying why, so `too_many_candidates` is
unreachable from this form — being told afterwards to untick three would be being asked to guess
which three mattered. Its wording is in the catalogue anyway, along with `forecast_has_no_calendar`
and `candidate_not_in_forecast`, because the code is the contract with every caller rather than
with this screen.

**Two halves of the question gate the button; the claim inside it does not.** Nothing goes out
without a date and at least one candidate — a target with nothing droppable asks only whether the
plan already gets there, which the band above has already said. The *confidence* is not gated the
same way: it is a claim with bounds the server states, so an empty box goes as null and the
complaint lands on the field, exactly as the assumptions above it do.

**A tick list that failed to load says so.** The first version swallowed it, on the argument that
the plan screen shows the same request's failure already. That is wrong in a way worth recording:
an empty tick list and a tick list that did not arrive look identical, and the first of them reads
as *"there is nothing you could drop"* — an answer, and the wrong one, on the one panel in this
product that must never say anything by accident.

**When the bar is already met, neither list is rendered at all.** The bullets say the answer leads
with whether the bar is met and then gives the lists; a met bar makes both of them advice nobody
needs, and an empty "what it would take" heading reads as a search that failed rather than as a
question that did not arise.

**Every string is from the catalogue by construction**, not by inspection: the test setup fails
any test that renders a key with no wording, so the eighteen cases in `TargetDate.test.tsx` are the check. Branch
coverage is 100% across the frontend, and the last two branches to fall were the unmount guard on
the work request and giving a ticked candidate back.

---

## Step 6 — Close out ✅ *done*

- `roadmap.md`: mark M7 done, and with it **Tier 2** — variance contribution and inverse queries
  were the whole of it.
- `roadmap.md`: **correct its own M7 note** — M6's ranking is not the shortlist, and decision 6
  says why. That sentence has been in the document since before M6 existed and is wrong in a way
  that would have produced a systematically misleading feature.
- `roadmap.md`: record what M11 inherits — "what if we add a person" is this machinery with
  `capacity` as the lever instead of a cut, which is a parameter change on a replayed run rather
  than a second feature.
- `roadmap.md`: record what M10 inherits — a target date and a confidence are now things a plan
  can hold an opinion about, which is what a plain-language sentence needs.
- `product-concept.md`: *Inverse queries* stops being design intent, and Tier 2 is complete.
- `CLAUDE.md`: a cut is a draw discarded so the comparison is paired; the priority order never
  moves; the numbers never add; the cumulative answer is computed; candidates come from the
  caller and never from the server.

### As built — where it differs from the above

All six bullets are done, and two of them turned out to be larger than they read.

**Correcting the M7 note was already done, in step 0.** The blockquote in `roadmap.md` that says
M6's ranking is not the shortlist was written while this plan was — the correction is only worth
anything if it lands before somebody builds from the wrong sentence, not after. What close-out
added was the tense: the section now reports what shipped rather than what to watch for.

**A seventh thing needed correcting, and this step found it.** `roadmap.md`'s *Which forecast runs
are history* section says M7 "breaks the assumption" that every row in `forecast_runs` is somebody
deliberately re-forecasting, and sets out two ways to answer it. **M7 took the first — nothing is
persisted at all** — so that section now records the answer rather than leaving an open question
whose premise stopped being true. It matters because M10's sliding-date detector reads that table,
and an open question about it reads as a hazard that has not been dealt with.

**What M11 and M10 inherit went into `roadmap.md`'s M7 section rather than into theirs**, which is
where M6 put the same thing and is the right place: a milestone's own section is what somebody
reads when they get to it, and a note about the future left in a future section is a note nobody
finds until it is too late to shape the work. M11's is that "what if we add a person" is a
parameter change on a replayed run rather than a second feature; M10's is that a target date and a
confidence are now things a plan can hold an opinion about.

**`CLAUDE.md` gained a section rather than a paragraph.** The five rules the bullet lists are five
different ways of getting this feature quietly wrong, and four of them look like simplifications
from the outside — which is the same reason the M3a decisions have a section of their own. It sits
under *Forecasting* beside variance contribution, since both are lenses on a replayed run and both
turn on the same rule about numbers that must not be added.

---

## The review pass — what a read of the whole milestone changed

Six steps written one after another leave seams that only a read of the whole thing finds. Two of
these were defects, and both would have shipped looking entirely reasonable.

**Naming the same work twice was weighed twice, ranked twice, and cut twice.** `positionsOf`
resolved candidates in the order given and kept duplicates, so a request naming one item twice
spent two simulations to produce two identical rows in the ranking — and, worse, put that index
into the search's `remaining` list twice, letting the greedy search "cut" one piece of work at two
of its steps. **One sacrifice reported as two, with a confidence figure that was real.** The screen
cannot produce a duplicate, since candidates are ticked rather than typed, which is exactly why
nothing found it: the API is the contract, and the form is one caller of it. It is now
de-duplicated with the first mention keeping its place, and the count still taken over what the
request named, since that is what a caller has to shorten. `weighsWorkNamedTwiceOnlyOnce` asserts
two candidates and four simulations from three named.

**A new forecast left the previous answer on screen.** `TargetDate` held the date, the ticks and
the answer, and `ForecastPanel` re-rendered it with a new `run` without resetting any of them — so
asking for a fresh forecast left **a list of work to drop that had been measured against a run no
longer displayed**. It is the same staleness the contributions panel clears itself for, arriving
through a whole component rather than one section, and the fix is to key the panel on the run: a
different run is a different question. The test for it fails without the key, which was checked
rather than assumed.

**Two ways to replay one stored run had appeared.** Step 3 added `replay`, and `contributionsTo`
went on building its own `Engine.run` call by hand — so a parameter added to a run in future would
be passed by one of them and not the other. That fails loudly rather than silently, since M6's
guard compares the replay against the row, but it fails as *"this forecast cannot be broken
down"*, which is a poor way to learn that a replay had stopped being told about something. Both now
go through `replay`, and the two M3b assumptions are read off the row by `teamFactorOf` and
`scopeGrowthOf` — needed twice, because whether either was modelled at all is what decides whether
it gets a contribution row.

**A stranded Javadoc block, from extracting the helpers in step 3.** `titlesIn` lost its
documentation to `titleOf`, which then carried two comment blocks in a row — the first of them
describing a method three lines further down. Nothing warns about that.

**A bound with no test, and it is the awkward kind.** Round one of the search is already paid for
by the time the simulation budget is first consulted, so a candidate limit raised past that budget
would overspend it without ever reaching the check that refuses.
`theCandidateLimitLeavesRoomForTheSearchToRun` asserts the two constants keep their
relationship, since nothing else would notice them drifting apart.

**Two catalogue sentences were untrue in a case they render in.** "This forecast was made before
any of the work now in this plan" is false when the plan's work has all been *archived* since —
which is the other way that list comes out empty — so it now says what is true of both and what to
do about it. And "untick something to try a different 12" was a sentence with a number where a
noun belongs.

**Smaller things.** `ForecastRun.hasReadableCalendar` referred to `WorkingCalendar` by its fully
qualified name rather than importing it. `Answer` took `t` and `locale` as props though it is a
component, so it reads its own. `percent(baseline)` was recomputed four times in one method. Two
new strings used curly apostrophes where the catalogue uses straight ones.

**And a coverage flicker turned out to be a test passing for the wrong reason.** One run reported a
single uncovered statement in `ProjectPage`, a file this milestone does not touch; the next was
clean. It was the same statement every time — the branch behind *"says so when archiving is
refused"* — and a test cannot pass without running the line that produces the message it asserts.
It could, because the refusal was queued with `mockResolvedValueOnce`: **that page loads five
resources and the test waits only for the project**, so the queued 404 was sometimes eaten by one
of the four still in flight, which then showed the identical refusal in *its own* banner while the
archive it was meant for quietly succeeded. This codebase already has the rule — *a test double
that answers every URL alike is a lying double* — and this is its other half: **a double that
answers by turn is lying about which request it answered.** Both refusal tests now key on the
request, and the statement stays covered across three consecutive runs.

**Both suites, both coverage gates.** 805 backend tests and 390 frontend, 0 of 623 backend branches
missed, and 100% of the frontend's statements, branches, functions and lines — stable, which is
the part that took a fix rather than a re-run.

---

## Migrations

**None.** Everything is evaluated against a stored run, replayed from its seed — M6's decision 1,
spent a second time. A table of "cut scenarios" would be a record of a question somebody asked
once, and the question is cheaper to ask again than to keep.

---

## Sequencing and risk

**The risk in M7 is that it is the first feature whose output is an instruction.** Every milestone
so far reported something: a band, a date, a ranking. This one says *drop these two things*, and
somebody will. A forecast that is wrong wastes a meeting; a cut list that is wrong deletes work.

**The one that will actually go wrong** is decision 2 being simplified. Removing the item from the
plan, or emptying its estimates, both look like obviously correct ways to model a cut, and both
silently decouple the random streams — the second one especially, because `weighsNothing()`
returns before the draw and nothing anywhere fails. The measurement above says what that costs:
the noise doubles and lands in the same range as the effect being measured, so the ranking becomes
a coin flip that looks exactly like an answer. The defences are the byte-for-byte test in step 2,
decision 2, and this paragraph.

**The second is decision 7 being softened into a column of percentages.** Three cuts with "+5%",
"+3%" and "+2%" beside them will be read as thirteen percent by somebody in a hurry. The
cumulative answer exists so that the honest number is available and computed; the singles exist so
that a reader knows which candidates are worth considering at all. They must not look like the
same table.

**Two things that will look like bugs and are not.**

- **Cutting a large task buys nothing.** It is off the path that decides the finish. That is the
  entire reason this milestone re-runs the schedule instead of ranking by size, and it is the same
  fact M6 reports from the other side.
- **Cutting two things buys less than the two numbers beside them.** They overlap. The cumulative
  figure is the measured one and the singles are each measured alone; both are true and they are
  answers to different questions.

**What this milestone must not absorb.** Performing the cut is the plan screen's, and archiving
already exists there. Adding people is M11's, and it is the same machinery with a different lever.
Plain-language output and the burn-up are M10's. The line to hold is that M7 evaluates a named set
of candidates against one stored run and stops.
