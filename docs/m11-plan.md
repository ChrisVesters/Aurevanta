# M11 — Resources and people: implementation plan

> **Proposal, 2026-08-19. Built, 2026-08-19.** Six steps, **two migrations**, and the first change to the engine's
> model since M3b — `Engine.VERSION` moves to **3**. Nothing here draws a random number: what
> changes is what the scheduler is allowed to start, not what anything is sampled from. Each step
> gains its `### As built — where it differs from the above` in the same change as its code, not at
> the end.
>
> **Read the measurements first.** They were taken before a line of this was designed and they
> reorder the milestone: `roadmap.md` warns loudest about the scheduling *heuristic* being a
> modelling assumption, and the heuristic is worth 0–9% where the thing it lists as an ordinary
> bullet — **resources being typed rather than pooled** — is worth 14–59%, in the optimistic
> direction, every time. The heuristic warning is not wrong; it is second-order, and this plan
> spends its risk budget accordingly.
>
> **Scope, and it is smaller than `roadmap.md`'s bullets.** That section lists five things and
> **two of them are cut here** — *availability* (working days, holidays, part-time) and
> *duration from effort* (decisions 1 and 5). What is left is one question asked properly: **we
> do not have four interchangeable people, we have two backend engineers and a staging
> environment, and only one thing can use the environment at a time.**
>
> **The largest liability in this milestone is not the scheduler, it is the version bump.** M6
> replays stored runs to explain them, M7 replays them to weigh cuts, and M10 compares them to
> each other and refuses across an engine change. A bump that cannot reproduce version 2 cuts
> every plan's history in two on the day this ships. Decision 2 is how it does not, and the
> measurement below is the evidence that it can: a resource-aware scheduler given one pool
> reproduces today's answer **to the last bit**, in ten thousand runs, with today's scheduler as
> the oracle.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | What a resource is, and what needs one ✅ *done* | M2's schema conventions |
| 2 | The scheduler stops counting slots ✅ *done* | 1, M3a's `Schedule` |
| 3 | A run that knows who was available ✅ *done* | 2, M3a's snapshot |
| 4 | Saying it: declaring resources, and what the forecast reports ✅ *done* | 3 |
| 5 | What if we hire someone? ✅ *done* | 3, M7's counterfactuals |
| 6 | Close out ✅ *done* | 1–5 |

**Two migrations are expected**, and they are the first schema this product has added since
`V16`. Everything M6 through M10 needed was already stored; this is not — a resource is a thing
somebody declares, and a requirement is a fact about an item that nothing can derive.

---

## The measurements this plan is built on

**Four runs, taken against the shipped engine before this plan was written.** The plans are
synthetic — sixty items in ten stages of six, each item a log-normal fitted from a P10 between
4 and 16 hours and a P90 three times it, ten thousand runs, the 80th percentile of the finish.
**Trust the direction and the order of magnitude rather than the exact percentages**: a
different plan shape moves every number here, and none of the four conclusions depends on which
shape was used.

### The one that justifies the milestone

The same plan, the same durations, the same priority order, six units of capacity — scheduled
first as **six interchangeable slots** (which is what this product models today) and then as
**two pools of three**, with each item needing one pool or the other. *Share* is how much of the
plan needs the first pool:

| Work needing the larger pool | Six interchangeable slots | Two pools of three | Later by |
|---|---|---|---|
| 50% | 238.1 h | 271.1 h | **+13.9%** |
| 70% | 238.1 h | 314.4 h | **+32.0%** |
| 90% | 238.1 h | 378.8 h | **+59.1%** |

**Pooling is a relaxation, so the error only ever runs one way.** Any schedule that is feasible
when the work is typed is feasible when it is not, which is why every row above is later and no
row could have been earlier. **A capacity number is therefore not an approximation of a team, it
is a lower bound on when they finish** — and it gets worse exactly as a team gets more
specialised, which is the direction real teams move in as they grow.

### The one that says the version bump is survivable

The same prototype scheduler, given **one pool of six** rather than two of three, against the
shipped `Schedule` at capacity six:

| | 80th percentile |
|---|---|
| `Schedule`, capacity 6 | 238.1 h |
| Resource-aware prototype, one pool of 6 units | 238.1 h |
| **Apart by** | **0.00%** |

**That is the whole of decision 2 in one line.** A resource-aware scheduler is not a different
model that happens to agree; given one undifferentiated pool it takes the same decisions in the
same order, so version 2 can live inside version 3 the way version 1 lives inside version 2 —
and every stored run keeps replaying.

### The one that corrects `roadmap.md`

Three defensible priority rules, on plans built to make the choice matter. **Without resources**,
sixty items in stages and then twenty-four items with three long poles among them and no
dependencies at all:

| Plan | Capacity | most-work-waiting | write order | shortest first |
|---|---|---|---|---|
| Ten stages of six | 2 | 564.8 h | +0.0% | — |
| | 4 | 293.4 h | +1.8% | — |
| | 8 | 243.5 h | +0.0% | — |
| Three long poles, no edges | 2 | 509.1 h | +0.0% | +2.0% |
| | 3 | 370.6 h | +0.0% | +1.1% |
| | 4 | 344.3 h | +0.0% | +4.4% |

**And with the resources typed**, two pools of three at 70%:

| Rule | 80th percentile | Against longest-first |
|---|---|---|
| longest first | 314.4 h | — |
| shortest first | 309.0 h | −1.7% |
| write order | 287.1 h | **−8.7%** |

**Two things fall out and both are worth having.** The rule is worth **0–4.4%** where capacity
is one number and up to **8.7%** once it is typed — so resources make the heuristic matter
*more*, and it is still the smaller of the two by between two and seven times, depending on how
specialised the team is. And
`most_work_waiting` is **identical to write order on a plan with no dependencies**, which is
not a bug: priority is the work waiting behind an item, and on a graph with no edges nothing is
waiting behind anything. It bites on graphs and nowhere else.

### The one that corrects `roadmap.md` again

What a forecast costs today, and how much of that is the scheduler at all — 500 items, ten
thousand runs, on the machine this was written on:

| | |
|---|---|
| 100 items, 10,000 runs | 95 ms |
| 500 items, 10,000 runs | 344 ms |
| — of which drawing the durations | 221 ms |
| — of which scheduling | **186 ms, or 46%** |

`roadmap.md` says this milestone is "the first genuinely expensive thing in the plan … seconds
rather than milliseconds". **Not from resources.** Scheduling is under half of a forecast that
takes a third of a second, and a resource check is a loop over the pools an item names — small,
constant, and inside a decision the scheduler already makes. What *would* cost seconds is
availability, which is cut (decision 1) and is where that warning should be re-read when it is
built.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What M11 is for | **Concurrency, not calendars.** Availability is cut and becomes the next milestone. |
| 2 | What happens to `Engine.VERSION` | **3, containing 2 exactly** — one pool, no requirements, byte for byte. |
| 3 | Resources or types | **One concept.** A resource is a named pool with a unit count; a person is a pool of one. |
| 4 | What an item may need | **Units of any number of pools, held for its whole duration.** |
| 5 | Whether two people make an item faster | **No.** Effort is what somebody estimated for the task; occupancy is not speed. |
| 6 | What an item with no requirement does | **Takes one unit of whichever pool has one free**, in declaration order. |
| 7 | Whether a blocked item holds its place | **No — non-delay.** The next thing that fits starts. |
| 8 | The priority rule | **Unchanged, and no second rule.** Measured at 0–9%, against 14–59% for the resources. |
| 9 | What a run stores | **The whole declaration**, copied onto the run like the estimates. |
| 10 | What happens to `capacity` | **Derived from the pools when there are any**, and unchanged when there are none. |
| 11 | What M11 must not become | **Not assignment, not a Gantt, not a timesheet, not availability.** |
| 12 | Whether people can see it | **A resource is not a person's account**, and nothing here reports on anybody. |

### Decision 1 — Availability is cut, and it is the next milestone rather than the icebox

`roadmap.md` lists *availability — working days, holidays, part-time allocation* and calls it
"unglamorous and the place where forecasts quietly stop matching reality if it is skipped". It is
cut from here, and the reason is not that it matters less.

**It is the change that makes the engine date-aware, and everything else in this milestone is
not.** Today the engine is a pure function over effort in hours; M4 lays a calendar over its
answer afterwards, which is exactly why a calendar change is not a model change and why
`WorkingCalendar.RULE` can name a rule that arrives later without moving a date already
published. A person who is away next Thursday cannot be modelled in hours: the scheduler has to
know what day it is, which means dates enter `Schedule`, `Engine`, the snapshot and the replay —
and the cost measurement above stops applying, because a walk over a calendar per item per run is
not a constant check.

**So the split is where the engine's unit changes**, which is the same seam M3a and M3b were
split on and the same one M4 declined to cross. What ships here answers *how many of these can
be going at once, and who can do which* — in hours, as today. What comes next answers *and when
is anybody actually there*.

**It arrives as a new calendar rule name**, never as an edit to `five_day_week`. `roadmap.md`
already says so and M4 built the mechanism for it; this plan does not spend it.

| Rejected | Why |
|---|---|
| Availability in this milestone | Doubles the model change and invalidates the cost measurement, for a feature nobody can use until resources exist. |
| Part-time as a fractional unit | Units are whole things. Half a person is an availability question wearing an integer's clothes. |
| Availability in the icebox | It is the reason the roadmap wants resources at all. Naming it as next is the honest place. |

### Decision 2 — Version 3 contains version 2, byte for byte

`Engine.VERSION` moves to 3 because the scheduler changes. **The measurement above is the
commitment that comes with it**: a plan that declares no resources is scheduled as one pool of
`capacity` units with no requirements, and that reproduces today's answer exactly — the same
draws in the same order and the same decisions from them.

**This is the third time this trick has paid and it is worth naming as a pattern.** Version 1 is
version 2 with both M3b parameters at zero; a forecast with an observer attached is the same
forecast; and now version 2 is version 3 with one pool. In each case the new thing is strictly a
generalisation, and the test is byte identity rather than agreement to a tolerance.

**What it buys is everything downstream of a stored run.** M6 replays a run to rank what widened
it and refuses if the replay disagrees; M7 replays it to weigh cuts; M10 replays a pair of them
six times over to account for a movement. Without containment, every one of those stops working
for every run made before this ships — silently for the reader, since what they would see is a
refusal they cannot act on.

**What it does not buy is comparability, and that is correct.** `Comparison` refuses across an
engine version and will refuse across 2→3. A plan re-forecast under resources for the first time
genuinely answered a different question, so M10's drift window ends at that boundary and its
decomposition declines the pair. **Replay yes, comparison no** — and the two are different
questions that a reader should be told apart, which step 4 owns.

### Decision 3 — A resource is a pool with a name and a count, and that is the only concept

`roadmap.md` describes resources *and* types: "a named thing with finite capacity … People are
one type; environments, licences and equipment are others", plus type-level requirements ("any
backend engineer"). **That is two hierarchies where the measurement needed one.** What moved the
answer by 14–59% was work being unable to cross from one pool to another; nothing in it depended
on which individual did what.

So there is one table. A resource has a name, a number of units, and optionally a person:

- *Backend engineers × 3* — the type-level requirement, expressed directly.
- *Staging environment × 1* — the licence-and-equipment case, expressed identically.
- *Ada × 1* — a person, which is a pool of one and needs no second concept.

**A parallel hierarchy is what M1a spent a whole milestone undoing**, in a different form: the
cheap modelling choice that has to be unpicked later. Individuals-inside-types can be added by
splitting a pool into pools of one, and nothing about the schema has to change for it.

### Decision 4 — An item may need units of several pools at once, for its whole duration

A task that needs a backend engineer *and* the staging environment needs both, together, from the
moment it starts until it ends. That is the ordinary resource-constrained scheduling model and the
loop cost is one pass over the pools an item names.

**Held for the whole duration and never partially released**, which follows from the
non-preemption `Schedule` already commits to: once something starts it runs to completion. A model
where the environment is let go halfway would need to know what fraction of a task is what, and
nothing in this product records that.

### Decision 5 — Two people on one item does not make it faster

`roadmap.md`'s *duration from effort* bullet says that with an allocation, "M2's stored effort
finally converts to duration honestly". **This plan disagrees, and the disagreement is the
sharpest thing in it.**

Requiring two units of a pool means the item **occupies** two units. It does not halve. Three
reasons, and the third is the one that decides:

- **Estimating a task already implies who does it.** M5 asks for a bad case, a good case and a
  typical case *for the work*, and nobody answering those questions is imagining an unspecified
  number of people. Dividing by a headcount afterwards re-uses one number as two.
- **Brooks, and it is not a joke about a famous book.** Effort divided by people is linear
  speed-up with no communication cost and no ramp, which is false for every task anybody has
  measured. Modelling it as true is precisely the confident-garbage failure this product exists
  to prevent, in the one place a reader would never look for it.
- **There is no oracle.** Every modelling decision in M3 is checkable against arithmetic that
  exists outside this codebase. "Two people finish this in 60% of the time" is checkable against
  nothing, and a number nobody can check is the shape of thing `m3a-plan.md` refuses.

| Rejected | Why |
|---|---|
| `duration = effort / units` | Brooks. And it makes the answer move by exactly the factor nobody stated. |
| A configurable speed-up curve | A knob whose value nobody can source is a claim the server makes on somebody's behalf. |
| Effort *per unit* required | Doubles what every estimate means, silently, for every item already estimated. |

**`roadmap.md`'s bullet is corrected in place** at close-out, since the wrong version is the
quotable one.

### Decision 6 — An item that names no resource takes one unit of whatever is free

The common case on the day this ships is a plan of five hundred items and no requirements on any
of them. Three answers were possible and two are wrong:

- **Unconstrained** — an item nobody assigned can always start. A plan's first forecast after
  declaring resources would then be wildly optimistic, because most of it ignores the constraint
  entirely.
- **Required** — nothing may be forecast until every item names a pool. That is five hundred rows
  of data entry before the first answer, which is how a feature goes unused.
- **One unit of whichever pool has one free**, taken in declaration order. Generic work that
  anybody can pick up, which is what an unannotated item *is*.

The third also has the property decision 2 needs: with one pool declared and no requirements
anywhere, every item takes one unit of it and the schedule is today's capacity model exactly.

**The declaration order is part of the rule and is stated**, because it decides which pool a
generic item eats into and therefore what is left for the specialised work behind it. It is
deterministic, explicable in a sentence, and reported: a run says how many items named nothing,
the way it already says how many carry no estimate.

### Decision 7 — A blocked item does not hold its place

With one number for capacity, "the highest-priority startable item" is unambiguous. With pools it
is not: the next item in priority order may need a pool that is full while the one behind it needs
one that is free. **The scheduler starts the one that fits** — a non-delay schedule.

The alternative is to leave the slot idle until the specific pool frees up, which models a team
that will not do available work because a particular person is busy. Real teams do not, and a
model that assumed they did would report dates later than the plan for a reason nobody could act
on.

**It is a new degree of freedom that resources introduce, so it is part of the priority rule**
rather than a detail underneath it. The rule keeps its name, and that is exact rather than
convenient: with no resources declared nothing is ever selectively blocked, so a run made under
`most_work_waiting` before this milestone and one made after take the same decisions. A name means
what it meant.

### Decision 8 — The priority rule is unchanged, and there is no second one

Measured at 0–4.4% without resources and up to 8.7% with them, against 14–59% for the resources
themselves. `Schedule.PRIORITY_RULE` stays `most_work_waiting`, no alternative ships, and the
mechanism for a second one — a name, stored, refused across by `Comparison` — stays exactly as
M10 left it.

**What changes is how loudly this is described.** `roadmap.md` calls the heuristic the thing users
"will not intuit" and puts it at the top of *what makes this hard*; the measurement puts the worst
case at a twelfth of the finish, on the plan built to make it show, with the resources typed. The
three sentences that matter are kept — it is an assumption, two rules give two answers, the rule
is stored — and the emphasis moves to where the error actually is.

### Decision 9 — A run stores the whole declaration

`ForecastInputs` already copies the estimates, the statuses and the edges onto the run, because a
replay years later must not read today's plan. **Resources are the same and more so**: a team
changes faster than a plan does, and a run whose replay read today's pools would be an exact
account of a forecast nobody was ever given.

So the snapshot gains the pools and the requirements as they stood. **This is M6's decision 1
pointing the other way and it is worth being precise about why.** M6, M7, M8 and M10 all derive
rather than store, because what they compute is a function of things already written down. This is
not derived from anything — it is an input somebody stated, and inputs are what a run has always
copied.

### Decision 10 — Capacity is derived when there are pools, and untouched when there are not

Two knobs would be one too many: a plan with pools does not also need a global cap, and a reader
told "capacity 4" *and* "3 backend, 2 frontend" cannot tell which is binding.

- **No resources declared** — the capacity box is what it is today: required, no default, printed
  beside the answer.
- **Resources declared** — the box disappears and `capacity` is the sum of the units. It is still
  stored on the run, so `forecast_runs.capacity` keeps meaning what it has always meant and every
  screen that prints it keeps working.

### Decision 11 — What M11 must not become

- **Not assignment.** Nothing here says who *is* doing a task, only what kind of thing it needs.
  A per-person work queue is a board, and *What Aurevanta is not* rules those out in as many words.
- **Not a Gantt.** The scheduler knows when every item started in every run; drawing one bar per
  item is a chart of one simulation out of ten thousand, which is the single-point estimate this
  product exists to replace wearing a picture.
- **Not a timesheet.** A resource has units and a name. It does not have hours logged against it.
- **Not availability.** Decision 1.
- **Not a second capacity model.** Decision 10.

### Decision 12 — A resource is not a person's account

A resource may link to a `users` row, and that link is a convenience for finding the person, not a
permission and not a report. **Nothing in this milestone tells anybody how busy anybody is.** The
moment a screen ranks people by utilisation, this product has become a management tool aimed at
individuals rather than a forecasting tool aimed at plans — which is a different product with
different ethics, and it is not this one. M8 already made the same call for calibration: *people
are named and never ranked*.

---

## Step 1 — What a resource is, and what needs one ✅ *done*

**Goal.** The schema, and the two endpoints that fill it.

- **`resource` is a new feature package** — entity, repository, service, web types — beside the
  other domain packages and pointing only at `project`'s conventions, not at `item`.
  - `resources` — `tenant_id`, `name`, `units` (positive), `archived_at`, and a nullable
    `user_id` that does not cascade, for the reason `estimates.estimator_user_id` does not:
    removing somebody must not delete the model of the team they were in.
  - **Names are not unique**, for M1a's reason and the same one `projects` has: two pools called
    "Designers" is somebody's business, and the id is what addresses them.
  - **Archived, never deleted**, like everything else in this domain. A pool a run was made
    against has to stay readable.
- **`work_item_resources`** — `work_item_id`, `resource_id`, `units`, unique together. It lives
  in `item`'s package or its own; the arrow points from the requirement to both, and neither
  points back.
- `GET|POST /api/resources`, `PATCH|DELETE /api/resources/{id}` (archive), and
  `PUT /api/items/{id}/resources` for the requirements of one item — a whole set at a time,
  because a requirement is meaningless alone and a screen edits them together.
- **Any member may do all of it**, like everything else about a plan. Roles govern administration.

**Tests.** A pool with no units is refused and a pool with negative units is refused. Two pools
with one name are both allowed. Archiving a pool that a requirement names leaves the requirement
readable. A requirement naming a pool in another organisation is not found. The unique pair
refuses a second requirement on the same item and pool rather than silently adding units.

**Done when** a plan can say *this needs a backend engineer and the staging environment* and read
it back.

### As built — where it differs from the above

**Archiving is a `POST` and there is no `DELETE`, which the bullets got wrong.** They say
`PATCH|DELETE /api/resources/{id} (archive)`, and `ProjectController` settled this two
milestones ago in as many words: *a `POST` rather than a `DELETE` because nothing is deleted*.
A pool has a second reason of its own — a run stores the declaration it was scheduled under, so
a pool that had vanished would leave that snapshot describing an identifier. It is
`POST /{id}/archive` and `POST /{id}/unarchive`, exactly as a plan is.

**The table is `requirements` and not `work_item_resources`.** The plan named it for the two
ends it joins; every other table here is named for the concept — `estimates`, `dependencies` —
and `dependencies` is a join with a payload too. One word across the table, the entity and the
package is worth more than a name that describes the foreign keys, and *requirement* is the word
the roadmap and this plan both already use.

**Its own package, and the arrows are why.** A requirement points at `item` and at `resource` at
once, so putting it in either would have made those two depend on each other for the sake of a
join. `estimate` and `dependency` are the precedent — a thing that hangs off an item is a
package that points at `item` — and this hangs off two.

**Two endpoints the bullets did not name, and one they did that has no reader.**
`GET /api/projects/{id}/requirements` is `estimate`'s rule arriving: a screen drawing five
hundred rows reads a plan's worth at once, and asking per item would be five hundred requests to
draw one page. `GET /api/items/{id}/requirements` is what the form editing one row reads back.
And `GET /api/resources/{id}` was **built and then removed** — a team is read as a team, every
screen that wants one wants the list, and the coverage gate is what found it: the only exercise
that endpoint had was its own refusal.

**`PUT` is the first one in this application** and it earns the verb: replacing a whole set is
what a `PUT` means, and the alternative — add a line, change a line, remove a line — would make
*it needs these two things* a sequence a reader has to reassemble rather than a fact arriving
once. An empty set is a claim rather than a mistake, and it is how somebody says this is generic
work anybody can pick up.

**A pool may be named after a person, and that needed a refusal of its own.**
`person_not_a_member` is about the person somebody named, where `not_a_member` is about the
caller; one code for both would have somebody re-authenticating over a mistyped colleague. It is
checked on the way in and never on the way out, which is the estimator rule exactly: a pool named
after somebody who has since left keeps naming them, because it records what a team was.

**`MembershipService` gained one method rather than this reaching around it.** `select` records
a visit, because choosing an organisation to act under *is* a visit, and naming a resource after
somebody is not — so `memberOf` is the lookup without the side effect, and `hasMember` is now
expressed in terms of it rather than being a second copy of the query.

**Four accessors were written and deleted.** `Resource.getTenant`, and `Requirement`'s `getId`,
`getTenant` and `getCreatedAt`: nothing reads them, and every existing entity in this codebase
carries none. The coverage gate is what says so — the accepted exclusion is accessors *no logic
reads*, and the honest way to take it is to not write them until something does.

**Counts.** 19 cases in `ResourceApiTests` and 14 in `RequirementApiTests`; 1,050 backend tests
pass, with every new class at zero missed branches and zero missed instructions. Three new codes
reached the frontend catalogue and `BACKEND_CODES`, which is the list that claims to be the whole
contract — 461 frontend tests still pass at 100%.

---

## Step 2 — The scheduler stops counting slots ✅ *done*

**Goal.** `Schedule` takes pools and requirements, and answers exactly what it answers today when
given neither.

- **`Schedule.of` gains the declaration**: units per pool, and per item the units it needs of
  each. Everything it prepares once — the graph, the topological order, the priority key — is
  unchanged, because none of it depends on resources.
- **`finish` checks the pools before starting** and skips what does not fit (decision 7), taking
  units on start and returning them on completion. That is the whole change to the loop.
- **Work already under way is running whatever the pools say**, exactly as it already runs over
  capacity: it has visibly begun, and a model that says otherwise is wrong about the world.
- **An item with no requirement takes one unit in declaration order** (decision 6).
- **Discovered work inherits its parent's requirement.** Work found behind a backend task is
  backend work; anything else would need a rule about what an unlisted item is made of, and
  nothing measured says what.

**Tests.** **The oracle is byte identity**: one pool of *n* units with no requirements produces,
for the same durations, exactly what `Schedule` at capacity *n* produces today — asserted run for
run over ten thousand draws, not to a tolerance. Two pools of three against six interchangeable
slots is later, and by more as the imbalance grows: the measurement above, made into a case. An
item needing two units of a pool of two runs alone. An item needing more units of a pool than that
pool holds is refused when the schedule is prepared — rather than never starting, in a loop whose
termination argument has never had to consider an item that cannot fit. A blocked item does not
stop the item behind it starting.

**Done when** the same plan, scheduled two ways, differs only where a pool is the reason.

### As built — where it differs from the above

**The containment is proved against numbers captured before the change, not against the new
code.** The bullets ask for byte identity between one pool of *n* and capacity *n*, and the
honest way to get it was to read six exact finishes out of `Schedule` **before** it knew what a
resource was — a twelve-item plan with lags, work under way, two pieces of discovered work and
five drawn duration sets — and assert them afterwards. `answersExactlyWhatItAnsweredBeforeThereWereResources`
holds them to the last bit. The capacity method now delegates to the pool one, so a test
comparing the two would have compared two spellings of one call; this compares the scheduler
against its own past.

**`Resourcing` is the declaration, and it holds the refusal that the loop's termination depends
on.** Work asking for more of a pool than that pool holds can never start, and `Schedule.finish`
has no guard against that — its termination argument is that with nothing running every unit is
free, so anything that can ever start can start then. The refusal is at declaration time for
that reason rather than as input hygiene.

**A performance guard caught a real regression, which is exactly what it is for.** Stepping over
work that does not fit means the start loop can no longer stop at the first thing it cannot
start — and the first version had no reason to stop at all, so it walked every ready item on
every event. `EngineTests.aPlanAtTheCeilingForecastsInsideARequest` failed at **2.08 seconds**
against its two-second ceiling, on a plan that had been taking about three hundred milliseconds.
`anyFree` is the fix: it restores the old bound exactly in the one-pool case, and the same plan
now runs in **347 ms** — against 344 ms measured before this milestone, so the resource check
costs nothing measurable where nothing is typed.

**One branch of `fits` was dead and the coverage gate found it.** With `anyFree` guarding the
loop, work that names nothing always fits, so searching the pools for a free unit was a search
whose answer was already known. It is a `return true` with the invariant written above it.

**Work already under way takes its units and may push a pool below nothing.** The bullets say it
runs "whatever the pools say" and this is what that means arithmetically: the counts go negative
and nothing new starts until enough come back. It is the same thing capacity did — `inFlight`
could exceed it — and the single-pool equivalence depends on it.

**Under-way work is also the only caller that may take a unit nobody has.** That is why `take`
has a fallback that decrements the first pool regardless: it is unreachable from the scheduling
loop, which asks `fits` first, and reachable only from work that has visibly begun.

**Counts.** 11 new cases in `ScheduleTests` and 9 in `ResourcingTests`; 1,070 backend tests
pass, with `Schedule` and `Resourcing` at zero missed branches and zero missed instructions. No
API, no schema and no engine change — `Engine` still calls the capacity method, which is step
3's to move, and `Engine.VERSION` is still 2 until it does.

---

## Step 3 — A run that knows who was available ✅ *done*

**Goal.** The engine, the snapshot and the stored run, and the version bump that survives them.

- **`Engine.run` takes the declaration** and passes it to `Schedule`. It draws nothing new: a
  resource takes no randomness, so **the generator's next value after a run is untouched** — the
  property `TeamFactor.NONE` and `ScopeGrowth.NONE` are already asserted on.
- **`Engine.VERSION` becomes 3**, and version 2 is version 3 with one pool and no requirements
  (decision 2).
- **`ForecastInputs` gains the pools and the requirements** as they stood at the moment of the run
  (decision 9). `ForecastSnapshots` keeps its own `ObjectMapper`, so nothing about the API's
  configuration can change what an old snapshot means.
- **`capacity` is derived from the pools** and still stored (decision 10).
- **A new limitation, `unassigned_work`**, for a plan whose items mostly name nothing — the
  shape `unestimated_items` already has, and for the same reason: a reader has to know which
  part of the answer rests on a declaration nobody made.

**Tests.** **Every stored run made under version 2 replays under version 3 and reproduces its
stored percentiles**, which is `ForecastApiTests`' existing assertion doing the job it was written
for — this is the milestone it was waiting for. A run with resources declared reports version 3
and one without still reports 3, because the code is the same code. `Comparison` refuses a 2/3
pair. M6's ranking, M7's cuts and M10's decomposition all still answer for a version 2 run.

**Done when** a forecast made last month can still be explained, and one made today knows the
team is not interchangeable.

### As built — where it differs from the above

**`Engine.run` kept a capacity form, and that is what proves the containment rather than
weakening it.** The alternative was to make every caller build a one-pool declaration, and the
cost would have been editing every test in the engine's suite — including the ones holding exact
percentiles to seventeen digits. Those tests are the evidence: they are **untouched** and still
pass, so version 3 gives version 2's answers because it is the same arithmetic, not because
somebody adjusted an expectation.

**The declaration lives beside the items rather than inside them.** A requirement could have been
a field on `PlannedItem`; it is a top-level list keyed by item, because M10's decomposition
rebuilds that list twice — once with the newer progress and once with the newer estimates — and a
requirement belongs to neither question. Held outside, it is untouched by both and moves with the
plan when the plan does. **The cost is that a requirement change lands in the `SCOPE` term of a
decomposition**, which is not quite what that term names; the terms still sum, and naming it here
is cheaper than a seventh state in the account.

**Capacity became one of two ways to say one thing, and neither is defaulted.** The bullets say
capacity is derived from the pools; what they do not say is what happens to the *field*. It is
optional at the request and decided by the service, which is the only place that knows whether
the organisation has described a team — so `capacity_required` when it has not and
`capacity_not_applicable` when it has. Refused rather than ignored, which is
`progress_not_applicable`'s rule: silently dropping input is worse than refusing it, because the
person is not told they have been overruled. A `@NotNull` on the field would have made the first
forecast after describing a team a refusal about a box that should no longer be on the screen.

**`unassigned_work` fires only where it can change the answer**, which is where there is more
than one pool. With one pool — and with none, which is a capacity — naming nothing and naming
that pool are the same claim, so the warning would have fired on every forecast anybody ran and
meant nothing on any of them. No threshold, no counts, and no columns to carry them.

**A requirement on a put-away pool is left out and said out loud** — `requirements_on_archived_resources`
— which is exactly what an arrow into archived work already does, and for the same reason: a
resource the organisation no longer has cannot be waited for.

**One gap is left open deliberately, and step 6 should settle it.** `Comparison` sees the *size*
of a team, because capacity is stored and derived from the pools — so hiring somebody is a
`CAPACITY` difference and M10 reports it as a changed question rather than as drift. What it
cannot see is a **reshuffle that keeps the total**: three backend and three frontend becoming two
and four is invisible, and the date it moves would be read as a plan sliding. Closing it means
putting the team's shape where `ForecastTerms` can reach it cheaply — a column, since the drift
detector walks a whole history and parsing every snapshot to compare two runs is not what that
read costs today. It is written here rather than done here because the milestone's own decision 3
lists four things and this would be a fifth, and because M10's review pass is the shape of thing
that should decide it.

**Counts.** 9 new cases in `ForecastApiTests`; 1,071 backend tests pass, with `Engine`,
`Schedule`, `Resourcing`, `ForecastInputs` and `ForecastService` at zero missed branches and zero
missed instructions. Four new codes reached the frontend catalogue — two refusals and two
limitations — and 461 frontend tests still pass at 100%.

---

## Step 4 — Saying it: declaring resources, and what the forecast reports ✅ *done*

**Goal.** A screen for the team, a control on the item, and a forecast that says what it assumed.

- **A resources page**, under `/app/resources`: name, units, and an optional person. Reachable by
  every member. It is a list and a small form, and it is deliberately not a screen about people.
- **The requirement sits on the item row**, beside the estimate and the progress: a small form
  listing the pools and how many units, opened from the row like the other four.
- **The forecast panel says what it scheduled against.** The pools and their units join the
  assumptions already printed beside the number — they are exactly that kind of thing, and a band
  read without them is a band about a team the reader has to guess at.
- **The capacity box disappears when there are pools** and its hint says why, rather than being
  disabled.
- **A run made under a different engine version says so where a reader will look**: M10's earlier
  forecasts list already names what each run assumed, and this is the first time two runs of one
  plan can be genuinely incomparable. The sentence has to be about the plan — *this was forecast
  before your team was described* — and not about a version number.

**Tests.** The panel prints the pools it was run against. The capacity field is absent, not
disabled, once a pool exists. An item with no requirement says so rather than showing an empty
list. The earlier-forecasts list explains an incomparable run rather than dropping it or showing
a broken comparison.

**Done when** somebody can read the number and tell which team it was about.

### As built — where it differs from the above

**A third migration, and the first bullet could not be honest without it.** *The forecast panel
says what it scheduled against* means the run's own team, not today's — reading an old number
beside a team it never had is exactly the mistake `V14` refuses for calendars. The declaration
was already in the snapshot, and that was not enough: two readers want it per run, and the
snapshot beside it holds five hundred items and every range anybody typed. `V19` copies the
pools and their units onto the row, null on every run made before there was a team to describe
and `[]` on one made by an organisation that has described none — two different facts, and the
second is the one a capacity still answers.

**It closed step 3's gap on the way past, which is why it is here rather than in step 6.**
`Comparison` could see the *size* of a team and not its shape: three backend and three frontend
becoming two and four holds the capacity still, moves the date, and would have been read as a
plan sliding. With the column in place that is a `Difference.RESOURCES` and thirty lines, so
leaving a known-wrong comparability rule standing for one more step would have been the worse
trade.

**The capacity box is absent rather than disabled**, which the bullets ask for — and the
sentence in its place says what answers the question instead. Nothing else was needed to make
the request valid: an absent box already sends `null`, because `numberField` was written for
exactly that.

**Names come off the organisation's list and units off the run**, which is M6's split for the
work it ranks, arriving here whole: a pool renamed since is not a thing that moved, one put away
since is marked, and one this organisation no longer holds at all says so rather than rendering
as a blank. Asserted on all three.

**A requirement is a number against every pool rather than a list somebody adds to**, because
the endpoint replaces a set and a form that added lines would make one fact into a sequence. The
form says what an empty set means where somebody is deciding — *leave them all empty and anybody
can pick this up* — rather than leaving it to be discovered as a limitation beside a date.

**Two flaky tests were written and fixed, and both were mine.** A fixture declaring two pools in
the same instant ordered them by identifier, which is stable for one set of rows and arbitrary
between two fixtures — so a case asserting *which* pool came first passed and failed on
alternate runs. And a page helper that waited for the heading rather than for the listing raced
its own request. Neither would have been found by a single run, and declaration order is part of
the model rather than of a listing, so the first was worth finding.

**Counts.** 6 new cases in `ForecastApiTests`, 14 in `ResourcesPage.test.tsx`, 7 in
`WorkItems.test.tsx` and 6 in `ForecastPanel.test.tsx`; 1,078 backend tests and 491 frontend
tests pass, both at zero missed branches — the frontend at 100% of statements, branches,
functions and lines, and every backend class this step touched at zero missed instructions.

---

## Step 5 — What if we hire someone? ✅ *done*

**Goal.** The question `roadmap.md` calls the most compelling one this unlocks, answered with
M7's machinery.

- **`POST /api/forecasts/{runId}/hires`**, taking a pool and a number of units, and replaying the
  stored run with that pool one unit larger. Nothing is written; the answer is a date at each
  confidence and the distance from the run's own.
- **One pool at a time, and no search.** M7 searches because a cut list is combinatorial and its
  budget is stated; this is a question with a handful of answers, and a search over hiring plans
  is a staffing tool rather than a forecast.
- **It weighs and never decides**, exactly as M7 does. The answer is a number of days, not a
  recommendation, and it says out loud that a new person is not productive on day one — which is
  the ramp-up this model deliberately does not have.

**Tests.** A pool that is not binding buys nothing, and the answer says so rather than showing a
zero somebody reads as a rounding error. A pool that is binding buys something and buys less the
second time — the diminishing return that is the actual answer to the question. Hiring into a
plan whose finish is decided by a dependency chain buys nothing at all, which is the case worth
being able to demonstrate.

**Done when** the answer to *should we hire* is a distribution rather than a feeling.

### As built — where it differs from the above

**The answer is a short series rather than one number, and that is the whole feature.** The
bullets say "a pool and a number of units"; what ships weighs *every* count from one up to
that number, because the second person being worth less than the first is the answer to
"should we hire" and a single figure hides it. The rows are cumulative and measured — the
difference between two of them is what the next person adds — and nothing on screen puts them
in a column that invites addition, which is M7's rule about cuts arriving here.

**The pairing came free, where M7's had to be arranged.** A cut takes its draw and discards
it, because an item that took no draw would shift every later number in the run and turn the
measurement into noise. Units change what may *start* and never what is sampled, so the two
runs being compared draw the same numbers in the same order without anything being done about
it. That is worth knowing before somebody "simplifies" the cut to match.

**A run scheduled against a capacity is refused rather than answered**
(`forecast_has_no_resources`), which the bullets do not mention and which is the line worth
drawing. A capacity is one undifferentiated pool, so "one more" has an answer — and it is the
question the forecast form already asks, one field away, by running the plan again with a
larger number. What this endpoint exists for is *which* pool, which only means anything once a
team has been described.

**A screen came with it.** The plan's bullets describe an endpoint, and M10 learnt what that
costs: its steps 3 and 4 both ended with an endpoint and no reader, and step 6 had to settle
it. So the panel is here — offering only the pools the run was actually scheduled against,
because a pool declared since is refused and a screen that invited somebody to pick it would
be a trap rather than a check.

**The model has no ramp-up and the screen says so.** A unit added is at full rate from its
first hour, which no new joiner is — so every answer carries that caveat beside it rather than
behind a disclosure. It is the one place this milestone's own model is optimistic in a way the
number cannot show, and decision 5 is why it is a sentence rather than a curve: a ramp nobody
measured is a number with no source.

**And it found a bug in M10's decomposition.** `MovementService` rebuilds the older run's
inputs twice — once with the newer progress, once with the newer estimates — and both rebuilds
dropped the team, so those states were scheduled against a capacity while the states either
side of them used the pools. The account's terms would still have summed, because the sum
telescopes and the last state is the newer run either way; what would have been wrong is every
term in the middle, silently. **The test that catches it took two attempts**, and the reason is
worth keeping: with every item naming nothing, any number of pools behaves exactly like one
capacity of the same total, so the fixture needs a *requirement* before the difference exists
at all.

**Counts.** 10 new cases in `ForecastApiTests`, 2 in `ForecastInputsTests`, 1 in
`MovementApiTests` and 6 in `ForecastPanel.test.tsx`; 1,091 backend tests and 497 frontend
tests pass, the frontend at 100% of statements, branches, functions and lines.

---

## Step 6 — Close out ✅ *done*

**Goal.** The record matches what was built.

- Each step's `### As built` is written in the change that built it. This step is the
  whole-milestone read.
- `roadmap.md`: M11 marked done with its own *As built*; **the *duration from effort* bullet
  corrected in place** (decision 5) and **availability promoted to the next milestone with its
  own section** (decision 1), rather than left as a bullet inside a milestone that did not build
  it. The *what makes this hard* section is re-weighted against the measurements.
- `product-concept.md`: *capacity modelling* is half-answered and says which half.
- `CLAUDE.md`: a section on what a resource is, what version 3 contains, and why occupancy is not
  speed.
- **The review pass**, as in M5 through M10. Every one of the last six found something, and M10's
  found the milestone reintroducing a form its own second decision existed to remove.

**Done when** the next reader can tell what M11 decided without reading its code.

### As built — where it differs from the above

**The review pass found one property this milestone rests on and had never asserted.** Work
that names no resource takes one unit of whichever pool has one free — and it follows that **a
team nobody has annotated behaves exactly like the capacity it adds up to**, however many pools
it holds. That is not a footnote: it is what makes describing a team safe to adopt, it is why
declaring one changes nothing until somebody says what the work needs, and it is the reason the
bug step 5 found in M10's decomposition hid behind two fixtures before a requirement made it
visible. It is now `poolsNobodyHasNamedAreTheCapacityTheyAddUpTo`, with its mirror beside it:
one named requirement is enough to tell the two apart.

**And it sharpened what the milestone claims.** The 14–59% is real and it is *earned*: a team
gets exactly its old answers until the work is annotated, one item at a time, through a form
this product opens per row. That is the first thing a real user will feel and nothing in the
icebox covers it, so it is written into `roadmap.md`'s *what I would build next* rather than
left as a pleasant surprise.

**Two capacity overloads survive with only tests calling them**, and that is a judgement rather
than an oversight. `Schedule.of(…, int capacity)` and `Engine.run(…, int capacity, …)` are the
place the containment is *stated* — "this is the method, and the one above is it with one pool"
— and they are what let the whole engine suite go through this milestone unedited, which is the
evidence that version 3 gives version 2's answers. M10's review deleted `Comparison.identical()`
for having no caller but its own test; the difference is that this pair says something, and
deleting it would mean thirty call sites expressing the containment through the very code path
being tested.

**What the close-out changed outside the code.**

- `roadmap.md`: M11 marked done with its own *As built*; **the *duration from effort* bullet
  struck through and corrected in place**, because the wrong version is the quotable one;
  **availability promoted out of M11's bullets into its own M12 section** rather than left
  inside a milestone that did not build it; and *what makes this hard* re-weighted against the
  measurements — both of its warnings are kept and both are now followed by what they turned
  out to be worth.
- `product-concept.md`: the *capacity modelling* deferral is three-quarters answered, and the
  note says the part that paragraph never mentions — treating a team as one number is not a
  simplification with an acceptable error, it is optimistic by 14 to 59%.
- `CLAUDE.md`: a section on what a resource is, what version 3 contains, why occupancy is not
  speed, and why a team nobody has annotated answers exactly as a capacity does.

**Counts.** 2 new cases in `ScheduleTests`; 1,093 backend tests and 497 frontend tests pass, the
frontend at 100% of statements, branches, functions and lines and every class this milestone
added at zero missed branches and zero missed instructions.


### The code review, and what it changed

**A review of the whole milestone found six things and all six were acted on.** That is
unlike M10, where three of eight were argued down; the difference is worth naming, because
five of these six are one shape — **M11 introduced a second dimension to the model and each
of them is somewhere that dimension was not carried through.**

**The scheduler's scan was bounded by the wrong question, and it is bounded by that question
in exactly the shape this milestone exists to model.** `anyFree` stops the scan when no pool
has a free unit, which restores what counting slots did and is what the step 2 build measured
against its budget. But a team of ten backend and one designer running work that is nearly all
backend leaves the designer's unit free for most of the plan — so the guard is true throughout,
and every completion event walks every ready item again. Measured on the same five-hundred-item
plan: **449 ms with one pool, 2,276 ms with those two, against a budget of 2,000.** The
milestone's own budget case never saw it, because it forecasts against a capacity. The guard
now asks whether anything *waiting* could use anything free — a per-pool demand tally
maintained as items become startable — and the same plan comes back in **531 ms**. It is a
strictly tighter stopping condition, so it can only end the scan earlier and never differently:
the whole engine suite, `ForecastApiTests`' stored-snapshot replays included, passes unedited.
`EngineTests` now budgets both shapes, because the single-pool one demonstrably does not cover
the other.

**A requirement larger than the pool it names turned every forecast of that plan into a 500.**
`Resourcing.of` refuses it — decision 4's termination argument depends on there being none —
but nothing on the write path did, and an `IllegalArgumentException` is the one failure shape
this codebase's conventions forbid: Boot's default document, no `code`, nothing for the browser
to translate. It is reachable two ways and the second is the one that matters: a requirement
larger than the pool can simply be *typed*, and a pool can be **shrunk below a requirement that
was legal when it was written**. Both are now refused, and they need two refusals rather than
one because the second is a door neither package may watch alone — `requirement` cannot see a
later edit to a pool, and `resource` cannot look at what depends on it without pointing an
arrow back the way it came. So `RequirementExceedsPoolException` turns somebody away where the
bound is on screen, and `WorkNeedsMoreThanTheTeamHasException` refuses the forecast, which
catches every route into that state including any added later. **Neither drops the need
instead**: leaving it out makes the work generic and the answer sooner than the plan can
possibly be delivered, with nothing on screen looking amiss.

**M10's decomposition attributed hiring to scope.** This is the one that would have been read
and believed. `ForecastInputs` holds the pools beside the items, so to a decomposition reading
that snapshot a run made after somebody joined differed from its predecessor in the *plan* —
and a reader who had changed nothing about the work was told their scope had grown by however
long the new person saved them, with `ASSUMPTIONS` reporting zero. The terms still summed,
because the sum telescopes whichever line the days are put on; what was wrong was the line, and
nothing about the number could show it. `Movement.Step.RESOURCES` is now its own term, **split
out of `SCOPE` rather than added beside it** — no two existing terms swapped places, exactly as
`CALENDAR` is a split out of the assumptions, which is why `Movement.RULE` stays
`progress_first`. **The requirements travel with the pools and not with the work**, and that is
load-bearing rather than tidy: a requirement is only readable against a declaration holding the
pool it names, so a state carrying the newer needs against the older team is not a state
anybody could have run. An account costs seven simulations now rather than six.

**The needs form deleted requirements it had never rendered.** It is built from the
organisation's live pools and the endpoint replaces the whole set, so opening it on work that
named a pool the team had since put away and pressing save destroyed that row without a word.
The API publishes `resourceArchived` precisely so a reader can be told, and no screen read it.
It now renders such a row, marked, and preserves it — while still offering only pools *in use*
for a *new* requirement, which is a different rule and still right.

**And a hire can make the date later.** `daysEarlier` is a difference between two dates and
this is a fixed-priority list scheduler over a precedence graph, which is where Graham's anomaly
lives: another unit can let low-priority work start early, tie up what a critical-path successor
was about to need, and push the finish out — and each end is rounded up to a whole day on its
own besides. The panel had two sentences for a number with three signs, so a negative arrived as
"-2 days sooner", which reads as a bug in the page rather than as a fact about the plan. There
is a third sentence now. Clamping it to zero was the alternative and it would have turned "more
people can be slower here" — worth knowing, and hard to believe without being shown — into a
shrug.

**The sixth was a stranded Javadoc block**: `described`'s documentation had been left above
`hires` by the step 5 build, where it compiled into nothing and the method it belonged to had
none. Moved.

**And one thing the review did not find, which the coverage gate did.** `ForecastInputs`'
two-argument constructor — the plan with no team — has had no caller since step 5 fixed
`withProgress` and `withEstimates` to carry the declaration through. Deleted, on M10's review's
own precedent for `Comparison.identical()`. The capacity overloads on `Schedule` and `Engine`
stay for the reason recorded above: those *state* the containment, and this stated nothing.

### The layout, which nothing had

**M11 shipped five class names and no CSS for any of them** — `resources`, `resource-list`,
`needs`, `hiring`, `hires` — so the resources page rendered as an unstyled bulleted list at the
window's full width, and both of the milestone's submit buttons were raw browser buttons: they
are the only two `type="submit"` in this application that never got `className="primary"`. **The
code review did not find this and could not have**, which is worth recording as a property of
review rather than as a lapse: it reads source, and a stylesheet with nothing in it for a screen
looks exactly like a screen that needs nothing. The check that finds it is opening the page.

Nothing here is new design. Each block is the rule its sibling already keeps — `.resources` is
`.members`, `.resource-list` is `.project-list`, the form that changes a pool opens under its row
with the dashed rule `.estimate-form` uses, the form that declares one is separated from the list
the way `.invite-form` is, `.needs` is `.dependency-form`, and `.hiring` is `.forecast .target`,
because the cuts and the hires are one question asked from two directions.

**Two things only rendering could have told me**, and both were wrong on the first attempt:

- **The needs form's boxes did not line up.** Every row is label, box, hint, and the label took
  the slack — so each row's box sat wherever that row's hint left it, and "3 available" and "Put
  away — this still needs it" are different lengths. The hint takes the slack now and the label
  and box are fixed, which is the whole of what makes the column read as a column.
- **The hiring form came out as a right-aligned stack.** `.forecast form` turns every form in the
  panel into a column, and a rule that sets `display: flex` without restating `flex-direction`
  inherits it — so `align-items: flex-end`, meant to sit the button on the fields' baseline,
  pushed everything to the right instead. It is stated explicitly now, with a comment saying it
  is an exception to the rule above rather than an addition to it.

Checked at 1,504px and at 390px, both screens.

**Counts.** 6 new backend cases and 3 new frontend ones; **1,100 backend tests and 500 frontend
tests pass**, the frontend at 100% of statements, branches, functions and lines, and every class
this review touched at zero missed branches and zero missed instructions.


---

## Migrations

**Two, and they are the first since `V16`.**

- `V17__resources.sql` — the pools, per tenant, with units, an optional user and an `archived_at`.
- `V18__requirements.sql` — what each item needs, unique on the pair. **Named for the concept
  rather than for the two ends it joins**, which step 1's record argues: `dependencies` is a join
  with a payload too, and it is not called `work_item_work_items`.
- `V19__forecast_resourcing.sql` — **a third, which this plan did not expect**. Step 4's own
  record argues it: the declaration is in the snapshot and that is not enough, because two
  readers want it per run and the document beside it holds five hundred items. It is also what
  lets `Comparison` see the *shape* of a team rather than only its size, which is the gap step 3
  wrote down.

**Neither backfills anything**, and that is a claim rather than an omission: a plan forecast
before this milestone was forecast against a capacity number, and inventing a pool for it would
put a declaration nobody made into the one table whose purpose is to record what was assumed. It
is `V14`'s decision — the calendar columns left null on runs made before there was a calendar —
arriving in a second place.

---

## Sequencing and risk

**The risk that would be worst is the version bump, and it is bounded by one test.** If version 3
does not reproduce version 2, then M6 cannot explain an old run, M7 cannot weigh cuts against one,
M10 cannot account for a movement into one, and every plan's history is cut in half on the day
this ships — visibly, since what a reader gets is a refusal. The containment is measured (0.00%
apart, ten thousand runs) and the test is byte identity rather than agreement, which is the
strongest form this codebase has and the one M3b already relies on.

**The risk that is most likely is scope.** Availability is cut, and it will look like a small
addition at every point during the build — a working day per pool, a holiday list, a part-time
fraction. It is not: it moves the engine from hours to dates, and every one of those three is a
different question about what a date means. Decision 1 is the whole argument and it should be
re-read rather than re-derived.

**The risk that is most expensive to discover late** is decision 5. Effort-divided-by-people is
one line of code, it makes the model feel more powerful, and it will be proposed — most likely by
somebody looking at a plan where two people are obviously going to pair on a task. It has no
oracle, it contradicts every measurement anybody has taken of real teams, and it would be
invisible in every test in this repository, because nothing here knows what a task *should* take.

**What this milestone must not absorb.** Assignment, boards, utilisation reporting and Gantt
charts are all one small step from a resource model and all four are ruled out — three by
*What Aurevanta is not* and the fourth by decision 11. The shareable link, the PNG export and the
scheduled digest are still the icebox's. And the metering question `roadmap.md` raises here —
that this is the first feature with real economics behind it — is a commercial decision that
section already answers: forecast quality is not a commercial axis, and neither is the honesty of
a model.

**Two things that will look like bugs and are not.**

- **A plan that got slower when nothing about it changed.** Declaring resources for the first
  time will move most plans out, by up to half on a specialised team, and that is the measurement
  at the top of this document rather than a regression. The date was always wrong in that
  direction; it is the first honest one.
- **A pool with spare units that buys nothing.** Hiring into a plan whose finish is decided by a
  dependency chain changes nothing at all, and step 5 exists partly to be able to show it.
