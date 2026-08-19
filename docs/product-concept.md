# Aurevanta — Concepts and Planned Features

> **Status: design intent, and Tiers 1 and 2 are now built.** As of 2026-08-17 the schema this
> document implies exists and something reads it: the original tenancy design–chosen handles built tenancy, identity and teams,
> **The plan schema built the estimation schema** — projects, work items, immutable P10/P50/P90 estimates
> with an estimator, progress and actuals, and a precedence graph, with the plainest possible
> UI to fill them — **The simulation engine built the engine, both halves of it**, **The calendar turned its hours into a
> date at a chosen confidence**, **Elicitation replaced the question those estimates are collected by**,
> **The contribution ranking made the band say what it is made of**, and **The inverse query turned it into "what would it take to hit
> this date?"**. `roadmap.md` sequences the rest and is newer than this document wherever the two
> disagree.
>
> **The core principle below is no longer only an argument.** Ranges are fitted to log-normals,
> sampled ten thousand times, scheduled over the precedence graph at a stated capacity, and
> read off as a band; every run is stored with its seed so it can be replayed exactly. **The
> two sections this document argues hardest for are now arithmetic**: *Unknown unknowns
> dominate the error* and *Independence is a lie* are built, marked below, and asked as two
> questions on the forecast screen. **Both of Tier 1 is built** — the rollup and the ship date
> — so the bar this document set for beating a spreadsheet has been met, and *The input problem
> is harder than the maths* is mostly built too. **Tier 2 is built as well**: variance
> contribution says what the band is made of, and inverse queries run the question backwards, so
> the usage pattern this document describes — something opened *during* planning rather than a
> reporting surface — is the one the product now has. Tier 3 and everything after it is still
> design intent. Two of the *Open questions* at the end were answered by the plan schema and are marked as
> such; the rest stand.

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

**Decided and built: log-normal, fitted from P10 and P90, with the stated P50 kept as a
*signal* and never as an input.** Durations are positive and right-skewed, so this matches
reality, and it does not pretend a worst case exists.

**What decides it is the bounded maximum, not the fit quality.** PERT-beta honours all three
points a person typed, which sounds strictly better — but it needs a maximum, and the number
somebody would put there is the one thing nobody knows. Asking for it invents the tail; deriving
it from the P90 invents it silently. A distribution that cannot represent "it could always be
worse" is the wrong shape for the thing being modelled, whatever it does with the three points
it was given.

**The P50 is therefore evidence rather than a parameter.** A fit from two points reports the
ratio of the stated middle to the implied one: 1.0 is agreement, and the further either way, the
harder the three points are arguing with each other. That is a fact about the *estimate*, and it
is surfaced — a forecast whose items disagree with themselves reports `inconsistent_estimates`
beside the band. Pointing at *which* estimate is elicitation's, because it is elicitation feedback and
belongs where the question is asked.

> **Discharged by elicitation.** The ratio is published on every estimate and the review that precedes
> saving one says so in words, where the question was asked. The threshold that decides "a long
> way" is stated **once**, beside the arithmetic it bounds, and read by both the forecast and the
> form — two rules about one estimate would eventually disagree, and the one on the plan screen
> would be the one nobody noticed had drifted. The browser is never told the number; it renders
> a flag the server sent, the way it renders a `code`.

## Planned features

### Tier 1 — the minimum that beats a spreadsheet ✅ *built*

**Monte Carlo rollup. ✅ *built by the simulation engine*** Fit → sample → aggregate. Produces a full project
distribution rather than a single number.

**Ship date at a confidence level. ✅ *built by the calendar*** Nobody asks for a distribution; they
ask what date they can commit to. A single confidence control (50% / 80% / 95%) resolving to
a calendar date. This also reframes stakeholder negotiation: "can we go faster" is answered by
"we can commit at lower confidence," which is the honest trade.

> **Built, 2026-08-17, and the reframing is literal.** Moving the control changes the date on
> screen with no request going out, because all five percentiles are already in the run — so
> the two numbers read as two readings of one forecast rather than as two forecasts, which is
> the only way the trade reads as a trade.
>
> **What this section did not say is where the calendar comes from**, and that turned out to
> be the whole of the work. Hours become days by dividing by a working day, and that day
> is **one worker's** — never the team's daily total. The engine's output is a completion time
> with capacity already inside it, so dividing by a team's total counts capacity twice and
> produces a date wrong by exactly the factor a team is proudest of, with the band unchanged
> and nothing on screen looking amiss. So the working day is a required box with no default,
> stated per forecast and stored on it beside a named calendar rule, and printed back beside
> every date it produced.
>
> **A date is the first thing this product emits that looks like a fact**, which is why the
> hours stay on screen beneath it. An hours band advertises that it came out of a model; "Aug
> 25" does not, and it gets pasted into a plan with the assumption behind it left in the
> browser.

### Tier 2 — analysis over the same engine, no new schema ✅ *built*

**Variance contribution. ✅ *built by the contribution ranking*** Rank tasks by their contribution to the *spread* of
the project outcome, not by duration. A 20-day task estimated 18–22 is nearly risk-free; a
5-day task estimated 2–30 is what wrecks the forecast. Directly answers "what should I
spike next to tighten the plan," and is the most defensible feature in the product —
point-estimate tools cannot produce it.

> **Built, 2026-08-17, and it cost no schema.** Each item's sampled duration is correlated with
> the plan's completion across every run — by replaying the stored forecast out of its own seed,
> which is what the simulation engine kept a seed for. So it answers for every forecast this product has ever made
> rather than only for the ones since.
>
> **Two things this paragraph did not know it was claiming.** The first is that "contribution to
> the spread" reads as a percentage and **is not one**: the squared correlations sum to exactly 1
> only for a chain worked by one person with no common cause, and in any real forecast the shared
> team factor makes everything move with everything, so they sum to well over one. A pie chart of
> them would show a plan accounting for three hundred percent of its own uncertainty — precisely
> the precise-looking wrong number this document exists to complain about — so it is a ranking
> with a bar, and no percentage appears anywhere.
>
> The second is that **two of the rows are not tasks.** The shared team factor and the work nobody
> has listed are both sources of spread and either can dominate; a report that ranked only tasks
> would answer "which of these should I spike" while hiding that the honest answer is sometimes
> "none of them, because what is widening this plan is not on the list".
>
> **And "rank by duration" is not the only wrong answer — so is "rank by variance".** Measured: an
> item with *forty-five times* the variance of any other in its plan accounted for 1.2% of the
> spread, because it sat beside the chain that actually decided the finish, while each of the five
> narrow links on that chain accounted for about 18%. A summing model would have sent somebody to
> spike the one thing least worth touching.

**Inverse queries. ✅ *built by the inverse query*** Run the question backwards: not "when will this finish" but
"what do I cut to hit 1 November at 85% confidence?", ranking candidate scope removals by the
confidence each one buys. This changes the usage pattern from reporting surface to
something opened during planning.

> **Built, 2026-08-17, and like the contribution ranking it cost no schema.** The target date becomes hours through the
> run's own calendar, and every candidate is measured by replaying that stored run with the work
> imagined away — nothing is written, so this answers about forecasts made long before it existed.
>
> **The paragraph above hides the decision the whole feature turns on.** A cut cannot be modelled
> by taking the item out of the plan, and — worse, because it is silent — it cannot be modelled by
> emptying its estimates: an item that weighs nothing takes no draws, so every later item in the
> run is sampled from a different place in the random stream. Measured, that noise lands in the
> same range as the effect being measured, and the ranking becomes a coin flip that looks exactly
> like an answer. **A cut item keeps its estimates, takes its draws, and is worth nothing**, so
> both sides of the comparison see the same random numbers.
>
> **And the answer is two answers, deliberately kept apart.** What each candidate buys *on its own*
> may never be added up: two cuts on one chain shorten the same path and buy barely more than one,
> and two on separate branches leave the later of them deciding. So the set that reaches the date
> is **searched for and measured at every step**, and the screen never puts the two in one column.
> This is the same trap as the contribution ranking's shares, in a form far more tempting to fall into, because every
> figure is a percentage with a plus sign in front of it.
>
> **What it deliberately is not** is a scope editor. It weighs what somebody names and says what
> each is worth; deciding is done on the plan screen, where the work can be seen in context. Which
> work is negotiable is a judgement about value, and nothing in this schema records any — so a
> server that proposed its own candidates would be recommending that somebody delete work for
> happening to sit on the deciding path.

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

> **Built, 2026-08-18, and one word in the paragraph above is wrong.** Interruptions, holidays
> and the eight-focused-hours point are all inside a history of weeks; **scope growth is not**.
> What the history absorbs is the *drag* of past discovered work — a team that closed five a week
> while two in ten were unlisted has a rate of five, earned partly on work nobody had written
> down — so projecting the items you can see at that rate is optimistic by exactly that share.
> Nothing in the schema says which items were discovered mid-flight, so it cannot be measured; it
> is said instead, on every answer.
>
> **And the gap turned out to need more care than "present them alongside".** Four things differ
> between the two forecasts, and two of them make the estimate-based one look slow while two make
> it look fast — so a subtraction of the two dates is partly a difference in the question rather
> than a disagreement the team is having with itself. What ships is both dates with the four
> differences named underneath, and the two that vary carrying the run's own numbers. Nothing
> averages them: one number in the middle ends the conversation this exists to start.
>
> **What it needs is what nothing else here needs: nothing.** No estimate, no assumption, no
> actual — only that work has been finished and dated, which this product already requires. That
> is why it can answer on the day it ships where calibration's calibration cannot, and it is the strongest
> argument for the pair of them existing side by side.

### Deferred

**Dependencies and parallelism**, and **capacity modelling** (people, allocation,
holidays, working days — mapping an effort distribution onto calendar dates). Both
multiply schema complexity considerably. A flat list of tasks already demonstrates the
core value.

> **Superseded, 2026-08-06.** `roadmap.md` reverses the first half of this. Measurement
> showed that summing a flat list is not a neutral simplification — it silently assumes one
> worker doing everything in sequence, and the same ten items forecast at 51 or 86 days
> depending only on structure. Precedence dependencies therefore moved into the schema and
> the engine; capacity modelling remains later, as the resource model.

> **Half-answered, 2026-08-17, and the halves came apart in a way this note did not expect.**
> The parenthesis above bundles *people, allocation, holidays, working days — mapping an
> effort distribution onto calendar dates* as one deferral. The calendar took the last clause on its own:
> hours become dates through one stated working day and a named calendar rule, and that needed
> no schema for people, no allocation and no holiday list. **What made it separable is that the
> assumption is stated per forecast rather than modelled** — it is copied onto the run like
> capacity, so nothing has to know who is working when.
>
> The rest stays the resource model's, and arrives as a **new rule name** rather than as a better version of
> this one. Every run made under `five_day_week` keeps resolving under it, so real availability
> landing later cannot move a date this product has already published — which is the one thing
> a holiday list would otherwise do to every historical forecast at once.

> **Three-quarters answered, 2026-08-19, and the deferral splits once more.** the resource model built *people*
> and *allocation* and deliberately did not build *holidays*: a resource is a named pool with a
> number of units, an item says how many units of which pools it ties up, and the scheduler
> starts only what can have what it needs. All of that is still in **effort**, which is what
> made it separable — units decide what may start, and the calendar over the answer is still
> the calendar's.
>
> **The part of this paragraph that turned out to matter is the part it does not mention.** The
> deferral treats capacity modelling as schema complexity to be avoided; measured, treating a
> team as one number is not a simplification but a **lower bound** — the same six units read as
> six interchangeable slots finish 14% to 59% earlier than as two pools work cannot cross
> between, and it gets worse the more specialised a team is. A flat capacity does not demonstrate
> the core value with an acceptable error; it demonstrates it optimistically, in the one
> direction this product exists to correct.
>
> **What is left is holidays and part-time**, which is availability and is the only half that needs the
> engine to know what day it is.

## Modelling concerns to design around

### The input problem is harder than the maths ✅ *mostly built by elicitation*

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

> **Built, 2026-08-17, and two of the three framings shipped.** Surprise framing is how the
> two ends are now collected and betting framing is how the high one is confirmed; **comparative
> framing moved to calibration**, because the only comparison available today is against other
> *estimates*, and comparing a guess with a guess would spread anchoring across a whole plan
> rather than within one item. It is reference-class forecasting only once March's actual is
> known.
>
> **What this section did not say is that the order matters more than any of the framings**, and
> that turned out to be the whole work. Three numbers asked together anchor on whichever is
> answered first, and three boxes invite the middle to go first — so 3/5/8 is the middle plus a
> bit and minus a bit, around an anchor nobody examined. They are now asked one at a time, bad
> case first because it is the only one of the three with nothing above it, the middle last
> because the fit does not use it and it is the one number that can afford to be anchored. No
> earlier answer is on screen while the next is asked, and the percentile names appear nowhere.
>
> **The paragraph above about flagging a tight P90 was measured before it was built, and it does
> not catch what it was written for.** 3/5/8 has a P90 1.60× its P50 and a stated middle within
> 2% of the one its own ends imply; so do 2/3/5, 5/8/13 and 1/2/3. **The canonical garbage is
> *coherent* garbage** — internally consistent, plausibly shaped, and invisible to anything that
> looks at three numbers in isolation. The flag ships, advises and never refuses, and it is a
> backstop rather than the defence.
>
> **And the honest caveat.** Every claim in this section is a hypothesis about human judgement
> that nothing in the codebase can settle: a better form is a better form, and whether it
> produces honester ranges is calibration's to answer. That is why each estimate now records *how it was
> asked for* — split the calibration record by that column and the question answers itself.
>
> **Calibration built the split and did not answer the question, which is the honest state of it.**
> `GET /api/calibration` returns a `byMethod` breakdown, so the two forms are already scored
> apart; what it needs is completed work carrying both an estimate and a measured actual, and
> that will take months to accumulate. **Comparative framing moved past calibration as well**, and for a
> reason worth keeping: it needs the reference class calibration *creates*, so it could not be built in
> the work that first makes one exist — and it would be a third method splitting a thin
> record three ways rather than two.

### Unknown unknowns dominate the error ✅ *built by the common-cause model*

Projects rarely overrun because known tasks exceeded their P90. They overrun because of
work nobody listed. Every ticket can be estimated well and the project can still be 100%
late, because the ticket list itself grows.

An honest model therefore has two uncertainty sources: duration uncertainty on known
work, and **scope uncertainty** about how much unknown work appears. The second is
usually the larger, and it is estimable from history — if the last five projects grew
40–90% in ticket count, that is a distribution to sample from and multiply through.
Ignoring it is why other tools produce forecasts that look precise and land wrong.

> **Built, and one sentence above needed sharpening to build it.** "Multiply through" is
> exactly right for a sum and not available in a schedule: unknown work needs a *position*.
> Each discovered item attaches as a successor to a uniformly chosen existing one, so it
> becomes ready when that item finishes and competes for capacity like everything else —
> `design/common-cause-and-scope-growth.md` decision 3 records the three positions rejected and why. The range is asked for
> as two percentages on the forecast screen, the count is sampled per run, and the fractional
> part is rounded stochastically so that a plan of ten items growing 4% grows four times in
> ten rather than never. Estimating it from history is still ahead: calibration is where a team's own
> plans start answering this question, and the rule then is **propose from history, never
> default**.
>
> **Calibration shipped without spending that**, and the reason is that the two are not the same
> quantity. What it measures is how wide *one person's* range should have been around *one*
> task; a shared factor is one draw applied to everything at once, and the errors calibration records
> contain both added together — so handing its multiplier to the team factor would count the
> shared part twice. Separating them means decomposing the residuals by *when* they happened,
> which needs many completed plans and belongs after the throughput forecast says the same thing from the other
> direction.

### Independence is a lie ✅ *built by the common-cause model*

Naive Monte Carlo samples each task independently, so good and bad luck cancel out and
the project band comes out implausibly tight. Reality has common causes: if the team is
short-staffed or the codebase fights back, everything runs long together.

Sampling a single shared **team factor** per simulation run and applying it across all
tasks captures most of this at very little cost, and widens the band to something
believable.

> **Built, and "very little cost" was accurate — one multiplication per item.** The factor is
> log-normal with its median pinned to exactly 1, so it widens the band without moving the
> centre; a factor with a *mean* of 1 would have quietly dragged every forecast down. It is
> asked for as the only form of the question anybody can answer — *in a bad stretch, how much
> longer does everything take?* — and read as the factor's own P90. It multiplies what each
> item has **left**, never what it has already cost: hours already spent are measured, and no
> multiplier belongs on a measurement.

### Merge bias

Where parallel branches must both complete, expected finish is later than either branch
alone, and this compounds at every join. Simulation captures it for free; spreadsheets
get it wrong universally. Worth surfacing as an explicit insight rather than burying it
inside a number.

> **Captured, not surfaced — and the reporting work decided that deliberately, 2026-08-19.** The engine has
> modelled it since the simulation engine: `Schedule` waits for every predecessor, so the effect is inside every
> band this product has ever published. What the reporting work cut is the *number* — surfacing it explicitly
> was one of that work's five bullets, and it is a correction to a figure the reader it was
> written for has not yet understood. It is in the icebox under *Modelling depth* with the
> probabilistic critical path, which it shares a cost with: both need the scheduler to report
> which items decided each run, which is modelling work wearing a communication label.

## Open questions

- ~~**Unit of estimation** — task, story, or epic?~~ *Answered by the plan schema: **task**.* Coarser
  units hide scope growth inside the estimate, which the simulation engine's scope-uncertainty model would
  then count a second time. The quantity is **effort in hours**, never duration — duration
  is effort divided by what is assigned to it, and that division is the resource model's.
- ~~**Multi-estimator support**~~ *Answered by the plan schema: **schema now, UI later**.* Several
  estimates may sit on one item, one current per estimator, and they are read back
  together — so two people disagreeing is stored rather than refused. What is *done* with
  the disagreement is the simulation engine's, and the session UI that makes it a group activity is in the
  icebox. It was a schema decision, as this bullet said, and the schema was built for it.
- ~~**Communicating to stakeholders**~~ *Answered by the reporting work: **all three, and the example
  sentence above is wrong.*** Output must reach people who do not know what P90 means.

  > **Built, 2026-08-19, and the parenthesis is the part to correct.** "85% likely to finish
  > between 12 October and 20 November" is a **two-sided** interval, and every date this product
  > publishes is one-sided: a percentile is the day by which that share of the runs had finished.
  > The two forms are different claims and the two-sided one is the one that gets misread —
  > *so it will not be before the 12th?* is a question nobody manages against, about the end of
  > the distribution the model is worst at, and it quietly halves the confidence a reader thinks
  > they have at the far end. What ships is **"there is an 85% chance this is finished by
  > 20 November"**, in the largest type on the panel, with the plan's own name in it because a
  > confidence and a day describe nothing away from the screen they are already on.
  >
  > **The burn-up is a table with a picture over it**, in that order: the text equivalent is
  > built first and is what the tests assert, and the drawing is `aria-hidden` inline SVG
  > carrying nothing the table does not. Its cone comes from the throughput bootstrap rather
  > than from the engine — a burn-up's future is *items over time*, and the engine forecasts
  > effort — so the picture and the number beside it are one forecast read twice. It narrows
  > because the backlog is a **ceiling**, not because uncertainty falls away.
  >
  > **And the sliding-date detector is not a rule about "keep moving out".** Measured: a plan
  > that is not slipping still moves out one week and in the next, so three successive increases
  > fire on 86% of plans re-forecast weekly for half a year with no slide in them. What ships
  > measures how far the date has drifted since the oldest *comparable* run, against the width
  > of the band the plan itself admits to — three days on a three-week band is nothing, and
  > three days on a two-day band is the plan coming apart.
  >
  > What the reporting work also built, which this bullet does not name: **an account of why the date moved**,
  > whose terms sum to the whole of it because each is measured with every earlier one already
  > applied.
