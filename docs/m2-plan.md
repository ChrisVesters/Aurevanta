# M2 — The estimation schema: implementation plan

> **Scope.** `roadmap.md` M2: the first migration that carries real domain data — projects,
> work items, three-point estimates, precedence dependencies, and progress — plus the plainest
> UI that can fill them. Explicitly excluded: the simulation engine (M3), elicitation design
> (M5), calendars and capacity (M11), and handle-based routing.
>
> **How to read this.** Decisions first, because two of them were open in `roadmap.md` and one
> reverses an assumption it made. Then six steps, each a reviewable commit that leaves the
> build green: `./mvnw test` (format gate, 100% branch coverage) and, where the frontend is
> touched, `npm run lint && npm run build && npm run test`.
>
> **This is the most expensive thing in the product to get wrong.** M3 through M9 all read
> from this schema, and two of them (M8 calibration, M10's sliding-date detector) read *history*
> that cannot be reconstructed if it was never written. The parts of this plan that look like
> over-provision — immutable estimates, an estimator that outlives a membership — are there for
> that reason and are the parts to leave alone if the milestone needs trimming.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | Projects | — |
| 2 | Work items | 1 |
| 3 | Estimates, and what coverage means | 2 |
| 4 | Progress, and actuals | 2 |
| 5 | Dependencies | 2 |
| 6 | Close out | 1–5 |

**There is no fat to cut here**, which is worth stating plainly rather than discovering
halfway. Drop estimates and there is no product. Drop dependencies and M3 forecasts a flat
list, which `product-concept.md` already recorded as producing 51 or 86 days for the same ten
items depending only on structure. Drop progress and M8, M9 and M10 all lose their input. The
only genuinely optional thing in this milestone is how good the UI looks, and the plan already
assumes it looks bad.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Unit of estimation | **Task**, per `roadmap.md`. Coarser units hide scope growth inside the estimate, which M3 would then double-count. |
| 2 | Multi-estimator | **Schema now, UI later.** Several estimates per item, one current per estimator. |
| 3 | Effort or duration | **Effort**, in hours. Duration is effort divided by what is assigned to it, and that division is M11's. |
| 4 | Dependency types | **Finish-to-start with lag**, only. |
| 5 | Items with no estimate | **Forecast what is estimated and report coverage.** *(was open)* |
| 6 | Who may write plan data | **Any member may do everything.** Roles govern administration only. *(was open)* |
| 7 | Handle in the URL | **No.** M2 does not route by handle, so M1a's two deferrals stay deferred. *(reverses a roadmap assumption)* |
| 8 | Forecast runs | **Deferred to M3**, which is what writes one. *(departs from the roadmap's bullet list)* |
| 9 | Destructive operations | **Archive, never delete.** Follows from decision 6, and M8 needs it independently. |
| 10 | Scale target | **500 items per project**, stated so that "does this need pagination" has an answer. |

### Decision 5 — Forecast what is estimated, and say what was left out

An estimate is optional. A plan with thirty items and twelve estimates is not a broken plan;
it is every real plan on the day somebody first opens the tool. So a forecast covers the items
that carry an estimate and **reports its own coverage prominently** — "covers 12 of 30 items"
— rather than refusing, or quietly forecasting a subset as though it were the whole.

The alternative worth naming is imputation: give unestimated items a distribution derived from
the estimated ones and mark them as invented. It is defensible and it is what a mature version
of this product probably does. It is not what M2 does, for two reasons. It invents data, and a
number that came from nowhere is exactly what this product exists to stop people producing.
And it needs a per-forecast record of *what was imputed*, which is a forecast-run concern, and
forecast runs are M3 (decision 8).

**M2's half of this decision is making coverage computable and visible.** The project and item
responses carry whether an estimate exists and how many items have one; the UI says so on the
project page. M3 inherits a number it does not have to invent.

### Decision 6 — Any member may do everything, and so nothing may be destroyed

Roles govern administration — invitations, members, organisation settings — and nothing else.
Every member of an organisation may create a project, add items, estimate them, record
progress and draw dependencies. **This was chosen over the narrower alternative** (owners own
projects, members own the work inside them), and the reasoning to record is that estimation is
a team activity: multi-estimator support, which decision 2 shapes the schema for, is
meaningless if only owners can estimate, and a per-project permission model is a lot of
machinery to build before anyone has asked for it.

**What it costs, and what M2 does about it.** Widening write access makes destructive acts
everyone's to perform. A member who deletes a project deletes a colleague's work, and unlike
removing a member — which an owner can undo by inviting them back — there is nothing to put
back. So M2 has **no hard delete at all**: projects and items carry `archived_at`, archiving
hides them, and unarchiving is a member's to perform too.

That is not only a response to decision 6. It is independently required: an estimate is
evidence for M8 calibration, and deleting the item it hangs on would destroy that evidence
years before the feature that reads it exists. Two independent reasons for the same column is
usually a sign the column is right.

**Immutability is doing authorization work here as well**, which is worth noticing. Because an
estimate is never updated in place (decision 2), no member can rewrite a colleague's estimate
even though every member can write estimates. The strictest rule in this schema is the one
nobody had to enforce.

### Decision 7 — M2 does not put a handle in a URL

`roadmap.md` says M2 "is the step that first puts an organisation handle in a URL and so ends
the grace period" for reserved handles and retired-handle redirects. **That was an assumption,
and it is not true of this plan.** Plan URLs are `/app/projects/{id}`, as every route in the
application already is. Nothing about entering a plan requires an organisation handle in the
path; the organisation comes from the access token, as it does everywhere else.

So M1a's two deferrals stay deferred, and security findings 3 and 4 are not accelerated by
this milestone. They remain worth doing — 4 especially, while the API is unreleased — but on
their own schedule under *Security debt*, not this one's.

This is a scope reduction taken deliberately. M2 is already the largest schema risk in the
product, and adding a routing change and a reserved-word policy to it would be spending the
riskiest milestone's attention on something with no domain content.

### Decision 8 — Forecast runs are M3's table, not M2's

The roadmap lists forecast runs among M2's schema, on the grounds that the history "cannot be
reconstructed later". The premise is right and the conclusion does not follow: **nothing
produces a forecast until M3**, so there is no history accumulating in the meantime to lose.
What designing the table now would cost is real — its columns are the engine's inputs,
assumptions and outputs, none of which exist yet, so every one of them would be a guess made
without the thing that reads it.

The obligation this transfers to M3 is stated here so it cannot be forgotten: **M3 must persist
every run from its first commit**, not add persistence once the engine works. That is the
version of the roadmap's warning that survives.

### Decision 3 — Effort, in hours

Effort rather than duration, per the roadmap: duration bakes in an allocation assumption, and
M11 exists to make allocation explicit.

**The unit is hours**, stored as `numeric`. A "day" is a calendar word — it presumes a working
day of some length, which is precisely the assumption M11 has to surface rather than inherit.
A team that thinks in days multiplies by their own day length, and when M11 arrives that
multiplication becomes a setting instead of a convention buried in old rows. The UI may well
show days; the column stores hours.

### Decision 10 — 500 items per project

`roadmap.md` lists "no scale target has been set" as a known thin spot, and the reason it
matters is that it decides whether M3 forecasts synchronously and whether M2 needs pagination.
**Fixed here at 500 items per project**, which is a large plan by the standards of the teams
this is for, and which comfortably fits one response and one simulation in a request.

So: no pagination in M2, and no cursor API to design around. If a project ever needs more than
this, that is a product signal — the unit of estimation is a task (decision 1), and 500 tasks
in one project is usually a portfolio wearing a project's name.

---

## Step 1 — Projects

**Goal.** An organisation can hold more than one plan, and they cannot see each other's.

- `projects` — `id`, `tenant_id`, `name`, `description`, `created_at`, `archived_at`.
- **The name is not unique**, and this time that is a decision rather than an oversight. M1a
  spent a milestone removing an accidental uniqueness constraint on an organisation's name;
  two projects called "Q3 platform work" in one organisation is ordinary, and the id is what
  addresses them.
- `POST /api/projects` `{name, description?}`, `GET /api/projects`, `GET /api/projects/{id}`,
  `PATCH /api/projects/{id}` `{name, description}`, `POST /api/projects/{id}/archive` and
  `/unarchive`.
- **Tenant from the token, never the request**, and every id looked up *together with* the
  tenant — the rule the security review checked the whole of M1 against.
- `GET /api/projects` lists unarchived by default, `?archived=true` for the rest.
- Frontend: `/app/projects` lists them, `/app/projects/{id}` is the project page, and a nav
  entry beside Members. Visible to every member (decision 6).

**Tests.** A project belongs to the organisation that made it and is invisible to another, with
a two-organisation fixture so a leak fails. A member creates one. Archiving hides it from the
default listing and unarchiving brings it back. Two projects may share a name.

**Done when** an organisation has somewhere to put a plan.

---

## Step 2 — Work items

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

---

## Step 3 — Estimates, and what coverage means

**Goal.** The product's actual content: a range, from a named person, that is never overwritten.

- `estimates` — `id`, `tenant_id`, `work_item_id`, `estimator_user_id`, `p10_hours`,
  `p50_hours`, `p90_hours`, `created_at`. **No `updated_at`, and no update endpoint.** A
  revision is a new row.
- **The estimator is a user, not a membership.** A person who leaves the organisation still
  made the estimate, and M8 calibrates *per estimator* across everything they ever estimated.
  A membership can be deleted — M1 made sure of that — so hanging this off one would delete
  history as a side effect of somebody leaving. This mirrors "removal deletes the membership,
  never the identity".
- **Current estimate = latest `created_at` per (item, estimator).** Several estimators may hold
  a current estimate on one item at the same time, which is decision 2 working: their
  disagreement is signal, and M3 decides what to do with it. M2 only has to store it and be
  able to find the current ones cheaply — an index on `(work_item_id, estimator_user_id,
  created_at desc)`.
- **Validation:** `0 < p10 <= p50 <= p90`. New constraint codes in `ApiExceptionHandler.CONSTRAINT_CODES`
  and `errors.validation` in `en.ts`, per the existing rule.
- **Coverage** (decision 5): the item response says whether it has a current estimate; the
  project response carries `itemCount` and `estimatedItemCount`. The project page states it in
  words — "12 of 30 items estimated" — because a number nobody reads is not a disclosure.
- Frontend: three fields on an item. **Deliberately plain, and deliberately not good.** M5 is
  elicitation, and `product-concept.md` is explicit that three boxes labelled P10/P50/P90
  produce 3/5/8 without thinking, which is worse than no tool because the garbage now carries
  a probability. M2 must not pretend this form is the answer, and the plan should not smuggle
  M5 in under "polish".

**Tests.** An estimate cannot be updated — there is no route that does it, and a second one
creates a second row with the first still readable. Two estimators hold current estimates on
one item simultaneously. `p50 < p10` is refused with a field code. An estimate survives its
estimator being removed from the organisation. Coverage counts only items with a current
estimate, and ignores archived items.

**Done when** the schema holds everything M8 will need in three years, and nothing can quietly
erase it.

---

## Step 4 — Progress, and actuals

**Goal.** A forecast can exclude what is already done instead of re-predicting the past.

- Columns on `work_items`: `status` (`NOT_STARTED` / `IN_PROGRESS` / `DONE`), `started_at`,
  `completed_at`, `actual_effort_hours`.
- `PATCH /api/items/{id}/progress` `{status, startedAt?, completedAt?, actualEffortHours?}`.
- **Transitions are checked**, because the timestamps are evidence: `DONE` requires a
  `completed_at`, `IN_PROGRESS` requires a `started_at`, and going back to `NOT_STARTED`
  clears both rather than leaving a start date on something that has not started.
- `actual_effort_hours` is optional even when done. Most teams do not track it, and refusing to
  let somebody mark an item finished because they cannot say how long it took would mean
  refusing the common case to serve M8.

**Tests.** Each transition and each refusal. Actuals are optional on a completed item. Clearing
back to not-started clears the timestamps. Progress on an item in another organisation is a 404.

**Done when** the plan knows what has already happened.

---

## Step 5 — Dependencies

**Goal.** The plan has a shape, so M3 can forecast something other than a queue of one.

- `dependencies` — `id`, `tenant_id`, `project_id`, `predecessor_item_id`, `successor_item_id`,
  `lag_hours`, `created_at`. Unique on `(predecessor_item_id, successor_item_id)`.
- **Finish-to-start only, with lag** (decision 4).
- **Refused on write:** a self-edge, an edge between two projects, a duplicate, and **a cycle**.
- **Cycle detection needs a lock, and this is the sharpest thing in the milestone.** Two edges
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

**Done when** the schema can express a plan whose parts overlap.

---

## Step 6 — Close out

- `roadmap.md`: mark M2 done, answer the two rows its decision table calls *Unresolved*, and
  correct the claim that M2 puts a handle in a URL — it does not, and the deferrals that
  claim carried stay where they are.
- `roadmap.md`: M3 inherits an obligation from decision 8 — persist every forecast run from the
  first commit. Record it under M3, not here.
- `CLAUDE.md`: the domain packages and what each owns; immutability and why; the estimator
  outliving the membership; archive-not-delete; the dependency lock and why it is a lock.
- `product-concept.md`: its status banner still says nothing is implemented, which stopped
  being true at M0 and becomes actively misleading once the domain it describes exists.

---

## Migrations

Four, one per schema step, continuing from `V6__invitations.sql`:

| | |
|---|---|
| `V7__projects.sql` | `projects`, with `tenant_id` and an index on `(tenant_id, archived_at)` |
| `V8__work_items.sql` | `work_items`, indexed on `(tenant_id, project_id, archived_at)` |
| `V9__estimates.sql` | `estimates`, indexed on `(work_item_id, estimator_user_id, created_at desc)` |
| `V10__progress_and_dependencies.sql` | progress columns on `work_items`; `dependencies` with its unique edge index |

**No data migration**, because there is no domain data to move — this is the first of it.

**Every table carries `tenant_id`** including the ones reachable only through a parent. It is
denormalisation, and the reason is that the isolation rule is enforced in application code, so
it has to be *easy* to write a correctly-scoped query and awkward to write a wrong one. A join
away is far enough to forget.

---

## Sequencing and risk

**The riskiest thing here is not a bug, it is a shape.** Steps 1, 2 and 4 are ordinary CRUD and
will be fine. Step 3 decides what M8 can ever say, and step 5 decides what M3 can ever model.
If review attention is rationed, spend it there.

**Immutability is the constraint most likely to be eroded by a well-meaning change.** It will
look, at some point, like an `UPDATE` would be simpler than a new row — when somebody fixes a
typo in their own estimate, most likely. The answer is that a corrected estimate is a second
estimate, and that M8 measures how often a person's ranges contained the truth, which is a
question about what they *said at the time*. Guard it with the test, not the intention.

**The dependency lock is the second.** It guards a property of a graph rather than of a row,
which is the case where "just use a constraint" does not apply — Postgres has no unique index
for "acyclic". Anyone who removes the lock because the code reads more simply without it will
be right about the code and wrong about the graph.

**What this milestone must not absorb.** Elicitation (M5) will look tempting the moment the
three-box form is on screen and obviously bad. The forecast (M3) will look tempting the moment
there is a plan with numbers in it and nothing that reads them. Both are the ordering
principle's warning arriving in person: this milestone's output is a schema and a way to fill
it, and a beautiful plan editor that forecasts nothing is precisely the tool this product
exists to replace.
