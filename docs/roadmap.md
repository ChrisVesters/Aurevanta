# Aurevanta — what is left to build

> **Everything this document used to sequence is built.** What Aurevanta does today is
> described in `product.md`, and *why each piece works the way it does* is in `design/`. This
> document is now only the forward list: the one substantial feature still outstanding, the
> refinements behind it, an icebox of ideas nobody has committed to, and the cross-cutting
> work that was never a feature.
>
> **The ordering principle has not changed.** Build what makes the forecast trustworthy before
> what makes it comfortable. A beautiful task list that sums P50s is precisely the tool this
> product exists to replace, and every item below is judged against that.

---

## Next — Availability: when anybody is actually there

**The one substantial feature outstanding, and the only one that changes what the engine
means.** Everything built so far forecasts *effort*; the calendar turns that into a date
through a single stated working day. Availability is what turns it into wall-clock time
through the days people are actually there.

**It was deliberately cut from resourcing, not overlooked.** Describing a team as pools of
units answered *who can do what at the same time*; this answers *when they are there*, and the
two are separable — which is why one shipped without the other and the forecast it produces is
honest about what it does not model.

- **Working days, holidays and part-time allocation, per resource.** A pool of three where one
  person is away next week is not a pool of three that week, and nothing in the scheduler can
  say so today: units are whole things and there is no calendar inside it.
- **A new calendar rule name, never an edit to `five_day_week`.** Every run stores the rule it
  was read under precisely so that a better calendar arriving later cannot move a date already
  published — `WorkingCalendar.RULE` is the mechanism, and a run under a rule this code no
  longer implements reports its hours and says so rather than being re-read under today's.
- **Duration from effort, honestly.** With a real allocation, hours become a duration through
  somebody's actual availability rather than through one stated working day. It is *not* effort
  divided by headcount — that was refused on the grounds that there is no oracle for it, and
  this does not reopen it.

**What makes it hard is not what made resourcing hard.** The scheduler currently decides *what
may start*; this makes it decide *when*, which means dates enter `Schedule`, `Engine`, the
stored snapshot and every replay. It is where the existing cost measurements stop applying,
because a walk over a calendar per item per run is not a constant-time check. And it is a second
`Engine.VERSION` bump, so the containment question has to be asked again: an engine with
availability has to contain one without it exactly, or every run made before it becomes a record
that can be read and never replayed.

---

## Deferred — genuinely later

Refinements of resourcing and availability that neither needs to be useful.

- **Skills and matching** — resource types cover "any backend engineer"; individual skill
  levels and proficiency-adjusted durations are a further step.
- **Resource levelling and optimisation** — suggesting a schedule rather than evaluating
  the one implied by the priority rule.
- **Cross-project portfolio sharing** — people split across several projects at once, which
  turns scheduling into a tenant-wide problem rather than a per-project one.
- **Other dependency types** — start-to-start, finish-to-finish, start-to-finish.

---

## Icebox — ideas worth keeping, not yet scheduled

Unordered and uncommitted. Four of these are arguably mis-filed; see the note at the end.

### Getting work in

- **Import from an issue tracker** — Jira, Linear, GitHub Issues, Azure DevOps. One-way
  import with periodic refresh, not two-way sync, which is where these integrations usually
  drown. The same connection feeds the throughput forecast's throughput history for free.
- **CSV import/export** — the unglamorous escape hatch that makes people willing to try the
  tool, and willing to trust that they can leave.
- **Plan templates** — project shapes a team repeats, with their historical actuals
  attached as a starting reference class.
- **Grouping, filtering and search inside a plan** — the plan screen renders every live item
  in one flat table ordered by name, and 500 items per project is the *stated* ceiling, so at
  its own declared scale the screen is unusable by construction. The cheap version needs no
  schema and is view-only: search, filter by status or by estimator, collapse what is not
  being discussed. The expensive version is a parent/child hierarchy, and it is expensive for
  a reason the plan schema already gave when it fixed the unit of estimation at *task* — a parent carrying
  its own estimate double-counts the scope of its children, which is the objection that ruled
  epics out as the unit in the first place. **Tags are the middle answer**: grouping without a
  rollup, so nothing acquires an estimate that is not a task's. **All of this is about
  reading a plan and none of it is about forecasting one** — the thing that produces a date
  for part of a plan is *Milestones* under *Getting forecasts out*, and the two get conflated
  because a heading and a milestone look alike on screen and are nothing alike underneath.

### Getting forecasts out

- **Shareable read-only forecast link** — a URL a stakeholder opens without an account.
  The reporting work is entirely about reaching people who do not know what P90 means, and right now every
  one of them would need to log in.
- **Scheduled digest** — weekly email or Slack: "the 85% date moved out 6 days." Turns the
  sliding-date detector from something you must remember to check into something that finds
  you.
- **Snapshot export** — PNG or PDF of a forecast for slides. Unglamorous; it is how the
  numbers actually reach a steering committee.
- **Milestones — a date for part of a plan, not just the end of it.** A forecast answers one
  question about a whole project, and the dates people actually negotiate are usually interior
  ones: a beta, a regulatory submission, the thing that has to exist before the conference.
  Today the only way to ask about one is to split the work into a second project, which breaks
  every dependency crossing the line.

  **The engine is closer to this than it looks.** `Schedule.finish` already keeps a `readyAt`
  per item and advances a completion time as each one lands — it knows when every node
  finished and reports only the last. And the simulation engine keeps an unestimated item in the graph as a
  zero-effort node precisely so precedence survives it, so **a milestone is that node with a
  name**: the work it waits on points at it, it takes no draw, and it needs no estimate.
  What would have to change is what the scheduler *reports*, not what it samples — the shape
  the contribution ranking's change had, where an observer watched a run without taking a draw and `Engine.VERSION`
  did not move. If that turns out not to hold it is a version bump, and the reporting layer has to be told:
  comparing runs across one is exactly how this tool would report a date sliding when nothing
  moved.

  **What it multiplies is the argument for it.** the calendar's date at a chosen confidence, the contribution ranking
  of what is widening the band, the inverse query's list of what to cut — each answers for a whole plan and
  would answer per work, which is the scale somebody argues at. The inverse query most of all: *what do I
  drop to hit the beta* is a question people ask, and *what do I drop to hit the whole project*
  mostly is not. It is also where merge bias stops being a talking point — a milestone is a join
  by construction, so the effect shows up per group instead of buried in one total. **That is now
  the cheapest route to it**: the reporting layer cut surfacing it as a number, and the entry under *Modelling
  depth* is what this would make worth building.

  **Not a section and not a sprint.** Grouping a plan so it can be *read* is the separate entry
  under *Getting work in*, and it is the cheaper thing — tags without a rollup, so nothing
  acquires an estimate that is not a task's. A work is something you forecast: it has a
  date and no workflow, because boards and states work moves through are what *What Aurevanta
  is not* rules out in as many words.

  **One schema question to settle before any of it.** Whether one task can serve more
  than one work — "everything needed for launch" and "everything needed for the security
  review" overlap, so many-to-many is the truthful answer and a nullable column on the item is
  the cheap one. Choosing the cheap one here is the shape of mistake chosen handles spent a whole work
  undoing.

### Modelling depth

- **Discrete risk register** — "the vendor API may slip: 30% likely, adds 10–20 days."
  These are Bernoulli events, not duration variance, and real projects fail on them. A few
  lines in the sampler.
- **Correlation groups** — items sharing a component, a person, or an unknown technology fail
  together. **This is now precisely one step beyond a thing that exists**: the common-cause model's global team
  factor is one log-normal multiplier drawn per run and applied to every remaining duration, and
  a group is the same multiplier drawn per group and applied to its members. The sampling is a
  few lines. What it actually costs is a *grouping somebody has to define* — schema, a way to
  put an item in a group, and a screen to do it on — plus a second question to ask per group,
  which is the part elicitation says is hard. One shared factor already captures most of the effect,
  which is why this stays here rather than moving up.
- **Learning curves and ramp-up** — a new joiner does not deliver at full rate on day one,
  and adding people to a late project has a known cost. Only meaningful once the resource model exists.
- **The probabilistic critical path** — with uncertainty, no single path is *the* critical one.
  Report how often each path drives completion (criticality index); a path that is critical in
  40% of runs is a different management problem from one that is critical in 99%. **Not the same
  thing as the contribution ranking, and the difference matters**: an item that never varies can decide the
  finish in every single run and widen the band by nothing at all. Criticality is measured inside
  the scheduler and answers "what is holding this up"; contribution is measured against the
  outcome and answers "what is making this uncertain". Conflating them gives a number that means
  neither. **Cut from the reporting layer** — it is strictly more statistics than the band it would sit beside,
  and it needs `Schedule` to report which items decided each run, which is modelling depth
  wearing a communication label. It sits here rather than under *Presentation* for that reason;
  the heatmap that draws it is the presentation half.
- **Merge bias, surfaced explicitly** — where parallel branches must both finish, expected
  completion is later than either alone, and it compounds at every join. Simulation gets it free;
  spreadsheets get it wrong universally, so it is worth naming rather than burying inside a
  total. **Cut from the reporting layer with the bullet above**: as a figure it is a correction to a number the
  reader that work is for has not yet understood. **The icebox's own *features* entry is
  where it becomes cheap** — a milestone is a join by construction, so the effect shows up per
  group instead of inside one total.

### Trust and feedback

- **Backtesting** — replay a team's historical data and show what Aurevanta would have said
  six months ago versus what happened. The fastest way to earn trust from a sceptic, and it
  needs no new modelling.
- **A proper variance decomposition** — Sobol or ANOVA indices, which partition the spread
  including the interactions between sources, and so *would* add up to a whole where the contribution ranking's
  squared correlations deliberately do not. Turned down there rather than forgotten: it costs at
  least a re-run per source where the ranking costs one for the whole plan, and the ranking is
  the honest ninety percent. Worth revisiting only when somebody asks a question the ranking
  cannot answer — "how much of the spread is the team factor *on its own*, with the items held
  still" is the shape of that question.
- **Estimate hygiene warnings** — flag estimates gone stale, ranges pasted identically
  across items, and clustering on 3/5/8. Extends elicitation's overconfidence check from single
  estimates to patterns across a plan. **Both single-estimate checks now exist** as
  `EstimateQuality`, with their thresholds stated once beside the arithmetic, so this extends
  functions rather than reimplementing rules — and the clustering half is the interesting one
  precisely because elicitation's measurement showed that 3/5/8 passes every check that looks at one
  estimate alone. Seeing it forty times in a plan is a signal nothing at the single-estimate
  level can produce.
- **Closing the estimation loop** — calibration builds every part of the path from a range to what the
  work turned out to take, and joins none of them. A range is written in one small form on a plan
  row; what the work took is reported in a different small form on the same row; how the two
  compared is read on a separate page reached from the nav. **Nobody is ever told how their own
  estimate turned out.** The record is an organisation-wide aggregate, so the single most
  instructive sentence this product could say — *you said 10 to 40 hours and it took 100* — is the
  one thing it does not.

  What that looks like: ask what the work took at the moment somebody marks it finished, rather
  than as the fourth box of a form about status; show that against the estimate as soon as both
  exist; and put calibration's coverage counts on the plan screen as the work they name, so "45 finished
  tasks never recorded how long they took" becomes a list somebody can act on instead of a number
  they can only read.

  **The unflattering half of the same point is that most of calibration's bookkeeping is evidence
  disqualified by when a form was filled in.** Work finished with no start reported; estimates
  counted as reports only because they were written on the start day. Each of those is honest, and
  each is a range that told the truth about a task and cannot be scored because of the order two
  screens happened to be visited in. **So the fix is at collection time and never at scoring
  time** — `design/calibration.md` decision 1 is precise about why loosening the boundary is the one change
  that makes the number kinder without making it better. Fewer ways for evidence to fall out, not
  a better-worded account of why it did.

  **Two things it must not become.** Not time tracking, which *What Aurevanta is not* rules out
  and which this is not: one number at completion is not a timesheet. And not a correction fed
  back into the engine — `design/calibration.md` decision 8 — because the loop worth closing is a person's,
  and the moment the model closes it on itself the record measures the correction rather than the
  estimator.

### Collaboration

- **Delphi / planning poker sessions** — the plan schema already keeps the schema open for several
  estimates per item. This is the UI that makes multi-estimator real, and disagreement
  between estimators is itself a signal worth surfacing.
- **Commitment tracking** — record what was promised, at what confidence, on what date;
  then report whether it landed. Organisational calibration, one level up from calibration's
  per-estimator version.
- **Discussion on estimates** — a range without its reasoning is hard to revisit six weeks
  later, when the person who set it has forgotten why.

### Presentation

- **Gantt with uncertainty bands** — every bar spanning P10–P90 rather than a false hard
  edge. The familiar view, without the false precision that makes Gantt charts lie.
- **Criticality heatmap** — colour items by how often they land on the critical path,
  pairing with *the probabilistic critical path* under *Modelling depth* — which is where that
  entry went when the reporting layer cut it, and which has to exist first: this is the drawing, and the
  criticality index is the measurement.
- **Confidence dial** — drag from 50% to 95% and watch the date move. Makes the
  confidence-versus-date trade tangible instead of abstract.
- **Distribution curves — one per estimate, and one for the whole simulation.** The
  percentile table states five numbers; the shape says why the mean sits above the middle and
  why the right tail is the part worth managing, which is the reporting layer's argument arriving early and
  cheaply. **The plan's curve needs no new data at all**: every run already stores a
  hundred-bucket histogram in `forecast_runs.outputs`, both read endpoints already send it,
  and `ForecastPanel` draws none of it — `design/simulation-engine.md` step 6 records that payload arriving
  unused and says this screen is what would notice first. **A task's curve needs a decision,
  and the simulation engine already took the same one once**: the browser must not fit the distribution, because
  two rules that can disagree about one estimate is what `PasswordRules` exists to prevent, so
  the fit is *published* — `mu` and `sigma`, or points to plot — rather than re-derived from
  P10 and P90 inside a chart component. **And a task two people estimated is a mixture rather
  than a curve.** the simulation engine decision 3 samples one estimator per run, so the honest picture is
  bimodal — which is the strongest reason to draw it at all, since a shape says "these two do
  not agree" in a way two rows of a table do not, and smoothing them into one hump would be
  the averaging that decision refuses, arriving through the presentation layer. Two things it
  must not do: interpolate the histogram into a precision the ranges never had, and let the
  limitations slide from beside the number into a footnote under a picture — `no_team_factor`
  means whatever curve is drawn is narrower than the truth. It would also be the *first* chart
  in the product, which is what *Reworking the interface* is about when it says that work
  belongs before the reporting layer spends effort on charts; and the accessibility bar is much harder to hold
  in a drawing than in a form, so the percentile table stays as the text equivalent rather
  than being replaced by one.

### Speculative, flagged as such

- **LLM-assisted decomposition** — "break this epic into items." Plausible and dangerous:
  a machine-generated work breakdown carrying machine-generated estimates is exactly the
  confident garbage this product exists to prevent. If it is ever built, the output must be
  a draft a human estimates, never an estimate itself.

### Probably mis-filed

Four of the above are not really nice-to-haves:

| Idea | Why it may deserve a place |
|---|---|
| **Import from an issue tracker** | Closer to an adoption blocker. Nobody re-types a backlog to trial a tool, so this may gate real-world usage entirely. |
| **Shareable read-only link** | the reporting layer's whole purpose is reaching non-specialists; requiring them to hold an account undercuts it. Cheap to build, large audience unlocked. |
| **Discrete risk register** | A modelling gap rather than a feature. A forecast that models duration variance but ignores known discrete risks is systematically optimistic in a way the band does not show. |
| **Closing the estimation loop** | calibration's own coverage counts are the argument. A calibration record needs finished work carrying both an estimate and a measured outcome, and the outcome is optional because nothing in the product asks for it at the moment somebody would know it — so the ordinary answer is "nothing scored yet", and no amount of arithmetic behind that changes it. **A feature whose usual output is an explanation of why it has no output has not shipped.** This is the work that decides whether the whole of calibration ever says anything. |

---

## Future — unsequenced

Not the icebox, which holds estimation ideas that nobody has committed to. These are
questions already answerable today, recorded so the answer is not re-derived under pressure
later.

### Account security

None of these is estimation work, so none competes with the estimation work. They are here
rather than in the icebox because all three are changes to *identity*, which is the one part
of this product already built: they land on the original tenancy design/the team model code rather than waiting on a schema that
does not exist yet. The first two are written together because they touch the same
credential, and one of them cannot honestly be built before the other; the third widens what
a credential is allowed to be.

- **Two-factor authentication.** TOTP first — an authenticator app needs no delivery
  infrastructure, where SMS needs a provider and is the weakest factor on offer anyway. Three
  things this codebase already decided constrain how it goes in. **The ordering rule from the
  verification gate applies unchanged**: whether an account has a second factor is checked
  only *after* the password, or sign-in becomes a way to ask which addresses hold accounts.
  **`SignInRateLimiter` counts failures**, and a wrong code is a guess exactly as a wrong
  password is, so it belongs under the same budget rather than a second one beside it.
  **Recovery codes are the hard part, not the TOTP maths** — a second factor is also a second
  way to lock somebody out of their own account, and under a hard verification gate the
  password reset that exists to recover an account must not become a way around the factor.
  Distinct from SSO, which the team model excluded: an organisation that federates gets its factor from
  the identity provider, and the two would coexist rather than replace each other.
- **Staying signed in for longer.** Today the only credentials are the twelve-hour access
  token and the identity token — `security.md` says a grep of both sides finds no refresh
  token — so "remember me" as currently reachable means *lengthening the twelve hours*, which
  widens precisely the window Security debt finding 1 is about. **That makes this dependent on
  finding 1 rather than merely adjacent to it.** The honest shape is a short-lived access
  token beside a long-lived credential that can be withdrawn, and "can be withdrawn" is the
  server-side per-request check (`token_version`, or a stored refresh row) that finding 1's
  fix introduces. Built the other way round, this ships a longer-lived credential nothing can
  take back, and sells it as convenience. A refresh token would also be a **third token kind**,
  which the existing split already accommodates: endpoints guard on `SCOPE_TENANT` rather than
  on the absence of `SCOPE_IDENTITY`, so a new kind reaches nothing until it is granted
  something deliberately.
- **A credential a machine can hold.** Every credential this product issues names a person and
  expires within twelve hours, so anything automated has to sign in *as* somebody, holding
  their password to do it. That is fine while nothing is automated, and the icebox is already
  full of things that are: an issue-tracker import is inbound and carries the *other* system's
  credential, but CI marking work finished, and a bot recording actuals for calibration to score, are
  both pushing in. **The reason it sits beside the two above rather than in the icebox is that
  it is the same argument reaching a third place**: a token that does not expire is exactly the
  window Security debt finding 1 is about, opened deliberately this time, so it needs the same
  server-side withdrawal that finding 1's fix introduces — plus a scope narrower than a
  person's, and a row somebody can see and revoke on the members screen. Built as "remember me
  for robots", it is finding 1 with a feature name on it.

**Ordering, if both are picked up:** finding 1, then longer sessions, which is that fix
turned into a feature. Two-factor authentication is independent of both and can go at any
time — though it is worth noting that the account it protects can still be held for twelve
hours by anybody who obtained a token before the factor was added.

### Which forecast runs are history

**Every forecast is written down, and that is right up until something starts asking in
bulk.** `forecast_runs` has no update and no delete because the point of the table is that
somebody asked twice; the drift detector walks successive runs and its movement
decomposition diffs two of them. Both readers assume every row is a person deliberately
re-forecasting the same plan.

**The inverse query threatened that assumption and the resource model threatened it harder.** Inverse queries rank candidate
scope cuts by re-running the schedule without each one, and "what if we hire someone?" sweeps a
team — dozens of runs to answer one question. Landing those in the same table would give the
detector a history that is mostly hypotheticals, and give the diff two runs that were never about
the same plan.

> **Neither did, and both were built to.** the cut search and the resource model's hires are read-only replays out of
> a stored run's own seed: forty simulations can go past for one question without
> `forecast_runs` gaining a row. That table still means one thing — somebody asked the engine —
> which is what the drift detector walks.

**So the question is what a run is *for*, and it was cheap to answer then and awkward
afterwards.** Either a scenario is never persisted — the engine is pure and a run costs about
300ms, so nothing forces a row — or the table gains a kind and every reader of history filters
on it. What must not happen is the third outcome, where scenarios are stored because storing
was the code path that already existed, and the detector degrades with nobody able to say from
which release.

> **The inverse query took the first of the two, and `ForecastApiTests` holds it there.** A cuts request writes
> nothing at all: every counterfactual is a replay of a run that already exists, in memory, out
> of its own stored seed. Forty simulations can go past for one question and `forecast_runs`
> gains no row — so the history stays what it has always been, a record of somebody deliberately
> asking twice. **The resource model inherits the answer rather than the question**, and the case that would
> reopen it is a scenario somebody wants to keep and come back to, which is a different feature
> and needs the kind column this section describes.

### Deleting a person

**Nothing here deletes an account, and that is a decision this product has not taken yet.**
Removing somebody deletes their membership and never their identity — the team model was explicit — and
`estimates.estimator_user_id` and `forecast_runs.requested_by_user_id` point at `users` with
no cascade on purpose, because calibration evidence has to outlive somebody leaving. All of
that is right, and what follows from it is that an erasure request has no answer in this
product. The first one will arrive with a deadline attached, which is the argument for
settling it while nobody is waiting.

**The two honest answers do not convert into each other, so the choice is real.**
*Pseudonymise* — clear the address, the display name and the credential, keep the row and
everything pointing at it — leaves calibration's history intact for every colleague who did not leave,
and keeps a forecast attributable to *someone*. *Delete* takes their estimates with it and
silently rewrites the calibration record of people who never asked for anything. The first is
what the immutability discipline in this schema already implies; the second is what somebody
reaches for under a deadline, because it is what "delete my account" sounds like it means.

**Adjacent and much easier: exporting what this product holds about one person**, which is
the request that usually arrives first. Every row involved is reachable from one `users` id,
so it needs no decision — only a screen and an endpoint.

### Generalising the unit of estimation

The plan schema stores effort in hours (`design/plans-and-estimates.md` decision 3), which is the right decision to ship.
This is here because "make the unit configurable" sounds like one change and is three, and
the cheap-looking version of it is the one that hurts.

**Nothing about the plan schema makes any of them worse, so none of this is perishable.** That is worth
stating, because chosen handles was: handles had to be fixed early since links get bookmarked into state
nobody controls. Nothing outside this database refers to an hour. Every row written today is
unambiguously hours, so a later migration backfills a default rather than guessing what old
rows meant — which is exactly why adding a unit column *now*, against a need nobody has, buys
nothing.

- **Days, weeks, ideal-hours — free, and already provided for.** The same quantity rescaled;
  `design/plans-and-estimates.md` already stores hours and lets the UI show days. The multiplier is a setting —
  and the calendar has since given a working day a length, though **not one this can borrow**: that
  number is stated per *run* and stored on it, because it is an assumption a forecast was made
  under rather than a display preference. A unit setting reading it would make every historical
  estimate rescale when somebody edited a working day, which is the failure `design/calendar-and-dates.md`'s
  decision 5 exists to prevent.
- **Story points — moderate, and the cost is not in the schema.** The migration is a column
  rename plus a unit on the *project* (never on the estimate: multi-estimator means several
  estimates aggregate on one item, and mixed units there is undefined aggregation). The work
  is that points only become time through a **velocity, which is itself uncertain** — sampled
  points × sampled velocity, a few lines inside the simulation engine's loop and a more natural fit here than
  anywhere else, since this engine already samples distributions and the throughput forecast's throughput history
  is where a defensible velocity would come from. Three consequences: **The resource model needs the
  conversion first**, because effort divided by an allocation only means anything in time
  units; **Calibration needs actuals in hours regardless**, since nobody records "actual story points";
  and **`lag_hours` stays time whatever the project uses**, because a finish-to-start lag of
  three points is meaningless. A points project therefore stores both units, and the unit is a
  property of a quantity rather than of the installation.
- **Money is a second axis, not a second unit** — and this is the point worth having written
  down. It is *mathematically easier* than time, not harder: costs genuinely sum, so there is
  no scheduler, no critical path and no merge bias, and the closed form the simulation engine rejected would be
  adequate. What doubles is the output surface, which is what "not cost or budget modelling"
  above is really protecting. **So the trap is a `unit` column on `estimates`.** An item that
  costs money also takes time, and anyone asking for cost wants both at once; a unit slot gives
  each item one estimate in one unit and makes the thing they asked for harder to build. Cost,
  if it ever happens, is a **second estimate alongside the time one**, sharing the P10/P50/P90
  machinery and the immutability discipline and not the unit slot.

### Reworking the interface

Every screen in this product is deliberately plain, and each was built that way for a stated
reason rather than by neglect: the team model kept the auth forms minimal to reach a team product, and the plan schema
says outright that its plan-entry UI "looks bad" and that making it good is a later concern.
Enough of them exist now — auth, members, settings, projects — that "make it look like a
product" is real work rather than a tidy-up, so it is recorded as such.

**What this is not is elicitation.** The three-box P10/P50/P90 form will be the ugliest thing on
screen and the most tempting thing to fix, and styling it is the one change that cannot help:
`product-concept.md` is explicit that three boxes produce 3/5/8 without thinking, which is a
question-design problem. **Elicitation replaces what is asked; this replaces how everything looks.**
Conflating them means a beautifully styled form eliciting exactly the same garbage.

> **Half of that is now spent, and it is the half that was not this.** elicitation replaced what the
> estimate form asks: three boxes became three questions asked one at a time, and the percentile
> names are gone from the screen. It is still deliberately plain — one input, a hint, a review
> and four buttons — so the sentence above stands exactly as written for the other half. What
> changed is that the most tempting thing to style is no longer the ugliest thing on screen for
> the *reason* it was: it is plain now because everything is, not because fixing it needed a
> work. **The trap it names has not moved.** A beautifully styled form eliciting the same
> garbage was always the risk, and it still is — elicitation changed the question, and nothing about how
> anything looks has been shown to change an answer.

**What is actually there today**, so the size of it is not a surprise: one hand-written
`App.css` of about 770 lines, a dozen colour variables in `index.css` with a dark-mode block,
and components assembled per screen from `Field` and bespoke markup. There is no component
library and no design system — class names agree by convention, which is why `.members` and
`.projects` share a rule that neither owns. It is coherent, and it is coherent by hand.

**Deferrable, but not indefinitely, and the deadline is the reporting layer rather than taste.** Nothing
outside the browser depends on how a screen looks, so this is not perishable the way handles
were — but the reporting layer is where charts arrive, and a confidence cone, a burn-up and a criticality
heatmap are exactly the things that get built twice if the second build is a rework. The
accessibility bar the auth forms set is the same argument from the other side: it is
cross-cutting above, it is much harder to hold in a chart than in a form, and a rework is
when it is either kept or quietly lost.

> **The reporting work did not wait, and it is worth saying exactly what that cost.** One chart exists now: a
> burn-up with a cone, inline SVG, no library, coloured from the same dozen variables as
> everything else — so a rework restyles it rather than fighting a dependency's own opinions.
> What it will cost twice is the drawing, and that is accepted: what it deliberately did *not*
> cost twice is the half a rework does not touch, because the table came first and the picture
> was added over it. **The deadline in this paragraph has therefore passed rather than been
> met**, and the argument it makes is unchanged for the two charts still to come — the heatmap
> and the Gantt are both in the icebox, and both are much larger drawings than a cone.

**It still ranks below the engine, and this is the section where that has to be said.** The
ordering principle's warning is precise about this failure: a beautiful plan editor that
forecasts nothing is the tool this product exists to replace. So the honest position is that
the interface is worth doing well, worth doing before the reporting layer spends effort on charts inside it,
and worth doing after there is a forecast to put in front of anyone.

---

## Cross-cutting — never a feature of its own

Threaded through the above rather than scheduled as a block.

- **Security** — rate limiting arrived in the team model; what is left is below as *Security debt*, which
  is a reviewed list rather than the guess this bullet used to be. The JWT secret must still be
  set explicitly in any real deployment.
- **Localisation** — infrastructure exists, English only. Adding a locale is a catalogue
  file *once* the backend sends codes instead of prose (the team model).
- **Accessibility** — the auth forms set this bar; keep it as charts arrive, where it is
  much harder. A confidence cone needs a non-visual equivalent. **The reporting work is the first test of that
  and it inverted the sentence**: the equivalent is not a fallback bolted to a picture, it is
  built first, it is what the tests assert, and the drawing is added over it `aria-hidden`
  carrying nothing the table does not. Two reasons beyond accessibility, and both are why the
  order is worth keeping for the next chart: a cone described in words has to be *understood*
  before it can be described, which is a better filter on whether it is worth drawing than
  drawing it is — and a text equivalent is not what a restyle touches, so the half that survives
  the rework below is the half built first.
- **Operations** — CI, container build, migration strategy, health and metrics. **Plus a
  per-tenant limit on concurrent forecasts**, which `design/simulation-engine.md` names as the thing that
  actually bounds what one member can make this server do: `Engine.MAX_SAMPLE_COUNT` is a
  bound on absurdity rather than a promise of speed, and the simulation runs *inside* the
  request's transaction, so a few large forecasts at once occupy database connections as well
  as processors. **The contribution ranking widened that surface and it is worth saying so**: a contributions
  request replays a whole run, measured at about half a second at five hundred items, and
  unlike asking for a forecast it is a `GET` that any reader can repeat as fast as they can
  click. The limit is the fix; making one endpoint non-transactional would only move half the
  problem and leave two shapes of the same code.
- **API documentation** — OpenAPI, once the domain endpoints exist and are stable.

### Dates the schema accepts and reality does not

**Found in use rather than in review, which is the reason it is written up here.** A plan in the
development database held a task marked `DONE` with a completion date of 25 August while the day
was the 18th, and another `IN_PROGRESS` with a start date in December. Both were typed into this
product's own progress form, and the server took them.

**The gap is that every check in the domain is about *internal* consistency and none is about
external.** `WorkItemService.requireConsistent` asks whether the four fields agree with each other
— a state that needs a date has one, a completion is not before its start, nothing carries what its
status cannot hold — and never whether they agree with *when it is*. Nothing anywhere refuses a day
that has not happened.

**What it costs is not a tidy record but a feature that silently stops working.** the throughput forecast's throughput
reads `completed_on`, and a completion after the day being asked about is refused with
`throughput_out_of_order` — correctly, because a history whose last week is before its own last
delivery would give wrong numbers nobody could see. The panel then catches the refusal and shows
nothing, so the whole comparison disappears from a plan for a reason no screen names. **The
downstream readers are the ones who suffer, and they are all the recent ones**: calibration scores an
estimate against an outcome dated in the future, the burn-up draws work completing before it
happened, and the throughput forecast refuses outright.

**Three things to settle, and only the first is obvious.**

- **Refuse or warn?** This product does both elsewhere and the split is principled:
  `progress_out_of_order` refuses because a completion before its start is a typo and nothing
  downstream can tell a typo from a fact, while `EstimateQuality` only advises because a tight band
  is sometimes correct. A completion date in the future is the first kind. **Refuse**, alongside
  the check that already sits beside it.
- **Where "now" is, which is the sharp part.** A server cannot know the caller's today — that is
  the whole of why `starts_on` and the throughput forecast's `asOf` are stated by the browser and why `todayHere` exists.
  So the check has calibration's decision 1 problem in miniature: a completion dated "tomorrow" is a typo in
  Lisbon and this morning in Auckland. A day of tolerance against the server's own UTC date is the
  cheap answer and it is a *claim*, so it belongs written down beside the check rather than inferred
  from a comparison operator.
- **What happens to the rows already stored.** A new refusal does not reach back, so every reader
  must still cope with a date that could not be written today — which means the throughput forecast's refusal stays and
  the fix is at both ends. **That is the honest scope**: a validation rule alone would make the
  problem stop growing and would not fix one plan that already has it.

**And the smaller half, which is free.** The progress form's `<input type="date">` carries no
`max`, so the browser invites the value the server would then refuse. That is the rule this product
already follows on the progress form — offering only the boxes a status has room for, so nobody is
invited to type something that will be thrown back — applied to a date rather than to a field.

**Not scheduled, and here rather than inside a feature for this section's stated reason**: a
correctness list inside a feature's bullets is scope, and scope is what gets cut.

### Security debt

Four findings from the review taken after chosen handles and before the plan schema. **`security.md` is the record** —
it carries the exploit paths, the fixes, what was deliberately accepted, and what was checked
and found sound. This table exists so the debt is visible from the plan; it is not a second
copy of the reasoning, and it should not become one.

| # | | Severity | Cheapest moment |
|---|---|---|---|
| 1 | The credential cannot be withdrawn, and sits where a script can read it | Medium | Any time — but the two halves are one migration, so do them together |
| 2 | Registration is a free, repeatable account-existence oracle | Medium | Before a registration screen is redesigned; the ordering fix is free today |
| 3 | An invitation token is interpolated into an API path unencoded | Low | Before the plan schema — its blast radius is the endpoint set |
| 4 | Raw invitation tokens travel in the request line | Low | Before the API is public; fixing 4 removes 3 |

**Cross-cutting rather than scheduled, deliberately.** None of these is estimation work, and a
list of security fixes sitting inside a feature's bullets competes with that feature for
the same attention — which, given the ordering principle above, is a competition security
loses every time the plan is trimmed to reach the engine sooner. They belong here, where the
question is *when is this cheapest* rather than *what is this work made of*.

**Worth knowing before picking any of them up**: finding 1's second half would make CSRF this
application's problem for the first time. CLAUDE.md currently argues that CSRF needs no defence
*because* authentication is bearer-only with no cookie — true today, and false the moment a
cookie is set.

### Decided: forecast quality is not a commercial axis

Tiering the *method* — a cheaper closed form on lower plans, sampling on higher ones — was
considered and rejected. The closed form is not a cheaper approximation of the same model;
it is a **different, blinder model** that cannot represent the team factor (see the simulation engine). Gating
on it would mean selling a band that is too tight without saying so, which is the exact
failure this product exists to prevent. Sampling also costs milliseconds, so there is no
infrastructure bill to recover.

Everyone gets the same model. If the product is ever monetised, the axes that scale with
real cost and real value are **seats** (the team model), **history depth** (calibration/the throughput forecast need accumulated
actuals), **scale** (large portfolios, the inverse query inverse-query sweeps) and **org features** (SSO,
audit, portfolio rollups) — never statistical method.

Revisit only if a feature arrives whose cost is genuinely non-trivial: scheduled
re-forecasting, very large portfolios, or an LLM-assisted elicitation step in elicitation.

**The resource model is the first candidate.** Resource-constrained scheduling inside every Monte Carlo
run is seconds rather than milliseconds and scales with both item and resource count — real
compute, unlike the sampling engine itself. If anything in this product is ever metered,
that is the honest place for it. The principle still holds: meter the *scale of the plan*,
never the quality of the maths applied to it.

---

## What I would build next

**Availability, and nothing before it.** It is the only outstanding item that changes what a
number means rather than how it is presented — and it is the one the rest of the forward list
keeps deferring to. Everything in the icebox is a further lens on a forecast that already
works; availability is the last thing that makes the forecast itself more true.

The temptation that has not changed is the *plan-entry UI that already exists and looks bad*.
It is meant to. What the estimate form asks was the point, not how it looks, and the interface
rework is recorded under *Future* rather than skipped. Neither it nor anything in the icebox is
the engine.

