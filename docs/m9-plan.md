# M9 — Throughput cross-check: implementation plan

> **Built, 2026-08-18.** All six steps are done and each carries its own *As built* section.
> **No migration, no column and no index**, and no change to anything the engine samples —
> `Engine.VERSION` is still 2. **It is the one forecast in this product that needs nothing of
> anybody**: no estimate, no assumption, no measured actual, only that work has been finished and
> dated, which this product already requires. That is why it can answer on the day it ships where
> M8's calibration cannot.
>
> **Scope.** `roadmap.md` M9: a second, independent forecast from historical throughput, with no
> estimation involved — and **the gap between the two forecasts is the deliverable**. When the
> team says six weeks and their own history says eleven, that is far harder to dismiss than either
> number alone, because both came from the team. Excluded and argued below: story points and
> velocity (icebox — decision 11), per-person throughput (decision 11, and it is M8's leaderboard
> objection again), replacing the engine with this (decision 10), and reading throughput across a
> whole organisation rather than one plan (decision 9).
>
> **How to read this.** Decisions first. The one that decides whether this milestone reports
> signal or a comfortable number is **decision 5** — a bootstrap cannot imagine a week worse than
> the worst one it has seen, and the measurement below says what that costs. The one that decides
> whether the *comparison* means anything is **decision 7**: the two forecasts default to
> answering slightly different questions, and a gap that is partly a difference in the question is
> worse than no gap at all. **Decision 6 corrects `roadmap.md`.**
>
> **Why this is worth building straight after M8.** M8 shipped an instrument with no reading in
> it: calibration needs finished work carrying an estimate *and* a measured actual, and the actual
> is optional because most teams do not track it. **M9 needs neither.** `completed_on` is required
> on every item ever marked done, so the history it reads already exists in full for every plan
> this product holds — which makes it the one forecast that can say something on the day it ships.
>
> **And the honest caveat.** This is the first forecast in the product that is not checkable
> against arithmetic. M3 had a closed form, M4 a calendar anybody can count on their fingers, M6 an
> exact degenerate case. A bootstrap's answer is right if the future resembles the past, and
> nothing in this repository can tell you whether it does. What can be measured is how much the
> answer moves for reasons that are not about the team, and that is what the section below does.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | A team's weeks, including the empty ones ✅ *done* | M2 |
| 2 | The projection, and what a bootstrap cannot see ✅ *done* | 1 |
| 3 | Reading a plan's history and what is left in it ✅ *done* | 1 |
| 4 | On an endpoint ✅ *done* | 2, 3 |
| 5 | The gap, beside the band ✅ *done* | 4 |
| 6 | Close out ✅ *done* | 1–5 |

**M9 adds no migration.** Every number it reads is already stored: `work_items.completed_on` is
required on anything marked done, and the backlog is the items that are not. No index either —
`ix_work_items_tenant_project_archived` reaches one plan's items, and 500 of them is the stated
ceiling.

---

## The measurement this plan is built on

**Two runs, and the second is the one that decides the milestone.** Both are simulations of the
method rather than measurements of this codebase — there is nothing built yet to measure — so they
are reproducible from the description alone: a bootstrap of the kind decision 4 specifies, against
a process whose truth is known because it was generated.

A bootstrap resamples the weeks a team actually had. It is *unbiased* whatever the history length
— the first table shows that, and it is the reassuring half. What moves is how much the answer
depends on which weeks you happened to observe.

A team completing a Poisson(5) items a week, forty items left, the 85% answer taken over 2,000
bootstrap runs, repeated across 400 independent histories:

| History | Mean 85% answer | Spread | Middle 80% of the answers |
|---|---|---|---|
| 4 weeks | 10.0 | 2.41 | 7 – 13 weeks |
| 8 weeks | 10.0 | 1.83 | 8 – 12 |
| 13 weeks | 9.9 | 1.34 | 8 – 12 |
| 26 weeks | 9.9 | 0.96 | 9 – 11 |
| 52 weeks | 9.8 | 0.71 | 9 – 11 |

The truth is 10 weeks. **At one month of history the answer lands anywhere between seven weeks and
thirteen**, and every one of those is a confident-looking date. A quarter of history halves it; a
year halves it again.

### And the half that is not symmetric

The table above uses a well-behaved process. Real teams are not well behaved: they lose a week to
an incident, a launch, a conference. Take a team that runs at five a week and loses **one week in
ten** to something — truth: P50 nine weeks, P85 eleven, P95 thirteen.

| History | Saw no bad week | Its 85% answer | The answer when a bad week *was* observed |
|---|---|---|---|
| 8 weeks | **41%** | 9.9 | 12.7 |
| 13 weeks | **23%** | 9.7 | 11.8 |
| 26 weeks | 5% | 9.5 | 11.5 |
| 52 weeks | 0% | 9.0 | 11.3 |

**Three things follow, and they are the spine of this plan.**

**A bootstrap cannot imagine a week worse than the worst one in its window.** At two months of
history, *two teams in five* have never observed the bad week that happens to them every ten, and
their forecast answers 9.9 against a truth of 11 — early, confident, and wrong in the one direction
this product exists to correct. That is decision 5, and it is a limitation to publish rather than a
bug to fix: no amount of resampling invents an event the window does not contain.

**Short histories are bimodal rather than merely noisy.** The same team answers 9.9 or 12.7
depending on nothing but whether an incident fell inside the window — and the second is an
*overshoot*, because one bad week out of eight weighs 12.5% where the truth is 10%. A short history
is not a blurry version of the right answer; it is one of two wrong ones.

**A quarter of history is the point where this starts being worth showing, and a year is where it
settles.** That decides decision 12's floor and what the screen says below it.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What is counted | **Items completed, never hours.** `completed_on` is required; `actual_effort_hours` is not, and M8 measured what that costs. |
| 2 | The period | **A calendar week, under a stored rule name** — `WorkingCalendar.RULE`'s argument, one layer up. |
| 3 | Empty weeks | **Part of the history**, and dropping them is the single easiest way to get this wrong. |
| 4 | How it is projected | **A bootstrap over observed weeks**, not a fitted distribution. Constant throughput gives an exact answer. |
| 5 | What it cannot see | **A week worse than the worst one observed.** Reported, never patched. |
| 6 | Does it absorb scope growth? | **No, and `roadmap.md` is wrong about this.** It absorbs the drag of past discovered work, not the fact that more will appear. |
| 7 | What the two forecasts are compared on | **One question, stated four ways**, or the gap is partly a difference in the question. |
| 8 | Where the date comes from | **Elapsed weeks, with no working day** — which is exactly why it absorbs holidays. |
| 9 | Whose history | **The plan's own.** An organisation-wide rate needs to know how attention is split, and nothing records that. |
| 10 | What it is not | **Not a replacement for the engine, and not a tiebreaker.** Two forecasts that disagree are the output. |
| 11 | What M9 must not become | **Not velocity, not story points, not a per-person rate.** |
| 12 | How little history is too little | **Stated once beside the arithmetic**, with a floor that refuses and a span the reader can see. |

### Decision 1 — Items completed, never hours

The history is a count of items finished per week, read off `work_items.completed_on`.

**This is what makes M9 the one forecast that can answer on the day it ships.** `DONE` requires a
completion date — `progress_date_required` refuses it otherwise — so every item this product has
ever seen finished carries one. `actual_effort_hours` carries no such requirement, and M8's
coverage counts are the measurement of what that means in practice: most finished work never
records it. A throughput built on hours would be a second feature whose ordinary answer is
"nothing to report yet", and there is no reason to build two of those.

It also keeps the promise in the milestone's name. Counting hours would need somebody to have
estimated or measured something; counting items needs somebody to have *finished* something, which
is the one thing a team does whether or not they use this tool properly.

| Rejected | Why |
|---|---|
| Hours completed per week | Needs `actual_effort_hours`, which is optional and mostly absent — M8's own problem, bought a second time. |
| Story points per week | The icebox's, and it needs a unit this schema does not have. |
| Items *started* per week | Starting is optional (`DONE` needs no start), and a started item is not a delivered one. |

### Decision 2 — A calendar week, under a name

Completions are bucketed into weeks, and **which week** is a stored rule name rather than a
constant nobody can see.

This is `WorkingCalendar.RULE`'s argument one layer up: two defensible definitions give two
different histories from identical data, so an answer computed under one must never be silently
compared with one computed under another. A week beginning Monday and a week beginning Sunday put
a Sunday deploy in different buckets, and on a short history one bucket boundary can move a
percentile. The rule travels with the answer, and a second definition later is a **new name**
rather than an edit to this one.

**A week rather than a day, and a week rather than a month.** Days are mostly zeros and make the
bootstrap resample noise; months give a team eighteen data points in a year and half of them
before anything changed. A week is also the unit teams already describe themselves in.

### Decision 3 — Weeks with nothing completed are part of the history

A week in which nothing was finished is a week the team had. Dropping it inflates the rate by
exactly the fraction of the time the team was not delivering — which is the fraction this whole
milestone exists to capture.

**This is the easiest thing here to get wrong**, because the data arrives as a list of completion
dates and the obvious implementation groups them. Grouping produces only the weeks that have
something in them. Holidays, the incident, the week everybody was in a workshop and the week two
people were ill are all *absent* from that grouping, and they are precisely what `roadmap.md` means
when it says throughput "implicitly absorbs interruptions, holidays … and the fact that nobody
works eight focused hours". Absorbing them means counting them.

So the history is every week from the first completion to the as-of date, and a week with no
completions contributes a zero. Step 1's test asserts a specific case: a team that finished ten
items in one week and nothing for three reads as 2.5 a week, not ten.

### Decision 4 — A bootstrap over observed weeks, not a fitted distribution

Draw weeks with replacement from the history, accumulate items until the backlog is covered, count
the weeks. Repeat ten thousand times and read the percentiles off.

**No distribution is assumed**, which is the point. A Poisson fit is the obvious alternative and it
asserts something false about every real team: that weeks are independent draws of a single rate,
so no week is ever four standard deviations out because the build broke. The empirical distribution
makes no such claim — it says only that the future weeks look like some multiset of the past ones.

It also has an oracle, which a fitted model would not: **a team that completed exactly five items
every week for twenty weeks, projecting forty items, must answer exactly eight weeks in every
single run, with zero spread.** That is checkable by hand and it is what step 2 is built on, the
way `EngineTests` is built on a closed-form sum.

### Decision 5 — It cannot imagine a week worse than the worst one observed

This is the measurement above, made into a decision. A bootstrap resamples what it has; if the
window contains no bad week, no run will contain one either, and the answer comes back early and
confident.

**It is published rather than patched.** The three fixes anybody would reach for are all worse:

| Rejected | Why |
|---|---|
| Fit a distribution with a tail | Invents a tail nobody observed, and asserts weeks are independent draws of one rate — decision 4. |
| Inflate the answer by a fudge factor | A number with no source, applied to a forecast whose whole claim is that it came from the team. |
| Refuse below a year of history | Two teams in five would never see a forecast, and the honest answer at 13 weeks is *useful and wide*, not absent. |

What ships instead is the window itself: how many weeks, the first and last, the best week and the
**worst** week observed. A reader who knows their team stops for a week each quarter can see in one
line whether the history contains one. That is the same move M3a made with its limitations and M8
made with its coverage — say what the number did not include, beside the number.

### Decision 6 — It does not absorb scope growth, and the roadmap is wrong about this

`roadmap.md` says throughput "implicitly absorbs interruptions, holidays, **scope growth**, and the
fact that nobody works eight focused hours". Three of those four are right. Scope growth is not,
and the error runs in the flattering direction.

**What the history absorbs is the *drag* of past discovered work.** A team that closed five items a
week while two of every ten were things nobody had listed has a rate of five, earned partly on
unlisted work. Project the forty items you can see at five a week and you get eight weeks — but
historically only four of every five completions went to listed work, so forty listed items take
ten. **The projection is optimistic by exactly the share of throughput that goes to work nobody has
written down yet**, and that share is the thing M3b models explicitly and this cannot see at all.

Nothing in the schema says which items were discovered mid-flight, so it cannot be measured here —
`work_items.created_at` against a plan's start is the nearest thing, and it would count a backlog
typed in over two weeks as scope growth. So this is a **limitation and not a correction**:
`throughput_excludes_unlisted_work` goes out with every answer, and decision 7 is where it matters.

### Decision 7 — The two forecasts must be compared on one question

**The gap is the deliverable, and the risk is that the gap is partly a difference in the
question.** Left alone, the two forecasts differ in four ways that have nothing to do with the team
disagreeing with itself:

| | The engine (M3) | Throughput (M9) |
|---|---|---|
| Unlisted work | Modelled, from two parameters somebody stated | Not modelled — decision 6 |
| Unestimated items | In the graph at zero effort, and reported as a limitation | **Counted like any other item** — it needs no estimate |
| Calendar | Effort hours divided by a stated working day | Elapsed weeks, holidays already inside them |
| Interruptions | Absent unless somebody put them in the team factor | Inside the history by construction |

**Two of those four make the engine look slow and two make it look fast**, so a raw subtraction is
not interpretable. What ships is therefore not a number called "the gap": it is the two dates side
by side with the four differences named under them, and the one that can be removed *is* removed —
the screen compares against a run whose scope-growth parameters the reader can see, and says so
when they are not zero.

**The second row is worth reading twice**, because it is the one advantage throughput has outright:
an unestimated item is a hole in the engine's answer and is ordinary evidence to this one. A plan
that is half estimated gets a full throughput forecast and a caveated engine forecast, and that is
the comparison at its most useful.

### Decision 8 — Elapsed weeks, and no working day anywhere

`WorkingCalendar` exists because the engine's output is *effort* and a date needs an assumption
about what a day holds. Throughput has no such problem: a week of history is a week of wall clock,
holidays and Fridays and all. Multiplying it by a working day would be M4's own error — capacity
counted twice — arriving from the other side.

So the answer is a number of **calendar weeks** from the as-of date, and its date is the as-of date
plus that many weeks. **Nothing in M9 reads `working_hours_per_day` and nothing should.** It is
also why throughput absorbs holidays for free while the engine cannot: a holiday is a week with a
smaller number in it, already counted.

**The as-of date is stated by the caller**, as `starts_on` is on a forecast run and for the same
reason: what day it is where somebody is sitting is a fact only their browser holds, and
`toISOString()` reports tomorrow after seven in the evening in New York.

### Decision 9 — The plan's own history, not the organisation's

Throughput is measured from the completions in **this plan**.

The alternative is tempting and wrong in a way nobody can see. An organisation's rate is the sum of
what it finished across every plan; applying it to one plan's backlog assumes the team spends all
of that rate here. A team running three plans would get three forecasts, each assuming it has the
whole team — and every one of them would be optimistic by however much attention goes elsewhere.
Correcting that needs to know how attention is split, and **nothing in this schema records it**:
there are no assignments, no allocations, and M11 is where resources arrive.

The cost is that a young plan has no forecast. That is the same answer M8 gives to an organisation
that has finished nothing, and it is preferable to a confident number built on a split nobody
stated. **The organisation-wide variant is named and deferred**, and what it waits on is M11.

### Decision 10 — Not a replacement, and not a tiebreaker

Two forecasts that disagree are the output. This does not average them, pick one, or fold the
throughput answer into the engine as a prior.

**Averaging is the change that will be proposed**, because two numbers is uncomfortable and one is
tidy. It destroys the only thing the milestone produces: "the team says six weeks and their own
history says eleven" is an argument that starts a conversation, and "eight and a half weeks" is a
number that ends one with nobody having learnt anything. It is also the M3a averaging objection —
turning disagreement into false confidence — arriving one level up.

### Decision 11 — What M9 must not become

- **Not velocity, and not story points.** A rate of *points* per week needs a unit this schema
  does not have; the icebox records what adopting one would cost and names throughput as where a
  defensible velocity would eventually come from. Items per week needs nothing.
- **Not a per-person throughput.** It is M8's leaderboard objection with a different metric:
  ranking people by items closed is won by closing small items, and this product ranks work.
- **Not a burn-up.** That is M10's, it is a chart, and it answers "how far along" rather than
  "when".
- **Not a second thing to configure.** The engine asks for five assumptions; this asks for a date
  and nothing else, and that asymmetry is most of why the comparison is worth having.

### Decision 12 — How little history is too little, stated once beside the arithmetic

Two numbers, both in the pure class next to the function they bound, following
`EstimateQuality.TIGHT_BAND`:

- **A floor that refuses.** Below it there is no forecast, only the window. The measurement puts
  four weeks at an answer spanning seven to thirteen against a truth of ten, which is not a wide
  forecast but a random one.
- **A bar below which the answer is published *and* flagged.** A quarter of history still answers
  eight to twelve, and two teams in ten have not yet seen their own bad week. That is worth showing
  and worth marking.

**The browser is told neither number.** It renders a flag the server sent, exactly as it does for
`EstimateQuality` and for M8's coverage — one rule about one history, in one place.

---

## Step 1 — A team's weeks, including the empty ones ✅ *done*

**Goal.** A list of completion dates becomes a history, and the weeks nothing happened in are in
it.

- **`Throughput` in `forecast.model`** — pure, like everything beside it. Built from a list of
  completion dates and an as-of date; yields the per-week counts, how many weeks, the first and
  last, and the best and worst week.
  - **Every week from the first completion to the as-of date**, so a quiet fortnight contributes
    two zeros rather than nothing (decision 3).
  - **The history begins at the first completion, not at the plan's creation.** A plan typed in
    last year and started last month is not evidence that the team does nothing; counting the idle
    months would make the rate a property of when somebody opened a form. That is a bias and it is
    named: the answer excludes the time before anybody began.
  - `Throughput.RULE`, a name, and the week's first day stated once here (decision 2).
  - Refuses a completion after the as-of date rather than bucketing it into a week that has not
    happened — a fact about the input, like `LogNormalFit`'s refusals.
- **`Throughput.WORTH_SHOWING` and `WORTH_TRUSTING`** — decision 12's two numbers, beside the
  arithmetic they bound, with the reasoning from the measurement in their javadoc.

**Tests.** **The case the whole decision rests on**: ten items finished in one week and nothing for
three reads as 2.5 a week, not ten. A team with one completion has one week and no spread. Weeks
are bucketed on the stated first day, asserted across a week boundary and across a year boundary,
where ISO week numbering is at its least intuitive. The as-of date extends the history: the same
completions read a month later are the same items over more weeks and so a lower rate — which is
what a plan going quiet looks like and must not be invisible. Best and worst come back as observed
rather than as fitted. A completion after the as-of date is refused.

**Done when** a week nobody finished anything in counts as a week.

### As built — where it differs from the above

**The rule is `monday_week`, and choosing it deleted a test rather than passing one.** The bullets
above ask for a case at the year boundary "where ISO week numbering is at its least intuitive" —
and the reason it is unintuitive is that a week *number* needs a year beside it, and the pair
disagree with the calendar for a few days each January. Keying a bucket by **the Monday on or
before** is the same bucketing with none of that arithmetic: 1 January 2026 is a Thursday whose
week begins on 29 December 2025, and nothing has to know which year's week one that is. The test
survives and now asserts the property rather than guarding a calculation that no longer exists.

**The two bars are 13 and 52, and the javadoc says the quarter is a concession.** The measurement
does not really support a cliff at a quarter: one month of history answers 7–13 weeks against a
truth of 10, and a quarter answers 8–12 — wider than it looks and not much better. What separates
them is the second table, where 41% of teams at two months have never observed their own bad week
against 23% at a quarter. **Neither is good**, and the floor sits at a quarter because decision 5
already rejected refusing more: two teams in five would then never see a forecast at all. The
constant carries that reasoning so nobody later reads 13 as a measured threshold.

**An empty history is legal and is not an error.** The bullets do not say what a plan nobody has
finished anything in produces, and the answer matters because step 4 reports the window whether or
not there is a forecast. It is a history of no weeks: `observed()` is false, the counts are true
zeros, and the five accessors that would have to invent something — the mean, the best and worst
week, both ends of the span — refuse. That is `Proportion.measured()`'s shape, and for its reason:
nought weeks is not a rate of nought.

**The order the completions arrive in is not assumed**, which the bullets left to step 3's query.
The earliest is found rather than taken off the front, so a caller that sorts and one that does not
get the same history — and the test says so, because the query's `order by` is exactly the kind of
thing a later change relaxes without noticing what depended on it.

**Two smaller things the tests made explicit.** `to()` is the week the *question* was asked in and
not the week the last item landed in, because otherwise a plan that has gone quiet reads exactly
like one still moving — `askingLaterAboutTheSameWorkReadsAsASlowerTeam` is that property. And
`weeks()` hands back a clone, asserted, because a history that gives away the array it is made of
is not a value.

**Counts.** 14 cases in `ThroughputTests`; 901 backend tests pass, with `Throughput` at zero missed
branches and zero missed instructions.

---

## Step 2 — The projection, and what a bootstrap cannot see ✅ *done*

**Goal.** The arithmetic exists, pure, with an oracle behind it, and it says what it could not see.

- **`ThroughputForecast`** — resample weeks with replacement, accumulate until the backlog is
  covered, count the weeks; ten thousand runs; percentiles read off the result.
  - **The same five percentiles the engine reports**, so the two answers are read at the same
    confidences and M4's control means the same thing on both.
  - `java.util.Random`, for `m3a-plan.md`'s reason: its algorithms are in its contract, so a JDK
    upgrade cannot silently move an answer.
  - **A deterministic seed from the question** — the plan, the as-of date and the backlog count —
    published in the answer and overridable. Nothing is stored (decision 10's neighbour), so the
    property worth having is that asking twice gives the same answer rather than a number that
    jitters when somebody refreshes.
  - **A backlog of nothing is not a forecast of zero weeks.** It is a plan with nothing left, and
    it answers that rather than a date.
  - **A history whose every week is zero cannot finish**, and the loop must not run forever. It
    refuses, because "never" is not a percentile and a team that has completed nothing has no
    throughput — which is the floor in decision 12 arriving as arithmetic rather than as a policy.
- **What it did not do, as codes** — the same shape `ForecastLimitation` has:
  `throughput_excludes_unlisted_work` (decision 6), `throughput_window_is_short` (decision 12),
  and `throughput_saw_no_bad_week` is *not* one of them, because nothing can know that. What ships
  instead is the observed worst week, and the screen puts it where a reader can recognise it.

**Tests.** **The oracle is exactness**: twenty weeks of exactly five, forty items, answers exactly
eight weeks in every run with zero spread — checkable on paper, and it is what a fitted model could
not offer. A history of alternating 10 and 0 answers the same *mean* and a visibly wider band than
a history of steady 5, which is the property the whole milestone is for. Doubling the backlog
roughly doubles the weeks and does not change the shape. The same seed gives the same answer and a
different seed gives a different one — the pair, so that neither passes because the sampler does
nothing. **The blindness is asserted rather than assumed**: a history with no zero week never
produces a run containing one, which is decision 5 written as a test so that nobody later "fixes"
it. An empty backlog and an all-zero history each answer rather than hang.

**Done when** the answer can be checked by hand on a history anybody can add up.

### As built — where it differs from the above

**`ThroughputForecast` is the result and `Throughput.project` is the sampler**, rather than a
second class doing the resampling. `Engine.run` is the shape the bullets had in mind, and the
difference is that an engine holds nothing while a history *is* the thing being sampled from —
`history.project(40, runs, seed)` reads as what it does, and a class whose only field is a
`Throughput` would be indirection.

**The seed is a parameter and is not derived here.** The bullets ask for "a deterministic seed from
the question — the plan, the as-of date and the backlog count", and a plan is a `UUID`:
`forecast.model` imports nothing from this codebase and must not start. So the pure function takes
a seed and **step 4 derives it**, which is exactly the arrangement `Engine` and `ForecastService`
already have. The property the bullets wanted — asking twice gives the same answer — is unchanged
and is asserted here as a pair, same seed and different seed, so that neither case passes because
the sampler is doing nothing.

**The two limitation codes move to step 4 as well**, for a related reason: `ForecastLimitation`
lives in the `forecast` *feature* package. Neither is a property of the arithmetic anyway — one is
unconditional and the other is `worthTrusting()` read at the boundary.

**"The loop must not run forever" needed more than the all-zero guard, and that is the real
addition.** Refusing a history of nothing catches the impossible case and misses the same problem
in slow motion: one completion in five years against a backlog of five hundred has a *nonzero* rate
and a loop that runs into the next millennium. `MOST_WEEKS` — ten years — bounds every run, and
`unfinishedRuns` counts the runs that reached it rather than returning the horizon as though the
plan had finished there. A cap that is not reported is the failure mode the workflow rules call
silent truncation, and `aRateTooSlowToFinishStopsAtTheHorizonAndSaysSo` is what says this one is
not.

**Two conventions copied deliberately rather than chosen.** The percentile is nearest-rank with no
interpolation and the deviation divides by the count rather than one less — both are `Engine`'s,
and both are copied *because* they are: two answers read at the same confidence have to be read the
same way, or the gap between them is partly a difference in rounding. The comment on each says so.

**No histogram**, unlike `Forecast`. Nothing in M9 draws a chart, and the icebox's distribution
curves are where one would matter. Left out rather than carried unused.

**The oracle came out exactly as hoped**, which is worth recording because a bootstrap is the kind
of thing that usually cannot be checked: twenty weeks of exactly five, forty items, and every one
of ten thousand runs answers eight weeks with a standard deviation of zero. Beside it,
`theSameAverageWithAWorseWeekIsAWiderAnswer` is the milestone in one case — ten-and-nothing has the
*identical* mean rate to a steady five and a visibly wider answer, which is what a mean cannot see
and what resampling is for.

**Counts.** 10 new cases, 24 in `ThroughputTests`; 911 backend tests pass, with `Throughput` and
`ThroughputForecast` at zero missed branches and zero missed instructions.

---

## Step 3 — Reading a plan's history and what is left in it ✅ *done*

**Goal.** The two numbers the projection needs come off the plan, scoped and cheap.

- **`WorkItemService.completionsIn(callerId, tenantId, projectId)`** — the completion dates of the
  plan's finished work, ascending. Archived items are **included**: a task that was finished and
  later put away was still delivered, and dropping it would make tidying up look like a slowdown.
- **`WorkItemService.remainingIn(...)`** — how many items are not done. Archived ones are
  **excluded** here, and the asymmetry is the point: putting work away means it is not going to be
  delivered, so it is not in the backlog, while the record of what *was* delivered does not change.
  This is the same split M8 makes between coverage counts and scored evidence, and both comments
  say which.
- **Unestimated items count**, needing no special case — decision 7's second row, and the one
  place this forecast is strictly better informed than the engine's.
- Both go through `ProjectService`, so the plan is fetched rather than assumed and a plan that is
  not there answers `project_not_found` rather than an empty history.

**Tests.** A plan's history holds only its own items, and a two-organisation fixture proves it.
Archived finished work is in the history and archived unfinished work is not in the backlog, each
asserted directly, because the two rules point opposite ways and a single "ignore archived" would
pass one of them. Items still in progress are in the backlog and not the history. `not_a_member`
and `project_not_found`.

**Done when** the backlog and the history disagree about archived work, deliberately.

### As built — where it differs from the above

**`remainingIn` hands back an `int` and not the `long` a count arrives as**, through
`Math.toIntExact`. `Throughput.project` takes an `int`, so the narrowing has to happen somewhere,
and here is where the argument for it lives: 500 items to a plan is the stated ceiling this
milestone's arithmetic assumes. A number that could not fit is a broken assumption and should say
so, rather than wrap silently into a backlog of minus two billion — and step 4 does not have to
think about it at all.

**The completions query filters `completedOn is not null`, and that is not defensive noise.** A
completion date is required on anything marked done, so the filter cannot drop ordinary data. What
it stops is a row written *outside* the service — which `ForecastApiTests` does, and which this
class does — putting a `null` into a `List<LocalDate>` and failing inside `Throughput.of` with
nothing pointing back here. `finishedWorkWithNoDayOnItIsLeftOut` writes exactly that row. Such an
item is not finished work being ignored; it is finished work nobody can place in a week.

**Two methods and two project lookups**, where one call returning both would have halved them. Kept
apart for M8's reason: each is a different question, and each re-reads the caller's standing rather
than trusting a check made somewhere else. Four indexed lookups across step 4's request is the
price of not having a copy of the membership rule to keep in step.

**One test looks vacuous and is the point.** Nothing in either query has ever heard of an estimate,
so `anUnestimatedItemCountsLikeAnyOther` asserts something the code could hardly get wrong today —
and it is decision 7's second row written down, which is the one place a throughput forecast is
better informed than the engine's. A later change that quietly counted only estimated work would
fail it.

**The tests live in `item` as `PlanHistoryTests`**, not under `forecast`, because that is where the
code is. What they are about is the pair of rules that point in opposite directions, and each is
asserted alone — a single "ignore archived" would pass one of them and look right in both.

**Counts.** 13 cases in `PlanHistoryTests`; 924 backend tests pass, with `WorkItemService` at zero
missed branches and zero missed instructions.

---

## Step 4 — On an endpoint ✅ *done*

**Goal.** `GET /api/projects/{projectId}/throughput?asOf=…` answers what the plan's own history
says.

- **A `GET` with a query parameter, unlike every forecast endpoint before it**, because it writes
  nothing and takes one date. `POST /api/projects/{id}/forecasts` creates a run; this creates
  nothing, which is decision 10's neighbour: the history is already stored, dated, and reproducible
  as of any day, so a row would be a cached answer to a question that is cheap to ask again.
- **The response** carries the window (weeks, first, last, best, worst), the backlog count, the
  five percentiles in weeks, the five dates, the seed, the rule name, and `limitations`.
  - **Weeks and dates both**, for M4's reason: the weeks are what the history produced and the
    dates are one presentation of them, and publishing only the date would hide the unit the answer
    was actually computed in.
  - **Percentiles are null together when the window is below the floor**, and the window is not —
  the reader gets the history and no forecast, which is M8's empty state in a second place.
- Any member, organisation from the token, `project_not_found` and `not_a_member` and no other
  refusal — plus `throughput_history_too_short`, which is the floor.

**Tests.** `ThroughputApiTests`: a plan with a known history answers known weeks; the window is
reported whether or not the forecast is; below the floor the percentiles are absent and the
refusal names the floor; the seed round-trips so two identical requests agree; tenant isolation on
a two-organisation fixture; an as-of date before the last completion is refused.

**Done when** a plan with three months of history answers, and one with three weeks says why not.

### As built — where it differs from the above

**The bullets contradict each other about the floor, and the contradiction is resolved toward the
window.** One says the percentiles are null below it and "the window is not — the reader gets the
history and no forecast, which is M8's empty state in a second place"; the last lists
`throughput_history_too_short` among the refusals. Both cannot hold: a refusal withholds the
window, which is the half a reader can judge for themselves. So it is a **limitation** and the
answer is a `200` carrying the history and a reason.

**There are three ways to have no projection, not one.** The floor is the one the bullets named.
The other two fall straight out of step 2: `remaining == 0` is refused by the pure function because
a plan with nothing left is not a forecast of no weeks, and a rate that does not clear the backlog
inside `MOST_WEEKS` would put every percentile on the horizon — a censored number that reads as a
date. Each says which it is, and in all three the window ships.

**`ThroughputLimitation` is its own enum and not `ForecastLimitation`.** That one is serialised into
`forecast_runs.outputs` and read back years later, which is why nothing may ever be deleted from
it; nothing here is stored at all. Two lifetimes and two rules about changing — one enum would have
handed the looser of them to the stricter, and the first person to tidy a throughput code out would
have taken a stored forecast with it.

**The seed is derived and is not overridable**, where the bullets say "published in the answer and
overridable". A run stores its seed because it is written once and replayed years later; nothing
here is written, so reproducibility means only that asking twice agrees — which a seed computed
from the plan, the day and the backlog gives without a knob. And decision 11 is explicit that this
endpoint takes a date and nothing else: an optional parameter on the one forecast whose selling
point is that it asks for nothing is exactly what that warns about. It is still published, so an
answer can be explained.

**`throughput_out_of_order` is a new problem code, and it had to be.** The bullets ask that an
as-of before the last completion be refused, and `Throughput.of` already refuses it — as an
`IllegalArgumentException`, which would have surfaced as a five hundred. It is named for
`progress_out_of_order`'s reason: two dates disagreeing about which came first, where nothing
downstream could tell the disagreement from a team that delivers ahead of time.

**And a second, larger gap the step opened by accident.** This is the first endpoint in the product
with a **required query parameter**, and a missing or unreadable one arrived as Boot's default —
`400` with a problem document carrying no `code`, which is precisely the failure
`ApiExceptionHandler`'s own documentation records: a refusal that loses its code is silent, because
a problem document is still a problem document. `handleUnusableParameter` closes it for both cases
at once, reporting `validation_failed` with the parameter under `errors` in the shape a field
complaint already has — `not_null` for absent, `invalid` for unreadable, both already in the
frontend catalogue. That is a fix to shared machinery rather than to M9, and it is here because M9
is what made it reachable.

**Its own controller, not a sixth method on `ForecastController`.** That one creates and reads rows
in `forecast_runs` — the history M10 walks of somebody deliberately re-forecasting. This creates
nothing and reads no run.

**Counts.** 16 cases in `ThroughputApiTests`; 940 backend tests pass, with every `Throughput` type,
the new refusal and `ApiExceptionHandler` at zero missed branches and zero missed instructions.

---

## Step 5 — The gap, beside the band ✅ *done*

**Goal.** Two dates, side by side, with the four things that differ named under them.

- **On the plan screen, inside `ForecastPanel`**, under the band rather than on a page of its own.
  `roadmap.md` is explicit that the gap is the deliverable, and a second page is two numbers nobody
  puts next to each other.
- **The comparison is presentational and is not a new quantity.** The screen shows the engine's
  date at the confidence already chosen, the throughput date at the same confidence, and the
  difference in days — and it does not name that difference as a measured figure, because it is a
  subtraction of two answers to slightly different questions (decision 7). What it names instead
  are the four differences, from the table in decision 7, with the two that are *live* for this
  run filled in: whether scope growth was assumed, and how much of the plan is unestimated.
- **The confidence control moves both.** M4's control reads percentiles already in the response and
  sends no request; this keeps that property by holding both sets of percentiles client-side, so
  the trade reads as one trade rather than two screens.
- **The window is on screen with the answer** — "13 weeks of history, best 9, worst 0" — because
  decision 5 says a reader who knows their team is the only one who can tell whether the window
  contains a bad week.
- **Nothing here averages, ranks or resolves the two.** When they disagree the screen says they
  disagree.

**Tests.** Both dates render at the chosen confidence and both move when it changes. A plan below
the floor shows the window and no second date, and the panel is otherwise unaffected — the
throughput read is its own request and a failure in it must leave the band alone, which is M8's
track-record line rule and is asserted the same way. The four differences render, and the two live
ones carry this run's numbers. Mocked by URL, since the panel now makes four requests.

**Done when** somebody can see that their plan says six weeks and their history says eleven, and
what the two are not agreeing about.

### As built — where it differs from the above

**The difference *is* named in days, which the bullets could be read as forbidding.** They say the
screen "shows … the difference in days — and it does not name that difference as a measured
figure". Built, the sentence is *"The history is 69 days later than the estimates — which is the
ordinary result, and the reason to look at both"*: the number is there because subtracting two
dates on screen is something a reader does anyway and doing it badly is worse, and what keeps it
honest is that it is a sentence rather than a figure, it sits above the four differences rather
than instead of them, and later says out loud that later is expected.

**Both "no difference" cases are sentences too.** A run that assumed no growth is short by unlisted
work exactly as the history is, and a fully estimated plan does not have decision 7's second
difference at all. The bullets only describe the rows when they differ; saying *"this is the one
difference the two do not have"* is more useful than leaving somebody to notice a line is missing,
and it is what the two extra branches cover.

**The throughput read is keyed on the plan and not on the run.** Everything else in this panel
reloads when a forecast is made; this answer moves when work is *finished*, so re-running the
engine leaves it alone. That is the same distinction M8's track-record line draws, arriving for a
different reason.

**And the step broke four tests in another suite, which is the finding worth keeping.**
`ProjectPage.test.tsx` already carried a comment explaining that `mockResolvedValueOnce` is the
wrong tool on a page with several reads in flight — written up when the *refusals* were fixed and
left in place for the four *successes*. Adding a sixth read is what finally made it fail, and it
failed only under coverage, which is slow enough to change the ordering. The four one-shot queues
are now `answerWrites`, keyed on the method the way `refuseWrites` already was, so the race is gone
rather than made less likely. **That comment predicted its own next failure and nobody acted on it**,
which is the argument for fixing a known-latent thing when you are next in the file.

**`NOTHING_SCORED` and `NO_HISTORY` moved into `src/test/render.tsx`.** Two suites now render the
panel, so two suites need doubles for the two reads it makes on mount — and a copy in each is how
one of them comes to answer `/throughput` with a project, which is precisely what the second suite
was doing before this.

**Counts.** 10 new cases, 434 frontend tests pass, at 100% of statements, branches, functions and
lines.

---

## Step 6 — Close out ✅ *done*

**Goal.** The record matches what was built.

- Each step's `### As built — where it differs from the above` is written in the change that built
  it. This step is the whole-milestone read.
- `roadmap.md`: M9 marked done with its own *As built*, and **decision 6's correction written into
  the M9 section itself** — the "implicitly absorbs … scope growth" sentence is wrong and the next
  reader must not inherit it. The *What is next* line moves to M10.
- `CLAUDE.md`: a throughput section in the shape of the others.
- `product-concept.md`: whatever it says about a second opinion, answered.
- **The review pass**, as in M5 through M8: read the milestone end to end and record what that read
  changed. Every one of the last four found something, and M8's found the milestone contradicting
  its own decision.

**Done when** the next reader can tell what M9 decided without reading its code.

### As built — where it differs from the above

`roadmap.md` marks M9 done, carries its own *As built*, and — the part that mattered — **has the
"implicitly absorbs … scope growth" sentence corrected in place**, because the wrong version is the
quotable one and a note in a plan nobody opens would not have stopped it being repeated.
`product-concept.md` says the same, and adds what the build learnt that "present them alongside"
did not anticipate. `CLAUDE.md` has a throughput section in the shape of the others. *What is next*
is M10.

### The review pass — what a read of the whole milestone changed

**It found three things, and one of them was this milestone contradicting an argument it had made
one step earlier.**

**`ThroughputService.historyOf` trusted the query's ordering.** It took the last element of the
completions to decide whether the request should be refused — while `Throughput.of`, written the
step before, deliberately *finds* the earliest rather than taking the front, and step 3's *As
built* says so in as many words. One of the two assuming an order and the other not is the worse of
both: the `order by` becomes load-bearing in one place and documented as not load-bearing in the
other. It is `Collections.max` now, with a test that passes today either way and exists so the
check is not what breaks when somebody relaxes the query.

**`WEEKS_AT` was `DATE_AT` under a second name.** A throughput answer carries the same five
percentiles under the same field names, so the alias held nothing — two names for one object, and a
reader having to check whether they agree. Gone, with the reason moved onto `DATE_AT`.

**And the one found by using it rather than by reading it.** A plan in the development database
held a task marked `DONE` with a completion date a week in the future — typed into this product's
own progress form, which puts no upper bound on the date. The endpoint refused correctly with
`throughput_out_of_order`, and the panel's `catch` then **took the entire comparison off the screen
with nothing anywhere saying why**. Two things were wrong: the catalogue comment asserted the code
was "reached by a client other than this one", which the data disproves; and "a failure must leave
the band alone" had been built as "say nothing at all". A refusal that names something fixable in
somebody's own plan is exactly the one to pass on, so the block now renders with the reason where
the second date would have gone, and the wording says which task to go and correct. **The
underlying gap — that nothing in the domain refuses a day that has not happened — is written up in
`roadmap.md` under *Dates the schema accepts and reality does not*, and is not M9's to fix.**

**One case the read added rather than corrected.** A run made before M4 has no dates, and the
history beside it still has one of its own: it now shows what it has rather than nothing, and a
test says so.

**A second pass for dead weight found two more, and one of them was another decision the build had
not kept.**

**`throughput_window_is_short` was emitted by the server and rendered nowhere.** Decision 12 asks
for a bar "below which the answer is published *and* flagged", and decision 5 says the window ships
so a reader can judge whether it contains their own bad week. The window shipped; the flag did not,
so an answer drawn from a quarter of history looked exactly like one drawn from three years. It now
sits under the date and says what the window alone cannot — that a short history is *why* the worst
week above may be missing. The sample plan generated while demonstrating the feature has 27 weeks
and would have shown a date with no warning at all, which is how it was noticed.

**`Throughput.weeks()` was called by no application code.** `project` resamples the array directly
because it is on the same class, and the response reports figures rather than a list of integers —
so a public accessor, its defensive copy and the test asserting that copy all existed for nobody.
That is `BandScore.percentile()` from M8's cleanup, in a second place. The step 1 assertions that
used it now go through the accessors a reader is actually shown, and they pin the same cases
exactly: `weekCount`, `completed`, `perWeek`, `best` and `worst` distinguish ten-then-nothing from a
steady two and a half just as an array comparison did. The order-independence case compares the two
*projections* instead, which is the stronger statement — two histories that project identically are
the same history.

**And one thing checked and deliberately left.** `throughput_excludes_unlisted_work` is also never
rendered as a limitation, and that is correct rather than an oversight: its content is on screen as
the first of the four differences, which says the history does not model growth and is short by
whatever nobody has written down. Rendering the code as well would say it twice.

**What the read did not find** is worth recording, since two decisions were the ones most likely to
have quietly drifted: nothing anywhere averages the two forecasts or picks between them, and no
week that had no completions is ever dropped from a history. Decisions 3 and 10 are intact, and
each has a test whose failure would be the first thing anybody saw.

---

## Migrations

**None, and this time it is not a design achievement so much as a consequence.** `completed_on` has
been required on every finished item since `V10`, and the backlog is the items that do not have
one. M6 and M7 stored nothing because their answers were replays; M8 added a table because the
evidence it read was being written over. This reads a column that is already there, already
required, and already dated.

**No index either.** `ix_work_items_tenant_project_archived` reaches one plan's items and the
stated ceiling is 500 of them, so the history is a scan of a few hundred rows behind an index that
already exists. Anybody about to add one should have a measurement first.

---

## Sequencing and risk

**The risk in M9 is that it is the first forecast with no oracle behind its claim.** Step 2 has an
exact case — constant throughput, an answer anybody can divide by hand — and that checks the
*sampler*. Nothing checks the *premise*, which is that next quarter resembles last quarter. M3 had
a closed form for its arithmetic and stated its assumptions on screen; this has the same
arrangement and one of the assumptions is much larger.

**The one that will actually go wrong** is decision 3, and it will go wrong in the implementation
rather than in the argument. Completion dates arrive as a list, the obvious thing to do with a list
of dates is group them, and grouping silently produces only the weeks that have something in them —
which inflates the rate by exactly the idle fraction and produces a *faster* forecast with nothing
looking wrong. The defences are the 2.5-not-10 test in step 1, decision 3, and this paragraph.

**The second is decision 6 being un-corrected.** The roadmap sentence is quotable and wrong, and
the failure mode is that somebody reads it, concludes M9 already handles scope growth, and compares
a throughput forecast against an engine run configured with 40% growth — producing a gap that is
mostly the growth parameter. Step 6 changes the sentence at source for that reason.

**The third is decision 10, and it will be proposed by somebody reasonable.** Two dates is
uncomfortable; averaging them is one line. It deletes the milestone.

**Two things that will look like bugs and are not.**

- **A throughput forecast much *later* than the engine's.** That is the expected result and the
  reason the milestone exists — the engine sums estimates of focused work and the history contains
  every meeting, incident and holiday. A team seeing them agree closely should be more suspicious
  than one seeing them differ.
- **A throughput forecast much *earlier* than the engine's, on a half-estimated plan.** The engine
  carries unestimated items at zero effort and says so; throughput counts them like anything else.
  The engine is the one under-reporting there, and decision 7's second row is what says so.

**What this milestone must not absorb.** The burn-up and the plain-language sentences are M10's.
Resources and allocation are M11's, and they are also what an organisation-wide throughput waits
on. Story points are the icebox's. The line to hold is that M9 counts what a plan finished, projects
what it has left, and puts the answer next to the other one.
