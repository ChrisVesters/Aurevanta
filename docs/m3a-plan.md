# M3a — The simulation engine: implementation plan

> **Scope.** `roadmap.md` M3, split as that document asks: **this plan is M3a** — distribution
> fitting, sampling, the graph scheduler with one global capacity, forecast-run persistence,
> and the plainest possible way to ask for a forecast and read one. Explicitly excluded and
> carried to **M3b**: the shared team factor and scope uncertainty. Also excluded: calendar
> dates (M4), variance contribution (M6), inverse queries (M7), named resources (M11).
>
> **How to read this.** Decisions first, because this milestone is almost entirely decisions —
> the code is a few hundred lines of arithmetic and the risk is all in what that arithmetic
> models. Then seven steps, each a reviewable commit that leaves the build green: `./mvnw test`
> (format gate, 100% branch coverage) and, where the frontend is touched,
> `npm run lint && npm run build && npm run test`.
>
> **This is the product.** Every milestone before it existed to give this one something to
> chew on, and every milestone after it is a lens over its output. It is also the first
> milestone whose failure mode is not a bug: a wrong model produces a plausible number, and a
> plausible number is worse than an error, because nobody goes looking for it.
>
> **M3a's band is knowingly too tight, and decision 12 is about saying so.** Independence is a
> lie — `roadmap.md` measured a shared team factor moving the true P90 by 13 days on ten tasks
> — and M3a does not model it. Shipping a band this product knows to be narrow, without
> labelling it, would be the exact failure it exists to prevent.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | Fitting a range to a distribution ✅ *done* | — |
| 2 | Sampling one item ✅ *done* | 1 |
| 3 | Scheduling the graph ✅ *done* | — |
| 4 | The engine, end to end ✅ *done* | 2, 3 |
| 5 | Persisting a run, and asking for one | 4 |
| 6 | Reading a forecast | 5 |
| 7 | Close out | 1–6 |

**Steps 1–4 touch no database, no Spring and no HTTP.** They are the only classes in this
application that are pure functions over primitives, which is why they are four steps rather
than one: each is independently testable against arithmetic that can be checked by hand, and
that is the whole defence against a model that is confidently wrong.

**Steps 3 and 1 are independent**, so if two people work on this, that is the seam.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | One milestone or two | **Two. This plan is M3a**; the team factor and scope uncertainty are M3b, with a plan of their own. |
| 2 | Fitting | **Log-normal from P10 and P90**, with the stated P50 kept as a *signal* and never as an input. |
| 3 | Several estimators on one item | **Sample an estimator per item per run.** Disagreement becomes uncertainty rather than being averaged away. |
| 4 | Items with no estimate | **Kept in the graph as zero-effort nodes.** Dropping them would silently delete precedence. |
| 5 | Work already under way | **The estimate conditioned on what has already been spent** — a truncated draw, not a fresh one. |
| 6 | Capacity | **A required input with no default**, because it changes the answer and every default is a hidden claim. |
| 7 | Which ready item starts first | **Most work waiting behind it**, computed statically, named on the run, tie-broken by write order. |
| 8 | How many runs, and where | **10,000 by default, seeded, synchronous**, against a measured wall-clock budget. |
| 9 | What a run stores | **Its resolved inputs, its seed and its engine version** — so any run can be re-run exactly. |
| 10 | Numerics | **No new dependency.** Φ and Φ⁻¹ are two functions, tested against published values. |
| 11 | A plan with nothing estimated | **Refused** (`nothing_to_forecast`), because a forecast covering nothing is not a forecast. |
| 12 | Saying what the model leaves out | **The run carries its own limitations**, and the screen states them beside the number. |

### Decision 1 — M3a and M3b, and where the line falls

`roadmap.md` says M3 is oversized and should be split before work starts, and names the cut:
M3a is fitting, sampling and the graph scheduler with a fixed global capacity, which is enough
to produce a real forecast; M3b is the team factor and scope uncertainty. **This plan takes
that cut unchanged**, and the reason to write it down again is that the line is not where it
first looks.

**The cut is to the build, not to the planning**, and this document first said otherwise. It
argued that M3b should be planned later, against an engine that exists — which sounds prudent
and is backwards: the roadmap's worry was that keeping them together "risks the correlation
modelling being rushed to get the milestone finished", and a plan written in advance is the
defence against that rather than a cause of it. M3b also carries the one genuinely open
modelling question in this milestone — where new work attaches in a graph — and a question left
open until the week it blocks somebody is a question answered under time pressure.
`m3b-plan.md` settles it now.

The tempting line is "the engine, then the refinements". That is wrong: the team factor is not
a refinement of a working engine, it is the difference between a band that is right and a band
that is 6% too narrow on the roadmap's own measurement. The line that is actually defensible
is **what can be verified against a whole-plan closed form**. Everything in M3a has one — a sum
of independent log-normals has an exact mean and variance, and a schedule with capacity 1 is
that sum. Once a shared factor is sampled across items, that closed form returns the wrong
answer *by construction* (`roadmap.md` measures it returning 214.0 against a true 222.2), so it
stops being available as a check on the total.

**M3b is not left without oracles, though, and saying it was would be too strong.** It has
three narrower ones — exactness for a single item under a factor, the roadmap's own published
209.4 → 222.2, and, best of all, byte-identical equivalence with this milestone once its two
parameters are zero. `m3b-plan.md` decision 8 carries them. The honest statement of the line is
therefore: **M3a is verified whole and M3b is verified in pieces**, so M3a goes first because it
is what the pieces get bolted to and what the equivalence test measures against.

**What M3a therefore is not:** a band anybody should commit to. See decision 12.

### Decision 2 — Log-normal from the two ends, with the middle as evidence

Two parameters from two points, as `product-concept.md` prefers:

```
sigma = (ln P90 - ln P10) / (2 * 1.2815515655446004)
mu    = (ln P10 + ln P90) / 2
```

so that the implied median is `exp(mu)`, which is `sqrt(P10 * P90)`. Durations are positive and
right-skewed, and a log-normal does not pretend a worst case exists — which is the property
that matters, because a bounded fit is a claim nobody made.

**The stated P50 is never an input**, and this is the decision inside the decision. Three points
over-determine two parameters, so something has to give, and the alternatives are to fit all
three by least squares or to honour all three with a PERT-beta. Both bury the disagreement in
the fit. Keeping the two ends and *reporting* the discrepancy — `statedP50 / impliedP50` —
turns it into what it actually is: a signal that the three numbers are not mutually consistent,
which is exactly what `product-concept.md` asks for and what M5 will want to act on.

The tails are also where the information is. P10 and P90 are what elicitation works hardest to
get honest; the P50 is the number people answer fastest and think about least.

**Two edge cases the schema permits and this must survive.** `P10 == P90` is accepted by M2
(`acceptsThreeNumbersThatAreAllTheSame`), which makes `sigma` zero: the fit is a point mass and
every draw returns `exp(mu)`. That is not a degenerate case to refuse, it is somebody saying
they are certain, and the sampler has to return the same number rather than divide by zero.
And every value is strictly positive, guaranteed by `@Positive`, so `ln` is always defined —
which is why the estimate validation is worth reading before touching this.

### Decision 3 — Disagreement becomes uncertainty, not an average

M2 stores one current estimate per estimator per item and made a point of it: "two people who
disagree about a task are telling M3 something, and it is M3 that decides what to do about it."
This is that decision.

**Each run picks one of the item's current estimates, uniformly, and samples from it.** Over
ten thousand runs that is a mixture distribution whose spread contains both the uncertainty
each estimator stated *and* the distance between them. Two people saying 5–10 and 50–100
produce a wide, honestly bimodal band, which is the truth: nobody knows which of them is right,
and that not-knowing is uncertainty of exactly the kind this product exists to carry.

| Rejected | Why |
|---|---|
| Average the fitted `mu` and `sigma` | Produces a tight band around the geometric middle of two people who do not agree. It converts disagreement into false confidence, which is the failure mode named on the first page of `product-concept.md`. |
| Take the most recent, whoever wrote it | Throws away the reason the schema keeps several. It also makes the forecast depend on who happened to estimate last. |
| Widen to the envelope (min P10, max P90) | Invents a distribution nobody stated, and a single outlier estimate silently becomes the whole band. |

**The estimator is drawn per item, independently.** The alternative — one estimator chosen per
*run*, so a run is "the plan as Alice sees it" — is more honest about a real effect, since
somebody optimistic is usually optimistic about everything. It is also unbuildable here,
because no two items are estimated by the same set of people, so most runs would have to fall
back to a per-item draw anyway. Correlated estimator bias is a common cause, and common causes
are M3b's subject; this is recorded there rather than solved here.

### Decision 4 — An unestimated item stays in the graph, weighing nothing

M2 decided that a forecast covers what is estimated and reports its coverage. **What nobody
noticed until this plan is that "leave it out" has two meanings, and one of them is wrong.**

Take `A → B → C`, where B has no estimate. Drop B and its edges, and A and C are no longer
related: the scheduler runs them in parallel, and the forecast is shorter than any structural
reading of the plan supports. The item's *effort* was unknown; its *position* was not, and
position is what `roadmap.md` measured as moving the answer from 51 to 86 days.

So an unestimated item stays a node, keeps both its edges and both lags, and contributes zero
effort. Precedence survives, the lag survives, and the only thing missing is the thing that
was actually missing. It occupies no capacity, because it takes no time.

**This is still optimistic and that is the point of reporting coverage.** A run says how many
items it counted the effort of, out of how many it scheduled, and the screen says it in words.
Imputing a distribution for B is defensible and is what a mature version of this does; it
invents data and needs a per-run record of what was invented, which is M2's decision 5 and
stays answered the same way.

### Decision 5 — Work under way is the estimate, conditioned on what it has already cost

An item that is `DONE` contributes nothing: its work is not ahead of the forecast. An item that
is `NOT_STARTED` contributes a full draw. `IN_PROGRESS` is the one that needs deciding, and the
obvious answers are both bad: a full draw ignores the work already done, and "estimate minus
actual, floored at zero" invents a floor and biases everything that crosses it.

**The honest model is the conditional distribution.** If a task was estimated as X and `a`
hours have demonstrably already gone into it, then what is known is that X > a, and the
remaining work is `(X | X > a) - a`. For a log-normal that is one line of inverse-CDF sampling:

```
p = Phi((ln a - mu) / sigma)        // the mass already ruled out
u ~ Uniform(p, 1)
remaining = exp(mu + sigma * PhiInv(u)) - a
```

This has a property worth stating out loud, because it will look like a bug the first time
somebody sees it: **the longer a task has already run, the more work it has left**. That is
what a right-skewed distribution means, it is what actually happens on real projects, and a
model that did not do it would be flattering.

**Where no effort was recorded, the condition is empty and it is a full draw.** `actual_effort_hours`
is optional and usually absent, so this is the common path; the truncation is the refinement
available to teams that do record it. Two numerical hazards go with it, and both belong in the
tests: `sigma == 0` has no CDF to invert (return `max(exp(mu) - a, 0)`), and an `a` far beyond
the P90 pushes `p` against 1, where `PhiInv` loses all precision — so `p` is clamped just below
1 and the draw lands in the extreme tail, which is the right answer for a task that has already
outrun its own worst case.

**An in-progress item is ready at time zero regardless of its predecessors.** It has visibly
started, so a plan that says it could not have is wrong about reality, and reality wins. If
more items are in progress than the stated capacity, they all run: the same rule, applied to
the same disagreement.

### Decision 6 — Capacity is required, and every default would be a lie

`roadmap.md` is unusually direct here: a dependency graph without a capacity model assumes
*unlimited* parallelism and is "optimistic by the same margin that summing is pessimistic". The
same ten items came out at 51.1 or 86.3 at the P90 depending only on how many people were
available. A number that moves the answer by 70% is not an implementation detail.

**So `capacity` is a required field on the request, with no default anywhere.** It is the
maximum number of items that may be in flight at once. This is M1a's argument about the
organisation handle, reaching a second place: *a refusal is only ever raised against something
somebody chose*, and its twin is that an assumption is only honest when somebody made it. A
server that picked would leave the caller holding a consequence they never chose, and there is
no value it could pick that would not be a claim about a team it knows nothing about.

The frontend explains what the number means and does **not** pre-fill it — for the reason
`useProposedSlug` gets its own paragraph in `CLAUDE.md`: a default that costs one keystroke to
accept is a default people accept unread, and this is not a field to accept unread.

Named resources, allocations and part-time availability are M11. One integer is the crude
version of all of it, and it is crude *visibly*, which is the property M4's working-day
assumption is also asked to have.

### Decision 7 — The priority rule is a modelling assumption, so it gets a name

When more items are ready than there are slots, something decides which starts. `roadmap.md`
says this plainly for M11 and it is already true here: two defensible rules produce two
different forecasts from identical data, so the rule cannot be an implementation detail.

**The rule is: start the item with the most work waiting behind it** — the total estimated
effort of everything reachable downstream — **ties broken by the order the items were written
down.** It is computed once per forecast rather than per run, from the *stated* P50s rather
than from each run's draw, which makes it stable across runs and explicable in a sentence: the
plan works on whatever unblocks the most.

Computing it from each run's sampled durations would be marginally better scheduling and much
worse as a model: the rule would then vary run to run, and "why did it schedule that first" would
have no answer. A stable rule is also what makes two forecasts of the same plan comparable,
which M10's sliding-date detector depends on.

The rule's name is stored on every run (`priority_rule`), so a run made under a different rule
is never silently compared with one made under this one.

### Decision 8 — Ten thousand runs, seeded, inside the request

`roadmap.md` measured sampling error at ±0.77% at 10k and ±0.20% at 100k, against closed-form
errors of 2.2% to 5.4% on the cases where the closed form is weakest. **10,000 is an order of
magnitude better than the alternative this project rejected, at a tenth of the cost**, and it
keeps a forecast inside a request, which M2's 500-item ceiling was fixed in order to allow.

- **Synchronous**, and the ceiling that makes it safe is arithmetic rather than hope: 500 items
  × 10,000 runs is five million sampled durations and five million scheduling events. Step 4
  carries a **measured budget** — a 500-item plan forecasts in under two seconds — asserted by a
  test rather than assumed. If that gate fails, the lever is parallelising runs across cores
  with a splittable generator seeded per run, which keeps reproducibility; queuing is the
  answer only if that is not enough, and it is not this milestone's problem to pre-solve.
- **`sampleCount` may be raised by the caller and is bounded server-side.** A forecast is the
  first endpoint in this application whose cost is CPU rather than a query, and any member may
  ask for one. The bound is what stops that being a way to pin the server from inside; a
  per-tenant concurrency limit is the next thing if it ever is, and is recorded rather than
  built.
- **The seed is drawn per run and stored**, which is what makes a forecast an object that can be
  argued with rather than a number that appeared. See decision 9.

### Decision 9 — A run stores enough to be re-run exactly

M2's decision 8 handed this milestone one obligation in as many words: **persist every forecast
run from the first commit**, not once the engine works. This is the shape of it.

A run stores three things beyond its outputs:

- **Its resolved inputs, as a snapshot.** The live rows will not do. Items get reworded, edges
  get rubbed out, and progress changes daily — so a run that referenced them would describe
  something that no longer exists within a week. M10's movement decomposition ("out 8 days: +5
  new scope, +4 re-estimates, −1 progress") is a *diff of two snapshots*, and it is impossible
  against rows that moved.
- **Its seed.**
- **Its engine version**, an integer bumped by hand whenever the model changes.

Together those mean any stored run can be re-run and will produce its stored numbers exactly.
That is worth more than it first appears: it means **anything M3a did not store can be
recomputed later without being wrong**. M6 needs per-item durations correlated against project
outcome across runs; it does not need M3a to have hoarded ten thousand vectors, because it can
replay the run that produced them. The seed is the compression.

**Stored as JSONB rather than as child tables.** A run's inputs are a *value* — nobody queries
into them, they are read whole by whatever replays or diffs them — and normalising them would
create a second schema to migrate in lockstep with the first, for a table nothing joins to. The
headline percentiles get real columns beside it, because M10's sliding-date detector compares
successive runs and that is a query.

**A run is never updated and never deleted**, like an estimate, and for the same reason: it is
the record of what was said on a date. The engine version is what makes an old one still
readable rather than quietly reinterpreted.

### Decision 10 — Two functions are not a dependency

The engine needs the standard normal CDF and its inverse: Φ to find how much probability an
in-progress item has already used up, and Φ⁻¹ to invert it. Ordinary sampling needs neither —
`RandomGenerator.nextGaussian()` is in the JDK.

**Both are written here, roughly sixty lines, tested against published quantiles.** The
alternative is Apache Commons Math, which is well-tested and would also hand over log-normal
moments for free. It is turned down for two reasons: the surface actually needed is two
functions, and `commons-math3` has been in maintenance with `math4` unreleased for years, which
is not a thing to put underneath the one part of this application that must still be trustworthy
in five years.

The check that makes this safe is that both functions have exact known values to test against —
Φ(0) = 0.5, Φ⁻¹(0.9) = 1.2815515655446004, Φ(Φ⁻¹(p)) = p across the range — so "did we get the
numerics right" is answerable rather than a matter of trust. The same constant appears in
decision 2's fit, which is a pleasant accident: getting it wrong breaks both, loudly.

### Decision 11 — A forecast of nothing is refused

A plan where no item carries an estimate produces a schedule of zero-effort nodes finishing
instantly. That is arithmetically correct and useless, and returning it would be this
application stating a completion time it has no evidence for.

`nothing_to_forecast`, `422`, raised when the coverage count is zero. It is actionable in the
way this codebase asks refusals to be: the remedy is to estimate something, and the caller is
looking at the plan already.

**A partially estimated plan is not refused**, at any coverage above zero — M2's decision 5
settled that, and re-litigating it here as a threshold ("refuse below 50%") would be inventing a
number to hide behind.

### Decision 12 — The run carries what the model does not do

This milestone ships a band that is too tight, and it knows by how much: `roadmap.md` measured
a shared team factor moving a true P90 from 209.4 to 222.2 on ten wide tasks, and scope
uncertainty is described there as usually the larger of the two.

A tool that reports a P90 without saying that is doing the precise thing this product was
written to replace. **So every forecast response carries its own limitations as data** — a list
of machine-readable keys (`no_team_factor`, `no_scope_uncertainty`, `unestimated_items`,
`inconsistent_estimates`), translated by the frontend like every other code — and the screen
prints them beside the number rather than behind a link.

The first two are properties of the engine version and are always present in M3a; **M3b
removes them by building what they name**, which is the cleanest possible definition of when
M3b is done. The other two are properties of the plan being forecast.

This is the decision most likely to be trimmed as "we'll add the caveat later", and it is the
one thing here that cannot go in later: a number that has been seen without its caveat has
already been written into a slide.

---

## Step 1 — Fitting a range to a distribution ✅ *done*

**Goal.** Three numbers a person typed become two parameters, and the disagreement between them
becomes visible.

- New package `forecast.model`, holding **no Spring, no JPA and no I/O** — the only such package
  in the application, which is the reason it is separated by purity rather than by feature.
- `Normal` — Φ and Φ⁻¹ (decision 10), plus the P10 z-score as a named constant.
- `LogNormalFit.from(p10, p90)` → `mu`, `sigma`; `median()`, `mean()`, `variance()`.
- `LogNormalFit.consistency(statedP50)` → the ratio of stated to implied median. One number,
  reported rather than acted on: 1.0 is agreement, and the further either way, the more the
  three points argue with each other.
- Handles `sigma == 0` without dividing by anything.

**Tests.** Φ and Φ⁻¹ against published values, and `Φ(Φ⁻¹(p)) == p` across the range. A fit
recovers the P10 and P90 it was given, to floating-point tolerance. `mean` and `variance` match
the analytic formulae. Three equal numbers fit a point mass. A stated P50 above and below the
implied one produce ratios either side of 1. Extreme but legal inputs — 0.01 hours, 10^10 hours
— stay finite.

**Done when** a range has a distribution and nothing has been assumed about the middle.

### As built — where it differs from the above

- **`quantile` is deliberately *not* refined against `cdf`, and that is a decision this step
  discovered rather than inherited.** One Halley step using `cdf` would take Acklam's part in a
  billion down to machine precision, and it is the obvious thing to write. It also destroys the
  test above: `cdf(quantile(p)) == p` would then be true *by construction*, so the assertion
  that reads as the strongest evidence in the file would be evidence of nothing. What is there
  instead is two rational approximations, derived by different people from different forms,
  neither knowing the other exists, agreeing to a part in a billion. **The accuracy was the
  cheaper thing to give up**, since it is used to place a draw inside a distribution somebody
  described to two significant figures.
- **`LogNormalFit.from` refuses, and the plan did not ask it to.** `0 < p10 <= p90` is already
  guaranteed by `@Positive` and `estimate_out_of_order`, so neither refusal is reachable through
  the API — but this is a pure function that a test calls directly, so the branch is *coverable*,
  which is exactly the distinction `ProjectService.lockForGraphChange` makes in the other
  direction. The alternative is worse than an unused branch: `Math.log` of a negative is a silent
  `NaN` that would surface as a percentile several steps later with nothing pointing back here.
- **Hart's CDF is accurate absolutely, not relatively, and step 2 needs to know it.** It holds
  about one part in 10^15 of the *whole distribution*, which in the deep tail means roughly one
  part in 10^9 of the answer — measured at 8σ, not assumed. This costs decision 5 nothing,
  because a truncated draw asks for the mass a task has already spent, which is a number near
  *one* where absolute accuracy is exactly what matters. It would cost a great deal to anybody
  comparing two very small probabilities, so it is stated in the test rather than left to be
  rediscovered.
- **The constant is `P90_Z` and positive**, where the bullets say "the P10 z-score". Same number,
  and the sign belongs at the call site: the fit divides by `2 * P90_Z`, and writing it negative
  would put a minus in every formula that reads it.
- **Three test tolerances had to become relative**, which is worth a line because the first
  version of each was absolute and passed for the wrong reason. Recovering an end runs back
  through `quantile`, whose error is relative, so a fixed tolerance is one that passes at eight
  hours and fails at eight thousand — and `numeric(12, 2)` reaches ten billion.
- **`LogNormalFit` grew no `quantile` method**, though step 2's truncated draw will want one.
  Nothing in this step needed it, and the test computes the percentile itself in one line;
  adding it now would have been API written against a caller that does not exist yet.

---

## Step 2 — Sampling one item ✅ *done*

**Goal.** One draw of how long a piece of work takes, given everything known about it.

- `ItemModel` — an item's identifier, its list of fitted estimates (one per estimator), its
  status, and the effort already spent. Built from primitives; nothing in this package knows
  what a `WorkItem` is.
- `ItemModel.sample(RandomGenerator)`:
  - `DONE` → zero, always.
  - no estimates → zero, always (decision 4). It is still a node; it just weighs nothing.
  - otherwise pick one estimate uniformly (decision 3), then draw from it.
  - `IN_PROGRESS` with a recorded effort → the truncated draw of decision 5, minus what is
    spent, never below zero.
- The clamp guarding `PhiInv` near 1, and the `sigma == 0` path through truncation.

**Tests.** A `DONE` item draws zero however it was estimated. An unestimated item draws zero. A
single estimator's draws converge to the analytic mean and variance (this is the oracle
arriving early — it is exact for a log-normal, so the tolerance is sampling error alone). Two
estimators far apart produce a mixture whose mean is between theirs and whose variance exceeds
both — the property that decision 3 exists to produce, asserted rather than described. A
truncated draw never returns less than zero and always exceeds `a - a`. An item that has
already spent more than its P90 still returns a finite, positive remainder. Two runs from the
same seed produce identical sequences.

**Done when** the two decisions this product's honesty rests on — disagreement, and work
already done — are arithmetic rather than intention.

### As built — where it differs from the above

- **`WorkItemStatus` is reused rather than mirrored, against this step's own bullet.** "Nothing
  in this package knows what a `WorkItem` is" is right about the entity, its repository and its
  service, and wrong about the enum: it has *no imports at all*, and its own documentation
  already says "what a forecast needs is only whether an item is still ahead of it, and these
  three answer that" — it was written anticipating this reader. A local copy would have been
  three constants restated plus a mapping function, and that mapping would have been a `switch`
  over an enum, which `WorkItemService` explains at length is a coverage trap. `forecast`
  depending on `item` is already the arrow step 5 draws. **`forecast.model` still has no Spring,
  no JPA and no I/O**, which is the property the bullet was protecting.
- **The truncated draw samples the surviving tail directly rather than inverting a probability
  near one.** Decision 5 writes it as `p = Φ((ln a − mu)/sigma)`, then `u ~ Uniform(p, 1)`. That
  is correct arithmetic and poor floating point: `p` is a number near 1, which is the half of
  the range a double holds worst, and the API can reach `p == 1.0` exactly — an item put at 19
  to 21 hours with six minutes logged — where `quantile` has no answer. What is built computes
  the *upper* tail, `Φ((mu − ln a)/sigma)`, which `cdf` returns accurately, and draws uniformly
  within it. Same distribution, and the same reason `Normal.cdf` computes the smaller tail and
  subtracts only when it must.
- **A comprehensively outrun estimate returns nothing, and this needed deciding.** Nineteen to
  twenty-one hours with a hundred spent puts the conditioning point past 37 standard deviations,
  where the distribution holds no probability at all. There is no honest draw to make, so the
  remainder is zero — the model reporting that it has been falsified rather than forecasting.
  The remedy is a revision, which M2 makes a new row. It is reachable through the API, so it is
  a branch with a test rather than a hypothetical.
- **`LogNormalFit` grew the `at(z)` that step 1 declined to write.** Step 1 recorded leaving it
  out because no caller existed; two callers now do — an ordinary draw passes a Gaussian, a
  conditioned one passes a point from the surviving tail — and having both go through one
  method is what stops them drifting about what the distribution is. `median()` became `at(0)`.
- **The conditional draw got an oracle the plan did not ask for, and it is the strongest test in
  the step.** `E[X | X > a]` has a closed form, so the sampler's average over two hundred
  thousand draws is checked against an answer worked out rather than sampled — they agree to
  about one part in ten thousand. The plan only asked that the remainder be non-negative and
  finite, which would have passed for a sampler that was merely *plausible*.
- **"The longer it has run, the more it has left" is not monotonic, and the plan says it as
  though it were.** Measured on an 8–40 hour estimate the remainder goes 17.2 hours at five
  spent, 14.2 at twenty, 17.1 at fifty, 34.0 at two hundred: it falls while the bulk of the
  distribution is being used up and rises once only the tail is left. The claim survives in the
  form that matters — work that has run long enough has more ahead of it than work that has
  not — and the test asserts that rather than the overstatement.

---

## Step 3 — Scheduling the graph ✅ *done*

**Goal.** Durations become a completion time, and the number of people is finally in the model.

- `Schedule.finish(durations, edges, capacity, priority)` → the moment the last item finishes.
  Pure, deterministic, and takes no random generator: given the same durations it gives the same
  answer, which is what makes it testable without statistics.
- Event-driven: an item becomes *ready* when every predecessor has finished and that
  predecessor's lag has elapsed; ready items start in priority order as slots free; an item once
  started runs to completion (**non-preemptive**, which is an assumption and is named as one).
- A lag is a wait rather than work, so it occupies **no capacity** — an item can be counting down
  a lag while every slot is busy.
- `IN_PROGRESS` items are ready at time zero whatever their predecessors say, and start
  regardless of capacity (decision 5).
- The priority key of decision 7 — total downstream effort, then write order — computed once, on
  the graph rather than on a draw.
- **A topological pass runs once, before any sampling, and refuses a cyclic graph.** M2
  guarantees acyclicity under a lock, so no request can reach this branch — but this function
  takes primitives, so a test can hand it a cycle directly, and it is coverable exactly because
  it is pure. That is the difference from `ProjectService.lockForGraphChange`, which was written
  to refuse and had the refusal removed for being unreachable.

**Tests.** A chain at capacity 1 finishes at the sum of its durations; the same chain at
capacity 100 finishes at the same time, because a chain has no parallelism to find. Ten
independent items at capacity 10 finish at the maximum, at capacity 1 at the sum, and at
capacity 3 at something between — the whole of `roadmap.md`'s 51-versus-86 table, as a unit
test. A lag delays a successor exactly, and does not consume a slot. A diamond finishes at the
longer branch. An in-progress item starts at zero despite an unfinished predecessor. Two items
tied on priority start in write order, twice, from a shuffled input. A cycle is refused.

**Done when** the aggregator is a scheduler, and the capacity assumption is a number somebody
can see.

### As built — where it differs from the above

- **`Schedule` is prepared once and run many times, not the static function the bullet
  describes.** `finish(durations, edges, capacity, priority)` and "computed once, on the graph
  rather than on a draw" are two bullets that contradict each other: a static function taking
  the edges would redo a graph walk and a transitive closure ten thousand times. Worse than the
  cost, it would let the priority rule be recomputed per run, which is exactly what decision 7
  exists to stop. `Schedule.of(edges, typicalEfforts, underWay, capacity)` does the graph work,
  and `finish(durations)` is the per-run half. It is **immutable, so runs may be parallelised**
  — which is step 4's stated lever if the budget is missed.
- **The engine works in array positions, not identifiers.** `Precedence` names both ends as
  `int`, and mapping `UUID`s to positions happens once, outside this package. Walking a
  five-hundred-item graph ten thousand times through a hash map would cost more than the
  scheduling.
- **The priority key needs a transitive closure, and that ties it to the 500-item ceiling.**
  "Total downstream effort" is the sum over everything *reachable*, which a diamond would
  otherwise double-count, so it is a bitset closure — 500 × 500 bits, which is nothing at that
  size and quadratic beyond it. **The standard scheduling heuristic is the longest chain behind
  an item rather than the total work**, it is O(n + m) with no closure, and it is a better
  scheduler; it is not a better *sentence*, and decision 7 chose the rule a person can repeat
  back. Worth revisiting at M11, where the priority rule stops being a tie-breaker and starts
  being the model.
- **Work already under way had to be excluded from being released a second time**, which the
  bullets do not mention and which is a real bug rather than a subtlety: an in-progress item
  with an unfinished predecessor starts at zero, and then its predecessor finishing would have
  marked it startable again and run it twice. The plan's own test — "an in-progress item starts
  at zero despite an unfinished predecessor" — is what catches it.
- **The heap is written out rather than taken from `java.util`.** `PriorityQueue<Integer>` boxes
  every one of the several million operations a forecast performs, which was measured against
  step 4's two-second budget and is roughly where it would be spent. The priority selection
  needs no heap at all: the order is static, so it is a `BitSet` over positions and "highest
  priority available" is "lowest set bit".
- **Nothing validates that a duration is not negative**, deliberately, and it is the one check
  a reader might expect. `ItemModel.sample` cannot return one, and the alternative is five
  million comparisons per forecast to catch a bug that has no way in.
- **One expectation in the tests was wrong before the code was**, which is worth recording
  because it is the failure mode this whole milestone is about: ten items at capacity four was
  written down as finishing at 15 because it looked right, and it finishes at 19. The scheduler
  was correct and the plausible number was mine.

---

## Step 4 — The engine, end to end ✅ *done*

**Goal.** Ten thousand runs, one distribution, and an answer to "how do we know this is right".

- `Engine.run(items, edges, capacity, sampleCount, seed)` → percentiles, mean, and a histogram.
  Still pure, still no Spring.
- Percentiles by sorting the completed runs and indexing — no interpolation cleverness, since
  10,000 samples put the P90 between two adjacent order statistics that differ by less than the
  sampling error anyway.
- Reported: P10, P50, P80, P90, P95, and the mean. **P80 because M4's confidence control is
  50/80/95** and a percentile that is not stored is a re-run to answer.
- A fixed-bucket histogram, so M10 can draw a curve without replaying.
- One `RandomGenerator` seeded once, consumed in run order. Deterministic by construction.

**Tests — and this step is where the roadmap's oracle is cashed in.**

- **The closed form as oracle.** A chain at capacity 1 is a sum of independent log-normals, so
  its true mean is the sum of the means and its true variance the sum of the variances —
  *exactly*, for any shapes. Assert the engine converges to both within sampling tolerance.
  This is the test that answers "how do we know the simulator is right", and it is worth its own
  name in the file.
- **The percentile check on the case the closed form gets right.** `roadmap.md` measured 40
  tight tasks (18–22d) at a true P90 of 811.1 with the normal approximation exact to 0.0%.
  Reproduce it and assert agreement — a case where two independent methods must meet.
- **Reproducibility.** The same inputs and seed produce byte-identical output, twice, and a
  different seed does not.
- **Convergence.** The spread across ten different seeds at 10,000 runs sits inside the ±0.77%
  the roadmap measured. This is the test that will fail if the sampler is subtly biased.
- **The budget.** 500 items, 10,000 runs, under two seconds. Tagged so it can be excluded from a
  laptop run if it proves flaky on shared CI, but *present*: decision 8's synchronous answer is
  only true while this holds, and nothing else would notice it stopping being true.

**Done when** there is a forecast, and a reason to believe it.

### As built — where it differs from the above

- **The oracle agreed to five thousandths of a percent, and that is the headline.** Forty tight
  tasks come out at **811.08** sampled, **811.12** from the closed form computed in the test,
  and **811.1** from the measurement `roadmap.md` took before any of this existed. Three routes
  to one number, none of them sharing a line of code. The assertions are deliberately set far
  looser than the agreement — at sampling error, about a third of a percent — so they stay
  tests of the engine rather than detectors of a changed seed.
- **The budget is not close: about 300ms** for five hundred items and ten thousand runs,
  against the two seconds decision 8 allowed. Six times over is comfortable enough that
  parallelising runs stays an unused lever, and the assertion is left at two seconds rather
  than tightened, because a tight wall-clock assertion fails on a busy machine and teaches
  nobody anything. What it guards is the order of magnitude.
- **`Forecast` carries a standard deviation, which the bullets do not list.** Without it the
  oracle above is only half checkable: the plan asks the test to assert convergence to the mean
  *and the variance*, and there was nothing to assert the variance against. M6 will want it
  too.
- **`java.util.Random`, and this is a decision rather than a default.** Decision 9 promises a
  stored run can be replayed from its seed years later, which is worth nothing if the numbers
  move underneath it. `Random` is the only generator in the JDK whose algorithms are written
  into its *contract* rather than only its implementation; `SplittableRandom` is faster and
  gets its Gaussian from a default method nothing promises to keep. A JDK upgrade silently
  changing old forecasts would be worse than any version bump, because the version would not
  have changed.
- **`Engine.VERSION` and the sample-count bound live here, not in step 5.** The version is a
  property of the model rather than of the table it gets stored in, and the bound is about what
  the engine can afford. `MAX_SAMPLE_COUNT` is a hundred thousand, which is a bound on
  absurdity rather than a promise of speed — what actually stops one member tying up a server
  is a limit on concurrent forecasts, and that is still not built.
- **The histogram's counts are a `List`, not an `int[]`.** A record with an array component
  compares identities rather than contents, which would have quietly broken
  `theSameSeedForecastsTheSamePlanIdentically` — the single test decision 9 rests on — into
  something that passes for the wrong reason.
- **Step 5 inherits a question this step surfaced.** The engine refuses an edge naming an item
  it was not given, which is right; but M2 lets an arrow point at *archived* work, and a
  forecast only loads what is live. So step 5 has to decide what an edge into archived work
  means, and say so rather than dropping it silently — which is the mistake M2's own step 4
  had to correct.
- **The one gap the coverage gate found was real.** No test drove the engine with work already
  under way: the sampler tested its half and the scheduler tested its half, and nothing
  exercised the wire between them. Decision 5 is one of this milestone's headline decisions, so
  that is the sort of hole worth having a gate for.

---

## Step 5 — Persisting a run, and asking for one

**Goal.** The engine is reachable, and every answer it has ever given is still readable.

- New package `forecast` beside `forecast.model`, holding the entity, repository, service,
  controller and responses — by feature, like everything else.
- `V12__forecast_runs.sql` (below). `forecast_runs` is written once and never updated, like
  `estimates`.
- `ForecastService.run(caller, tenant, projectId, capacity, sampleCount)`:
  - `requireMember` first, as everywhere — **any member may forecast** (M2 decision 6).
  - loads items, current estimates and edges through `ProjectService`, `WorkItemService`,
    `EstimateService` and `DependencyService` rather than reaching into their tables, so the
    dependency arrows keep pointing one way; `forecast` depends on all four and nothing depends
    on it.
  - refuses `nothing_to_forecast` (decision 11).
  - builds the snapshot, draws a seed, calls the engine, saves the run.
- `POST /api/projects/{projectId}/forecasts` `{capacity, sampleCount?}` → `201`.
  `GET /api/projects/{projectId}/forecasts` → newest first. `GET /api/forecasts/{runId}`.
  Addressed the way M2 addresses items: created and listed within a plan, read on its own.
- New codes in `problem`, and `capacity` / `sampleCount` bounds in `ApiExceptionHandler.CONSTRAINT_CODES`
  if they need any that is not already there.
- The response carries the limitations of decision 12 as codes.

**Tests.** A member forecasts a plan and the run is stored with its seed, its engine version and
its inputs. **Re-running a stored run's snapshot through the engine reproduces its stored
percentiles exactly** — the test that decision 9 exists for, and the one that fails the day
somebody changes the model without bumping the version. A plan with nothing estimated is
refused. A plan in another organisation is a 404, and so is a run. Coverage on the run matches
the project's own counts. A capacity of zero or a negative is refused; a sample count above the
bound is refused. An archived project still forecasts, for the same reason it still accepts
work. The endpoints refuse an identity token — the assertion `EstimateApiTests` was found to be
missing, so it is written here rather than inherited.

**Done when** a forecast is a record rather than an event, and M10's history has started
accumulating on its first day.

---

## Step 6 — Reading a forecast

**Goal.** Somebody can ask, and can see what they were and were not told.

- A forecast panel on the project page, below the work: capacity (required, not pre-filled — see
  decision 6), an optional sample count behind a disclosure, and a button.
- The result in hours: the band as a sentence, the percentiles as a short table, coverage in
  words the way the plan page already says it.
- **The limitations printed beside the number, not below the fold** — the four codes of
  decision 12, translated in `problems.ts` like every other code, never as server prose.
- The P50 consistency signal surfaced per item where it is worth surfacing: a stated middle far
  from the implied one is the first thing M5 will build on, and it costs nothing here beyond
  showing it.
- History: the runs already made, newest first, so a second forecast can be compared with the
  first by eye. That is not M10's sliding-date detector; it is the list that proves the data for
  it is being kept.
- **No calendar dates anywhere.** Hours, everywhere, until M4 — because a date needs a
  working-day assumption and inventing one here would bury it exactly where `roadmap.md` says it
  must not be buried.

**Tests.** Running a forecast draws the band. A refusal is shown, and `nothing_to_forecast`
reads as a plan to go and estimate rather than as an error. The limitations render, and a test
asserts they are on screen alongside the number rather than merely present in the payload.
Capacity is required and an empty box is refused before a request is made. Every string comes
from the catalogue, which the test setup already enforces.

**Done when** the product does the thing it exists to do, badly styled and honestly.

---

## Step 7 — Close out

- `roadmap.md`: mark M3a done, split the M3 section into M3a and M3b, and retire the "M3 is
  oversized" thin spot by having actually split it. Record what M3b inherits, including the two
  limitation codes it exists to delete.
- `roadmap.md`: M4 inherits the working-day assumption and the requirement that it be visible;
  M6 inherits that it may replay a run rather than store vectors.
- `CLAUDE.md`: the `forecast` and `forecast.model` split and why purity is the seam; the four
  modelling decisions somebody could quietly undo (mixture over estimators, zero-effort nodes,
  truncated in-progress draws, required capacity); the seed-and-version contract that makes a run
  reproducible; and that a run is never rewritten.
- `product-concept.md`: its banner says there is no forecast of any kind, which stops being true
  here. The "Distribution fitting" section's two candidates become one decision and a reason.
- `m3b-plan.md` already exists and needs no close-out from here. What it does need from this
  step is a note wherever M3a departed from its own bullets, because **M3b's first test replays
  an M3a run and asserts byte-identical output** — so anything this milestone did differently
  from what is written above is something that test is silently pinning.

---

## Migrations

| | |
|---|---|
| `V12__forecast_runs.sql` | `forecast_runs`, indexed on `(tenant_id, project_id, created_at desc)` |

One table, one migration, and nothing altered. The columns:

- `id`, `tenant_id`, `project_id`, `created_at` — as everywhere, with `tenant_id` on the row
  rather than reached through the project.
- `requested_by_user_id` — a **user**, not a membership, for the reason `estimates` gives: the
  person who ran it may have left, and the run is still a thing they did.
- `seed` (`bigint`), `sample_count` (`int`), `capacity` (`int`), `priority_rule` (`varchar`),
  `engine_version` (`int`) — the assumptions, each stored because each changes the answer.
- `item_count`, `estimated_item_count` — coverage *as run*, not as it is now.
- `p10_hours`, `p50_hours`, `p80_hours`, `p90_hours`, `p95_hours`, `mean_hours` — `numeric(14, 2)`,
  real columns because M10 queries them across runs.
- `inputs` (`jsonb`) — the snapshot of decision 9.
- `outputs` (`jsonb`) — the histogram and anything else not worth a column.

**No unique index and no conflict to map.** Two identical forecasts of the same plan are two
forecasts, not a duplicate; the point of the table is that somebody asked twice.

---

## Sequencing and risk

**The risk here is not a crash, it is a plausible number.** Steps 1 through 4 have no user, no
database and no HTTP, and every one of them can be checked against arithmetic that exists
outside this codebase. That is deliberate: it is the only part of this application where "the
tests pass" and "the answer is right" are genuinely different claims, and the closed-form oracle
in step 4 is what closes the gap. If review attention is rationed, spend it on step 4's tests
rather than on step 5's plumbing.

**The three modelling decisions most likely to be quietly undone**, in the order somebody is
likely to reach for them:

- **The mixture over estimators** (decision 3) will look like a bug the first time two people
  disagree and the band comes out wide. It is not; the band is wide because they disagree. The
  fix that will be proposed is averaging, and it is the one thing here that converts uncertainty
  into false confidence.
- **The truncated in-progress draw** (decision 5) will look like a bug because a task that has
  run long has more left, not less. It is the defining property of the distribution this product
  chose, and a model without it flatters every late project.
- **Required capacity** (decision 6) will look like friction, and somebody will add a default.
  The default is a claim about a team, made by a server that has never met them, and it moves
  the P90 by 70%.

**What this milestone must not absorb.** The team factor (M3b) will look like four lines inside
the sampling loop, because it is four lines inside the sampling loop — and adding it here would
cost the whole-plan closed form that proves everything under it, and would cost M3b the
equivalence test it uses to prove itself against this milestone. Both halves of that are why
`m3b-plan.md` is a separate build rather than a separate document. Calendar dates (M4) will look like a small conversion,
and will import a working-day assumption that this milestone has deliberately kept out. Both are
the ordering principle in its usual form: the next thing always looks cheap from here.

**What is genuinely optional** is step 6's appearance, as ever. What is not optional in step 6
is decision 12: a band without its caveats is this product's own failure mode with a chart on
it.
