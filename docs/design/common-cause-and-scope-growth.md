# Common cause and scope growth — the design record

> **Scope.** The second half of the simulation engine, and the half that cannot be checked against a
> closed form: **the shared team factor** and **scope uncertainty**. The first half builds an engine that
> samples each item independently and forecasts exactly the work somebody listed. Both of those
> are known to be wrong, and this work is where each stops being wrong. Excluded: calendar
> dates (the calendar), elicitation (elicitation), variance contribution (the contribution ranking), correlation *groups* beyond one
> global factor (icebox), and any use of history to propose these parameters (calibration/the throughput forecast).
>
> **How to read this.** Decisions first, as ever. The one this work exists to settle is
> decision 3 — *where new work attaches in a graph* — which `../roadmap.md` lists as an open
> question and which has no obvious answer, only a defensible one.
>
> **Why this is not optional polish.** `../roadmap.md` measured the shared team factor moving a
> true P90 from 209.4 to 222.2 on ten wide tasks: the closed form could not see it and neither
> can the simulation engine. `../product-concept.md` says scope uncertainty is usually the *larger* of the two
> sources. An engine that ships without either produces a band that is too tight in two
> directions at once, which is the failure mode this whole product was written to replace. The simulation engine
> ships with that stated on screen (its decision 12); **The common-cause model is what deletes the statement by
> removing its cause.**

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | The team factor ✅ *done* | the simulation engine |
| 2 | Scope growth ✅ *done* | the simulation engine |
| 3 | Both at once, and the engine version ✅ *done* | 1, 2 |
| 4 | Stating the assumptions, and storing them ✅ *done* | 3 |
| 5 | Asking the two questions ✅ *done* | 4 |
| 6 | Close out ✅ *done* | 1–5 |

Steps 1 and 2 are independent of each other and both are pure — the same seam the simulation engine had.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What the team factor is | **One log-normal multiplier per run, median pinned to 1**, applied to every remaining duration. |
| 2 | Where its parameter comes from | **A percentile somebody can answer** — "a bad stretch makes everything take up to N% longer" — fitted the same way an estimate is. |
| 3 | Where new work attaches | **As a successor to a randomly chosen existing item**, not as a multiplier and not appended at the end. |
| 4 | What new work costs | **A draw from the plan's own estimated items** — the plan is its own reference class. |
| 5 | How much new work | **A growth range in percent**, P10/P90, fitted and sampled per run, with stochastic rounding to a count. |
| 6 | Whether the two double-count | **No, and the reason is capacity** — one makes items longer, the other makes more of them. |
| 7 | Are the parameters required | **Yes, both**, like capacity. Zero is a claim, not an absence. |
| 8 | What replaces the closed form | **Three narrower oracles**, of which degenerate equivalence with the first half is the strongest. |
| 9 | What a version bump does to old runs | **The new engine must contain the old one as a special case**, or old runs stop being replayable. |
| 10 | The factor and work already spent | **The factor applies only to what is still ahead.** The past is measured, not modelled. |

### Decision 1 — One multiplier per run, with its middle pinned

`../product-concept.md`: naive Monte Carlo samples each task independently, so good and bad luck
cancel and the band comes out implausibly tight. Reality has common causes — short staffing, a
codebase that fights back, a quarter with three incidents in it — and they move everything
together.

**One factor `F` is drawn per run and multiplies every item's remaining duration.** One line
inside the loop, and it is the difference `../roadmap.md` measured at +6% on the P90 of ten wide
tasks.

`F` is log-normal with **`mu = 0`**, so its median is exactly 1. That is the decision inside the
decision, and getting it wrong would be quiet: a factor with *mean* 1 has a median below 1,
which drags the centre of every forecast down while claiming only to widen it. The estimates
already carry the central case — that is what a P50 is — so a factor whose job is common-cause
*spread* must leave the middle alone and pull the tails apart. Median 1 does exactly that: half
the runs are a good stretch, half are bad, and neither is the default.

So `F = exp(s · Z)`, and the whole of the modelling is the one number `s`.

### Decision 2 — Ask for a percentile, because nobody can answer a sigma

`s` is a log-standard-deviation. Nobody has an opinion about a log-standard-deviation, and a
product built on the premise that elicitation is the hard part must not ask for one.

**So the question is asked as a percentile of the factor itself:** *in a bad stretch, how much
longer does everything take?* An answer of 30% is read as the factor's P90 — a stretch bad
enough that only one run in ten is worse — and that, with the median pinned at 1, determines
`s` completely:

```
s = ln(1 + N/100) / 1.2815515655446004
```

which is **The simulation engine's `LogNormalFit` with one end fixed**, not a second piece of distribution
machinery. Three of the four fits in this engine now come from the same twenty lines, and the
constant that appears here is the same one that fits an estimate — so getting it wrong breaks
both, loudly, which is how decision 10 of the first half is paid off a second time.

This is the surprise framing of elicitation arriving early, and that is not an accident: it is the only
form of the question a person can actually answer. **It is also the parameter most obviously
derivable from history** — calibration's calibration data and the throughput forecast's throughput both bear on it — and the
plan is to ask now and propose from history later, never to invent a default in between.

### Decision 3 — New work is discovered *by* work, so it attaches to work

This is the open question `../roadmap.md` records and the reason the common-cause model needed a plan of its own:
*"when the model was a sum, unknown work was simply a multiplier. In a schedule, new work needs
a position — does it attach to the critical path, spread across the graph, or append at the
end? The choice materially changes the answer, and inflating the total is no longer a valid
shortcut."*

**Each sampled new item is attached as a successor to a uniformly chosen existing item, with no
lag.** It becomes ready when that item finishes, competes for capacity like everything else, and
lengthens whatever path it lands on.

| Rejected | Why |
|---|---|
| Multiply every duration by a scope factor | **This is not scope growth, it is estimation bias** — and mathematically it is indistinguishable from decision 1, since two multipliers compose into one. Shipping both would be one effect counted twice under two names. |
| Append it after everything else | Maximally pessimistic per unit of work, and structurally false: it says discovered work can never overlap anything, which is the one thing it always does. |
| Attach it to the critical path | Self-fulfilling. The critical path is where the model already hurts most, and loading new work onto it by construction guarantees the answer the model was built to test. |
| Leave it unattached, ready at time zero | Maximally optimistic: unlimited parallelism for exactly the work nobody has thought about yet. |

The uniform attachment is a claim — that unknown work is as likely to fall anywhere in the plan
as anywhere else — and it is a weaker claim than any of the alternatives. **The refinement worth
naming and not building** is weighting the choice by remaining effort, so that new work lands
where the work is; it is one line, it needs data nobody has yet to justify it, and it can go in
without a version bump changing anything already stored except the numbers it was always going
to change.

### Decision 6 — The two are not the same thing, and capacity is why

The obvious objection to shipping both is that they are the same effect wearing two hats: a plan
that grows by 30% and a plan whose every task takes 30% longer both come out around 30% longer.
Under the simulation engine that objection would be **right**, and decision 3's first rejected row is exactly it.

They separate the moment there is a capacity constraint. A multiplier makes each item longer and
changes nothing about how many can run at once. New items **compete for slots**: they make the
plan longer *and* they make everything else wait. At capacity 1 the two converge, which is worth
knowing and worth having as a test; at any realistic capacity they are measurably different
effects on the same total.

> *Both halves of that were measured in step 2, and the second half is not what this paragraph
> assumes. They converge at capacity 1 to a tenth of a percent, and above it **either one can be
> the heavier** — the multiplier where capacity binds, scope where the answer is decided by the
> longest path. See step 2's `### As built`; the conclusion below is strengthened rather than
> weakened, because two effects loading different bottlenecks are even less substitutable than
> two effects of different sizes.*

That is the whole justification for modelling scope as items rather than as a number, and it is
only available because the simulation engine made the aggregator a scheduler.

**The other half of the double-count worry is human**, and it is answered rather than dismissed:
estimators are asked for the effort of *this task as described*, and scope growth is by
definition work nobody described, so in principle they do not overlap. In practice some
estimators pad. Calibration is what will eventually say by how much, per estimator — and until then this
is a known softness rather than a hidden one.

### Decision 5 — A growth range, sampled, and rounded honestly

`../product-concept.md` already frames this: *"if the last five projects grew 40–90% in ticket
count, that is a distribution to sample from and multiply through."* So the input is a **range
in percent** — P10 and P90 of how much the item count grows — fitted log-normal by the same
machinery again, sampled per run, and multiplied by the number of items currently in the plan.

That produces a fractional count, and **rounding it is a decision with a bias in it.** Rounding
to nearest systematically shifts small growth to zero: a plan of 10 items growing 4% samples
0.4 new items, which rounds to none, every single run — so a small, real growth becomes exactly
no growth. The fix is one line: take the whole part, then add one more with probability equal to
the fraction. Over ten thousand runs the mean count is what was sampled, which is the only thing
that makes the sampled distribution mean anything.

### Decision 4 — The plan is its own reference class

A generated item needs a duration, and the honest place to get one is the plan itself: **pick one
of the plan's estimated items uniformly and draw from its fitted distribution.** New work looks
like existing work, stated as an assumption rather than assumed silently.

Sampling one item's distribution rather than averaging across all of them matters for the same
reason decision 3 of the simulation engine matters: an average is a narrower thing than the population it came
from, and the point of generated work is that we do not know which kind it will be.

`nothing_to_forecast` already guarantees at least one estimated item exists, so the reference
class is never empty — a refusal in the simulation engine doing load-bearing work here, which is worth noticing
before somebody relaxes it.

### Decision 7 — Both parameters are required, because zero is a claim

Capacity is required in the simulation engine because every possible default is a claim about a team the server
has never met. These two are different in one way: they *have* a neutral value, and it is zero,
and zero is precisely what the simulation engine does.

**They are still required**, and the reason is that zero is not neutral — it is the assertion
that nothing in this team's world has a common cause and that no unlisted work will ever appear,
and that assertion is false in every project either of us has seen. Defaulting to it would ship
the simulation engine's behaviour under the common-cause model's name, with the limitation notices deleted and nothing put in their
place. Somebody who genuinely wants to model a plan without either may say so by typing zero,
and the run will report that they did — a refusal, or an assumption, is only honest when it is
raised against something somebody chose.

### Decision 8 — What replaces the oracle the simulation engine had

The simulation engine's argument for the split was that a schedule at capacity 1 is a sum of independent
log-normals with an exact mean and variance, and that a shared factor destroys that check by
construction — `../roadmap.md` measured the closed form answering 214.0 against a true 222.2. That
is still true, and it is why these two features are here and not there. **What it did not say,
and should have, is that this half has oracles of its own — they are just narrower.** Three of them:

- **Degenerate equivalence, which is the strongest.** With `s = 0` and growth `0–0`, this engine
  must produce output **byte-identical to the simulation engine's** for the same seed and inputs. That single test
  covers the entire the simulation engine surface as a regression, and it is what decision 9 rests on.
- **Exactness for one item.** If `X` is log-normal(`mu`, `sigma`) and `F` is log-normal(`0`, `s`)
  and they are independent, then `F · X` is log-normal(`mu`, `√(sigma² + s²)`) — exactly. So a
  one-item plan with a team factor has a closed form after all, and it pins the factor's
  implementation precisely even though a whole plan's does not.
- **The published figure.** `../roadmap.md` measured ten wide tasks (2–30d) at a true P90 of 209.4
  independent and 222.2 with the shared factor. That is a number produced outside this codebase,
  which makes it the most valuable kind of test there is — reproduce the setup and converge to it.
  *(It does not say what factor produced 222.2, so the setup is one fact short of reproducible;
  step 1's `### As built` records recovering it as a P90 of 1.30 — a 30% stretch.)*

Plus the properties that have no closed form but do have a direction: raising `s` widens the band
and leaves the median where it was; raising growth pushes every percentile out and never in;
scope and team factor converge on the same effect at capacity 1 and diverge above it (decision 6,
asserted rather than argued).

> **The simulation engine is built, and the equivalence test now pins more than this plan knew about.**
> "Byte-identical to the simulation engine's" means byte-identical to what the simulation engine *did*, not to what its bullets
> said, and the two differ in places. **Read the `### As built` sections of `simulation-engine.md` before
> touching anything in the sampling loop or the scheduler**, because that is where the
> difference is recorded and nothing else states it. The ones this test is silently holding:
>
> - **The generator is `java.util.Random`**, chosen because its algorithms are in its contract
>   rather than only its implementation. Swapping it for anything faster breaks equivalence by
>   definition, and would break every stored run with it.
> - **The order draws are taken in is part of the answer.** One generator is seeded once and
>   consumed in run order; a team factor drawn in the wrong place — even one that is always
>   zero — shifts every subsequent draw and the test fails for a reason that looks like
>   nothing. Step 1's own bullets already warn about this and it is worth reading twice.
> - **The conditional draw samples the surviving tail directly** rather than inverting a
>   probability, and a comprehensively outrun estimate has a remainder of exactly zero — the
>   model reporting that it has been falsified rather than forecasting. Decision 10 above
>   multiplies *that* remainder, so a factor applied to zero stays zero; it must not reach
>   around the conditioning to find something to multiply.
> - **The priority key is a transitive closure computed once, before sampling**, and ties break
>   by write order. Scope growth adds items to the graph, so it changes what that closure
>   contains — which is a real difference in behaviour, and exactly why it must be zero-growth
>   identical rather than approximately so.
> - **The histogram's counts are a `List` and its bucket edges are fixed at a hundred.** A
>   record with an array component compares identities rather than contents, which is how a
>   byte-identical assertion passes for the wrong reason.
>
> **What this is done by** is also sharper than these bullets: the first half emits `no_team_factor` and
> `no_scope_uncertainty` on every forecast it produces, and those two codes disappearing is the
> finish line.

### Decision 9 — A version bump must keep the old engine inside the new one

The simulation engine stores `engine_version` on every run so that a stored forecast can be re-run and reproduce
its numbers. The common-cause model is the first bump, so it is where the rule that makes that promise survivable
gets written down:

**Either the new engine contains the old one as a special case, or every run made before the
bump becomes a record that can be read and never replayed.**

The common-cause model takes the first route, and gets it almost free: the simulation engine *is* the common-cause model with `s = 0` and no growth. So
the migration backfills those two values onto existing rows, and the backfill is **true** rather
than merely convenient — it is exactly what those runs did. Replaying a version-1 run means
running version 2 with the parameters version 1 implied, and the degenerate-equivalence test is
what proves that is the same thing.

The rule has teeth for whoever bumps next. A future change that cannot be reduced to a parameter
setting — a different fit, a different scheduler — does not get to pretend: it bumps the version,
old runs become read-only history, and `../roadmap.md` gets told, because the reporting work's sliding-date
detector compares runs and comparing across an incomparable bump is how a tool reports movement
that never happened.

### Decision 10 — The factor applies to the future, not the past

The simulation engine samples an in-progress item's remaining work as its estimate conditioned on the effort
already spent. Multiplying *that* by a team factor is straightforward; multiplying the whole
estimate before conditioning is not, because the conditioning would then be against a quantity
that already has the factor in it, and the arithmetic stops meaning anything.

The tie-breaker is not convenience but sense: **`F` models common-cause risk that has not
happened yet.** Hours already spent are measured, and no multiplier should be applied to a
measurement. So the factor multiplies the remainder, and an item halfway through a bad quarter
carries the bad quarter only across what it has left.

---

## Step 1 — The team factor ✅ *done*

**Goal.** Good and bad luck stop cancelling.

- `TeamFactor.from(worseByPercent)` in `forecast.model` — the fit of decision 2, median pinned
  to 1, built on `LogNormalFit` rather than beside it.
- `TeamFactor.sample(RandomGenerator)` → one multiplier, drawn **once per run** and passed into
  the item loop rather than drawn inside it. Drawing it per item would be the bug this feature
  exists to fix, written by accident, and it would look identical from the outside.
- Applied to each item's **remaining** duration (decision 10), including generated work once
  step 2 exists.
- `worseByPercent == 0` → the factor is exactly 1 every time, with no draw taken at all: the
  generator must not be consumed, or a zero factor would still shift every subsequent draw and
  break decision 8's equivalence. **This is the subtlest thing in the work** and it is worth
  its own test.

**Tests.** The fit recovers its stated percentile. The median of many draws is 1, and the mean is
above it — the asymmetry that makes a right-skewed multiplier what it is. **A one-item plan with
a factor is log-normal(`mu`, `√(sigma² + s²)`)**, asserted against the analytic mean and variance
— the exactness oracle of decision 8. Ten wide tasks reproduce `../roadmap.md`'s 209.4 → 222.2.
Raising the percentile widens the band and leaves the P50 within sampling error of where it was.
A zero factor consumes no randomness: the same seed produces the same sequence as the simulation engine.

**Done when** The band widens for a reason, by an amount somebody measured before this code
existed.

### As built — where it differs from the above

- **The stretch this work was measured at is 30%, and nobody had written that down.**
  `../roadmap.md` reports 209.4 → 222.2 on ten wide tasks without saying what factor produced it,
  so the figure was not reproducible as stated — it is a target with a free parameter in it.
  Solving for the parameter gives `s = 0.205`, which is a P90 of **1.30**, and 30% is exactly
  the worked example decision 2 already uses. Both halves of that table are now a single test
  from one plan and one seed: **209.4 and 222.2 reproduce to within a percent.** The number
  was recovered rather than chosen, but it *was* recovered, and the next reader should know
  that the plan's third oracle needed one more fact than the plan carried.
- **`TeamFactor` holds a `LogNormalFit` and refuses one whose `mu` is not zero**, where the
  bullets imply a bare `s`. Two reasons, and the refusal is the interesting one. Holding the
  fit means a stretch is drawn by `LogNormalFit.at` — the same method an estimate is drawn
  through, so the two cannot drift about what a log-normal is. And decision 1 says a mean-1
  factor would move every forecast's centre *quietly*; a type that cannot hold a non-zero
  middle turns the quietest failure in the work into a loud one. It is the precedent
  `LogNormalFit.from`'s own refusals set in the simulation engine step 1: unreachable through the API, coverable
  by a test, and cheaper than a `NaN` surfacing four steps downstream.
- **The middle is written as zero rather than read back from the fit.** `from` fits
  `1/worst` against `worst` — a reciprocal pair, which is what makes the median 1 and is why
  this is `LogNormalFit` with one end fixed rather than a second piece of machinery. But
  `Math.log(1.0 / worst)` is only the negation of `Math.log(worst)` to within a rounding, so
  the fit's `mu` comes back at about 1e-17 rather than at zero. Near enough for any number a
  person sees, and not near enough for a constructor that refuses anything but zero — so the
  `sigma` is taken from the fit and the `mu` is stated.
- **The degenerate-equivalence test is here, as the sequencing section asked, and it is a
  golden-number test rather than a comparison against a second engine.** the simulation engine's `Engine` is not
  a thing that still exists to be called, so what is asserted is the seven numbers it produced
  — captured from the the simulation engine build before this step changed a line — against what the same plan
  and seed produce now with `TeamFactor.NONE`. Exact equality, not tolerance. The plan for it
  is deliberately awkward (work under way with hours against it, work nobody estimated, a fork
  and a lag), so the golden numbers pass through every branch of the sampler on the way out.
  Step 3's version of this, against a run stored in the database, is the same assertion one
  layer up and both are worth having.
- **A mirror sits beside every "no draw is taken" test**, because each of them would pass just
  as well if `sample` did nothing at all or if the parameter were ignored: a factor that
  stretches must advance the generator, and must move the P90 off its golden value. That is
  two assertions to make one meaningful, and it is the same shape as `DependencyGraphLockTests`
  needing its second case.
- **`Engine.run` takes the factor as a parameter and `ForecastService` passes `NONE`.** Nothing
  can ask for a stretch yet — that is step 4 — so `Engine.VERSION` stays at 1 and every run
  stored by this step is still, truthfully, a version 1 run. The bump belongs where the two
  effects compose, not where the first one becomes possible.
- **The factor multiplies durations and not lags**, which no bullet says either way. A lag is a
  wait rather than work; a short-staffed quarter does not make a fortnight's curing take three
  weeks. It follows from decision 1 saying "duration", and it is worth stating because the one
  line that applies the factor sits close enough to the scheduler to make the other reading
  look like an oversight.
- **`ItemModelTests` needed nothing.** Decision 10 asks the factor to multiply the remainder
  rather than the estimate, and multiplying what `ItemModel.sample` already returns *is* that —
  so the decision cost no code and got a test anyway: an estimate comprehensively outrun has a
  remainder of exactly zero, and a 200% stretch still finds nothing in it to multiply.

---

## Step 2 — Scope growth ✅ *done*

**Goal.** The forecast stops assuming somebody thought of everything.

- `ScopeGrowth.from(p10Percent, p90Percent)` — the fit of decision 5, and `sampleCount(items,
  RandomGenerator)` with the stochastic rounding, which is where the bias lives.
- Generated items get a duration by decision 4 and a position by decision 3, both drawn from the
  same generator so a run stays reproducible.
- They enter the scheduler as ordinary items: they occupy capacity, they can be scheduled behind
  each other if two land on the same parent, and they are never predecessors of anything that
  was actually planned — new work delays what comes after its parent only by competing for
  slots, never by being inserted into a path somebody drew.
- **The topological pass tolerates them by construction**: an edge from an existing item to a
  brand-new leaf cannot create a cycle, which is worth stating because it is the reason step 2
  needs no lock, no re-check and no new refusal.

**Tests.** A growth range of 0–0 generates nothing, ever, and consumes no randomness. Mean
generated count over many runs matches the fitted mean — the test that fails if rounding is naive,
and it fails loudly for small plans, which is the case that motivated the decision. Generated
durations have the same distribution as the plan's estimated items. Every generated item has
exactly one parent, drawn from the existing plan. At capacity 1, adding 20% scope lengthens the
total by about 20%; **at capacity 4 it lengthens it by more than a 20% duration multiplier
does**, which is decision 6 as an assertion. A plan of one item still grows.

**Done when** The larger of the two uncertainty sources is in the model, in the place `../roadmap.md`
said it had to be given.

### As built — where it differs from the above

- **Decision 6 is right that they separate and wrong about which way, and that is the finding
  of this step.** The prediction was that scope growth would be the heavier of the two once
  capacity binds. Measured on twenty tasks, a fifth more scope against a fifth longer
  durations: at capacity 1 the two agree to a tenth of a percent, exactly as predicted. Above
  it, **which one is heavier depends on what the plan is short of.** Where capacity binds — four
  slots, chains of five — the multiplier is heavier by 10% (174.5 against 158.7), because more
  smaller pieces pack into fixed slots better than fewer larger ones. Where there is room for
  everything at once, scope is heavier (74.7 against 73.6), because new work adds a step to a
  path and a multiplier only stretches the steps already there. **This is a better argument for
  keeping them apart than the plan's was**: they do not merely differ in size, they load
  different bottlenecks, so no single parameter can stand in for both. The test asserts all
  three measurements and says so.
- **What is fitted is the growth *multiplier*, not the growth percentage.** Decision 5 says a
  range in percent, fitted, and multiplied by the item count; that reading cannot take a low end
  of zero, because a log-normal never reaches it — and "usually it does not grow, but sometimes
  by 40%" is among the most honest answers anybody gives. Fitting `1 + p/100` accepts it, makes
  `from(0, 0)` come out as `NONE` with no special case anywhere, and is what
  `../product-concept.md`'s own "a distribution to sample from and **multiply through**" describes.
  The price is that a range starting at zero puts a tenth of its draws below a multiplier of 1,
  which this model reads as no growth rather than as a plan that shrank — so **a stated P10 of
  0% means exactly one run in ten finds nothing, by construction.** That is a property worth
  having and it is asserted.
- **`sampleCount` is called `sample`**, because `Engine.run` already has a `sampleCount` meaning
  the number of runs and the two would have sat three lines apart meaning different things. It
  also lines up with `TeamFactor.sample`.
- **Discovered work is a per-run argument to a schedule still prepared once**, which is the
  design decision the step needed and the plan did not name. Rebuilding the graph every run
  would mean a transitive closure ten thousand times, which is minutes rather than
  milliseconds. It works because a discovered item is always a leaf hanging off one existing
  item: nothing it adds can close a loop, nothing waits behind it, so `Schedule.of` is untouched
  and `finish(durations, parentOf, found)` carries the difference.
- **Two consequences of that, both of which are decision 7 rather than convenience.** Planned
  priorities do not move when work lands behind them — a key that shifted with a draw would
  leave two forecasts of one plan ordered differently and unable to be compared, which is the
  rule `typicalEffortHours` was written for. And discovered work sorts last: it has nothing
  waiting behind it, so it ties with every planned leaf, and among ties the plan works on what
  it wrote down first. Both follow from the existing rule; neither is an exception to it.
- **`finish` asks for *at least* one duration per item where it used to demand exactly one.** A
  run that discovers eleven pieces of work followed by one that discovers three reads fewer
  entries of the same array rather than allocating a smaller one, which is what keeps the
  zero-growth path allocating nothing at all. A draw from the wrong plan is still refused,
  because that draw is short.
- **`ItemModel` grew a second draw method**, `sampleAsNewWork`. `sample` answers what *this*
  item has left, so it returns zero for finished work and conditions on hours already spent —
  and finished work is a perfectly good description of what a new piece of work costs. Two
  questions, two methods; using the wrong one would have made new work free in exactly the
  plans that have the most evidence about it.
- **New work attaches uniformly among *all* items, including finished ones**, which is decision
  3 read literally. Work landing behind something already done is ready at once, which is the
  "unattached, ready at time zero" position decision 3 rejected — arriving through the back door
  for the finished fraction of a plan. It is left as written because the refinement that fixes
  it is the one decision 3 already names and declines: weighting the choice by remaining effort
  gives finished work a weight of zero and does this for free.
- **The engine refuses to grow a plan with nothing estimated in it**, since new work costs what
  this plan's work costs and such a plan has nothing to answer with. Unreachable through the
  API — `nothing_to_forecast` is what makes it so, two features after it was written for
  another reason entirely — and reachable from a test, which is the same distinction
  `LogNormalFit.from` draws.
- **The oracle for decision 4 came out exact.** One item and a plan certain to double is two
  draws from one distribution, so the mean is twice the fit's mean and the variance twice its
  variance: **39.918 sampled against 39.922, and a standard deviation of 2.219 against 2.214.**
  A generator drawing from the plan's average, or from a fixed size, or from the item it
  attached to, misses both.
- **The budget is still comfortable: about 560ms** for five hundred items and ten thousand runs
  with *both* parameters on, against the two seconds decision 8 of `simulation-engine.md` allowed. It was
  300ms without them, and the difference is scheduling the two hundred extra items a 20–60%
  growth range discovers each run.

---

## Step 3 — Both at once, and the engine version ✅ *done*

**Goal.** One engine, one version, and every earlier forecast still reproducible.

- The two compose inside the run loop: sample `F`, sample the scope count, generate and attach,
  then sample and schedule with `F` applied to every remaining duration.
- `ENGINE_VERSION` goes to 2.
- **The degenerate path is the compatibility layer**, not a branch beside it: version 1 is
  version 2 with `s = 0` and no growth, and there is no second code path to keep in step.

**Tests.** The equivalence test of decision 8 — a stored the simulation engine run, replayed at version 2 with zero
parameters, reproduces its percentiles byte for byte. Both effects together produce a wider band
than either alone. Determinism holds with both switched on. The generator is consumed in a fixed
order regardless of which parameters are zero, which is the invariant everything above rests on
and the one a careless refactor breaks silently.

**Done when** The engine models what it always should have, and can still answer for what it said
before it did.

### As built — where it differs from the above

- **There was nothing left to compose, and that is the report.** Steps 1 and 2 each added their
  parameter to the one run loop rather than to a branch beside it, so this step is the version
  bump, the tests that make it mean something, and no change to the loop at all. The plan's
  "the degenerate path is the compatibility layer, not a branch beside it" turned out to describe
  the way the two earlier steps had to be written, not a thing to do here.
- **The equivalence test was written in step 1, as the sequencing section asked**, so what this
  step added was its name: `versionTwoWithNoAssumptionsForecastsExactlyWhatVersionOneDid`. It
  still asserts the seven numbers captured from the the simulation engine build before a line of the common-cause model existed, and
  exact equality still holds now that both parameters are threaded through the loop — which is
  the whole of what version 2 promises about version 1.
- **The draw-order invariant is pinned by a hand-rolled oracle rather than by a property.** The
  plan asks that the generator be consumed in a fixed order regardless of which parameters are
  zero, and the honest way to check that is to work out what one run must come to and compare
  exactly. One run of a two-item plan at capacity 1, drawing from a generator of its own in the
  documented order — stretch, count, each discovered piece and where it landed, then the plan —
  asserted for all four combinations of the two parameters being none or not. **Exact equality,
  not a tolerance**: the durations are summed in the order the scheduler starts things, so
  floating point has nothing to disagree about. Any extra draw, any missing one, any reordering
  fails it immediately.
- **That oracle asks the two parameters for their own draws rather than imitating them**, so
  what it pins is the engine's ordering alone. How many draws a fit takes is `TeamFactor`'s and
  `ScopeGrowth`'s business and their own tests say so; a shadow implementation here would have
  made this test pass on a day both of them were wrong in the same way.
- **`ForecastApiTests` needed no change and gets its cross-version test in step 4.** Every run
  the service writes today is a version 2 run with both parameters at none, so replaying one is
  not yet a test of anything crossing the bump. Step 4's migration is what puts a version 1 row
  in the table with its three columns backfilled to zero, and its test list already asks for
  exactly that. Asserting it here would have meant fabricating a version 1 row from numbers this
  engine produced, which proves nothing the golden test does not already prove better.
- **Runs written between this step and step 4 carry a version and no parameters**, which sounds
  like a gap and is not: they are all zero, and step 4's backfill sets zero on every existing
  row. The backfill is true for them for the same reason it is true for the the simulation engine rows.
- **The frontend's forecast fixture said version 1.** Nothing renders it, so nothing failed —
  but a double that describes a payload the server no longer sends is the same species of
  problem as one that answers every URL alike, and it was one line.

---

## Step 4 — Stating the assumptions, and storing them ✅ *done*

**Goal.** A run says what was assumed, and a stored run keeps saying it.

- `V13__forecast_assumptions.sql`: `team_factor_worse_by_percent`, `scope_growth_p10_percent`,
  `scope_growth_p90_percent` on `forecast_runs`, **backfilled to zero** — which is not a default
  standing in for missing data but a true statement about what those runs did.
- Real columns rather than the `inputs` document, for the reason capacity and `priority_rule` got
  them: these are the assumptions the reporting work compares across runs, and a comparison is a query.
- `POST /api/projects/{projectId}/forecasts` gains three required numbers, validated
  `@PositiveOrZero` with a sane ceiling, and `scopeGrowthP90 >= scopeGrowthP10` refused as a
  document-level code the way `estimate_out_of_order` is — it is a relationship between fields,
  not a fault in either.
- **The limitation codes `no_team_factor` and `no_scope_uncertainty` are deleted** from the
  engine's output. That is the concrete definition of the common-cause model being done. `unestimated_items` and
  `inconsistent_estimates` stay, because they are properties of a plan rather than of the model.
- The response carries the three assumptions back, so what produced a number travels with it.

**Tests.** A run stores all three. Existing rows read back as zero after the migration and replay
correctly. A negative percentage is refused; a P90 below the P10 is refused with its own code and
without pointing at a box. The two retired limitation codes appear on no response. A run made with
zeros still says so — the assumption is reported whether or not it is doing anything.

**Done when** no forecast in the database is missing the account of how it was made.

### As built — where it differs from the above

- **The two limitation codes are retired, not deleted, and deleting them would have destroyed
  the history this work is careful about everywhere else.** `ForecastOutputs` holds
  `List<ForecastLimitation>` and every the simulation engine run has `no_team_factor` and `no_scope_uncertainty`
  sitting in its stored `outputs` document — so removing the constants would have made those
  runs fail to deserialise, taking the whole forecast history with them. The plan says
  "deleted from the engine's output", and that is exactly what happened: nothing writes them,
  the constants stay, and their documentation says which work retired them. The rule that
  made this necessary is `ForecastLimitation`'s own — limitations are stored rather than worked
  out at read time — which was written as a precaution in the simulation engine and became load-bearing here.
- **The ceiling was measured rather than picked, and it is 200%.** "A sane ceiling" has two
  possible readings and only one of them bites: every percent of scope growth is items the
  scheduler runs. At the five hundred a plan may hold, ten thousand runs cost **413ms with no
  growth, 655ms at 100%, 915ms at 200%, 1.9s at 500% and 3.4s at 1000%** — so 200 is the last
  value that leaves the two-second budget `simulation-engine.md` decision 8 measured with room in it,
  and that budget is the whole reason a forecast is answered inside its request. The team
  factor costs one multiplication and could go further; it shares the bound for want of a
  reason to differ.
- **The migration adds a default and then drops it, in two statements.** The backfill has to
  reach existing rows and must not survive as a rule for future ones: a default left in place
  would let an insert omit an assumption and be handed one by the database, which is precisely
  the "a server that picked would be making a claim about a team it has never met" the rest of
  this work refuses. Old rows get a true zero; nothing after may leave the question
  unanswered.
- **`@Digits(integer = 4, fraction = 2)` alongside the bounds**, matching `numeric(6, 2)` for
  the reason the plan schema put it on an estimate. Without it a team factor of `0.004` passes
  `@PositiveOrZero`, is used by the engine, and is stored as `0.00` — leaving a run whose
  columns describe an assumption it did not make and whose replay disagrees with its own
  percentiles. The same bug the plan schema found in the same shape.
- **The replay test now reads the assumptions off the run instead of naming them**, and the run
  it replays is made with all three non-zero. That converts `aStoredRunReplaysToTheNumbersItReported`
  from a test that would pass with the columns missing entirely into one that fails if they are
  not stored, not stored correctly, or not used. Its companion asserts the version 1 case: a run
  that assumed nothing reads back as `TeamFactor.NONE` and `ScopeGrowth.NONE`, which is what
  makes replaying a backfilled row the same thing as running the old engine.
- **Three different numbers in the storage test, on purpose.** Two same-typed adjacent columns
  would look perfectly well stored if the service handed them over transposed, and nothing else
  in the suite would notice. A `ForecastAssumptions` record was considered for the same reason
  and turned down: it would have tidied a sixteen-parameter entity constructor without removing
  the positional hazard, since the record would be built positionally too. The test removes it.
- **`scope_growth_out_of_order` refuses rather than swapping the ends**, which is the tempting
  fix. A range of 60 to 20 is a typo, and reading it as 20 to 60 would forecast against an
  assumption nobody made while reporting it back as though they had. It is checked before
  anything is looked up, like `estimate_out_of_order`, and it names no field.
- **The forecast screen is broken between this step and step 5, and the frontend suite does not
  notice.** `ForecastPanel` still sends `{capacity, sampleCount}`, so every request it makes now
  comes back `400` with three `not_null` errors, and `useFormFailure` — which knows nothing of
  those field names — shows the generic banner. The frontend's 332 tests stay green because they
  mock `fetch`, which is the "138 green tests said nothing" failure `CLAUDE.md` records in
  another form. Step 5 is what closes it, and there was no honest way to close it here: filling
  the three fields in on the client would be the invented default decision 7 exists to refuse.

---

## Step 5 — Asking the two questions ✅ *done*

**Goal.** Two more assumptions on screen, in language somebody can answer, next to a number they
change.

- The forecast panel gains: *"in a bad stretch, how much longer does everything take?"* and
  *"how much does a plan like this usually grow?"*, the second as a pair of percentages. Both
  required, neither pre-filled — decision 7, and the same argument `useProposedSlug` gets in
  `CLAUDE.md`.
- The result states all five assumptions beside the band: capacity, samples, the factor, and the
  growth range. Not behind a disclosure. A forecast whose assumptions are one click away is a
  forecast that gets screenshotted without them.
- **The the simulation engine limitation notice for these two disappears**, replaced by the stated values. What
  remains is coverage and estimate inconsistency.
- The history list shows the assumptions per run, because two runs of the same plan with
  different assumptions are not a movement and must not read as one — which is the reporting work's whole
  problem arriving early enough to design around.

**Tests.** Both questions are required and an empty box is refused before a request goes out. The
assumptions render beside the result. A refusal on the growth range reads as a question about the
two numbers rather than as an error. Every string comes from the catalogue.

**Done when** The two hardest questions in the model are the two most visible things on the
screen.

### As built — where it differs from the above

- **The rule about the disclosure turned out to be structural rather than stylistic.** The plan
  says the two questions are required and the result's assumptions are not behind a disclosure;
  what it does not say is that a *required* box may never go inside one. `useFormFailure` keeps
  the banner quiet when a complaint belongs to a field the visitor can see, and a field inside a
  collapsed `<details>` is in the DOM and invisible — so a required box in there would answer a
  refused submission with nothing at all on screen. The sample count is the only thing left
  behind the disclosure, and it is the only thing that has a right answer for everybody.
- **The growth range is one `<fieldset>` with one legend and two labelled boxes.** It is one
  question asked in two numbers, and the refusal for a range the wrong way round belongs to the
  pair — so the legend carries the question, the labels carry only "usually at least" and "and
  as much as", and `scope_growth_out_of_order` arrives in the banner naming both rather than
  marking either box invalid. The same shape `estimate_out_of_order` takes on the estimate form,
  and the test asserts that neither input is marked.
- **The two retired limitation codes stayed in the frontend too, and for the same reason they
  stayed in the enum.** This screen lists every forecast a plan has, not just its newest, so a
  run made last month still arrives carrying `no_team_factor`. Dropping the wording would have
  made the panel describe somebody's own history as "something this version cannot describe
  yet". Their tense changed — "it treats every task as independent" became "it treated" — because
  they now only ever describe a run that has already happened.
- **The fixture had to stop being an the simulation engine forecast.** `FORECAST` in `render.tsx` carried the two
  codes every the simulation engine run had; it now carries an assumption pair that is *not* zero and a limitation
  a plan can still earn. A fixture whose assumptions were both zero would let a screen that
  rendered them only when they were interesting pass, and one still carrying retired codes would
  have been describing a payload the server cannot produce.
- **`npm run test` passed while `npm run build` did not**, which is worth recording because it
  is the same lesson twice in one work. The interpolation helper first returned
  `Record<string, string>`, which is assignable to anything and told i18next nothing: the
  catalogue's typed keys only bite when the object's shape is known. Vitest does not typecheck,
  so 337 tests were green against a panel that would not compile. The type checker is the test
  here, and it is only run by the build.
- **The end-to-end contract was checked by name rather than by driving the app.** The field
  names in `ASKED_FOR`, in every input's `name`, and in `CreateForecastRequest` were compared
  directly, as were the response fields the client models against those `ForecastResponse`
  sends. That is the mismatch neither suite can see — step 4 broke the screen with all 332 tests
  green — and it is now matched on both sides. It is not the same as having watched the screen
  work, and it is what was done.
- **The panel is at 100% branch coverage and was not**, which the coverage gate caught: the
  `aria-invalid` and field-error branches for the team factor and the low end of the growth
  range had no test until one was written for them. Rejection branches are the point, and two of
  four new ones were missing.

---

## Step 6 — Close out ✅ *done*

- `../roadmap.md`: mark the common-cause model done and, with it, **The simulation engine** — the product exists. Retire the "scope
  uncertainty has no agreed position in a graph" thin spot by recording the answer and the three
  positions rejected.
- `../roadmap.md`: record what calibration, the throughput forecast and elicitation inherit — each of these two parameters is asked of a
  person now and derivable from history later, and that is a feature waiting on data rather than
  on a decision.
- `../roadmap.md` icebox: **correlation groups** ("items sharing a component, a person, or an unknown
  technology fail together") is now precisely one step beyond a thing that exists, so its entry
  should say so.
- `CLAUDE.md`: the composition rule of decision 6 — one makes items longer, the other makes more
  of them, and merging them would be one effect counted twice; the median-1 pinning and why a
  mean-1 factor would silently move every forecast; and decision 9's rule about version bumps.
- `../product-concept.md`: its *Independence is a lie* and *Unknown unknowns dominate the error*
  sections stop being design intent.

### As built — where it differs from the above

- **One of these edits is a correction to `../roadmap.md` rather than a status update.** Its
  209.4 → 222.2 table never said which factor produced 222.2, so the figure it holds up as the
  most valuable kind of test — a number produced outside this codebase — was not reproducible
  as written. The parameter was recovered by solving for it (a P90 of 1.30), the table now
  carries it, and the note says the general thing: **a measurement is only an oracle while
  every parameter that produced it is written down.** That is worth more than the row.
- **`CLAUDE.md` got two rules the step did not ask for**, both because they are invisible in
  review and neither would fail a test if broken. *A parameter set to none must consume no
  randomness* — a draw that changes no number still advances the generator and unreplays every
  stored forecast. And *retired is not deleted* — the two limitation codes stay in the enum and
  in the frontend catalogue because runs made before the common-cause model still carry them, and an enum missing
  a value present in stored JSON is a forecast that cannot be read at all. The step asked for
  the composition rule, the median-1 pinning and the version-bump rule; those are there too.
- **The thin spot was already struck through before this step ran**, having been answered when
  `common-cause-and-scope-growth.md` was written. What it needed was the difference between answered and built, plus
  the one thing the entry was missing: that the claim it settles on is the *weakest* of the four
  positions, and that the refinement which would sharpen it — weighting attachment by remaining
  effort — is named and deliberately not built.
- **The reason the calendar is next changed, rather than the ordering.** `../roadmap.md` had the calendar recorded as a
  *temptation to resist*: a date is what people ask for, the conversion looks like arithmetic,
  and the number being converted was knowingly too tight. That objection is spent, so the
  paragraph now says why the same work is the right next thing — and keeps the part that
  still holds, which is that the working-day assumption the calendar imports must stay crude and visible
  rather than becoming the first number in this product a server picked.
- **The icebox entry for correlation groups was made *less* encouraging, not more.** It read as
  "a modest step up from one shared factor", which is true of the sampling and false of the
  feature: the arithmetic is a few lines, and what it actually costs is a grouping somebody has
  to define — schema, a way to put an item in a group, a screen — plus one more question to ask
  per group, which is the part elicitation says is hard. Being one step from a thing that exists is a
  reason to describe the step accurately, not a reason to move it up.

### The review after the close-out

A pass over everything the common-cause model touched, after it was all in. No modelling error survived it, which
is the thing that mattered; what it did find was one efficiency regression, one caveat nobody
had written down, and three pieces of sloppiness.

- **The degenerate path was doing scope-growth work it had no scope growth for.** `finish` built
  the per-parent lists and copied the priority order on *every* run, discovered items or not —
  two passes over the whole plan added to the path every forecast takes whose owner answered
  zero, in the method whose own heap is hand-written to avoid boxing. Guarded on `found > 0`:
  **measured 359ms → 344ms** at five hundred items and ten thousand runs, consistently, with the
  golden equivalence test still exact. Smaller than it looked worth — 4%, not the 25% suspected —
  and kept because a path that does no work for a feature it is not using is the right shape
  regardless.
- **Growth is a share of the plan as it stands, and growth already realised is not deducted.**
  Nothing in the model knows what a plan looked like when it began, so a plan that has already
  grown is told to expect the full percentage again, and finished work counts towards the base.
  Both are defensible — the question asked is how much a plan like this one turns out to hold —
  and neither was written down anywhere. Now recorded on `ScopeGrowth.sample`, alongside the
  same answer decision 3 gives: it stops being a guess when calibration can read a plan's own history.
- **`MAX_SAMPLE_COUNT`'s javadoc quoted a cost this work had changed.** The worst case a caller can ask
  for went from roughly three seconds to roughly nine, because every run now schedules what it
  discovered as well. The bound stays — it was never what protects the server — but a comment
  stating a measured number that has moved is worse than no comment.
- **`ASKED_FOR` had quietly acquired a second duty**: it names the fields `useFormFailure` knows
  about *and* is now what the request body is built from. That is true and stable for this form,
  and it was implicit. Said out loud on the constant instead.
- **The empty state claimed four questions.** There are four required boxes and three questions —
  the growth range is one question with two ends, which is the whole reason it is a `fieldset`
  with one legend. The count is gone rather than corrected, since it would go stale again.
- **A test built one request body by string surgery on another** (`.replace("2000", "0")`), which
  works only while that substring appears once. Replaced with an overload that takes the sample
  count, so there is one template and no cleverness.

---

## Migrations

| | |
|---|---|
| `V13__forecast_assumptions.sql` | three columns on `forecast_runs`, backfilled to zero |

**The backfill is the interesting part and it is one word long.** Every existing run had no team
factor and no scope growth, so zero is not a placeholder chosen because something had to go
there — it is a correct record of what those runs assumed. That is the difference between a
backfill that preserves history and one that invents it, and it is only available because
decision 9 made the old engine a special case of the new one rather than a thing that used to
exist.

---

## Sequencing and risk

**The risk here is different from the simulation engine's, and worse.** the simulation engine could be wrong and be caught by
arithmetic. The common-cause model's two features have no whole-plan closed form — that is why they are separate —
so the defence is three narrower oracles and a set of directional properties. **The single most
valuable test in this work is the degenerate-equivalence one**, because it converts the
entire the simulation engine test suite into a regression suite for the common-cause model at the cost of one assertion. Write it in
step 1, not step 3.

**Three things that will look like bugs and are not**, in the order somebody will report them:

- **The band gets much wider and the middle barely moves.** That is the team factor working, and
  it is the entire point: the P50 was never the problem.
- **Two forecasts of the same unchanged plan differ.** They will, by more than they did under
  the simulation engine, because scope growth is a count and counts are lumpy. Seeds are stored precisely so this
  is investigable rather than arguable.
- **A small plan sometimes grows and sometimes does not.** Stochastic rounding, working as
  designed — and the alternative is a plan of ten items that can never grow by 4%.

**The thing most likely to be quietly broken** is the generator consumption order. Every
reproducibility guarantee in this engine, and the version-1 replay of decision 9, depends on
draws happening in a fixed order — including *not* happening when a parameter is zero. It is
invisible in review, it breaks nothing that a normal test would notice, and it silently
invalidates every stored run. The test that guards it is cheap and belongs in step 3.

**What this work must not absorb.** Correlation *groups* — per-component, per-person, per
unknown-technology factors — are the obvious next thought once one global factor exists, and they
are in the icebox for a reason: they need a grouping somebody has to define, which is schema and
UI, and one shared factor already captures most of the effect at a fraction of the cost.
Proposing either parameter from history is calibration/the throughput forecast's data and elicitation's framing, and doing it here
would mean inventing a default in the one place this plan refuses to.
