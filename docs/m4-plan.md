# M4 — A date you can commit to: implementation plan

> **Scope.** `roadmap.md` M4: a single confidence control (50 / 80 / 95%) resolving the engine's
> hours into a **calendar date**, and the working-day assumption that conversion needs — stated
> by somebody, stored with the run, and printed beside the answer. Excluded: holidays, part-time
> people and per-person calendars (M11), plain-language sentences and the burn-up (M10), inverse
> queries — "what do I cut to hit 1 November" (M7), and anything that changes what the engine
> samples (nothing here does).
>
> **How to read this.** Decisions first. The one that decides whether this milestone is right or
> merely finished is decision 2 — *whose* day the working day is — because getting it wrong
> produces a date that is too early by exactly the factor a team is proudest of, and nothing on
> screen would look wrong.
>
> **Why this is not optional, and why it is dangerous.** Nobody asks for a distribution; they ask
> what to promise. Until this exists the product's output is a band in hours, which is precisely
> the number a stakeholder cannot act on — and the reframing M4 buys is the whole negotiation:
> *"can we go faster?"* stops being answered with a capitulation and starts being answered with
> **"we can commit at lower confidence"**, which is the honest trade and is visible in one
> control. The danger is the mirror of that value. **A date is the first thing this product will
> emit that looks like a fact.** An hours band advertises that it came out of a model; "14
> November" does not, and it will be pasted into a plan with the assumption behind it left in the
> browser. M3's failure mode was a plausible number. M4's is a plausible *date*, which is worse,
> because a date is the thing people act on.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | The calendar, as a pure function ✅ *done* | M3 |
| 2 | Stating a calendar, and storing what was stated | 1 |
| 3 | A date on screen, and the trade behind it | 2 |
| 4 | Close out | 1–3 |

**M4 adds no sampling.** The engine already stores P10, P50, P80, P90 and P95 — P80 is there
because `m3a-plan.md`'s step 4 knew this milestone's control was 50/80/95, and that "a percentile
that is not stored is a re-run to answer". So nothing here touches `Engine`,
`Schedule`, or any run that has already happened. That is the smallest surface any milestone has
had since M1a, and it is worth noticing before somebody grows it.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Where the conversion lives | **A pure function in `forecast.model`**, beside the engine and not inside it. |
| 2 | Whose day the working day is | **One worker's**, never the team's — capacity is already in the number. |
| 3 | What the assumption is | **Hours per working day, plus a named calendar rule.** Monday to Friday, no holidays. |
| 4 | Where the start date comes from | **The caller states it.** The server's clock does not know what day it is where they are. |
| 5 | Whether dates are stored | **No — derived, and the *rule* is stored**, exactly as `priority_rule` is. |
| 6 | What happens to runs made before M4 | **They have no date at all**, and the columns are nullable. There is nothing true to backfill. |
| 7 | How hours divide into days | **Exactly, in `BigDecimal`.** A day boundary is a step function and a double is wrong by a whole day. |
| 8 | What the confidence control is | **A view over one run**, not a re-run. All five percentiles are already in the response. |
| 9 | Whether the assumptions are pre-filled | **The working day, no. The start date, yes** — one is a claim about a team, the other is a fact the browser holds. |
| 10 | What is on screen | **The date is the headline and the hours stay.** The hours are what the model produced; the date has an assumption on top. |

### Decision 1 — The conversion is a pure function, and not part of the engine

`forecast.model` is the only package in this application separated by *purity*. A calendar
belongs in it — it is arithmetic over primitives with an answer that can be checked by hand —
and it does **not** belong inside `Engine`, which stays in hours from end to end.

The distinction is not tidiness. The engine's output is a distribution over *effort*; the date is
one presentation of one percentile of it under an assumption the engine never made. Folding the
calendar into the sampling loop would put a working day inside ten thousand runs that do not need
one, and would make `Engine.VERSION` change every time somebody adjusts a holiday — which is
decision 9 of `m3b-plan.md` misfiring, because a calendar change is not a model change and must
not invalidate a stored forecast's numbers.

**The client is the other wrong place**, and more tempting: the browser knows the locale, the
timezone and today's date. But an assumption made in the browser cannot be stored on the run, and
an assumption that is not stored is one M10 cannot tell apart from a plan that moved. The
conversion is the server's; only the *formatting* is the browser's.

### Decision 2 — The working day belongs to one worker, not to the team

**This is the decision this milestone exists to get right.** `Schedule.finish` returns a
completion *time*: items run in parallel across `capacity` slots, and the number that comes back
is when the last one ends, not the sum of everybody's effort. Capacity is already inside it.

So the conversion is:

```
working days = ceil(hours / hours in one working day)
```

and the working day is **one person's** — six hours, or seven, or whatever a team believes one of
its slots actually delivers in a day. It is *not* the team's daily total.

The wrong version is seductive because it reads naturally: "we are four people doing six hours
each, so a working day is 24 hours". Dividing by that number **counts capacity twice** — once in
the scheduler, which already ran four things at a time, and once in the calendar — and it
produces a date four times too early. Nothing would look wrong: the band is unchanged, the
assumption is on screen, and the date is simply a lie. **The hint under the box says whose day it
is**, and step 1's test asserts that a plan at capacity 4 does not finish four times sooner than
the same plan at capacity 1 once both are converted.

### Decision 3 — Hours per working day, and a rule with a name

The roadmap's instruction is *"keep it crude and visible, and replace it in M11"*. So:

- **`workingHoursPerDay`** — one number a person states, like capacity.
- **A calendar rule with a name**, `five_day_week`: Monday to Friday count, Saturday and Sunday
  do not, and there are no holidays.

| Rejected | Why |
|---|---|
| Hours per calendar week, one number | Crude in the wrong way. It cannot say which day a plan lands on, and the answer this milestone exists to give is a day. |
| A configurable set of weekdays | A four-day week is real, and it is one more schema, one more control and one more thing to store per run for a case a team can express today by stating a shorter day. M11 owns the calendar properly. |
| Holidays now | The moment a holiday list exists it belongs to an organisation, gets edited, and every historical date moves — which is the whole of decision 5. It is M11's, with a new rule name. |

**The rule is a name and not a boolean** for the reason `priority_rule` is: two defensible rules
give two different dates from identical data, so a run made under one must never be silently
compared with a run made under another. When M11 adds holidays it adds a rule name, and every
run made before it keeps resolving under the rule it was made with.

### Decision 4 — The caller states the start date

A forecast has to start somewhere, and the obvious answer — "today" — is a trap in two ways.

The server does not know what day it is where the caller is. `Instant.now()` is a moment; a
*date* needs a timezone, and the one the server would pick is its own. This codebase has already
settled the shape of that argument: `started_on` and `completed_on` are dates precisely because
"there is no time of day in *we finished it on the twelfth*", and a server that invents one reads
back as the eleventh for every reader west of the meridian.

And "today" is often not the answer. A plan being forecast in October for a January start should
be forecast from January. Making the start an input costs one field and makes that ordinary.

So `startsOn` is a required date on the request, stored on the run. **If it is not a working day,
work begins on the next one** — a plan starting on a Saturday starts on the Monday, and says so
by finishing where a Monday start would.

### Decision 5 — The dates are derived; the rule is what gets stored

`forecast_runs` stores its six hour figures even though a seed and an engine version could
reproduce them. That is right, and it is right for a reason that does *not* apply here:
reproducing a percentile costs ten thousand simulations, and a run whose numbers could only be
recovered by re-running would be a row nobody could read cheaply.

A date costs a division and a walk over some weekends. **Store what is expensive or lossy to
reproduce; derive what is cheap and deterministic** — and the thing that makes it deterministic
across time is decision 3's rule name, not a stored copy of the answer.

| Rejected | Why |
|---|---|
| Five date columns beside the five hour columns | Pure redundancy — a deterministic function of four columns already on the row — and five more things to keep in step the first time the derivation is corrected. |
| Derive from a *setting* on the organisation | The failure this whole table exists to prevent. Somebody edits the working day, every historical date silently moves, and M10 reports a slide that never happened. The assumption is copied onto the run, exactly as capacity is. |

### Decision 6 — A run made before M4 has no date, and that is the honest answer

M3b's migration backfilled its three columns with zeros, and that was *true*: those runs really
did assume no common cause and no unlisted work. **The same move here would be a lie.** A run made
last week did not assume a six-hour day; it assumed nothing, because it produced no date at all.

So `starts_on`, `working_hours_per_day` and `calendar_rule` are **nullable**, a run without them
reports hours and no dates, and the screen says so in one line rather than showing a date that
nobody's assumptions produced. This is the same thread as M3b's retired limitation codes: history
keeps saying what it actually said.

It costs the response an optional field and the frontend a branch, and both are the price of not
inventing a record.

### Decision 7 — The division is exact, because a day boundary is a step

`ceil` turns a smooth quantity into a discrete one, so an error in the last bit of a double is
not a rounding difference — it is **a whole day**.

This was checked rather than assumed, and the first guess at an example was wrong. Over hours
from 1.00 to 4000.00, working days of 3.00, 3.50, 4.00, 5.00, 6.00, 6.50, 7.00, 7.50, 8.00 and
7.20 produce **no** disagreement between double and exact arithmetic. The ones that do are the
working days no binary fraction represents:

```
20.01 hours at 6.67 hours a day
  exact  : 3          -> 3 working days
  double : 3.0000000000000004 -> 4 working days
```

**6.67 is not a contrived input** — it is what somebody types for two thirds of a ten-hour day,
or a twenty-hour week across three days. At that working day there are 63 such hour values below
4000; at 3.01 there are 545. Both ends of the division arrive as `BigDecimal` — the percentile
from a `numeric(14, 2)` column, the working day from a `numeric(4, 2)` one — so doing it exactly
costs nothing and needs no conversion. `divide(..., 0, RoundingMode.CEILING)` is the whole of it.

### Decision 8 — The confidence control is a view, not a re-run

All five percentiles are in every response. So moving between 50, 80 and 95 changes a date on
screen **without a request going out**, and that is not an optimisation — it is the feature.

The reframing this milestone is for only works if the trade is immediate: somebody says "can we
go faster", and the answer is a control moving from 95 to 80 and a date moving two weeks earlier
while everybody watches. A round trip per click, or worse a re-simulation with a different seed,
would make the two numbers look like two different forecasts rather than two readings of one.

It also means the confidence level is **not** stored on the run. There is no such thing as the
confidence a run was made at; there is only the confidence somebody is reading it at.

### Decision 9 — The working day is not pre-filled; the start date is

Six required boxes is where this form arrives, and the instinct will be to fill some in. Two
decisions already say why the working day must stay empty: `m3a-plan.md` decision 6 refused to
default the capacity because **every default is a hidden claim**, and `m3b-plan.md` decision 7
kept its two parameters required even though they have a neutral value, because a box already
answered is a box nobody reads. A working day is the same claim about the same team — and it is
the number that turns a model's output into something a person will act on.

**The start date is different in kind, and the distinction is worth stating because it is the one
that keeps the rule honest rather than absolute.** Today's date is not a claim about this team —
it is a fact the browser holds and the server does not. Pre-filling it is telling somebody what
day it is, not what their week is worth. It stays editable, and a plan that starts in January is
one edit away.

**What is not the answer** is remembering the last run's assumptions and filling them in. That is
the same box-nobody-reads with a better excuse. The previous run's assumptions are already on
screen — M3b put them in the history line — so re-answering is copying something visible rather
than accepting something invisible. If that proves too tedious in practice, the fix is a
deliberate "same as last time" action somebody presses, not a form that fills itself in.

### Decision 10 — The date is the headline and the hours stay

The band in hours is what the model produced. The date is that number with an assumption on top,
and the assumption is exactly the kind that gets forgotten. Both are on screen: the date large,
the hours beside it, and the working day stated in the same sentence as the five assumptions M3b
already prints.

Removing the hours would leave nothing on the page that came out of the engine, and would make
the working day invisible in the way this milestone's own note warns about: *an assumption users
cannot see is one they will mistake for a result.*

---

## Step 1 — The calendar, as a pure function ✅ *done*

**Goal.** Hours become a day, by arithmetic somebody can check on their fingers.

- `WorkingCalendar` in `forecast.model` — no Spring, no JPA, no I/O, like everything beside it.
- `WorkingCalendar.RULE` = `"five_day_week"`, the name decision 3 stores.
- `finishOn(LocalDate startsOn, BigDecimal hours, BigDecimal hoursPerDay)` → `LocalDate`.
  - `ceil(hours / hoursPerDay)` in `BigDecimal`, decision 7.
  - Zero working days finishes on the first working day on or after the start.
  - Saturday and Sunday are not working days; a start on one moves to the Monday.
  - Refuses a working day that is not positive, and one longer than a day holds.
- Whole weeks are counted rather than walked, so a plan of four thousand hours costs the same as
  one of four.

**Tests.** Thirty hours at six a day from a Monday finishes that Friday. A plan with nothing left
finishes on the day it starts. Exactly one day's work finishes on the first day; one hundredth of
an hour more takes two. A Saturday start behaves exactly like the following Monday. Ten weeks of
work lands where a hand count puts it, weekends included. **20.01 hours at 6.67 a day is three
working days and not four** — decision 7, as a regression with its own name. Monotone in both
arguments: more hours never finishes earlier, a longer working day never finishes later. A
working day of zero, of a negative, and of twenty-five are each refused.

**Done when** a date can be produced from hours without anything having been assumed in private.

### As built — where it differs from the above

**Four refusals rather than two.** The bullets name a working day that is not positive and one
longer than a day holds. `finishOn` also refuses **negative hours**, and rejects a null for each
of its three arguments. Neither is reachable from step 2's caller — a percentile column is
non-negative by construction and the request will be validated before it gets here — but the
ceiling reads a negative as *zero days* and would return the start date, which is this codebase's
own rule about silently dropping input seen from the model side: a date arriving in place of a
failure is worse than the failure, because nothing downstream can tell it apart from an answer.
Each has a test, so the branch gate is paid rather than avoided.

**Twenty-four is allowed and twenty-five is not**, which the bullets left open. The boundary had
to be somewhere and step 2 already puts it there — `@Max(24)` on the request — so the two agree
rather than the model being looser than the API that guards it.

**The capacity assertion from decision 2 drives `Schedule` directly**, which the step's own test
list did not say and decision 2 did. Eight six-hour items finish in 48 hours at capacity 1 and 12
at capacity 4; both are converted at a **six**-hour day, giving eight working days and two. The
last line of that test is the one that matters: converting the capacity-4 hours at a
*twenty-four*-hour day — the team's total, the wrong reading — produces a different, earlier date,
and the test asserts the two are not the same. That is the bug written down beside the code that
avoids it, since it is the one no ordinary assertion can see.

**`WorkingCalendar.RULE` has a test of its own**, which reads as tautological and is not: step 2
stores that string on every run and step 3 reads it back, so the name is a wire value rather than
an implementation detail, and changing it is a migration rather than a rename.

**One method was written and deleted before it shipped.** An `isWorkingDay` predicate is the
obvious companion to `finishOn` and nothing calls it — the weekend skip is internal to the
arithmetic. A public method with no caller is a branch the coverage gate cannot honestly close
and a second answer to "what is a working day" waiting for M11 to disagree with.

---

## Step 2 — Stating a calendar, and storing what was stated

**Goal.** A run records the calendar it was read under, and can still be read without one.

- `V14__forecast_calendar.sql`: `starts_on date`, `working_hours_per_day numeric(4, 2)` and
  `calendar_rule varchar(40)` on `forecast_runs`, **all nullable and not backfilled** — decision
  6, and the deliberate opposite of `V13`.
- `POST /api/projects/{projectId}/forecasts` gains `startsOn` (required, a date) and
  `workingHoursPerDay` (required, `@Positive @Max(24) @Digits(integer = 2, fraction = 2)`).
- The response gains the three assumptions and five derived dates — `p10Date` … `p95Date` —
  every one of them absent for a run made before this milestone.
- `ForecastResponse` does the deriving through `WorkingCalendar`; nothing else in the application
  knows how a date is made.

**Tests.** A run stores all three and reports them back. The five dates ascend with their
percentiles. A run made with the columns null reports hours, no dates and no calendar — asserted
against a row written directly, since nothing can create one through the API any more. A working
day of zero, of a negative, of twenty-five, and a missing start date are each refused against
their own box. A start date the server would not have chosen — next January — comes back
unchanged. The rule name on the response is the one the run stored, not the one the code
currently has.

**Done when** no date this API publishes is separable from the assumption that produced it.

---

## Step 3 — A date on screen, and the trade behind it

**Goal.** The number people asked for, with the thing it rests on beside it.

- The forecast form gains two boxes: **when work starts** (pre-filled with today, editable) and
  **how many hours a working day holds** (empty, and its hint says *one person's day*, decision
  2).
- The result leads with a date at a chosen confidence — a control offering **50 / 80 / 95%** —
  and the hours band stays beneath it.
- Moving the control moves the date with no request (decision 8), which is the trade this
  milestone exists to make visible: lower confidence, earlier date, same plan.
- The working day joins the assumptions sentence M3b already prints, and the history line, for
  the reason the other five are there: two runs read under different calendars are not a date
  moving.
- A run made before M4 shows its hours and says in one line why it has no date.
- Dates render through `formatDay`, never `new Date(iso)` — the rule `CLAUDE.md` already states,
  and the one that decides whether a date is off by one for half the planet.

**Tests.** Both boxes are on screen and outside the disclosure; the working day starts empty and
the start date starts on today. Choosing 50, 80 and 95 shows three different dates and **sends no
request**. The hours remain visible at every confidence. A run with no calendar renders hours and
the explanation rather than a blank or a guess. A date one day before a month boundary formats as
that day in a timezone west of UTC. Every string comes from the catalogue.

**Done when** somebody can answer "can we go faster?" without leaving the screen or re-running
anything.

---

## Step 4 — Close out

- `roadmap.md`: mark M4 done, and with it **Tier 1** — the roadmap's own bar for "beats a
  spreadsheet" is a Monte Carlo rollup and a ship date at a confidence level, and both now exist.
- `roadmap.md`: record what M11 inherits — the working day is a stated number today and becomes a
  derived one when real availability arrives, which is a **new calendar rule name** rather than an
  edit to this one, and old runs keep resolving under `five_day_week`.
- `roadmap.md`: record what M10 inherits — a date per run under a stated calendar, which is what
  makes a sliding-date detector able to tell a plan that moved from a calendar that changed.
- `product-concept.md`: *Ship date at a confidence level* stops being design intent, and the
  *Deferred* note about mapping effort onto calendar dates gets its second half answered.
- `CLAUDE.md`: the working day is one worker's and capacity is already in the number; the
  division is exact because a day boundary is a step; a date is derived and the rule is stored;
  and runs made before a calendar existed have none.

---

## Migrations

| | |
|---|---|
| `V14__forecast_calendar.sql` | three **nullable** columns on `forecast_runs`, deliberately not backfilled |

**The interesting part is the absence of a default, and it is the mirror of `V13`.** That
migration backfilled zeros and could argue they were true. This one has nothing true to write: a
run made before M4 assumed no calendar, because it produced no date. A default here would invent
a claim on behalf of somebody who never made one, and it would do it in the one table whose whole
purpose is to say what was actually assumed.

---

## Sequencing and risk

**The risk in M4 is not arithmetic.** Every number here is checkable on paper, there are no
statistics, and step 1 is a few dozen lines with an answer a person can count out on a calendar.
That is exactly what makes it dangerous: the milestone will feel finished long before it is
right, because the part that can be wrong is not the part that can be tested easily.

**The one that will actually go wrong** is decision 2 — dividing by the team's hours rather than
one worker's. It is the natural reading of "how many hours a day do we work", it produces a date
that is wrong by the capacity factor, and every test that only checks *a date came out* passes.
The defence is the hint under the box, the assertion that converting does not undo capacity, and
this paragraph.

**Two things that will look like bugs and are not.**

- **A plan finishes on the same date at 80% and 95% confidence.** Short plans round to the same
  day; the band is in hours and the calendar is in days, so a difference smaller than a working
  day disappears. That is the resolution being honest, not the control being broken.
- **Adding a person does not halve the date.** Capacity moves the hours, and the hours already
  moved. If somebody expects the calendar to double the effect, they have decision 2's bug in
  their head rather than in the code.

**What this milestone must not absorb.** Holidays and per-person availability are M11, and the
moment either appears the calendar stops being a stated number and becomes a derived one — which
is a new rule name and a bigger milestone. Plain-language sentences ("85% likely to finish
between 12 October and 20 November") and the burn-up are M10, which is where an *audience* who
does not know what P90 means gets designed for. Inverse queries — "what do I cut to hit 1
November" — are M7, and they read this milestone's output rather than extending it. The line to
hold is that M4 turns one number into one date under one visible assumption, and stops.
