# Aurevanta — Feature Roadmap

> **Status: proposal, as of 2026-08-06; last revised 2026-08-17.** `product-concept.md`
> says *what* Aurevanta is and why; this document says *in what order we build it, and what
> has to be decided first*. M0, M1, M1a, M2, **all of M3**, **M4**, **M5**, **M6** and **M7**
> exist in code, so **Tier 1 and Tier 2 are both complete**: a plan with ranges in it produces a
> band that models common cause and unlisted work, states every assumption that produced it, and
> resolves to a date at a confidence somebody chooses. That was this document's own bar for
> beating a spreadsheet — a Monte Carlo rollup and a ship date at a confidence level — and both
> now exist. M5 then replaced the question those ranges are collected by, which is the one thing
> that decides whether any of it means anything; M6 made the band say what it is made of; and M7
> ran the question backwards, which is what turns a reporting surface into something opened
> *during* planning. Everything from here is a further lens on that number rather than the first
> one.
>
> **Where the two documents disagree, this one is newer.** `product-concept.md` defers
> dependencies and capacity modelling; measurement since (see M3) showed that summing
> durations silently assumes a single worker working end to end, so precedence moved into
> M2/M3 and resources became M11.

## The ordering principle

`product-concept.md` is blunt about this: without the simulation engine, the application
is "a table of numbers that users will paste into a spreadsheet and add up incorrectly."

That gives the running order. The engine (M3) is the product. Everything before it exists
to give the engine something to chew on; everything after it is a lens on its output. The
main risk in this plan is spending months on team management and CRUD screens and
arriving at the engine late — so M1 is deliberately trimmed to the minimum that lets more
than one person use the thing.

A second, less obvious risk: **M5 (elicitation) is not polish.** Three boxes labelled
P10/P50/P90 produce garbage that now carries a probability attached, which is worse than
no tool. M5 is scheduled early for that reason, not as a UX nicety.

A third: **structure changes the answer more than the estimates do.** The same ten items
forecast at 51 or 86 days depending only on how they depend on each other and who is
available (see M3). Dependencies therefore sit in M2/M3 rather than in "deferred" — the
aggregator is a scheduler from the start. Named resources are the refinement, and they wait
until M11 because that is where the complexity genuinely lives.

### What Aurevanta is not

Adding a dependency graph and resources moves this product within arm's reach of every
general-purpose planning tool. The differentiator is uncertainty handled honestly — not
scheduling features, which many tools already do adequately. These are non-goals unless
something changes:

- **Not a task tracker.** No workflows, boards, sprints or assignments-as-work-management.
  Items exist to be estimated and scheduled; day-to-day tracking belongs in the tool a team
  already uses, which is why import (icebox) matters more than replacing it.
- **Not time tracking.** M8 needs actual effort at completion, not timesheets.
- **Not cost or budget modelling.** A defensible future direction, but every feature in this
  plan is about *time*, and cost would double the modelling surface.
- **Not a document or requirements tool.** Items carry estimates, not specifications.

The test for any new feature: does it make the forecast more honest, more trusted, or more
actionable? If it only makes the tool more complete as a project manager, it is a non-goal.

---

## M0 — Tenancy and identity ✅ *done*

Multi-tenant foundation: `tenants` + `users`, registration creating an organisation with
its owner, JWT auth, tenant isolation enforced in the service layer.

**Carried debt, all cheap now and expensive later:**

| Gap | Why it matters |
|---|---|
| ~~A tenant can only ever have one user~~ | *Retired in M1 step 1:* identity is now separate from membership. `users` holds the person — one address, one password, one confirmed inbox — and a `memberships` row holds their standing in one organisation, so somebody consulting for two clients belongs to both with a different role in each. |
| ~~An organisation name is unique across the whole installation~~ | *Retired in M1a:* the name is a plain column that derives nothing, and `uq_tenants_slug` now guards a handle its owner chose. `existsBySlug` and that index still make the *handle* unique — deliberately, because it is an address — but the second "Acme Consulting" to arrive registers, and the only thing anyone is ever refused is something they typed. |
| ~~Per-field validation errors carry English prose, not codes~~ | *Retired in M1 step 4:* each field error now carries the constraint that failed and its bounds, and the frontend translates that.  |
| ~~No password reset, no email verification~~ | *Retired in M1 steps 5 and 6:* an unconfirmed address cannot obtain a token by any route, and reset is the way back for an account whose confirmation mail never arrived — which under a hard gate is the only way back. |
| Landing page advertises unbuilt features | Fine while private; must not go public as-is. |

---

## M1 — Make it a team product ✅ *done*

The smallest set that lets a second person join. Resist adding more here.

- **Invitations** — owner invites by email, invitee registers against an existing tenant
  via a signed, expiring token. Needs outbound email, which is new infrastructure.
- **Member management** — list, change role, remove. `UserRole` already has `OWNER`/`MEMBER`.
- **Password reset and email verification** — same email infrastructure; do them together.
- **Per-field error codes** — retire the field-name guessing from M0.

**Deliberately excluded:** SSO, granular permissions, org settings beyond a name. None of
them are needed to prove the core idea.

**As built.** `m1-plan.md` carries the thirteen steps, what each decided, and where each
departed from its own brief. Four things changed shape against the bullets above:

- **Identity split from membership**, which those bullets did not ask for and the first step
  did anyway. "Invitee registers against an existing tenant" quietly assumes an account
  belongs to one organisation, and that is a wall the first consultant with two clients
  walks into. Doing it before invitations existed is what made accepting one *add a
  membership* rather than apologise.
- **Email verification became a hard gate**, not a flag on a row. An unconfirmed address is
  refused a token by every route, which turns password reset from a convenience into the
  recovery path and makes mail delivery load-bearing — the cost that decision was worth
  paying and is worth remembering.
- **Rate limiting came with it.** Three endpoints send mail to an address the caller chose
  and need no credentials to do it, which is the definition of an email-bombing vector.
  Sign-in came under the same roof afterwards, on failures only.
- **Starting an organisation became an endpoint of its own.** Member management can leave
  somebody belonging to nothing, and "wait to be invited" is not a way out a person can take
  by themselves.

---

## M1a — Organisation names are not unique ✅ *done*

*A correction slotted after M1, not a split of it. Lettered to avoid renumbering M2–M11.*
`m1a-plan.md` breaks it into five steps, answers the decisions below, and records where each
step departed from its own brief.

M0 gave `tenants.slug` a unique index and derived it from the organisation's name, which
quietly made **the name itself unique across the entire installation**. Register "Acme
Consulting" and every other Acme Consulting on earth is told the name is *unavailable* — as
though the first to arrive owns it. There are thousands of them, and nothing about this
product changes that. It is the one refusal in the system a user cannot act on: they cannot
choose a different name, because it is their name.

**The slug stops being an identity and becomes a convenience.** Two ways to go — *neither was
taken; the handle became a field instead. Kept because the reasoning still explains what the
alternatives cost, and* **As built** *below says what happened.*

| | |
|---|---|
| **Recommended: keep the slug unique, disambiguate on collision** | `acme-consulting`, then `acme-consulting-2`. Readable URLs survive, and the name the user typed is never altered — only the handle derived from it. |
| Alternative: drop slugs, address organisations by id | Simpler and honest, but gives up readable URLs, which M10's shareable read-only link would want back. |

**What it touches** *(as anticipated; all three read differently once the handle is a field)*

- Drop the name pre-check in `OrganisationService` — which registration and "start another
  organisation" now share, so there is one place to drop it — and retire
  `organisation_name_unavailable` from the problem codes and the frontend catalogue.
- `Slug.of` gains a uniqueness pass. A name of pure punctuation currently yields an empty
  slug and its own refusal (`organisation_name_unusable`); with a suffixing scheme that can
  fall back to a generated handle instead, retiring a second refusal a user cannot act on.
- `registration_conflict` narrows to email only, since the slug can no longer collide.

**The consequence worth deciding before building it**

Once two organisations can share a name, **a person who belongs to both sees the same word
twice** — in M1's organisation switcher and in an invitation preview, both of which are now
built and neither of which has anything else to show. The switcher lists names; the preview
carries a name, an inviter and a role by design, because it is served to anybody holding the
link. Allowing duplicates is not finished until something distinguishes them: the slug, the
inviter's name, or a discriminator on the row. That is a design question, not a migration,
and it is now a question about screens that exist.

**Why here rather than later.** Nothing routes by slug *yet*. The moment M2's plan-entry UI
puts one in a URL, changing the scheme starts breaking links people have bookmarked and
pasted to colleagues. The change is small today and gets steadily less so.

### Decisions taken

Settled before building; `m1a-plan.md` carries the reasoning. **The first one changes what
this milestone is**, and the two questions below it dissolve into it rather than being
answered on their own terms.

| Question | Decision |
|---|---|
| Suffix collisions, or abandon slugs? | **Neither, quite: the handle becomes a required field the organisation owns**, proposed from its name. Suffixing survives only as the *suggestion* offered when a chosen handle is taken. |
| Can an organisation be renamed at all? | **Yes, name and handle both**, on an owner-only settings screen. "Does the slug follow the name?" stops being a question once they are separate fields: neither follows the other. |
| What distinguishes two identically named organisations on screen? | **The handle, shown only where the caller's own list repeats a name.** A display rule over data already on the wire, so no query and no endpoint. |

**Why a field rather than a silent suffix.** M0's refusal was unactionable because it was
aimed at the wrong thing: *"that organisation name is taken"* leaves a person nothing to do,
because it is their name. *"That handle is taken"* leaves them everything to do — a handle is
an address, not a name, and picking another costs nothing. So the refusal survives and stops
being a wall.

**The handle is required**, so there is no path where the server picks one and the caller is
left holding the consequence: every refusal is against something somebody typed. The form
proposes a handle as the name is typed and accepting it costs a keystroke fewer than changing
it, so the common case is free and the person has still chosen.

**Deferred to M2, deliberately, and recorded there:** reserved handles, and whether a changed
handle leaves a redirect behind. Both are free to defer only while nothing routes by handle,
and M2 is the step that ends that.

### As built

**This milestone did not do what it was written to do**, and that is the first thing to know
about it. It was written to make the derived slug survive a collision quietly; what shipped
makes the handle a field somebody fills in, which is a different answer to the same problem
and a smaller one. `m1a-plan.md` carries the five steps and where each departed from its brief.
Against the bullets above, four things read differently:

- **Nothing derives a handle any more, so nothing needed a uniqueness pass.** `Slug.of` did not
  gain one — it left the backend altogether. Proposing a handle from a name is what a form does
  while somebody types, so it is `proposeSlug` in the frontend now, and what stays on the server
  is the part the server is answerable for: the shape (`Slug.PATTERN`, which is the contract
  between the two) and the alternative offered when a chosen handle is taken.
- **Two refusals went, not one.** `organisation_name_unavailable` and
  `organisation_name_unusable` are both deleted. The second had nothing left to describe once a
  name derives nothing: a name of pure punctuation is now just a name. What replaces the first
  is `slug_taken`, which carries the next free handle so the refusal arrives with its own
  remedy — decided in preference to an availability endpoint, which would have been a surface
  anybody could walk to enumerate which organisations exist.
- **`registration_conflict` was retired rather than narrowed.** The bullet says it narrows to
  email; in fact `uq_users_email` now answers with `email_already_registered`, the code its own
  pre-check produces, so a race and the ordinary case became indistinguishable to the client —
  which is what they should always have been. The neutral leftover is `conflict`, naming no
  field, because a refusal that guesses at what the caller was doing is worse than one that
  admits it does not know. `ApiExceptionHandler` maps three unique indexes that way, and
  `ConstraintNamesTests` fails if a migration renames one out from under it.
- **There is deliberately no lock**, and it is worth saying so where the absence would
  otherwise look like an oversight. Once the handle is carried in the request there is no
  check-then-assign sequence to serialise: the pre-check refuses a taken handle, and the pair
  who get past it in the same instant meet `uq_tenants_slug`. Compare
  `MembershipService.lockOwners`, which guards an invariant nothing can repair once broken;
  this would guard a *message*. A rare, self-correcting, already-actionable refusal does not
  earn one.

**It also added a settings screen**, which M1 excluded in as many words ("org settings beyond a
name"). Once the handle is a field, an organisation that could never change its address would
be stuck with a decision made in its first thirty seconds — so `PATCH /api/organisations` and
`/app/settings` came with it, owner-only, name and handle together. It is the one part of this
milestone that adds rather than corrects, and the seam to cut along if that was the wrong call.

**No migration.** Every slug already in the database is unique and stays valid; the column and
its index are untouched. What changed is only where the *next* handle comes from.

---

## M2 — The estimation schema ✅ *done*

The first migration that carries real domain data. Getting this wrong is the most
expensive mistake available, because M3–M9 all read from it.

`m2-plan.md` breaks it into six steps, answers the two decisions below that this section
called unresolved, and records where each step departed from its own brief.

**Two things M1a deferred were expected to come due here** — on the assumption that this is the
step that first puts an organisation handle in a URL. **It was not, and now that it is built
there is no assumption left to argue with**: plan URLs are `/app/projects/{id}`, every one of
them inside `/app`, and the organisation comes from the access token as it does everywhere
else.
Nothing about entering a plan needed a handle in the path, and adding a routing change to the
milestone whose risk was already its schema would have bought nothing.

So both stay deferred, and they stay written here, because the reasoning still holds for
whichever milestone *does* put a handle in a URL:

- **Reserved handles.** Nothing stops an organisation calling itself `api` or `admin` today,
  because a handle addresses nothing. Decide before routing exists whether to reserve a list
  or to namespace handles under a prefix (`/o/<handle>/…`) so they can never collide with an
  application route. Reserving one back afterwards means migrating an organisation somebody
  has already linked to.
- **Redirects for retired handles.** M1a lets an owner change a handle and lets every link to
  the old one die, on the grounds that there are no links yet. From here there are. Adding a
  retired-handle table later is a table and a lookup; not adding it is silent breakage that
  nobody reports, because the person who follows a dead link is not the person who changed
  the handle.

**One of the open security findings got more expensive here, and the moment has passed.**
Finding 3 under *Security debt* — an invitation token interpolated into an API path unencoded
— has a blast radius bounded by *which endpoints exist*, and this milestone added seventeen of
them. It was not taken first and it is now larger than it was; that is the cost of leaving it,
recorded rather than quietly absorbed. Finding 4, which would remove it, was unaffected by M2
and stays on its own schedule.

**The schema itself:**

- **Projects** (or plans) — a named container per tenant.
- **Work items** — the unit that carries an estimate.
- **Dependencies** — a precedence edge between two items, finish-to-start, with optional
  lag. Cycles rejected on write. See below for why this is here and not deferred.
- **Estimates** — P10/P50/P90 against an item, **stored immutably** with a timestamp and
  an estimator. Never updated in place; a revision is a new row.
- **Item progress** — not started / in progress / done, with a start date and actual effort
  once finished. Without it there is no mid-project re-forecast, no burn-up (M10) and no
  calibration (M8); with it, a forecast can exclude completed work instead of re-predicting
  the past.
- ~~**Forecast runs**~~ — *moved to M3 by decision 8, and the warning moved with it.* The
  premise was right and the conclusion did not follow: nothing produces a forecast until
  M3, so no history was accumulating in the meantime to lose, while every column of the
  table is an input, assumption or output of an engine that did not exist yet. See the
  obligation recorded at the top of M3.
- **The minimum UI to enter a plan** — projects, items, estimates and dependency edges.
  Deliberately plain. The ordering principle warns against sinking months into CRUD screens,
  but the schema is useless without *some* way to fill it, and pretending otherwise hides
  real work from the plan. A polished plan editor is a later concern.

That immutability is non-negotiable even though the feature that needs it (M8 calibration)
is far off. Retrofitting history onto overwritten rows is impossible — the data is simply
gone.

### Decisions required before writing this migration

| Question | Recommendation |
|---|---|
| Unit of estimation — task, story, or epic? | **Task.** Coarser units hide scope growth inside the estimate, which M3's scope-uncertainty model then double-counts. |
| Multi-estimator (wideband Delphi / planning poker)? | **Design the schema for it now, build the UI later.** An estimate already has an estimator; allowing several per item is a uniqueness constraint, not a rewrite. Disagreement between estimators is itself signal. |
| Are estimates in effort or duration? | **Effort.** With resources (M11) this becomes load-bearing: duration is effort divided by what is assigned to it. Storing duration would bake an allocation assumption into the estimate and make M11 impossible without re-estimating everything. |
| Dependency types — just finish-to-start, or SS/FF/SF too? | **Finish-to-start only, with lag.** It covers the overwhelming majority of real plans; the other three multiply scheduling complexity for cases most teams never model. |
| What happens to items with no estimate? | **Settled: forecast what is estimated, and report coverage.** An estimate is optional and a forecast states how many items it left out. Imputing from a reference class is defensible, invents data, and needs a per-forecast record of what was invented — which is a forecast-run concern, and those are M3. |
| Can a MEMBER edit estimates, or only an OWNER? | **Settled: any member may do everything.** Roles govern administration only. Estimation is a team activity and multi-estimator support is meaningless without it. The cost is that destructive acts become everyone's, so M2 has no hard delete — projects and items archive. |
| How large is a plan? | **500 items per project**, fixed in `m2-plan.md` because it decides whether M2 needs pagination and whether M3 forecasts synchronously. Both answers are no and yes. |

### As built

**This milestone did what it was written to do**, which is worth saying plainly after M1a,
which did not. Five migrations (`V7`–`V11`), four backend packages — `project`, `item`,
`estimate`, `dependency` — seventeen endpoints, and two screens. The decisions above all
survived contact with the code. Against the bullets, five things read differently:

- **Forecast runs are M3's, per decision 8**, and are struck out above rather than deleted
  so the reasoning stays visible.
- **The lag is required, not optional.** The bullet above calls it optional, and the field is
  not: zero is the ordinary answer and it is a *claim* — there is no wait — so the server
  will not fill it in on somebody's behalf. What is optional is the box, which sends zero
  when it is left empty, because its own hint says that is what an empty box means.
- **Progress dates are dates, not instants.** `started_on` and `completed_on` are the only
  columns in this schema that are not timestamps, because they are the only ones recording a
  day a *person* reports rather than a moment the *server* observed. There is no time of day
  in "we finished it on the twelfth", and inventing one reads back as the eleventh for every
  reader west of the meridian.
- **An edge is deleted, everything else archives.** The no-hard-delete rule that decision 6
  bought holds for projects, items and estimates — and deliberately not for a dependency,
  which carries no history anything downstream reads and which, left dormant, would be a
  plan forecasting around a constraint nobody could see.
- **The dependency lock is the sharpest thing here and it is real.** Acyclicity is a property
  of a whole graph, so the plan's own row is locked for update while the graph is walked and
  the edge written. `DependencyGraphLockTests` releases two callers together, and its second
  case is the one worth having: the loop closes only through an arrow the loser had to have
  *read* the winner write.

**What did not get built, and should not have been.** The three-box P10/P50/P90 form is on
screen and is as bad as `product-concept.md` warned it would be. That is M5's problem and
fixing it here would have been the ordering principle's warning arriving in person. Same for
the forecast: there is now a plan with numbers in it and nothing that reads them, which is
exactly the state M3 exists to end.

---

## M3 — The simulation engine ⭐ *the product* ✅ *done*

Fit → sample → aggregate. Everything else is a view over this.

**Both halves are built, so the thing this whole document is ordered around exists.** A plan
with ranges in it produces a band; that band models the common cause that makes good and bad
luck stop cancelling, and the work nobody has written down yet; and the five assumptions
behind it are printed beside it rather than behind a disclosure. The two limitation codes M3a
shipped in order to be honest about what it was missing are emitted by nothing — which was
always the definition of this milestone being finished.

`m3a-plan.md` breaks **M3a** into seven steps and answers twelve decisions, four of which this
section did not know it was carrying: what to do when several people have estimated one item,
what an *unestimated* item does to a graph it sits in the middle of, what "remaining work" means
for a task already under way, and where the capacity number comes from.

`m3b-plan.md` breaks **M3b** into six and answers ten, including the open question this document
records below — **where new work attaches in a graph**. The answer is that it attaches as a
successor to a randomly chosen existing item, and the argument that settles it is that a
multiplier and extra items are the same thing until there is a capacity constraint and different
things afterwards. Modelling scope as a multiplier would have been the team factor counted twice
under a second name.

**M3 inherits one obligation from M2**, and it is easy to postpone into never: **persist every
forecast run from the first commit**, with its inputs, its assumptions and its results. M2
deliberately did *not* build the table — its columns are this engine's, and designing them
before the engine existed would have been guessing. The roadmap's original warning stands
unchanged and now applies here: this history cannot be reconstructed later, and M10's
sliding-date detector and the movement decomposition in the icebox both need it.

> **Discharged by M3a, in the same step that first produced a forecast.** `V12__forecast_runs.sql`
> landed with the endpoint rather than after it, so no forecast has ever been made that was not
> kept, and the history M10 reads started accumulating on its first day. A run is written once
> and never updated, like an estimate.

> **Split, as this note asked, and both halves are now built.** `m3a-plan.md` is **M3a** —
> fitting, sampling, the graph scheduler with one global capacity, forecast-run persistence and
> the plainest way to ask for a forecast. `m3b-plan.md` is **M3b** — the shared team factor and
> scope uncertainty. Two builds, two plans, written together: the worry this note records is
> that correlation modelling gets *rushed to finish the milestone*, and a plan written in
> advance was the defence against that rather than a cause of it. **It held**: M3b took six
> steps of its own and one of its decisions was overturned by measurement along the way, which
> is not a thing that happens to work being rushed to a finish.
>
> **The line is not where it first looks**, which is the one thing worth carrying up from those
> plans. The tempting cut is "the engine, then the refinements"; the defensible one is *what can
> be checked against a whole-plan closed form*. M3a has one — a schedule at capacity 1 is a sum
> of independent log-normals, with an exact mean and variance. The moment a shared factor is
> sampled across items that closed form is wrong by construction (214.0 against a true 222.2,
> measured below). **M3b is not left unverifiable**, it is verified in pieces instead: exactness
> for a single item under a factor, the 209.4 → 222.2 figure measured below, and byte-identical
> equivalence with M3a once its two parameters are zero. So M3a is proved whole, M3b is proved
> in parts, and M3a goes first because it is what the parts are measured against.

**M3a — built:**

- **Distribution fitting** — log-normal from P10 and P90, with P50 as a consistency check.
  Surface the discrepancy when the three points are mutually inconsistent rather than
  silently picking one.
- **Monte Carlo rollup** — sample per item, then **schedule the graph** to get a project
  completion per run. Not a sum: a sum is the special case where every item is one long
  chain. See "the aggregator is a scheduler" below.

**M3b — built:**

- **Shared team factor** — one factor sampled per run and applied across all items.
  Without it, good and bad luck cancel out and the band comes out implausibly tight.
- **Scope uncertainty** — a distribution over *how much unknown work appears*, sampled and
  multiplied through. `product-concept.md` argues this is usually the larger of the two
  uncertainty sources; omitting it is why other tools look precise and land wrong.
  **The open question the graph created — now answered, in `m3b-plan.md` decision 3:** when
  the model was a sum, unknown work was simply a multiplier. In a schedule it needs a
  *position*, and it gets one — each generated item attaches as a successor to a uniformly
  chosen existing item, so it inherits a place in the plan and competes for capacity. The
  multiplier is not merely a shortcut that stopped working; it is *the team factor wearing a
  second name*, since two multipliers compose into one. What separates the two effects is
  capacity: one makes items longer, the other makes more of them.

**Engineering notes.** Pure functions over primitives, no persistence — the most testable
code in the system, so property-based tests belong here. Decide early whether runs are
synchronous (simple, fine for hundreds of items) or queued (needed at thousands). Seed the
RNG so a forecast is reproducible and testable. *All three are settled by M3a: `forecast.model`
is pure and holds no Spring, runs are synchronous by decision 8 against a measured budget, and
the seed is stored on the row so a run can be replayed.*

### As built — M3a

Two packages — `forecast.model`, which is pure, and `forecast`, which is a feature like every
other — one migration, three endpoints and a panel below the work on the plan screen. The twelve
decisions in `m3a-plan.md` all survived the build; four steps of arithmetic were checked against
sums that exist outside this codebase before anything was persisted, which is the whole defence
against the failure mode this milestone actually has.

- **The oracle agreed to five thousandths of a percent.** Forty tight tasks come out at
  **811.08** sampled, **811.12** from the closed form computed inside the test, and **811.1**
  from the measurement taken in the table below before any of this code existed. Three routes to
  one number, sharing no line of code. That is the answer to "how do we know the simulator is
  right", and it is worth more than every other test in the milestone put together.
- **The synchronous answer has a lot of room.** Five hundred items at ten thousand runs takes
  about **300ms** against the two seconds decision 8 allowed, so queueing stays an unused lever
  and parallelising runs is not needed. The assertion is left at two seconds: what it guards is
  the order of magnitude, and a tight wall-clock assertion only fails on a busy machine.
- **`java.util.Random`, deliberately.** It is the only generator in the JDK whose algorithms are
  in its *contract* rather than only its implementation. A stored seed that replays differently
  after a JDK upgrade would be worse than a version bump, because nothing would have changed
  version.
- **A fifth limitation code appeared during the build** — `dependencies_on_archived_work`. M2
  lets an arrow point at work since archived, a forecast loads only what is live, and such an
  edge cannot be honoured. Dropping it silently was the one option not on the table.
- **A latent bug in the whole backend suite surfaced here**: every test class wiped `users`
  before `tenants`, which was only safe while nothing tenant-owned pointed at a person. That
  stopped being true in M2 and it kept working by luck of test ordering. Fixed in all eighteen
  classes.

**What M3b inherited, and what became of it.**

- **Two limitation codes it existed to delete.** Every forecast M3a produced reported
  `no_team_factor` and `no_scope_uncertainty` beside the band. Nothing emits either now.
  *They were retired rather than deleted*: the constants stay because every run made before
  M3b still carries them in its stored `outputs`, and an enum missing a value that exists in
  stored JSON is a forecast that cannot be read at all.
- **The equivalence test.** Written in M3b step 1 rather than step 3, as its plan asked. Since
  M3a's engine no longer exists to call, it is the seven numbers M3a produced — captured from
  that build before a line of M3b was written — asserted for exact equality. It pins everything
  M3a did differently from its own bullets, and it held at every step.
- **`Engine.VERSION` and the snapshot format.** Version 2, and version 1 is version 2 with both
  parameters at zero — draw for draw, because a parameter that changes no number also takes no
  draw. `V13` backfills the three new columns with zeros, which is a true record of what those
  runs assumed rather than a placeholder, so nothing became read-only history.

### As built — M3b

Two pure classes beside the four M3a left, no new package, one migration, three new request
fields and two more questions on the panel. The ten decisions in `m3b-plan.md` survived, one of
them by being **measured and found to point the other way**.

- **Decision 6 was right that the two effects separate and wrong about which is heavier.** It
  predicted scope growth would outweigh a duration multiplier once capacity binds. Measured on
  twenty tasks: at capacity 1 the two agree to a tenth of a percent, exactly as predicted; where
  capacity binds the *multiplier* is heavier by 10% (174.5 against 158.7), because more smaller
  pieces pack into fixed slots better than fewer larger ones; where capacity is plentiful scope
  is heavier, because new work adds a step to a path and a multiplier only stretches the steps
  already there. **They load different bottlenecks**, which is a stronger reason to keep them
  apart than "one is bigger" ever was.
- **The 209.4 → 222.2 figure in the table below reproduces**, and recovering it needed a fact
  this document did not record: it never said which factor produced 222.2. Solving for it gives
  a P90 of 1.30 — a 30% stretch, which is the worked example `m3b-plan.md` decision 2 already
  used. Both halves of that row are now a single test from one plan and one seed.
- **Scope growth fits the *multiplier*, not the percentage.** A log-normal cannot reach zero, so
  fitting the percentage directly would refuse "usually it does not grow, but sometimes by 40%"
  — one of the more honest answers anybody gives. Fitting `1 + p/100` accepts it and makes a
  range of 0–0 the degenerate case with no special code path. A stated low end of 0% therefore
  means exactly one run in ten discovers nothing, by construction.
- **Stochastic rounding, because rounding to nearest has a bias with a direction.** Ten items
  growing 4% is 0.4 new items, which rounds to none in every run for ever. Taking the whole part
  and adding one more with probability equal to the fraction makes the mean count the number
  that was sampled — and makes a small plan sometimes grow and sometimes not, which is the
  design rather than a fault.
- **Discovered work is a per-run argument to a schedule still prepared once.** Rebuilding the
  graph every run would mean a transitive closure ten thousand times. It works because a
  discovered item is always a leaf hanging off one existing item: no edge it adds can close a
  loop, and no priority it takes can displace anything, so the ranking stays a property of the
  plan exactly as decision 7 requires.
- **The ceiling on all three assumptions is 200%, and it was measured.** Every percent of scope
  growth is items the scheduler runs: at five hundred items and ten thousand runs, 413ms with no
  growth, 915ms at 200%, 1.9s at 500%, 3.4s at 1000%. 200 is the last value that leaves the
  two-second budget with room in it.
- **The screen was broken for one step, and no test noticed.** Making the three fields required
  on the server left the panel sending a body the API refuses; all 332 frontend tests stayed
  green because they mock `fetch`. The next step fixed it, and the contract is now checked by
  comparing field names across the seam — which is the one mismatch neither suite can see.

**What M8, M9 and M5 inherit.** Both of M3b's parameters are **asked of a person now and
derivable from history later**, and that is a feature waiting on data rather than on a decision.
M8's calibration record says how wide a team's bad stretches actually were; M9's throughput says
the same thing from another direction; M8's own history of plans says how much they grew. The
rule to keep when that data exists is the one this milestone spent a decision on: **propose from
history, never default.** A number the server filled in is a claim about a team nobody made, and
the whole reason these are required is that zero is a claim too. M5 inherits the framing — a
percentile of an outcome rather than a parameter of a distribution — which is the only form of
either question a person can answer, and which arrived here early because there was no other way
to ask it.

**What M4 inherits.** Nothing in M3 knows what a date is: the engine, the table, the API and
the panel are all in hours of effort, and the working-day assumption M4 needs has deliberately
not been invented anywhere it could be inherited by accident. M4 is the first place it gets
made, and the note in that section — keep it crude and **visible** — is the whole of what M3
was protecting.

> **Spent, and M3 stayed in hours throughout.** M4 put the calendar in `forecast.model` beside
> the engine rather than inside it, so `Engine` still never sees a working day and
> `Engine.VERSION` is still 2. That is the shape this note was holding open: a calendar change
> must not be readable as a model change, and it now cannot be.

**What M6 inherits.** A run stores its seed, its inputs and its engine version, so per-item
contribution can be obtained by **replaying** a run rather than by storing a sampled vector per
item per run. `Forecast` already carries a standard deviation, which step 4 added because the
oracle was only half checkable without it.

### The aggregator is a scheduler, not a sum

Ten identical items (P10 3d, P90 12d), changing nothing but the structure around them:

| Model | P50 | P90 |
|---|---|---|
| A. Strictly sequential — summing, as originally planned | 68.3 | 86.3 |
| B. Two parallel chains, both must finish | 38.6 | **51.1** |
| C. The same graph, but only one person available | 68.3 | **86.3** |
| *(one branch measured on its own)* | *33.5* | *46.6* |

Same items, same estimates, **51.1 or 86.3 at the P90** depending only on structure. Two
things follow:

**Summing was never neutral.** A flat list that adds up durations silently assumes one
worker doing everything end to end. That is a strong claim about capacity, made by accident.

**Dependencies and resources are complements, not separate features.** Rows B and C are the
same graph. Without a capacity model, a dependency graph assumes *unlimited* parallelism and
is optimistic by the same margin that summing is pessimistic — it swaps one hidden extreme
for the other. Shipping the graph alone would make forecasts confidently wrong in a new
direction.

So M3 aggregates over a graph from day one, with a **single global capacity** (how many
items may be in flight at once). A flat list is then the degenerate case of one chain, and
named resources in M11 are a refinement of the capacity number rather than a rewrite of the
engine. Retrofitting a scheduler into a summing aggregator later would mean rebuilding the
core.

Merge bias also stops being a talking point and becomes output: joining two five-item
branches costs **+15% at the P50** over either branch alone, and it compounds at every join.

### Decided: sample, and use the closed form as a test oracle

"Can we just calculate it instead of sampling?" was measured rather than assumed, against
a 2M-sample reference (itself accurate to about ±0.05%).

For a **sum of independent** log-normals, a closed form is genuinely good. Fit each task,
sum the means, sum the variances, read the percentile off a normal:

| Scenario | True P90 | Normal approx | Error |
|---|---|---|---|
| 40 tight tasks (18–22d) | 811.1 | 811.1 | 0.0% |
| 10 tight tasks | 206.0 | 206.0 | 0.0% |
| 10 wide tasks (2–30d) | 209.4 | 214.0 | +2.2% |
| 5 wide tasks | 117.0 | 123.3 | +5.4% |
| 9 tight + 1 dominant wide | 210.4 | 218.8 | +4.0% |

The Central Limit Theorem does the work; error only shows up with few items or extreme
skew.

**What settles it is not accuracy but expressiveness.** Add the shared team factor and the
closed form returns *the same number*, because summed variances assume independence:

| | True P90 | Closed form |
|---|---|---|
| 10 wide tasks, independent | 209.4 | 214.0 |
| 10 wide tasks, **shared team factor** | **222.2** | **214.0** |

> **Both rows are now a test, and reproducing them needed one fact this table did not
> record**: which factor produced 222.2. Solving for it gives a P90 of **1.30** — a 30%
> stretch, the same worked example `m3b-plan.md` decision 2 uses. The engine lands on both
> numbers to within a percent from one plan and one seed. A measurement is only an oracle
> while every parameter that produced it is written down, which is the lesson to carry to the
> next table like this one.

A common cause moved the real answer by 13 days and the formula could not see it. Scope
uncertainty (a random *number* of items) and merge bias (max of random variables, now part
of the graph model) fail the same way — not approximation error, but models that cannot be
written down. Each is a few lines inside a sampling loop.

Cost does not decide it either: sampling error at 100k runs is **±0.20%** (±0.77% at 10k),
an order of magnitude below the closed-form error, and reproducible with a seeded RNG.

Two consequences for how M3 gets built:

- **The closed form becomes the test oracle.** For the independent case, assert the sampler
  converges to the analytic mean and variance. That is how we answer "how do we know the
  simulator is right?"
- **Do not reach for Cornish-Fisher.** The obvious skew correction was measured at −16% to
  −65% — far worse than the plain normal, because the expansion breaks down at high
  skewness.

FFT convolution of discretised distributions is a real middle path — deterministic and
exact to grid resolution — but the team factor needs conditioning and mixing over a grid,
and max-operations get awkward. More machinery, less flexibility.

---

## M4 — A date you can commit to ✅ *done* — *planned in `m4-plan.md`*

**Tier 1 is complete.** A single confidence control (50 / 80 / 95%) resolves the engine's hours
into a calendar date, under a working day somebody states and the run keeps. This document's own
bar for beating a spreadsheet was a Monte Carlo rollup and a ship date at a confidence level;
both exist, and everything after this is analysis over the same engine.

`m4-plan.md` breaks it into four steps and answers ten decisions. **The one worth knowing from
here** is that the working day belongs to *one worker* and never to the team: the engine's output
is a completion time with capacity already inside it, so dividing by a team's daily total counts
capacity twice and produces a date wrong by exactly the factor a team is proudest of — with
nothing on screen looking wrong. The plan also settles that dates are *derived* rather than
stored, with the calendar rule named on the run the way `priority_rule` is, and that runs made
before M4 get no date rather than a backfilled one, which is the deliberate opposite of what
`V13` did.

**The smallest surface of any milestone since M1a, and it stayed that way.** Nothing here touches
`Engine`, `Schedule` or any run that has already happened: `WorkingCalendar` is a pure function
beside the engine, the three new columns are nullable and unbackfilled, and the five dates are
derived on the way out. `Engine.VERSION` is still 2, which is the property that matters — a
calendar is not a model, so adjusting one must never invalidate a stored forecast's numbers.

**Two things this built that the section above did not ask for.** The rule name stored on a run
is what *decides* whether that run gets dates at all, so a run made under a calendar the code
cannot resolve reports its hours and says so rather than being read through today's rule — the
alternative makes a rule change indistinguishable from a plan that moved, silently, which is the
one failure M10 cannot recover from. And the whole frontend suite now runs in `America/New_York`,
because a date bug that only appears west of the meridian is invisible in a suite that runs in
UTC.

**What M11 inherits.** The working day is a *stated* number today and becomes a derived one when
real availability arrives — and that arrives as a **new calendar rule name**, never as an edit to
`five_day_week`. Every run made under this one keeps resolving under it, which is what stops a
holiday calendar landing in M11 from silently moving every date this product has ever published.
`WorkingCalendar.RULE` and `ForecastRun.calendar_rule` are the whole of the mechanism; M11 adds a
constant and a branch, and `m4-plan.md`'s decision 3 is the argument for why it must.

**What M10 inherits.** Every run now carries a date *and* the calendar that produced it, which is
exactly what a sliding-date detector needs to tell a plan that moved from a calendar that changed.
Without the second half, two forecasts read under six- and eight-hour days would look like two
weeks of slip. This is the same reason M3b's assumptions are columns rather than prose, and it now
covers the number M10 actually reports.

Nobody asks for a distribution; they ask what date to promise. This also reframes the
negotiation: "can we go faster?" is answered with "we can commit at lower confidence,"
which is the honest trade rather than a capitulation.

> **Built as one control over one run.** Moving between 50, 80 and 95 changes the date on screen
> with **no request going out** — all five percentiles are already in the response — which is not
> an optimisation but the whole of the reframing: the trade only reads as a trade if both numbers
> are two readings of one forecast rather than two forecasts. It also means the confidence is not
> stored on a run, because there is no such thing as the confidence a forecast was *made* at.

*Note:* effort → calendar date needs at least a crude working-day assumption. Keep it
crude and **visible**, and replace it in M11 when real capacity arrives. An assumption
users cannot see is one they will mistake for a result.

> **Honoured, and the visible half took more care than the crude half.** The working day is one
> required box with a hint saying whose day it is, and it is printed back beside every date it
> produced — in the assumptions sentence and in each line of the history — never behind a
> disclosure. The crude half is `five_day_week`: Monday to Friday, no holidays, no part-time
> anybody. What made this note worth writing is the failure it names, and the defence against it
> is not a test: a date is the first thing this product emits that *looks like a fact*, since an
> hours band advertises that it came out of a model and "Aug 25" does not.

**M3a kept this out on purpose, so M4 is where it enters.** The engine, `forecast_runs`, the
API and the forecast panel are in hours of effort throughout, and there is no working day
implied anywhere for M4 to inherit unexamined — which is the point: an assumption made in
passing by the milestone below is exactly the one nobody can see. It is also a *stated input*
like capacity, not a constant: decision 6 in `m3a-plan.md` refused to default the capacity
because "every default is a hidden claim", and a working day is the same claim about the same
team.

---

## M5 — Elicitation that produces honest ranges ✅ *done* — *planned in `m5-plan.md`*

Not polish. The maths is easy; getting truthful ranges out of humans is the actual product.

- **Surprise framing** — "what number would make you genuinely surprised to have exceeded it?"
  People recognise surprise far better than they reason about tail probability.
- **Betting framing** — "would you take 9-to-1 odds this lands under X?" Makes
  overconfidence feel expensive.
- **Comparative framing** — "bigger or smaller than the auth migration in March?"
  Reference-class forecasting, and it improves as history accumulates.
- **Overconfidence warnings** — flag a P90 less than ~1.5× the P50; that pattern almost
  always means nobody thought about what could go wrong.

> **Built, and the last bullet is worth reading with the measurement below beside it.** Both
> checks advise and neither refuses: a tight band is sometimes exactly right, and a rule that
> blocked one would become a specification people learn to type — 3/5/8 with an extra step, and
> the product teaching the failure it exists to detect.

`m5-plan.md` breaks it into five steps and answers ten decisions, and it opens with a
measurement that reorders the four bullets above. **Both checks were run against the failure
they exist to catch, and neither catches it**: 3/5/8 has a consistency of 1.02 and a P90/P50 of
1.60, so it clears the ratio rule and agrees with itself almost perfectly — as do 2/3/5, 5/8/13
and 1/2/3. The canonical garbage is *coherent* garbage, invisible to any test that can be run on
three numbers in isolation. So the warnings are a backstop and **the question order is the
defence**: the unbounded end first, the middle last, one on screen at a time, because the fault
is that the three were never separately thought about and that leaves no trace in what was
stored.

**Comparative framing moves to M8**, where a reference class exists, and is the one of the four
bullets above that did not ship. Today the only comparison available is against other
*estimates*, which is a guess against a guess and would spread anchoring across a whole plan
rather than within one item — the opposite of what the rest of this milestone does. It becomes a
much better feature there than it could have been here: "bigger or smaller than the auth
migration?" is only reference-class forecasting once March's *actual* is known.

**What shipped.** Three questions asked one at a time — bad case, good case, typical case, in
that order and no other — with no earlier answer on screen while the next is asked, and no
percentile named anywhere on the form. Then a review, the first and only moment the three are
seen together, carrying the betting frame and both warnings. The three boxes are gone and there
is no fast path back to them: a quick way to the garbage is a quick way to the garbage, and the
people most certain they do not need the framing are the people it is for.

**M5's failure mode is that it cannot fail visibly**, and it shipped that way. Every milestone
before it had an oracle — a closed form, a byte-identical degenerate case, a calendar anybody can
count on their fingers — and this one has a hypothesis about human judgement that nothing in the
repository can settle. The form is better to use; whether it produces honester ranges is not
knowable yet and will not be for a year.

**What M8 inherits, and it is the whole of how that question gets answered.** Every estimate now
carries `elicitation_method` — `three_point` for the rows written before this milestone, which
`V15` backfilled truthfully because three boxes really were the only form this product ever had,
and `surprise_framed` for everything since. **Split the calibration record by that column and the
answer falls out**: did changing the question change how often a band contained the truth? There
is no other way to ask it. The alternative was `created_at` against a deploy date that lives
nowhere in the database, which is the reconstruction these documents exist to avoid.

M8 also inherits **comparative framing** as above, and a second, smaller thing: the two checks
below are now functions with thresholds stated once, so a hygiene report that wants to run them
across a whole plan extends something that exists rather than reimplementing a rule.

---

## M6 — Variance contribution ✅ *done* — *planned in `m6-plan.md`*

Rank items by contribution to the *spread*, not by size. A 20-day task estimated 18–22 is
nearly risk-free; a 5-day task estimated 2–30 wrecks the forecast.

Answers "what should I spike next to tighten this plan?" — and it is the most defensible
thing in the product, because point-estimate tools cannot produce it at all.

**Revisit for the graph.** With a summing model, contribution was simply each item's share
of total variance. With a scheduler it is not: an item with a huge range that sits well off
the critical path may contribute almost nothing, while a modest item at a merge point
contributes a great deal. Contribution has to be measured against *project completion* —
correlate each item's sampled duration with the project outcome across runs, rather than
computing it from variances in isolation. That is more work than the original description
implies, and it is also strictly more useful.

**It can be had by replaying, and does not need new columns.** M3a stores a run's inputs, its
seed and its engine version precisely so a run can be reproduced exactly; correlating each
item's sampled duration against the project outcome therefore means re-running a stored
forecast, not storing a duration vector per item per run — which at 500 items and 10,000 runs
would be five million numbers a table would have to carry for every button press. A replay costs
about 300ms. What M6 must not do is change the model without bumping `Engine.VERSION`, since a
replay that no longer reproduces its own stored percentiles is what the persistence test in
`ForecastApiTests` exists to catch.

> **Discharged, and the version did not move.** Watching the engine is an observer told after
> every draw that takes none of its own, so the same seed produces byte-identical percentiles
> whether anybody is listening or not — asserted, because that assertion is the whole argument
> that `Engine.VERSION` need not move. Measured at the largest plan this product supports, the
> accumulator costs nothing: 489 ms to replay five hundred items over ten thousand runs, 491 ms
> with the ranking being worked out as it goes.

`m6-plan.md` breaks it into five steps and answers ten decisions, and it **turns that last
sentence into a runtime guard**: the endpoint replays a run, compares the six figures it produces
against the six on the row, and refuses to explain a run that does not match. That is the
persistence test promoted out of the suite, and it needs no list of replayable versions to keep
in step — it asks the only question that matters, *does this still come out the same?*, and so
catches a version bump, a JDK generator change and an accidental edit to the sampler alike.

**Storing nothing is what makes it work on the past.** A stored contribution would exist only for
runs made after the column did; a derived one exists for every forecast this product has ever
produced, M3a's included. That is the argument, rather than the size of the vectors — and it is
M4's decision 5 read from the other side.

**Two things the section above does not say, and the plan does.** The obvious presentation of the
answer is a set of percentages, and **they do not add up**: with M3b's shared team factor
everything moves with everything, so the squared correlations sum to well over one. They sum to
exactly one only in the summing model this product deliberately stopped using — which is the
plan's own oracle, and the reason the number is shown as a ranking rather than a share. And
**two of the rows are not items**: the shared team factor and the work nobody has listed are both
sources of spread, either can dominate, and a report that ranked only tasks would answer "which
of these should I spike" while hiding that the answer is sometimes "none of them".

The plan also carries a measurement: the one-pass correlation formula everybody writes first is
wrong in the third decimal on a plan of a million hours and returns **NaN** on one of a billion,
which is not valid JSON. Welford's co-moment update is five lines and accurate to 1e-12 in the
case that breaks it.

**What shipped.** `GET /api/forecasts/{runId}/contributions` replays a stored run, ranks every
source of its spread, and adds no column to any table. The panel loads it only when somebody asks
— half a second is cheap for a reader who wants it and rude to charge everybody who opened the
page — and shows a bar per source with **no percentage anywhere**, because a share that reads as
a percentage is one somebody will add up.

**What M7 inherits, and the line between them.** M7 reads this ranking; it does not extend it.
Ranking what widens a plan is not proposing what to cut, and the section below already says why
the second needs machinery the first does not: a cut has to be evaluated by re-running the
schedule without it, because removing an item off the deciding path buys no time at all. What M6
actually hands over is **the seam rather than the shortlist** — that a stored run can be replayed
exactly, and that `RunObserver` lets somebody watch it go past. `m7-plan.md` decision 6 explains
why the ranking itself is the wrong shortlist: an item that never varies contributes nothing to
the spread and can be the best thing to cut.

**What M10 inherits.** Two things, and the second is the larger. **Criticality is a different
question**: "how often is this item on the path that decides the finish" can be high for an item
that contributes nothing, because an item that never varies decides the finish in every run and
widens nothing. Conflating the two produces a ranking that means neither, so M10 measures its own
thing inside the scheduler. And the **replay guard** is what lets any cross-run comparison be
trusted at all: M10 compares runs, and a comparison across an engine change is how a tool reports
a date sliding when nothing moved. M6 made "does this still come out the same?" a question the
server asks rather than one a test asks once.

---

## M7 — Inverse queries ✅ *done* — *planned in `m7-plan.md`*

Run the question backwards: not "when will this finish" but "what do I cut to hit
1 November at 85% confidence?", ranking candidate scope removals by the confidence each
one buys.

This is what turns Aurevanta from a reporting surface into something opened *during*
planning.

**Revisit for the graph.** Cutting an item off the critical path buys no time at all, so
naive "rank by size" scope suggestions will be wrong. Candidate cuts have to be evaluated
by re-running the schedule without them. M6's replay machinery is how a candidate gets evaluated
at all. What M7 must not do is mistake M6's ranking for the answer: a high contribution says an
item widens the band, not that removing it buys a date.

> **This section used to say M6's ranking was "the shortlist worth re-running", and that is
> wrong** — corrected here rather than left to mislead whoever builds it. M6 ranks by contribution
> to the **spread**, and a task that always takes exactly forty hours has a contribution of zero:
> it never varies, so nothing moves with it. Cutting it removes forty hours from every run, and it
> is frequently the *best* thing to drop. A shortlist drawn from M6 would systematically hide the
> certain-but-large work — which is exactly the work a team can most confidently plan to cut.
> `m7-plan.md` decision 6 carries the argument; decision 1 is what makes a shortlist unnecessary
> at all, by having the caller name the candidates.

`m7-plan.md` breaks it into six steps and answers ten decisions, and it opens with a measurement
that decides the shape of the whole thing. The obvious implementation forecasts the plan, forecasts
it again without an item and reports the difference — **and at ten thousand runs the baseline moves
1.2 points seed to seed while a cut worth having buys about five.** Pairing the two sides on one
seed halves the noise (1.84 pp of spread becomes 0.75 pp), and the ranking is what that rescues:
two candidates a point and a half apart can be ordered when the comparison is paired and cannot be
when it is not.

**Pairing is not free, and the way to get it is the plan's sharpest decision.** A cut cannot be
modelled by removing the item — that renumbers every edge and shifts every later draw — nor by
emptying its estimates, which is worse because it is silent: `ItemModel.sample` returns from
`weighsNothing()` *before* it draws, so a weightless item takes no draws and the generator runs
ahead. **A cut item keeps its estimates, takes its draws, and is worth nothing**, which leaves
every other item in the run sampled from exactly the same place in the stream.

**And the numbers may never be added.** This is M6's "these do not add up" in a form far more
tempting to add: three cuts with "+5%", "+3%" and "+2%" beside them read as thirteen percent, and
two cuts on one chain buy barely more than one. The cumulative answer is searched for and measured
at every step rather than inferred from the singles. That is more expensive — and once M11 lands, the
answer space widens from "what do I cut?" to include "what if we add a person?", which uses
the same machinery.

**What shipped.** `POST /api/forecasts/{runId}/cuts` takes a date, a confidence and up to twelve
candidates the caller names, and answers with three things: what each candidate buys **on its
own**, a set of them that reaches the bar with the confidence after every step, and **why the
search stopped**. It adds no column, no migration and no engine behaviour — one flag on
`ItemModel`, which the engine already read, and M6's `RunObserver`, which already existed. The
screen is a tick list over the plan's own work, and the two lists are stacked under separate
headings rather than tabled, because columns side by side are how somebody comes to add one to
the other.

**Two numbers this milestone put on the table.** The search budget is **forty simulations**: at
five hundred items each is half a second, twelve candidates that never reach the bar would be
seventy-nine of them, and forty is three times the cost of weighing the candidates once. So a
narrow shortlist searches deeper than a wide one — five candidates never reach the budget at all,
twelve stop after three steps — and **running out of budget is a distinct ending that the answer
names**, because a search reporting the best thing it happened to look at without saying so is the
failure mode of every heuristic. The other number is step 3's honest cost: **6.3 s** for a
baseline plus twelve cuts at the scale ceiling. The cap stays at twelve rather than the sample
count coming down, exactly as this document's own rule says — if the budget gives, it gives on
what is visible.

**What M11 inherits.** "What if we add a person?" is this machinery with `capacity` as the lever
instead of a cut: a stored run replayed under a changed parameter, counted against the same hours
budget, with the same paired comparison making the difference readable. It is **a parameter change
on a replayed run rather than a second feature** — which is why M11's own bullet can say it falls
straight out of here. The two limits M7 states are what it inherits alongside: capacity is not a
draw, so a counterfactual over it does not disturb the stream at all, but M11's real availability
arrives as a **new calendar rule name** and a run made under the old one still answers under the
old one.

**What M10 inherits.** A target date and a confidence are now things a plan can hold an opinion
about, which is precisely what a plain-language sentence needs: "85% likely by 20 November" is
M4's reading, and "and here is what it would take" is this. M10 also inherits the shape of the
honest refusal — a run this engine no longer reproduces is not advised on at all, since a
recommendation from a different model is an exact answer about a plan nobody forecast.

---

## M8 — Actuals and calibration feedback — *planned in `m8-plan.md`*

Record what happened, then measure the hit rate: of the items estimated, how many landed
inside their P10–P90 band? It should be 80%. Most teams score 30–50%.

Reporting that per estimator — and offering a correction factor — makes the tool improve
its users rather than merely serve them. Depends entirely on M2's immutable estimate
history.

**An estimate dated after the work began is not a forecast and must not be scored as one.**
Somebody who can already see how a task is going is writing a report, and counting it here
flatters the hit rate — which is the one number in this product whose whole value is that
it is unflattering. M2 makes this computable without a rule of its own: an estimate carries
an immutable `created_at` and an item carries `started_on`, so calibration can exclude
those ranges, or score them separately, which is arguably the more interesting report of
the two.

### Considered and rejected: making the schedule enforce it

The direct version is to refuse the situation rather than measure around it — no starting
work that has not been estimated, and no estimating work that has already started. Both
were proposed, and both are turned down here so the question is not reopened from scratch.

- **Estimating is optional, and that is settled above** (M2's decision table). A plan that
  is half estimated is every real plan on its first day, which is why coverage is reported
  prominently instead of demanded.
- **The deeper objection is that work starts whether or not anybody has filled this tool
  in.** Refusing to record a start does not prevent it; it makes the record less true than
  reality — and this record is what M8 and M10 read. A gate would buy tidier data by
  throwing away the evidence it was meant to protect. It is also workflow enforcement,
  which *What Aurevanta is not* rules out in as many words.
- **Freezing the estimate is unnecessary**, because nothing can change one: a revision is a
  new row and the original stays readable. That immutability is exactly what makes the
  exclusion above possible. Forbidding revisions would also delete the "estimates revised"
  term from the movement decomposition in the icebox, which is signal rather than noise.

If the concern wants surfacing before M8 exists, the cheap and honest form is a note on
screen — "this task has no estimate, so a forecast will leave it out" — rather than a
refusal. Saying what will happen is this product's job; deciding what a team may do next is
not.

---

## M9 — Throughput cross-check

A second, independent forecast from historical throughput, with no estimation involved. It
implicitly absorbs interruptions, holidays, scope growth, and the fact that nobody works
eight focused hours.

**The gap between the two forecasts is the deliverable.** When the team says six weeks and
their own history says eleven, that is far harder to dismiss than either number alone,
because both came from the team.

---

## M10 — Communicating to people who do not know what P90 means

- **Plain-language output** — "85% likely to finish between 12 October and 20 November."
- **Burn-up with a confidence cone**, narrowing as work completes.
- **Forecast history**, and from it a **sliding-date detector**: warn when successive
  re-forecasts keep moving out rather than converging. **M4 made this answerable in dates
  rather than only in hours**, and it did the harder half too: each run stores the calendar it
  was read under, so a detector can tell a plan that moved from a working day that changed. A
  comparison across runs made under different calendars — or across an `Engine.VERSION` bump —
  is the way this feature reports a slide that never happened.
- **Merge bias, surfaced explicitly** — the graph makes this a number rather than a
  talking point. Where parallel branches must both finish, expected completion is later
  than either alone, and it compounds at every join. Simulation gets it free; spreadsheets
  get it wrong universally. Worth naming in the output rather than burying inside a total.
- **The critical path is probabilistic** — with uncertainty, no single path is *the*
  critical one. Report how often each path drives completion (criticality index); a path
  that is critical in 40% of runs is a different management problem from one that is
  critical in 99%. **Not the same thing as M6's ranking, and the difference matters**: an item
  that never varies can decide the finish in every single run and widen the band by nothing at
  all. Criticality is measured inside the scheduler and answers "what is holding this up";
  contribution is measured against the outcome and answers "what is making this uncertain".
  Conflating them gives a number that means neither.

---

## M11 — Resources and people

Turns Aurevanta from a forecaster into a planning tool. This is the largest single
complexity jump in the plan, so it comes after the analysis features have proved the
engine.

- **Resources** — a named thing with finite capacity, per tenant. **People are one type**;
  environments, licences and equipment are others. Modelling them uniformly avoids a
  parallel hierarchy, and a person-resource may optionally link to a `users` row, since
  plenty of people who consume capacity never log in.
- **Requirements** — an item needs *n* units of a resource, or of a resource type, for its
  duration. Type-level requirements ("any backend engineer") are what make the model useful
  for planning rather than just recording an existing assignment.
- **Availability** — working days, holidays, part-time allocation. Unglamorous and the
  place where forecasts quietly stop matching reality if it is skipped.
- **Duration from effort** — with an allocation, M2's stored effort finally converts to
  duration honestly, replacing M4's crude working-day assumption. **As a new calendar rule
  name, never as an edit to `five_day_week`**: every run M4 produced stored the rule it was
  read under precisely so that a better calendar arriving here cannot move a date that was
  already published. A run under a rule the code no longer implements reports its hours and
  says so, rather than being resolved under the wrong one.
- **"What if we hire someone?"** — the most compelling question this unlocks, and it falls
  straight out of M7's inverse-query machinery once capacity is a variable.

### What makes this hard, stated up front

**It stops being a calculation and becomes scheduling.** Allocating finite resources across
a precedence graph is the resource-constrained project scheduling problem, which is NP-hard.
The engine needs a heuristic — a serial schedule generation scheme with a priority rule is
the standard choice — inside *every* Monte Carlo run.

**The heuristic is a modelling assumption.** Two defensible priority rules produce two
different forecasts from identical data. Users will not intuit this, so the rule has to be
visible and stable rather than an implementation detail.

**This is the first genuinely expensive thing in the plan.** Summing is trivial; scheduling
a few hundred items per run, across 100k runs, is seconds rather than milliseconds, and
grows with both item count and resource count. It is also, per the commercial note below,
the first feature where metering would have real economics behind it.

---

## Deferred — genuinely later

Refinements of M11 that are not needed to make it useful.

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
  drown. The same connection feeds M9's throughput history for free.
- **CSV import/export** — the unglamorous escape hatch that makes people willing to try the
  tool, and willing to trust that they can leave.
- **Plan templates** — project shapes a team repeats, with their historical actuals
  attached as a starting reference class.
- **Grouping, filtering and search inside a plan** — the plan screen renders every live item
  in one flat table ordered by name, and 500 items per project is the *stated* ceiling, so at
  its own declared scale the screen is unusable by construction. The cheap version needs no
  schema and is view-only: search, filter by status or by estimator, collapse what is not
  being discussed. The expensive version is a parent/child hierarchy, and it is expensive for
  a reason M2 already gave when it fixed the unit of estimation at *task* — a parent carrying
  its own estimate double-counts the scope of its children, which is the objection that ruled
  epics out as the unit in the first place. **Tags are the middle answer**: grouping without a
  rollup, so nothing acquires an estimate that is not a task's. **All of this is about
  reading a plan and none of it is about forecasting one** — the thing that produces a date
  for part of a plan is *Milestones* under *Getting forecasts out*, and the two get conflated
  because a heading and a milestone look alike on screen and are nothing alike underneath.

### Getting forecasts out

- **Shareable read-only forecast link** — a URL a stakeholder opens without an account.
  M10 is entirely about reaching people who do not know what P90 means, and right now every
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
  finished and reports only the last. And M3a keeps an unestimated item in the graph as a
  zero-effort node precisely so precedence survives it, so **a milestone is that node with a
  name**: the work it waits on points at it, it takes no draw, and it needs no estimate.
  What would have to change is what the scheduler *reports*, not what it samples — the shape
  M6's change had, where an observer watched a run without taking a draw and `Engine.VERSION`
  did not move. If that turns out not to hold it is a version bump, and M10 has to be told:
  comparing runs across one is exactly how this tool would report a date sliding when nothing
  moved.

  **What it multiplies is the argument for it.** M4's date at a chosen confidence, M6's ranking
  of what is widening the band, M7's list of what to cut — each answers for a whole plan and
  would answer per milestone, which is the scale somebody argues at. M7 most of all: *what do I
  drop to hit the beta* is a question people ask, and *what do I drop to hit the whole project*
  mostly is not. It is also where merge bias stops being a talking point — a milestone is a join
  by construction, so the effect M10 wants to name shows up per group instead of buried in one
  total.

  **Not a section and not a sprint.** Grouping a plan so it can be *read* is the separate entry
  under *Getting work in*, and it is the cheaper thing — tags without a rollup, so nothing
  acquires an estimate that is not a task's. A milestone is something you forecast: it has a
  date and no workflow, because boards and states work moves through are what *What Aurevanta
  is not* rules out in as many words.

  **One schema question to settle before any of it.** Whether a piece of work can serve more
  than one milestone — "everything needed for launch" and "everything needed for the security
  review" overlap, so many-to-many is the truthful answer and a nullable column on the item is
  the cheap one. Choosing the cheap one here is the shape of mistake M1a spent a whole milestone
  undoing.

### Modelling depth

- **Discrete risk register** — "the vendor API may slip: 30% likely, adds 10–20 days."
  These are Bernoulli events, not duration variance, and real projects fail on them. A few
  lines in the sampler.
- **Correlation groups** — items sharing a component, a person, or an unknown technology fail
  together. **This is now precisely one step beyond a thing that exists**: M3b's global team
  factor is one log-normal multiplier drawn per run and applied to every remaining duration, and
  a group is the same multiplier drawn per group and applied to its members. The sampling is a
  few lines. What it actually costs is a *grouping somebody has to define* — schema, a way to
  put an item in a group, and a screen to do it on — plus a second question to ask per group,
  which is the part M5 says is hard. One shared factor already captures most of the effect,
  which is why this stays here rather than moving up.
- **Learning curves and ramp-up** — a new joiner does not deliver at full rate on day one,
  and adding people to a late project has a known cost. Only meaningful once M11 exists.

### Trust and feedback

- **Forecast movement decomposition** — *why* the date moved, split into scope added,
  estimates revised, work completed, and time simply passing. "Out 8 days: +5 new scope,
  +4 re-estimates, −1 progress." This is the feature I would most want as a user, and I
  have not seen it done well anywhere.
- **Backtesting** — replay a team's historical data and show what Aurevanta would have said
  six months ago versus what happened. The fastest way to earn trust from a sceptic, and it
  needs no new modelling.
- **A proper variance decomposition** — Sobol or ANOVA indices, which partition the spread
  including the interactions between sources, and so *would* add up to a whole where M6's
  squared correlations deliberately do not. Turned down there rather than forgotten: it costs at
  least a re-run per source where the ranking costs one for the whole plan, and the ranking is
  the honest ninety percent. Worth revisiting only when somebody asks a question the ranking
  cannot answer — "how much of the spread is the team factor *on its own*, with the items held
  still" is the shape of that question.
- **Estimate hygiene warnings** — flag estimates gone stale, ranges pasted identically
  across items, and clustering on 3/5/8. Extends M5's overconfidence check from single
  estimates to patterns across a plan. **Both single-estimate checks now exist** as
  `EstimateQuality`, with their thresholds stated once beside the arithmetic, so this extends
  functions rather than reimplementing rules — and the clustering half is the interesting one
  precisely because M5's measurement showed that 3/5/8 passes every check that looks at one
  estimate alone. Seeing it forty times in a plan is a signal nothing at the single-estimate
  level can produce.
- **Closing the estimation loop** — M8 builds every part of the path from a range to what the
  work turned out to take, and joins none of them. A range is written in one small form on a plan
  row; what the work took is reported in a different small form on the same row; how the two
  compared is read on a separate page reached from the nav. **Nobody is ever told how their own
  estimate turned out.** The record is an organisation-wide aggregate, so the single most
  instructive sentence this product could say — *you said 10 to 40 hours and it took 100* — is the
  one thing it does not.

  What that looks like: ask what the work took at the moment somebody marks it finished, rather
  than as the fourth box of a form about status; show that against the estimate as soon as both
  exist; and put M8's coverage counts on the plan screen as the work they name, so "45 finished
  tasks never recorded how long they took" becomes a list somebody can act on instead of a number
  they can only read.

  **The unflattering half of the same point is that most of M8's bookkeeping is evidence
  disqualified by when a form was filled in.** Work finished with no start reported; estimates
  counted as reports only because they were written on the start day. Each of those is honest, and
  each is a range that told the truth about a task and cannot be scored because of the order two
  screens happened to be visited in. **So the fix is at collection time and never at scoring
  time** — `m8-plan.md` decision 1 is precise about why loosening the boundary is the one change
  that makes the number kinder without making it better. Fewer ways for evidence to fall out, not
  a better-worded account of why it did.

  **Two things it must not become.** Not time tracking, which *What Aurevanta is not* rules out
  and which this is not: one number at completion is not a timesheet. And not a correction fed
  back into the engine — `m8-plan.md` decision 8 — because the loop worth closing is a person's,
  and the moment the model closes it on itself the record measures the correction rather than the
  estimator.

### Collaboration

- **Delphi / planning poker sessions** — M2 already keeps the schema open for several
  estimates per item. This is the UI that makes multi-estimator real, and disagreement
  between estimators is itself a signal worth surfacing.
- **Commitment tracking** — record what was promised, at what confidence, on what date;
  then report whether it landed. Organisational calibration, one level up from M8's
  per-estimator version.
- **Discussion on estimates** — a range without its reasoning is hard to revisit six weeks
  later, when the person who set it has forgotten why.

### Presentation

- **Gantt with uncertainty bands** — every bar spanning P10–P90 rather than a false hard
  edge. The familiar view, without the false precision that makes Gantt charts lie.
- **Criticality heatmap** — colour items by how often they land on the critical path,
  pairing with M10's probabilistic critical path.
- **Confidence dial** — drag from 50% to 95% and watch the date move. Makes the
  confidence-versus-date trade tangible instead of abstract.
- **Distribution curves — one per estimate, and one for the whole simulation.** The
  percentile table states five numbers; the shape says why the mean sits above the middle and
  why the right tail is the part worth managing, which is M10's argument arriving early and
  cheaply. **The plan's curve needs no new data at all**: every run already stores a
  hundred-bucket histogram in `forecast_runs.outputs`, both read endpoints already send it,
  and `ForecastPanel` draws none of it — `m3a-plan.md` step 6 records that payload arriving
  unused and says this screen is what would notice first. **A task's curve needs a decision,
  and M3a already took the same one once**: the browser must not fit the distribution, because
  two rules that can disagree about one estimate is what `PasswordRules` exists to prevent, so
  the fit is *published* — `mu` and `sigma`, or points to plot — rather than re-derived from
  P10 and P90 inside a chart component. **And a task two people estimated is a mixture rather
  than a curve.** M3a decision 3 samples one estimator per run, so the honest picture is
  bimodal — which is the strongest reason to draw it at all, since a shape says "these two do
  not agree" in a way two rows of a table do not, and smoothing them into one hump would be
  the averaging that decision refuses, arriving through the presentation layer. Two things it
  must not do: interpolate the histogram into a precision the ranges never had, and let the
  limitations slide from beside the number into a footnote under a picture — `no_team_factor`
  means whatever curve is drawn is narrower than the truth. It would also be the *first* chart
  in the product, which is what *Reworking the interface* is about when it says that work
  belongs before M10 spends effort on charts; and the accessibility bar is much harder to hold
  in a drawing than in a form, so the percentile table stays as the text equivalent rather
  than being replaced by one.

### Speculative, flagged as such

- **LLM-assisted decomposition** — "break this epic into items." Plausible and dangerous:
  a machine-generated work breakdown carrying machine-generated estimates is exactly the
  confident garbage this product exists to prevent. If it is ever built, the output must be
  a draft a human estimates, never an estimate itself.

### Probably mis-filed

Four of the above are not really nice-to-haves:

| Idea | Why it may deserve a milestone |
|---|---|
| **Import from an issue tracker** | Closer to an adoption blocker. Nobody re-types a backlog to trial a tool, so this may gate real-world usage entirely. |
| **Shareable read-only link** | M10's whole purpose is reaching non-specialists; requiring them to hold an account undercuts it. Cheap to build, large audience unlocked. |
| **Discrete risk register** | A modelling gap rather than a feature. A forecast that models duration variance but ignores known discrete risks is systematically optimistic in a way the band does not show. |
| **Closing the estimation loop** | M8's own coverage counts are the argument. A calibration record needs finished work carrying both an estimate and a measured outcome, and the outcome is optional because nothing in the product asks for it at the moment somebody would know it — so the ordinary answer is "nothing scored yet", and no amount of arithmetic behind that changes it. **A feature whose usual output is an explanation of why it has no output has not shipped.** This is the work that decides whether the whole of M8 ever says anything. |

---

## Future — unsequenced

Not the icebox, which holds estimation ideas waiting for a milestone number. These are
questions already answerable today, recorded so the answer is not re-derived under pressure
later.

### Account security

None of these is estimation work, so none competes for a milestone number. They are here
rather than in the icebox because all three are changes to *identity*, which is the one part
of this product already built: they land on M0/M1 code rather than waiting on a schema that
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
  Distinct from SSO, which M1 excluded: an organisation that federates gets its factor from
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
  credential, but CI marking work finished, and a bot recording actuals for M8 to score, are
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

### Progress is written over, and the record is what M8 reads

**Perishable, in the same way `forecast_runs` was.** M2 handed M3 one obligation in as many
words — persist every run from the first commit rather than once the engine works, because
that history cannot be reconstructed later. Progress is the same shape of thing with no such
obligation attached: `WorkItem.recordProgress` writes `status`, `started_on`, `completed_on`
and `actual_effort_hours` straight over whatever was there, `work_items` carries no
`updated_at`, and nothing anywhere records *who* reported it. Every week this ships as it
stands is a week of progress history that cannot be backfilled, which is what puts it here
rather than in the icebox.

**It is the third kind of evidence and the only one that is not evidence.** An estimate is
immutable and names an estimator. A forecast run is immutable and names a requester. A
progress report — which M8 and M10 both read — is neither, and the asymmetry is not a
decision anybody took: `m2-plan.md` argues at length for the other two and this simply never
came up.

**The concrete cost is M8's own exclusion rule.** M8 refuses to score an estimate written
after the work began, comparing `estimates.created_at` against `started_on` — and `started_on`
can be moved afterwards by anybody, leaving no trace that it ever said something else. The hit
rate is the one number in this product whose entire value is that it is unflattering, and as
built it can be flattered by editing the date it is measured against. M10 wants the other
half: its movement decomposition carries a "−1 progress" term it can currently only *infer*
by diffing two forecast snapshots, rather than reading what somebody reported and when.

**The shape, when it happens**, is an append-only log beside the current columns rather than
instead of them — the item keeps its latest state for the screen and the scheduler, and the
log holds who said what, when. That is the estimates pattern exactly, and it is the reason an
estimate costs nothing to reason about.

### Which forecast runs are history

**Every forecast is written down, and that is right up until something starts asking in
bulk.** `forecast_runs` has no update and no delete because the point of the table is that
somebody asked twice; M10's sliding-date detector walks successive runs and its movement
decomposition diffs two of them. Both readers assume every row is a person deliberately
re-forecasting the same plan.

**M7 threatened that assumption and M11 threatens it harder.** Inverse queries rank candidate
scope cuts by re-running the schedule without each one, and "what if we hire someone?" sweeps a
capacity — dozens of runs to answer one question. Landing those in the same table gives the
detector a history that is mostly hypotheticals, and gives the diff two runs that were never
about the same plan.

**So the question is what a run is *for*, and it was cheap to answer then and awkward
afterwards.** Either a scenario is never persisted — the engine is pure and a run costs about
300ms, so nothing forces a row — or the table gains a kind and every reader of history filters
on it. What must not happen is the third outcome, where scenarios are stored because storing
was the code path that already existed, and the detector degrades with nobody able to say from
which release.

> **M7 took the first of the two, and `ForecastApiTests` holds it there.** A cuts request writes
> nothing at all: every counterfactual is a replay of a run that already exists, in memory, out
> of its own stored seed. Forty simulations can go past for one question and `forecast_runs`
> gains no row — so the history stays what it has always been, a record of somebody deliberately
> asking twice. **M11 inherits the answer rather than the question**, and the case that would
> reopen it is a scenario somebody wants to keep and come back to, which is a different feature
> and needs the kind column this section describes.

### Deleting a person

**Nothing here deletes an account, and that is a decision this product has not taken yet.**
Removing somebody deletes their membership and never their identity — M1 was explicit — and
`estimates.estimator_user_id` and `forecast_runs.requested_by_user_id` point at `users` with
no cascade on purpose, because calibration evidence has to outlive somebody leaving. All of
that is right, and what follows from it is that an erasure request has no answer in this
product. The first one will arrive with a deadline attached, which is the argument for
settling it while nobody is waiting.

**The two honest answers do not convert into each other, so the choice is real.**
*Pseudonymise* — clear the address, the display name and the credential, keep the row and
everything pointing at it — leaves M8's history intact for every colleague who did not leave,
and keeps a forecast attributable to *someone*. *Delete* takes their estimates with it and
silently rewrites the calibration record of people who never asked for anything. The first is
what the immutability discipline in this schema already implies; the second is what somebody
reaches for under a deadline, because it is what "delete my account" sounds like it means.

**Adjacent and much easier: exporting what this product holds about one person**, which is
the request that usually arrives first. Every row involved is reachable from one `users` id,
so it needs no decision — only a screen and an endpoint.

### Generalising the unit of estimation

M2 stores effort in hours (`m2-plan.md` decision 3), which is the right decision to ship.
This is here because "make the unit configurable" sounds like one change and is three, and
the cheap-looking version of it is the one that hurts.

**Nothing about M2 makes any of them worse, so none of this is perishable.** That is worth
stating, because M1a was: handles had to be fixed early since links get bookmarked into state
nobody controls. Nothing outside this database refers to an hour. Every row written today is
unambiguously hours, so a later migration backfills a default rather than guessing what old
rows meant — which is exactly why adding a unit column *now*, against a need nobody has, buys
nothing.

- **Days, weeks, ideal-hours — free, and already provided for.** The same quantity rescaled;
  `m2-plan.md` already stores hours and lets the UI show days. The multiplier is a setting —
  and M4 has since given a working day a length, though **not one this can borrow**: that
  number is stated per *run* and stored on it, because it is an assumption a forecast was made
  under rather than a display preference. A unit setting reading it would make every historical
  estimate rescale when somebody edited a working day, which is the failure `m4-plan.md`'s
  decision 5 exists to prevent.
- **Story points — moderate, and the cost is not in the schema.** The migration is a column
  rename plus a unit on the *project* (never on the estimate: multi-estimator means several
  estimates aggregate on one item, and mixed units there is undefined aggregation). The work
  is that points only become time through a **velocity, which is itself uncertain** — sampled
  points × sampled velocity, a few lines inside M3's loop and a more natural fit here than
  anywhere else, since this engine already samples distributions and M9's throughput history
  is where a defensible velocity would come from. Three consequences: **M11 needs the
  conversion first**, because effort divided by an allocation only means anything in time
  units; **M8 needs actuals in hours regardless**, since nobody records "actual story points";
  and **`lag_hours` stays time whatever the project uses**, because a finish-to-start lag of
  three points is meaningless. A points project therefore stores both units, and the unit is a
  property of a quantity rather than of the installation.
- **Money is a second axis, not a second unit** — and this is the point worth having written
  down. It is *mathematically easier* than time, not harder: costs genuinely sum, so there is
  no scheduler, no critical path and no merge bias, and the closed form M3 rejected would be
  adequate. What doubles is the output surface, which is what "not cost or budget modelling"
  above is really protecting. **So the trap is a `unit` column on `estimates`.** An item that
  costs money also takes time, and anyone asking for cost wants both at once; a unit slot gives
  each item one estimate in one unit and makes the thing they asked for harder to build. Cost,
  if it ever happens, is a **second estimate alongside the time one**, sharing the P10/P50/P90
  machinery and the immutability discipline and not the unit slot.

### Reworking the interface

Every screen in this product is deliberately plain, and each was built that way for a stated
reason rather than by neglect: M1 kept the auth forms minimal to reach a team product, and M2
says outright that its plan-entry UI "looks bad" and that making it good is a later concern.
Enough of them exist now — auth, members, settings, projects — that "make it look like a
product" is a piece of work rather than a tidy-up, so it is recorded as one.

**What this is not is M5.** The three-box P10/P50/P90 form will be the ugliest thing on
screen and the most tempting thing to fix, and styling it is the one change that cannot help:
`product-concept.md` is explicit that three boxes produce 3/5/8 without thinking, which is a
question-design problem. **M5 replaces what is asked; this replaces how everything looks.**
Conflating them means a beautifully styled form eliciting exactly the same garbage.

> **Half of that is now spent, and it is the half that was not this.** M5 replaced what the
> estimate form asks: three boxes became three questions asked one at a time, and the percentile
> names are gone from the screen. It is still deliberately plain — one input, a hint, a review
> and four buttons — so the sentence above stands exactly as written for the other half. What
> changed is that the most tempting thing to style is no longer the ugliest thing on screen for
> the *reason* it was: it is plain now because everything is, not because fixing it needed a
> milestone. **The trap it names has not moved.** A beautifully styled form eliciting the same
> garbage was always the risk, and it still is — M5 changed the question, and nothing about how
> anything looks has been shown to change an answer.

**What is actually there today**, so the size of it is not a surprise: one hand-written
`App.css` of about 770 lines, a dozen colour variables in `index.css` with a dark-mode block,
and components assembled per screen from `Field` and bespoke markup. There is no component
library and no design system — class names agree by convention, which is why `.members` and
`.projects` share a rule that neither owns. It is coherent, and it is coherent by hand.

**Deferrable, but not indefinitely, and the deadline is M10 rather than taste.** Nothing
outside the browser depends on how a screen looks, so this is not perishable the way handles
were — but M10 is where charts arrive, and a confidence cone, a burn-up and a criticality
heatmap are exactly the things that get built twice if the second build is a rework. The
accessibility bar the auth forms set is the same argument from the other side: it is
cross-cutting above, it is much harder to hold in a chart than in a form, and a rework is
when it is either kept or quietly lost.

**It still ranks below the engine, and this is the section where that has to be said.** The
ordering principle's warning is precise about this failure: a beautiful plan editor that
forecasts nothing is the tool this product exists to replace. So the honest position is that
the interface is worth doing well, worth doing before M10 spends effort on charts inside it,
and worth doing after there is a forecast to put in front of anyone.

---

## Cross-cutting, not a milestone

Threaded through the above rather than scheduled as a block.

- **Security** — rate limiting arrived in M1; what is left is below as *Security debt*, which
  is a reviewed list rather than the guess this bullet used to be. The JWT secret must still be
  set explicitly in any real deployment.
- **Localisation** — infrastructure exists, English only. Adding a locale is a catalogue
  file *once* the backend sends codes instead of prose (M1).
- **Accessibility** — the auth forms set this bar; keep it as charts arrive, where it is
  much harder. A confidence cone needs a non-visual equivalent.
- **Operations** — CI, container build, migration strategy, health and metrics. **Plus a
  per-tenant limit on concurrent forecasts**, which `m3a-plan.md` names as the thing that
  actually bounds what one member can make this server do: `Engine.MAX_SAMPLE_COUNT` is a
  bound on absurdity rather than a promise of speed, and the simulation runs *inside* the
  request's transaction, so a few large forecasts at once occupy database connections as well
  as processors. **M6 widened that surface and it is worth saying so**: a contributions
  request replays a whole run, measured at about half a second at five hundred items, and
  unlike asking for a forecast it is a `GET` that any reader can repeat as fast as they can
  click. The limit is the fix; making one endpoint non-transactional would only move half the
  problem and leave two shapes of the same code.
- **API documentation** — OpenAPI, once the domain endpoints exist and are stable.

### Security debt

Four findings from the review taken after M1a and before M2. **`security.md` is the record** —
it carries the exploit paths, the fixes, what was deliberately accepted, and what was checked
and found sound. This table exists so the debt is visible from the plan; it is not a second
copy of the reasoning, and it should not become one.

| # | | Severity | Cheapest moment |
|---|---|---|---|
| 1 | The credential cannot be withdrawn, and sits where a script can read it | Medium | Any time — but the two halves are one migration, so do them together |
| 2 | Registration is a free, repeatable account-existence oracle | Medium | Before a registration screen is redesigned; the ordering fix is free today |
| 3 | An invitation token is interpolated into an API path unencoded | Low | Before M2 — its blast radius is the endpoint set |
| 4 | Raw invitation tokens travel in the request line | Low | Before the API is public; fixing 4 removes 3 |

**Cross-cutting rather than scheduled, deliberately.** None of these is estimation work, and a
list of security fixes sitting inside a milestone's bullets competes with that milestone for
the same attention — which, given the ordering principle above, is a competition security
loses every time the plan is trimmed to reach the engine sooner. They belong here, where the
question is *when is this cheapest* rather than *what is this milestone made of*.

**Worth knowing before picking any of them up**: finding 1's second half would make CSRF this
application's problem for the first time. CLAUDE.md currently argues that CSRF needs no defence
*because* authentication is bearer-only with no cookie — true today, and false the moment a
cookie is set.

### Decided: forecast quality is not a commercial axis

Tiering the *method* — a cheaper closed form on lower plans, sampling on higher ones — was
considered and rejected. The closed form is not a cheaper approximation of the same model;
it is a **different, blinder model** that cannot represent the team factor (see M3). Gating
on it would mean selling a band that is too tight without saying so, which is the exact
failure this product exists to prevent. Sampling also costs milliseconds, so there is no
infrastructure bill to recover.

Everyone gets the same model. If the product is ever monetised, the axes that scale with
real cost and real value are **seats** (M1), **history depth** (M8/M9 need accumulated
actuals), **scale** (large portfolios, M7 inverse-query sweeps) and **org features** (SSO,
audit, portfolio rollups) — never statistical method.

Revisit only if a feature arrives whose cost is genuinely non-trivial: scheduled
re-forecasting, very large portfolios, or an LLM-assisted elicitation step in M5.

**M11 is the first candidate.** Resource-constrained scheduling inside every Monte Carlo
run is seconds rather than milliseconds and scales with both item and resource count — real
compute, unlike the sampling engine itself. If anything in this product is ever metered,
that is the honest place for it. The principle still holds: meter the *scale of the plan*,
never the quality of the maths applied to it.

---

## What I would build next

**M1 invitations, then M1a, then M2's schema decisions, then straight at M3a.** The
temptation will be to build satisfying CRUD screens for projects and tasks. Resist it: a
beautiful task list that sums P50s is precisely the tool this product exists to replace.

M1a earned its place in that sequence only because it was cheap *then*: a small change that
becomes a link-breaking one the moment M2 puts a slug in a URL. It was never more important
than the engine — nothing is — it was just perishable, and it is now spent.

**M1, M1a, M2, all of M3 and M4 are done, so that sequence is spent and Tier 1 exists.** A plan
with ranges in it produces a band that models common cause and unlisted work, with its six
assumptions printed beside the number, and resolves to a date at a confidence somebody chooses —
and the two disclosures M3a shipped in place of those models are emitted by nothing.

**M4 is spent, and it did not become what made it tempting.** The worry was that a date is what
people actually ask for while the conversion looks like arithmetic, so the working-day assumption
would arrive as the first number in this product a server picked. It did not: it is a required
box with no default, printed beside every date it produced, stored on the run under a named rule
so that a better calendar in M11 cannot move a date already published.

**M5 is spent too, and planning it moved one of its own bullets before a line was written.** The
overconfidence warning that section proposes does not catch 3/5/8 — nor 2/3/5, 5/8/13 or 1/2/3 —
and neither does the consistency check the engine already reports. Every Fibonacci triple agrees
with itself to within a few percent and sits just outside the ratio rule. So the checks shipped
as a backstop and the *question order* shipped as the defence, and anybody who had shipped only
the checks would have shipped the part that measurably does not work.

**M6 is spent, and it cost no schema at all.** Variance contribution comes from replaying a
stored run rather than from any column, which is what M3a's seed was kept for and the first
feature to spend it — so it answers for every forecast this product has ever produced rather than
only for the ones made since. Both traps that section warned about held: the ranking is measured
against project completion rather than computed from variances in isolation, and the model did
not move to make the measurement easier. `Engine.VERSION` is still 2.

**M7 is spent, and with it Tier 2.** It is the step that turns a reporting surface into something
opened *during* planning, and **planning it corrected this document twice**: a high contribution
says an item widens the band, not that removing it buys a date, and M6's ranking is *not* the
shortlist worth re-running, because an item that never varies contributes nothing to the spread
and is frequently the best thing to cut. What M7 hands the caller instead is the question "which
of these are you willing to drop?", because which work is negotiable is a judgement about value
that this server holds none of.

**It did not become a scope-editing screen.** It proposes, and somebody else decides — on the plan
screen, where archiving already exists and where somebody can see what else the work is connected
to. The tick list is over the plan's own work and nothing on that panel changes anything.

**And the thing it was most likely to get quietly wrong, it did not.** A cut is a draw taken and
*discarded*, never an item removed and never an estimate emptied — which is the difference between
a ranking and a coin flip that looks exactly like a ranking, and which no test downstream of it
would have announced. `Engine.VERSION` is still 2.

**What is next is M8**, and the ordering principle is why: everything built so far is a claim
about the future that nothing has yet checked against what happened. M8 is the first milestone
that can tell whether any of it is any good — and M5 shipped `elicitation_method` specifically so
that M8 can split its calibration record by how the question was put.

The temptation that has not changed is the *plan-entry UI that already exists and looks bad*. It
is meant to. M5 replaces what it asks, and the interface rework is recorded under *Future*;
neither is the engine.

### Was blocked on a decision — settled, and then built

Every one of these was answered before M2 started, and `m2-plan.md` carries the reasoning.
All seven are now in the schema.

| | Decision |
|---|---|
| Unit of estimation | Task |
| Multi-estimator | Schema now, UI later |
| Effort or duration | Effort, stored in hours — a "day" is a calendar word and calendars are M11's |
| Dependency types | Finish-to-start with lag |
| Items with no estimate | Forecast what is estimated, report coverage |
| Can a MEMBER edit estimates? | Yes — any member may do everything; roles govern administration only |
| How large is a plan? | 500 items per project |

### Known thin spots

Honest about where this plan is weakest, rather than discovering it mid-build:

- ~~**M3 is oversized**~~ *Split into M3a and M3b by `m3a-plan.md`, and both are now built* —
  which is what retires this rather than the split itself. The line held under construction in
  both directions: M3a is the half a closed form can verify, and it was verified against one to
  five thousandths of a percent before anything downstream of it existed; M3b then had to be
  proved in pieces, and its three narrower oracles all held — exactness for one item under a
  factor, the published 209.4 → 222.2 figure, and byte-identical equivalence with M3a at zero.
- ~~**Scope uncertainty has no agreed position in a graph**~~ *Answered in `m3b-plan.md`
  decision 3 and then built*: new work attaches as a successor to a uniformly chosen existing
  item, becomes ready when that item finishes, and competes for capacity like everything else.
  Appending at the end, loading the critical path and inflating durations were each rejected
  with a reason, the last of them because a multiplier is indistinguishable from the team
  factor and would be one effect counted twice. **The claim it makes is the weakest of the
  four** — that unknown work is as likely to land anywhere as anywhere else — and the
  refinement that would sharpen it is named and not built: weight the choice by remaining
  effort, so new work lands where the work is. That is one line and it needs data nobody has
  yet, which is the same answer M8 gives to everything else on this list.
- ~~**The plan-entry UI is barely scoped.**~~ *Resolved by building it.* The worry was that
  "minimal" was undefined and that dependency editing would quietly consume weeks. What
  minimal turned out to mean is two screens: a list of plans, and a plan whose work is a
  table of rows, each opening one of four small forms. Ordering is asked from one end only —
  "must finish before…" — so there is no control for which way an arrow points and no way to
  draw one backwards by misreading a label. The trap it named is still open by design: the
  three-box estimate form is on screen and is obviously bad, and that is M5's to fix.
- ~~**No scale target has been set.**~~ *Fixed at 500 items per project in `m2-plan.md`*, which
  is what decides whether M2 needs pagination (no) and whether M3 can forecast synchronously
  (yes). M11's affordability is still open at that size.
