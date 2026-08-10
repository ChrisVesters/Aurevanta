# Aurevanta — Feature Roadmap

> **Status: proposal, as of 2026-08-06.** `product-concept.md` says *what* Aurevanta is
> and why; this document says *in what order we build it, and what has to be decided
> first*. Only M0 exists in code.
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
| A tenant can only ever have one user | No invitation flow exists. This blocks M1 and makes the product single-player. |
| An organisation name is unique across the whole installation | `uq_tenants_slug` makes the second "Acme Consulting" to arrive unregisterable. Company names are not unique in reality, and nothing about this product makes them so. Scheduled as M1a. |
| ~~Per-field validation errors carry English prose, not codes~~ | *Retired in M1 step 4:* each field error now carries the constraint that failed and its bounds, and the frontend translates that.  |
| No password reset, no email verification | Anyone can register with an address they do not control. |
| Landing page advertises unbuilt features | Fine while private; must not go public as-is. |

---

## M1 — Make it a team product

The smallest set that lets a second person join. Resist adding more here.

- **Invitations** — owner invites by email, invitee registers against an existing tenant
  via a signed, expiring token. Needs outbound email, which is new infrastructure.
- **Member management** — list, change role, remove. `UserRole` already has `OWNER`/`MEMBER`.
- **Password reset and email verification** — same email infrastructure; do them together.
- **Per-field error codes** — retire the field-name guessing from M0.

**Deliberately excluded:** SSO, granular permissions, org settings beyond a name. None of
them are needed to prove the core idea.

---

## M1a — Organisation names are not unique

*A correction slotted after M1, not a split of it. Lettered to avoid renumbering M2–M11.*

M0 gave `tenants.slug` a unique index and derived it from the organisation's name, which
quietly made **the name itself unique across the entire installation**. Register "Acme
Consulting" and every other Acme Consulting on earth is told the name is *unavailable* — as
though the first to arrive owns it. There are thousands of them, and nothing about this
product changes that. It is the one refusal in the system a user cannot act on: they cannot
choose a different name, because it is their name.

**The slug stops being an identity and becomes a convenience.** Two ways to go:

| | |
|---|---|
| **Recommended: keep the slug unique, disambiguate on collision** | `acme-consulting`, then `acme-consulting-2`. Readable URLs survive, and the name the user typed is never altered — only the handle derived from it. |
| Alternative: drop slugs, address organisations by id | Simpler and honest, but gives up readable URLs, which M10's shareable read-only link would want back. |

**What it touches**

- Drop the name pre-check in `RegistrationService` and retire `organisation_name_unavailable`
  from the problem codes and the frontend catalogue.
- `Slug.of` gains a uniqueness pass. A name of pure punctuation currently yields an empty
  slug and its own refusal (`organisation_name_unusable`); with a suffixing scheme that can
  fall back to a generated handle instead, retiring a second refusal a user cannot act on.
- `registration_conflict` narrows to email only, since the slug can no longer collide.

**The consequence worth deciding before building it**

Once two organisations can share a name, **a person who belongs to both sees the same word
twice** — in M1's organisation switcher (step 12) and in an invitation preview (step 10),
neither of which has anything else to show. Allowing duplicates is not finished until
something distinguishes them: the slug, the inviter's name, or a discriminator on the row.
That is a design question, not a migration.

**Why here rather than later.** Nothing routes by slug *yet*. The moment M2's plan-entry UI
puts one in a URL, changing the scheme starts breaking links people have bookmarked and
pasted to colleagues. The change is small today and gets steadily less so.

### Decisions required before building it

| Question | Recommendation |
|---|---|
| Suffix collisions, or abandon slugs? | **Suffix.** Readable handles are worth keeping and cost one retry loop. |
| Can an organisation be renamed at all? | **Not yet** — out of scope here. But decide it now, because "does the slug follow the name?" has no good answer once links exist. |
| What distinguishes two identically named organisations on screen? | **Unresolved, and it blocks steps 10 and 12 being *right* rather than merely working.** |

---

## M2 — The estimation schema

The first migration that carries real domain data. Getting this wrong is the most
expensive mistake available, because M3–M9 all read from it.

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
- **Forecast runs** — every forecast persisted with its inputs, its assumptions and its
  results. M10's sliding-date detector, and the movement decomposition in the icebox, are
  impossible without this history, and it cannot be reconstructed later.
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
| What happens to items with no estimate? | **Unresolved, and it needs an answer before M3.** Real plans are mostly unestimated, so "refuse to forecast" makes the tool useless on first contact. Imputing from a reference class is defensible but invents data. Whatever is chosen must be visible in the output — a forecast half-built from guesses must not look like one built from estimates. |
| Can a MEMBER edit estimates, or only an OWNER? | **Unresolved.** M1 ships roles but no domain permissions. Every endpoint from M2 onwards needs an answer, and retrofitting authorisation across a built API is painful. |

---

## M3 — The simulation engine ⭐ *the product*

Fit → sample → aggregate. Everything else is a view over this.

> **This milestone is oversized and should be split before work starts.** It now carries
> distribution fitting, sampling, graph scheduling, the team factor and scope uncertainty —
> too much to land or review in one piece. A sensible cut: **M3a** fitting, sampling and the
> graph scheduler with a fixed global capacity, which is enough to produce a real forecast;
> **M3b** the team factor and scope uncertainty, which are refinements of a working engine
> and carry their own open questions. Keeping them together risks the correlation modelling
> being rushed to get the milestone finished.

- **Distribution fitting** — log-normal from P10 and P90, with P50 as a consistency check.
  Surface the discrepancy when the three points are mutually inconsistent rather than
  silently picking one.
- **Monte Carlo rollup** — sample per item, then **schedule the graph** to get a project
  completion per run. Not a sum: a sum is the special case where every item is one long
  chain. See "the aggregator is a scheduler" below.
- **Shared team factor** — one factor sampled per run and applied across all items.
  Without it, good and bad luck cancel out and the band comes out implausibly tight.
- **Scope uncertainty** — a distribution over *how much unknown work appears*, sampled and
  multiplied through. `product-concept.md` argues this is usually the larger of the two
  uncertainty sources; omitting it is why other tools look precise and land wrong.
  **Open question the graph created:** when the model was a sum, unknown work was simply a
  multiplier. In a schedule, new work needs a *position* — does it attach to the critical
  path, spread across the graph, or append at the end? The choice materially changes the
  answer, and inflating the total is no longer a valid shortcut.

**Engineering notes.** Pure functions over primitives, no persistence — the most testable
code in the system, so property-based tests belong here. Decide early whether runs are
synchronous (simple, fine for hundreds of items) or queued (needed at thousands). Seed the
RNG so a forecast is reproducible and testable.

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

## M4 — A date you can commit to

Tier 1 complete. A single confidence control (50 / 80 / 95%) resolving to a calendar date.

Nobody asks for a distribution; they ask what date to promise. This also reframes the
negotiation: "can we go faster?" is answered with "we can commit at lower confidence,"
which is the honest trade rather than a capitulation.

*Note:* effort → calendar date needs at least a crude working-day assumption. Keep it
crude and **visible**, and replace it in M11 when real capacity arrives. An assumption
users cannot see is one they will mistake for a result.

---

## M5 — Elicitation that produces honest ranges

Not polish. The maths is easy; getting truthful ranges out of humans is the actual product.

- **Surprise framing** — "what number would make you genuinely surprised to have exceeded it?"
  People recognise surprise far better than they reason about tail probability.
- **Betting framing** — "would you take 9-to-1 odds this lands under X?" Makes
  overconfidence feel expensive.
- **Comparative framing** — "bigger or smaller than the auth migration in March?"
  Reference-class forecasting, and it improves as history accumulates.
- **Overconfidence warnings** — flag a P90 less than ~1.5× the P50; that pattern almost
  always means nobody thought about what could go wrong.

---

## M6 — Variance contribution

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

---

## M7 — Inverse queries

Run the question backwards: not "when will this finish" but "what do I cut to hit
1 November at 85% confidence?", ranking candidate scope removals by the confidence each
one buys.

This is what turns Aurevanta from a reporting surface into something opened *during*
planning.

**Revisit for the graph.** Cutting an item off the critical path buys no time at all, so
naive "rank by size" scope suggestions will be wrong. Candidate cuts have to be evaluated
by re-running the schedule without them. That is more expensive — and once M11 lands, the
answer space widens from "what do I cut?" to include "what if we add a person?", which uses
the same machinery.

---

## M8 — Actuals and calibration feedback

Record what happened, then measure the hit rate: of the items estimated, how many landed
inside their P10–P90 band? It should be 80%. Most teams score 30–50%.

Reporting that per estimator — and offering a correction factor — makes the tool improve
its users rather than merely serve them. Depends entirely on M2's immutable estimate
history.

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
  re-forecasts keep moving out rather than converging.
- **Merge bias, surfaced explicitly** — the graph makes this a number rather than a
  talking point. Where parallel branches must both finish, expected completion is later
  than either alone, and it compounds at every join. Simulation gets it free; spreadsheets
  get it wrong universally. Worth naming in the output rather than burying inside a total.
- **The critical path is probabilistic** — with uncertainty, no single path is *the*
  critical one. Report how often each path drives completion (criticality index); a path
  that is critical in 40% of runs is a different management problem from one that is
  critical in 99%.

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
  duration honestly, replacing M4's crude working-day assumption.
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

Unordered and uncommitted. Three of these are arguably mis-filed; see the note at the end.

### Getting work in

- **Import from an issue tracker** — Jira, Linear, GitHub Issues, Azure DevOps. One-way
  import with periodic refresh, not two-way sync, which is where these integrations usually
  drown. The same connection feeds M9's throughput history for free.
- **CSV import/export** — the unglamorous escape hatch that makes people willing to try the
  tool, and willing to trust that they can leave.
- **Plan templates** — project shapes a team repeats, with their historical actuals
  attached as a starting reference class.

### Getting forecasts out

- **Shareable read-only forecast link** — a URL a stakeholder opens without an account.
  M10 is entirely about reaching people who do not know what P90 means, and right now every
  one of them would need to log in.
- **Scheduled digest** — weekly email or Slack: "the 85% date moved out 6 days." Turns the
  sliding-date detector from something you must remember to check into something that finds
  you.
- **Snapshot export** — PNG or PDF of a forecast for slides. Unglamorous; it is how the
  numbers actually reach a steering committee.

### Modelling depth

- **Discrete risk register** — "the vendor API may slip: 30% likely, adds 10–20 days."
  These are Bernoulli events, not duration variance, and real projects fail on them. A few
  lines in the sampler.
- **Correlated estimates** — beyond M3's single global team factor: items sharing a
  component, a person, or an unknown technology fail together. A correlation group is a
  modest step up from one shared factor.
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
- **Estimate hygiene warnings** — flag estimates gone stale, ranges pasted identically
  across items, and clustering on 3/5/8. Extends M5's overconfidence check from single
  estimates to patterns across a plan.

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

### Speculative, flagged as such

- **LLM-assisted decomposition** — "break this epic into items." Plausible and dangerous:
  a machine-generated work breakdown carrying machine-generated estimates is exactly the
  confident garbage this product exists to prevent. If it is ever built, the output must be
  a draft a human estimates, never an estimate itself.

### Probably mis-filed

Three of the above are not really nice-to-haves:

| Idea | Why it may deserve a milestone |
|---|---|
| **Import from an issue tracker** | Closer to an adoption blocker. Nobody re-types a backlog to trial a tool, so this may gate real-world usage entirely. |
| **Shareable read-only link** | M10's whole purpose is reaching non-specialists; requiring them to hold an account undercuts it. Cheap to build, large audience unlocked. |
| **Discrete risk register** | A modelling gap rather than a feature. A forecast that models duration variance but ignores known discrete risks is systematically optimistic in a way the band does not show. |

---

## Cross-cutting, not a milestone

Threaded through the above rather than scheduled as a block.

- **Security hardening** — rate limiting on auth endpoints, token revocation, audit trail
  for tenant-scoped access. The JWT secret must be set explicitly in any real deployment.
- **Localisation** — infrastructure exists, English only. Adding a locale is a catalogue
  file *once* the backend sends codes instead of prose (M1).
- **Accessibility** — the auth forms set this bar; keep it as charts arrive, where it is
  much harder. A confidence cone needs a non-visual equivalent.
- **Operations** — CI, container build, migration strategy, health and metrics.
- **API documentation** — OpenAPI, once the domain endpoints exist and are stable.

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

M1a earns its place in that sequence only because it is cheap *now*: it is a small change
that becomes a link-breaking one the moment M2 puts a slug in a URL. It is not more
important than the engine — nothing is — it is just perishable.

### Blocked on a decision, not on engineering

M1 can start today. M2 cannot, until these are settled — and all five are cheaper to answer
now than to retrofit:

| | Recommendation |
|---|---|
| Unit of estimation | Task |
| Multi-estimator | Schema now, UI later |
| Effort or duration | Effort — load-bearing once M11 exists |
| Dependency types | Finish-to-start with lag |
| **Items with no estimate** | **Open.** Blocks M3 as much as M2. |
| **Can a MEMBER edit estimates?** | **Open.** Every M2 endpoint needs it. |

### Known thin spots

Honest about where this plan is weakest, rather than discovering it mid-build:

- **M3 is oversized** and should be split into M3a/M3b before anyone starts it.
- **Scope uncertainty has no agreed position in a graph** — the model that made it easy
  (a flat sum) is gone.
- **The plan-entry UI is barely scoped.** It is named in M2 and deliberately minimal, but
  "minimal" has not been defined, and dependency editing is the kind of interface that
  quietly consumes weeks.
- **No scale target has been set.** Whether a plan holds 50 items or 5,000 decides
  synchronous versus queued forecasting, and how affordable M11 is. Worth fixing a number
  before M3a rather than discovering it under load.
