# M7 — Inverse queries: implementation plan

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
| 3 | What one cut buys | 1, 2 |
| 4 | The shortest list that gets there | 3 |
| 5 | On screen | 4 |
| 6 | Close out | 1–5 |

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

## Step 3 — What one cut buys

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

---

## Step 4 — The shortest list that gets there

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

---

## Step 5 — On screen

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

---

## Step 6 — Close out

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
