# Plans, work items and estimates — the design record

> **Scope.** The plan schema: the first migration that carries real domain data — projects,
> work items, three-point estimates, precedence dependencies, and progress — plus the plainest
> UI that can fill them. Explicitly excluded: the simulation engine (the simulation engine), elicitation design
> (elicitation), calendars and capacity (the resource model), and handle-based routing.
>
> **How to read this.** Decisions first, because two of them were open in `../roadmap.md` and one
> reverses an assumption it made. Then six steps, each a reviewable commit that leaves the
> build green: `./mvnw test` (format gate, 100% branch coverage) and, where the frontend is
> touched, `npm run lint && npm run build && npm run test`.
>
> **This is the most expensive thing in the product to get wrong.** the simulation engine through the throughput forecast all read
> from this schema, and two of them (calibration calibration, the reporting work's sliding-date detector) read *history*
> that cannot be reconstructed if it was never written. The parts of this plan that look like
> over-provision — immutable estimates, an estimator that outlives a membership — are there for
> that reason and are the parts to leave alone if the work needs trimming.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | Projects ✅ *done* | — |
| 2 | Work items ✅ *done* | 1 |
| 3 | Estimates, and what coverage means ✅ *done* | 2 |
| 4 | Progress, and actuals ✅ *done* | 2 |
| 5 | Dependencies ✅ *done* | 2 |
| 6 | Close out ✅ *done* | 1–5 |

**There is no fat to cut here**, which is worth stating plainly rather than discovering
halfway. Drop estimates and there is no product. Drop dependencies and the simulation engine forecasts a flat
list, which `../product-concept.md` already recorded as producing 51 or 86 days for the same ten
items depending only on structure. Drop progress and calibration, the throughput forecast and the reporting work all lose their input. The
only genuinely optional thing in this work is how good the UI looks, and the plan already
assumes it looks bad.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Unit of estimation | **Task**, per `../roadmap.md`. Coarser units hide scope growth inside the estimate, which the simulation engine would then double-count. |
| 2 | Multi-estimator | **Schema now, UI later.** Several estimates per item, one current per estimator. |
| 3 | Effort or duration | **Effort**, in hours. Duration is effort divided by what is assigned to it, and that division is the resource model's. |
| 4 | Dependency types | **Finish-to-start with lag**, only. |
| 5 | Items with no estimate | **Forecast what is estimated and report coverage.** *(was open)* |
| 6 | Who may write plan data | **Any member may do everything.** Roles govern administration only. *(was open)* |
| 7 | Handle in the URL | **No.** the plan schema does not route by handle, so chosen handles's two deferrals stay deferred. *(reverses a roadmap assumption)* |
| 8 | Forecast runs | **Deferred to the simulation engine**, which is what writes one. *(departs from the roadmap's bullet list)* |
| 9 | Destructive operations | **Archive, never delete.** Follows from decision 6, and calibration needs it independently. |
| 10 | Scale target | **500 items per project**, stated so that "does this need pagination" has an answer. |

### Decision 5 — Forecast what is estimated, and say what was left out

An estimate is optional. A plan with thirty items and twelve estimates is not a broken plan;
it is every real plan on the day somebody first opens the tool. So a forecast covers the items
that carry an estimate and **reports its own coverage prominently** — "covers 12 of 30 items"
— rather than refusing, or quietly forecasting a subset as though it were the whole.

The alternative worth naming is imputation: give unestimated items a distribution derived from
the estimated ones and mark them as invented. It is defensible and it is what a mature version
of this product probably does. It is not what the plan schema does, for two reasons. It invents data, and a
number that came from nowhere is exactly what this product exists to stop people producing.
And it needs a per-forecast record of *what was imputed*, which is a forecast-run concern, and
forecast runs are the simulation engine (decision 8).

**The plan schema's half of this decision is making coverage computable and visible.** The project and item
responses carry whether an estimate exists and how many items have one; the UI says so on the
project page. The simulation engine inherits a number it does not have to invent.

**The other alternative — refusing to let work start until it is estimated — came up once
step 4 was on screen, and is turned down under calibration in `../roadmap.md`.** It is recorded there
rather than here because that is where the concern behind it is actually answered: an
estimate dated after `started_on` is a report rather than a forecast, and calibration can
tell the two apart from data this work already stores. A gate would have overridden
this decision, and would have bought tidier data by refusing to record something that had
happened anyway.

### Decision 6 — Any member may do everything, and so nothing may be destroyed

Roles govern administration — invitations, members, organisation settings — and nothing else.
Every member of an organisation may create a project, add items, estimate them, record
progress and draw dependencies. **This was chosen over the narrower alternative** (owners own
projects, members own the work inside them), and the reasoning to record is that estimation is
a team activity: multi-estimator support, which decision 2 shapes the schema for, is
meaningless if only owners can estimate, and a per-project permission model is a lot of
machinery to build before anyone has asked for it.

**What it costs, and what the plan schema does about it.** Widening write access makes destructive acts
everyone's to perform. A member who deletes a project deletes a colleague's work, and unlike
removing a member — which an owner can undo by inviting them back — there is nothing to put
back. So the plan schema has **no hard delete at all**: projects and items carry `archived_at`, archiving
hides them, and unarchiving is a member's to perform too.

That is not only a response to decision 6. It is independently required: an estimate is
evidence for calibration calibration, and deleting the item it hangs on would destroy that evidence
years before the feature that reads it exists. Two independent reasons for the same column is
usually a sign the column is right.

**Immutability is doing authorization work here as well**, which is worth noticing. Because an
estimate is never updated in place (decision 2), no member can rewrite a colleague's estimate
even though every member can write estimates. The strictest rule in this schema is the one
nobody had to enforce.

### Decision 7 — the plan schema does not put a handle in a URL

`../roadmap.md` says the plan schema "is the step that first puts an organisation handle in a URL and so ends
the grace period" for reserved handles and retired-handle redirects. **That was an assumption,
and it is not true of this plan.** Plan URLs are `/app/projects/{id}`, as every route in the
application already is. Nothing about entering a plan requires an organisation handle in the
path; the organisation comes from the access token, as it does everywhere else.

So chosen handles's two deferrals stay deferred, and security findings 3 and 4 are not accelerated by
this work. They remain worth doing — 4 especially, while the API is unreleased — but on
their own schedule under *Security debt*, not this one's.

This is a scope reduction taken deliberately. The plan schema is already the largest schema risk in the
product, and adding a routing change and a reserved-word policy to it would be spending the
riskiest work's attention on something with no domain content.

### Decision 8 — Forecast runs are the simulation engine's table, not the plan schema's

The roadmap lists forecast runs among the plan schema's schema, on the grounds that the history "cannot be
reconstructed later". The premise is right and the conclusion does not follow: **nothing
produces a forecast until the simulation engine**, so there is no history accumulating in the meantime to lose.
What designing the table now would cost is real — its columns are the engine's inputs,
assumptions and outputs, none of which exist yet, so every one of them would be a guess made
without the thing that reads it.

The obligation this transfers to the simulation engine is stated here so it cannot be forgotten: **The simulation engine must persist
every run from its first commit**, not add persistence once the engine works. That is the
version of the roadmap's warning that survives.

### Decision 3 — Effort, in hours

Effort rather than duration, per the roadmap: duration bakes in an allocation assumption, and
the resource model exists to make allocation explicit.

**The unit is hours**, stored as `numeric`. A "day" is a calendar word — it presumes a working
day of some length, which is precisely the assumption the resource model has to surface rather than inherit.
A team that thinks in days multiplies by their own day length, and when the resource model arrives that
multiplication becomes a setting instead of a convention buried in old rows. The UI may well
show days; the column stores hours.

### Decision 10 — 500 items per project

`../roadmap.md` lists "no scale target has been set" as a known thin spot, and the reason it
matters is that it decides whether the simulation engine forecasts synchronously and whether the plan schema needs pagination.
**Fixed here at 500 items per project**, which is a large plan by the standards of the teams
this is for, and which comfortably fits one response and one simulation in a request.

So: no pagination in the plan schema, and no cursor API to design around. If a project ever needs more than
this, that is a product signal — the unit of estimation is a task (decision 1), and 500 tasks
in one project is usually a portfolio wearing a project's name.

---

## Step 1 — Projects ✅ *done*

**Goal.** An organisation can hold more than one plan, and they cannot see each other's.

- `projects` — `id`, `tenant_id`, `name`, `description`, `created_at`, `archived_at`.
- **The name is not unique**, and this time that is a decision rather than an oversight. Chosen handles
  spent a piece of work removing an accidental uniqueness constraint on an organisation's name;
  two projects called "Q3 platform work" in one organisation is ordinary, and the id is what
  addresses them.
- `POST /api/projects` `{name, description?}`, `GET /api/projects`, `GET /api/projects/{id}`,
  `PATCH /api/projects/{id}` `{name, description}`, `POST /api/projects/{id}/archive` and
  `/unarchive`.
- **Tenant from the token, never the request**, and every id looked up *together with* the
  tenant — the rule the security review checked the whole of the team model against.
- `GET /api/projects` lists unarchived by default, `?archived=true` for the rest.
- Frontend: `/app/projects` lists them, `/app/projects/{id}` is the project page, and a nav
  entry beside Members. Visible to every member (decision 6).

**Tests.** A project belongs to the organisation that made it and is invisible to another, with
a two-organisation fixture so a leak fails. A member creates one. Archiving hides it from the
default listing and unarchiving brings it back. Two projects may share a name.

**Done when** an organisation has somewhere to put a plan.

### As built — where it differs from the above

- **`MembershipService.requireMember` became public**, which is the whole of what "visible
  to every member" turned into. Owner-only endpoints already shared one rule that re-reads
  the membership rather than trusting the role in a token; any-member endpoints needed its
  twin, and writing a second check inside `project` would have been the second chance for
  one of them to drift. Every method in `ProjectService` starts with it and takes the
  organisation off the row that comes back.
- **Archiving and unarchiving are one service method with a boolean.** They are one decision
  read in both directions, and splitting them would have been two lookups and two membership
  checks to keep in step. `Project.archive` keeps the *first* moment, the way
  `User.markEmailVerified` does: archiving something already archived is a no-op arriving
  twice, not a fresh decision.
- **A description is nullable, and an empty one is stored as nothing.** The request records
  normalise `""` to null in their compact constructors, so the column has one spelling for
  an absence rather than two for a query to miss half of. A null on `PATCH` therefore
  *clears* it — which is a genuine value here, unlike the name, where a column that cannot
  be empty has no reading of null that is right.
- **`Project.isArchived()` was written and then removed.** Nothing read it, and an accessor
  no logic reads is an untested branch dressed as an API; the tests assert on
  `getArchivedAt()` instead. The same thing happened again in steps 2 and 3, which is worth
  noticing as a pattern rather than three accidents.

---

## Step 2 — Work items ✅ *done*

**Goal.** The unit that carries an estimate exists.

- `work_items` — `id`, `tenant_id`, `project_id`, `title`, `description`, `created_at`,
  `archived_at`. Progress columns arrive in step 4 and dependencies in step 5.
- `tenant_id` is carried on the row rather than reached through the project. It is
  denormalised on purpose: it is what makes every query filterable by the caller's own tenant
  without a join, and the isolation rule is only as good as it is easy to follow.
- `POST /api/projects/{id}/items`, `GET /api/projects/{id}/items`, `PATCH /api/items/{id}`,
  `POST /api/items/{id}/archive` and `/unarchive`.
- Frontend: the project page becomes a table of items with an inline add.

**Tests.** An item belongs to one project and one tenant; an item id from another organisation
is a 404, indistinguishable from one that never existed. Archiving an item keeps it out of the
default listing. 500 items load in one response.

**Done when** a plan can be typed in, with no numbers on it yet.

### As built — where it differs from the above

- **`WorkItemController` has no class-level path**, because its endpoints sit at two. An
  item is created and listed *within* a project, which is the only moment its plan has to
  be named; once it exists it is addressed at `/api/items/{id}`. Steps 3 and 5 address
  items directly too, so a path that repeated the project would be a second identifier the
  server had to check agreed with the first — and a mismatch between them is a refusal
  nobody could act on.
- **`WorkItemResponse` carries its `projectId`, where `ProjectResponse` carries no
  organisation.** The asymmetry is deliberate: an organisation is the one the caller's
  token already names, and a project is not, so without it a client renaming an item would
  have nothing to say which plan it had just changed.
- **Listing the work in a plan that is not there is `project_not_found`, not an empty
  list.** "No such plan" and "a plan with nothing in it" are different answers and only one
  is worth acting on, so `WorkItemService` fetches the project through `ProjectService`
  rather than assuming it.
- **An archived project still accepts work**, and the service says so. Archiving means the
  plan is not being worked from, not that it is sealed; refusing here would need a refusal
  nobody asked for, aimed at somebody tidying up an old plan.
- **The project page delegates rather than grows.** `WorkItems` loads and writes on its own
  instead of through `ProjectPage`, because the plan and its contents are separate resources
  on the server and answering for one another's failures would mean a plan that could not be
  renamed because its items would not load.
- **`ProjectForm` and `WorkItemForm` are two components, not one parameterised by a field
  name.** The field names are the server's, and they are what a per-field complaint is keyed
  by. What they *do* share is `optionalField` — "an empty box means nothing, not `''`" is
  the rule either of them could quietly get wrong, so it is stated once.

---

## Step 3 — Estimates, and what coverage means ✅ *done*

**Goal.** The product's actual content: a range, from a named person, that is never overwritten.

- `estimates` — `id`, `tenant_id`, `work_item_id`, `estimator_user_id`, `p10_hours`,
  `p50_hours`, `p90_hours`, `created_at`. **No `updated_at`, and no update endpoint.** A
  revision is a new row.
- **The estimator is a user, not a membership.** A person who leaves the organisation still
  made the estimate, and calibration calibrates *per estimator* across everything they ever estimated.
  A membership can be deleted — the team model made sure of that — so hanging this off one would delete
  history as a side effect of somebody leaving. This mirrors "removal deletes the membership,
  never the identity".
- **Current estimate = latest `created_at` per (item, estimator).** Several estimators may hold
  a current estimate on one item at the same time, which is decision 2 working: their
  disagreement is signal, and the simulation engine decides what to do with it. The plan schema only has to store it and be
  able to find the current ones cheaply — an index on `(work_item_id, estimator_user_id,
  created_at desc)`.
- **Validation:** `0 < p10 <= p50 <= p90`. New constraint codes in `ApiExceptionHandler.CONSTRAINT_CODES`
  and `errors.validation` in `en.ts`, per the existing rule.
- **Coverage** (decision 5): the item response says whether it has a current estimate; the
  project response carries `itemCount` and `estimatedItemCount`. The project page states it in
  words — "12 of 30 items estimated" — because a number nobody reads is not a disclosure.
- Frontend: three fields on an item. **Deliberately plain, and deliberately not good.** elicitation is
  elicitation, and `../product-concept.md` is explicit that three boxes labelled P10/P50/P90
  produce 3/5/8 without thinking, which is worse than no tool because the garbage now carries
  a probability. The plan schema must not pretend this form is the answer, and the plan should not smuggle
  elicitation in under "polish".

**Tests.** An estimate cannot be updated — there is no route that does it, and a second one
creates a second row with the first still readable. Two estimators hold current estimates on
one item simultaneously. `p50 < p10` is refused with a field code. An estimate survives its
estimator being removed from the organisation. Coverage counts only items with a current
estimate, and ignores archived items.

**Done when** The schema holds everything calibration will need in three years, and nothing can quietly
erase it.

### As built — where it differs from the above

Two of these are departures from what this step was written to do, and they are the first
two for that reason.

- **A band the wrong way round is refused as `estimate_out_of_order`, not as a field code.**
  The bullets above say "refused with a field code", and that is not what it is: each of the
  three numbers is perfectly good, and what is wrong is the relationship between them.
  `FieldProblem` exists to say "this box is wrong", so pointing at one of them would be
  picking a culprit arbitrarily. It is a document-level code, and the form shows it in the
  banner rather than beside an input. **The per-field codes this step did owe are there** —
  `Positive` → `positive` and `Digits` → `digits`, in `CONSTRAINT_CODES`, in
  `CODE_PRECEDENCE` (they can fail together: `-0.001` breaks both), and in `errors.validation`.
- **The item response does not say whether it is estimated.** The plan asked for a boolean
  there; what shipped is `GET /api/projects/{id}/estimates`, carrying every estimator's
  current range for the whole plan in one request. It subsumes the boolean, adds the numbers
  the form needs to fill itself in, and shows a colleague's estimate rather than merely
  admitting one exists. The reason to prefer it is structural: the boolean would have made
  `item` depend on `estimate` while `estimate` already depends on `item`, and this keeps
  that arrow pointing one way. The project response carries `itemCount` and
  `estimatedItemCount` as written.
- **`@Digits(integer = 10, fraction = 2)` matches the column exactly, and closes a real
  hole.** `0.005` hours passes `@Positive`, rounds to `0.00` on the way into
  `numeric(12, 2)`, and lands as an estimate of nothing — breaking the rule that had just
  admitted it, silently, after the check that enforces it.
- **Coverage is counted in `ProjectRepository`, in JPQL that names `WorkItem` and
  `Estimate` and imports neither.** It is the one place a feature reaches into another's
  tables, and it is worth being explicit about — the reason it is acceptable is that
  Hibernate parses every query at startup, so a renamed entity fails the context rather than
  the next reader. Compare the package name written as a string that the team model lost silently.
- **`ProjectService` grew a second way to read a project**, because the read model is not
  free: `get` hands back the entity for other services, `planned` adds the two counts for
  the API. Without the split, typing a task into a plan would have paid for two grouped
  counts nobody asked for.
- **The estimator comes off the membership that just proved the caller belongs**, rather
  than a second lookup by the identifier in their token. The lookup version had a branch for
  an account that is gone, which this method cannot actually have — a membership is what
  says the account exists — and an unreachable branch is one nothing can cover.
- **The two loads on the project page were made parallel.** `WorkItems` asks for the work
  and its estimates with one `Promise.all`; neither answer depends on the other, and waiting
  for the first before asking for the second doubled the time to draw the page.
- **`numberField` exists because `Number('')` is zero.** The obvious version of an
  untouched box sends an estimate of no hours, and the visitor is told their estimate must
  be more than zero about a field they never filled in.

---

## Step 4 — Progress, and actuals ✅ *done*

**Goal.** A forecast can exclude what is already done instead of re-predicting the past.

- Columns on `work_items`: `status` (`NOT_STARTED` / `IN_PROGRESS` / `DONE`), `started_at`,
  `completed_at`, `actual_effort_hours`.
- `PATCH /api/items/{id}/progress` `{status, startedAt?, completedAt?, actualEffortHours?}`.
- **Transitions are checked**, because the timestamps are evidence: `DONE` requires a
  `completed_at`, `IN_PROGRESS` requires a `started_at`, and going back to `NOT_STARTED`
  clears both rather than leaving a start date on something that has not started.
- `actual_effort_hours` is optional even when done. Most teams do not track it, and refusing to
  let somebody mark an item finished because they cannot say how long it took would mean
  refusing the common case to serve calibration.

**Tests.** Each transition and each refusal. Actuals are optional on a completed item. Clearing
back to not-started clears the timestamps. Progress on an item in another organisation is a 404.

**Done when** The plan knows what has already happened.

### As built — where it differs from the above

- **They are `started_on` and `completed_on`, and they are dates.** The bullets name them
  `started_at` / `completed_at`, which would have made them instants like every other
  timestamp in this schema — and they are not the same kind of fact. Everything else here
  records a moment the *server* observed; this records a day a *person* reports. There is no
  time of day in "we finished it on the twelfth", and storing one invents the very part
  nobody claimed: midnight UTC reads back as the eleventh for every reader west of the
  meridian. The frontend gets `<input type="date">` and the `yyyy-mm-dd` it hands back is
  exactly what the column holds, so no timezone exists anywhere between the two.
- **A state that needs a date and did not get one is refused, not stamped.** Defaulting to
  the server's clock was the alternative and is worse in a way that only shows up years
  later: calibration and the reporting work read these dates, and neither can tell one somebody reported from one
  the server guessed while nobody was looking. `progress_date_required` says which is
  missing without pointing at a box, because *which* box depends on the state in another.
- **`DONE` does not require a start.** The plan pairs each state with the date it needs and
  says nothing about this one; work is routinely ticked off by somebody who never marked it
  as begun, and refusing that would refuse the commonest way anything gets recorded. Where
  both dates *are* given they must agree about which came first — `progress_out_of_order`,
  refused for the same reason an estimate the wrong way round is.
- **A claim that carries what its own status cannot hold is refused** — `progress_not_applicable`.
  This is the one thing in the plan schema that shipped wrong and was corrected after somebody used it.
  It first kept whatever fitted the status and dropped the rest, which sounds careful and
  is not: hours typed against work marked not started were accepted, discarded, and never
  mentioned. Silently dropping input is worse than refusing it, because the person is not
  told they have been overruled. The entity now writes what it is given, and the service is
  the only place that says what a status means. Work is still put back to not started by a
  request that carries nothing — the difference is that the emptiness is now the caller's
  statement rather than the server's edit of it.
- **The form only offers the boxes a status has room for**, which is the same rule seen
  from the other end: the server refusing something a screen has just invited you to type
  would be a trap rather than a check.
- **Its own endpoint**, `PATCH /api/items/{id}/progress`, rather than more fields on the
  item. Rewording a task is planning and saying it finished on Tuesday is reporting — and
  keeping them apart stops a rename from being able to overwrite the dates calibration reads.
- **Dependencies did not share the migration.** The table below has them together in V10;
  they are V10 and V11 instead, so that each step is a commit that stands up by itself.
- **The frontend got a third row mode**, beside rewording and estimating, and it warns
  before a status change discards something already recorded — the boxes holding it have
  just disappeared, and somebody not told would reasonably assume the values survive.

---

## Step 5 — Dependencies ✅ *done*

**Goal.** The plan has a shape, so the simulation engine can forecast something other than a queue of one.

- `dependencies` — `id`, `tenant_id`, `project_id`, `predecessor_item_id`, `successor_item_id`,
  `lag_hours`, `created_at`. Unique on `(predecessor_item_id, successor_item_id)`.
- **Finish-to-start only, with lag** (decision 4).
- **Refused on write:** a self-edge, an edge between two projects, a duplicate, and **a cycle**.
- **Cycle detection needs a lock, and this is the sharpest thing in the work.** Two edges
  inserted at the same instant can each be acyclic against the graph as it was read and
  cyclic together. It is the `lockOwners` situation rather than the token-redemption one: a
  conditional `UPDATE` makes a race on *one row* safe, and this is a race on a property of the
  whole graph. So validation and insertion happen under a write lock on the `projects` row —
  one lock per plan, held for a graph walk over at most 500 items.
- A cycle is refused with its own code and, where it is cheap, the path that would have closed
  it. A refusal that says "this would create a cycle" and not *which* cycle is a refusal
  somebody has to go and find by hand.
- Frontend: on an item, "must finish before…" picking another item in the same project.

**Tests.** A two-item cycle, a three-item cycle, and a self-edge are each refused. A diamond is
accepted, because it is not a cycle and is exactly the shape merge bias comes from. Two
concurrent inserts that would together close a cycle leave one edge and one refusal, released
together as `SingleUseTokenServiceTests` does — a serial test cannot tell the lock from luck.
An edge to an item in another project is refused.

**Done when** The schema can express a plan whose parts overlap.

### As built — where it differs from the above

The bullets above describe what is refused and how the refusal is made safe, and all of that
holds. What they leave open is the shape of the API, and two of the answers are departures
worth naming rather than details.

- **There is a `DELETE`, and the step above never mentions one.** Nothing else in this
  application deletes: a project archives, an item archives, an estimate is never rewritten
  because calibration reads it years later. An edge is none of those things — it is a constraint the
  scheduler obeys until it is gone, and one drawn by mistake that merely went dormant would
  be a plan quietly forecasting around a line nobody could see. `DELETE
  /api/dependencies/{id}` removes the row, and needs no lock: the invariant is that the graph
  stays acyclic, and taking an arrow away cannot close a loop.
- **Both ends go in the body: `POST /api/dependencies` `{predecessorItemId, successorItemId,
  lagHours}`.** Neither end owns the other, so putting one in the path would have made the
  pair read as though one did — and would have left the server checking a second identifier
  against the first, which is the thing `WorkItemController` avoids by addressing an item on
  its own. The project is not in the request either: the items answer it, and a third
  identifier that could disagree with them would be a refusal nobody could act on. Listing is
  still by plan (`GET /api/projects/{projectId}/dependencies`), because a screen showing a
  plan needs every edge in it at once.
- **A self-edge is `self_dependency`, not `dependency_cycle`, and is answered before any row
  is read.** It is the only cycle decidable from the request alone, so answering it first
  means a caller who put one identifier in both boxes learns nothing about which items exist
  — and the remedy differs: a cycle is a plan to go and rethink, this is two boxes with the
  same thing in them.
- **`ProjectService.lockForGraphChange` takes the lock and refuses nothing**, returning
  `void`. It was first written to throw `ProjectNotFoundException` on an empty result, which
  is a branch no request can reach: every caller has already found a work item inside that
  plan, which is what says the plan exists and belongs here. An uncoverable refusal is a hole
  in the coverage gate dressed as diligence — the same thing `WorkItemService` says about a
  `switch` over an enum. A plan that is not there has no items and no edges either, so there
  is nothing for a lock over it to protect.
- **The cycle path rides on the problem document as `path`, the way `slug_taken` carries
  `suggested`.** Item identifiers rather than titles, since prose from the server is never
  shown to anybody; the client already holds the plan it is drawing. The list starts at the
  proposed predecessor, follows existing arrows back to it, and does not repeat the closing
  item — the frontend adds it back when it draws the loop, because otherwise the last arrow
  is the one step of the route nobody is shown.
- **`lag_hours` is required and `@PositiveOrZero`.** Zero is the ordinary answer and is a
  claim rather than a guess — there is no wait — so the server does not fill it in; a
  negative would be a lead, which is a different kind of edge than the one decision 4 models.
  That added `positive_or_zero` to `ApiExceptionHandler.CONSTRAINT_CODES`, to
  `CODE_PRECEDENCE` and to the catalogue. **The form does answer it**, sending zero for an
  empty box, because its own hint says that is what an empty box means — the strictness is
  the API's contract with every other caller, not a question to push at somebody typing.
- **A duplicate has both a pre-check and an index mapping.** `uq_dependencies_edge` joined
  `ApiExceptionHandler.CONSTRAINT_CONFLICTS`, so the pair who get past the check in the same
  instant are told `dependency_already_exists` rather than a bare `conflict` —
  and `ConstraintNamesTests` picked it up for free, which is the point of that test.
- **Cross-project and self-edge are `400`; duplicate and cycle are `409`.** The first two are
  facts about what the request names, and the second two are conflicts with what is already
  drawn.
- **The concurrency test is its own class, `DependencyGraphLockTests`, and it has two
  cases.** The second is the one worth having: the loop closes only through an arrow that was
  already there, so the losing caller has to have *read* the winner's write rather than
  merely been serialised against it.
- **The frontend asks from one end only.** "Must finish before…" is opened on the task that
  finishes first, so there is no box for which way round the arrow points and no way to draw
  one backwards by misreading a label. The list offers neither the task itself nor anything it
  already comes before — both are refusals the server would give — but it does not try to
  hide a cycle, because that is a property of the whole plan decided under a lock, and
  guessing at it here would hide options that are legal by the time the request lands.
- **The panel stays open once an arrow lands**, alone among the forms in this component,
  which all close on a successful write. Ordering is plural where rewording and estimating
  are not — a task that must finish before one thing usually must finish before two — and
  the list it just joined is in the same panel. It shipped closing on add while its button
  said *Done*, which was a design half-made: a label describing a place you leave, on a
  panel that left by itself. Rubbing an arrow out already kept it open, so this is also what
  makes the two halves of the panel behave alike. Remounted on `reloads` to empty the boxes,
  the way the add form does — and for the same reason it works there, since a refusal does
  not reload and so leaves what was typed alone.
- **Each row says both directions**: what it must finish before, and what it is waiting on.
  Either alone answers half the question somebody opened the plan with, and the other half
  would only be visible from a different row. An arrow pointing at work archived since is
  named as "a task that has been put away" rather than shown as a blank — the archived
  listing is a different screen, so its titles are not there to look up.

---

## Step 6 — Close out ✅ *done*

- `../roadmap.md`: mark the plan schema done, answer the two rows its decision table calls *Unresolved*, and
  correct the claim that the plan schema puts a handle in a URL — it does not, and the deferrals that
  claim carried stay where they are.
- `../roadmap.md`: the simulation engine inherits an obligation from decision 8 — persist every forecast run from the
  first commit. Record it under the simulation engine, not here.
- `CLAUDE.md`: the domain packages and what each owns; immutability and why; the estimator
  outliving the membership; archive-not-delete; the dependency lock and why it is a lock.
- `../product-concept.md`: its status banner still says nothing is implemented, which stopped
  being true at the original tenancy design and becomes actively misleading once the domain it describes exists.

### As built — where it differs from the above

- **Two of the four bullets were already spent.** The decision-table rows and the
  forecast-run obligation were written into `../roadmap.md` when *this* plan was written, not
  when the code landed, because both are things a plan has to settle before step 1 rather
  than after step 5. Finding a close-out item already done is what a close-out step is for;
  the note is here so the next reader does not go looking for the change in this commit's
  diff.
- **The handle claim was corrected twice, and only the second time was honest.** Writing this
  plan turned it from an assumption into a decision (7); this step turned it from a decision
  into a fact, because the work is now built and there is no longer an assumption to
  argue with. The deferrals it carried are still deferred, and are now recorded in
  `CLAUDE.md`'s routing note as well — the roadmap is where they are scheduled, and the
  routing note is where somebody about to add a handle to a URL would actually be looking.
- **`../roadmap.md` got an the plan schema *As built* section it was not asked for.** the team model and chosen handles both have
  one, and the plan schema being the work that went to plan is exactly the thing worth writing down:
  without it, a reader comparing the three would find the two that departed documented and
  the one that did not, silent — which reads as an omission rather than as a result.
- **Three stale claims elsewhere in `../roadmap.md` were the actual work of this step**, and
  none of them is in the bullets: the forecast-runs line still listed a table decision 8 had
  moved, "the plan-entry UI is barely scoped" was a thin spot that building it resolved, and
  the security note about finding 3 was written in the future tense for a moment that has
  now passed — its blast radius grew by seventeen endpoints and nobody took it first, which
  is a cost worth recording rather than quietly absorbing.
- **`CLAUDE.md` gained four sections rather than the one the bullet implies**, split the way
  the packages are: plans and their work, progress as a thing somebody *reports*, estimates
  and immutability, the graph and its lock — plus the plan screens, since a rule like "the
  form only offers the boxes a status has room for" is one half of a refusal whose other
  half is on the server, and the two are only comprehensible together.
- **`../product-concept.md` needed more than its banner.** Two of its *Open questions* — the
  unit of estimation and multi-estimator support — are answered by this work's decisions
  1 and 2, and a document whose banner said "partly built" while still asking them would have
  moved the misleading part rather than fixed it. The banner now also says what is *not*
  built, which is most of that document: there is no fitting, no sampling and no forecast, and
  the product's own argument about a table of numbers currently applies to itself.

---

## Migrations

One per schema step, continuing from `V6__invitations.sql` — five rather than the four
planned, because progress and dependencies were to share one and each step is a commit:

| | |
|---|---|
| `V7__projects.sql` | `projects`, with `tenant_id` and an index on `(tenant_id, archived_at)` |
| `V8__work_items.sql` | `work_items`, indexed on `(tenant_id, project_id, archived_at)` |
| `V9__estimates.sql` | `estimates`, indexed on `(work_item_id, estimator_user_id, created_at desc)` |
| `V10__work_item_progress.sql` | progress columns on `work_items` *(as built: dependencies moved to a migration of their own, so each step is a commit that stands up by itself)* |
| `V11__dependencies.sql` | `dependencies` with its unique edge index |

**No data migration**, because there is no domain data to move — this is the first of it.

**Every table carries `tenant_id`** including the ones reachable only through a parent. It is
denormalisation, and the reason is that the isolation rule is enforced in application code, so
it has to be *easy* to write a correctly-scoped query and awkward to write a wrong one. A join
away is far enough to forget.

---

## Sequencing and risk

**The riskiest thing here is not a bug, it is a shape.** Steps 1, 2 and 4 are ordinary CRUD and
will be fine. Step 3 decides what calibration can ever say, and step 5 decides what the simulation engine can ever model.
If review attention is rationed, spend it there.

**Immutability is the constraint most likely to be eroded by a well-meaning change.** It will
look, at some point, like an `UPDATE` would be simpler than a new row — when somebody fixes a
typo in their own estimate, most likely. The answer is that a corrected estimate is a second
estimate, and that calibration measures how often a person's ranges contained the truth, which is a
question about what they *said at the time*. Guard it with the test, not the intention.

**The dependency lock is the second.** It guards a property of a graph rather than of a row,
which is the case where "just use a constraint" does not apply — Postgres has no unique index
for "acyclic". Anyone who removes the lock because the code reads more simply without it will
be right about the code and wrong about the graph.

**What this work must not absorb.** Elicitation (elicitation) will look tempting the moment the
three-box form is on screen and obviously bad. The forecast (the simulation engine) will look tempting the moment
there is a plan with numbers in it and nothing that reads them. Both are the ordering
principle's warning arriving in person: this work's output is a schema and a way to fill
it, and a beautiful plan editor that forecasts nothing is precisely the tool this product
exists to replace.
