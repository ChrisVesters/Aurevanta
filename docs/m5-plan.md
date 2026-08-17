# M5 — Elicitation that produces honest ranges: implementation plan

> **Scope.** `roadmap.md` M5: replace what the estimate form *asks*, so that three numbers stop
> being 3/5/8. Surprise framing to collect the two ends, a betting check to confirm the high one,
> and two warnings that fire on ranges worth a second look. Excluded: comparative framing against
> the team's own history (needs M8's actuals — decision 4), the appearance of anything (the
> interface rework under *Future*), estimating in days rather than hours (M11 owns what a day is
> worth), planning-poker sessions and Delphi rounds (icebox), and anything that changes what the
> engine does with the three numbers once it has them (nothing here does).
>
> **How to read this.** Decisions first. The one that decides whether this milestone is right or
> merely finished is decision 2 — *the order the three questions are asked in* — because every
> other part of this plan is defence in depth behind it, and the measurement below says the
> defences do not hold on their own.
>
> **Why this is not optional, and why it cannot be checked.** `product-concept.md` is blunt:
> three boxes labelled P10/P50/P90 produce 3/5/8 without thinking, "which is strictly worse than
> no tool because the garbage now carries a probability". Every milestone since has been built on
> top of that form — M3 samples what it collects, M4 turns it into a date somebody will act on —
> so this is the input to a machine that has spent four milestones learning to be trusted.
>
> **And here is the milestone's own failure mode.** M3's was a plausible number and M4's a
> plausible date; **M5's is a form that feels better and changes nothing.** There is no test that
> can fail when elicitation does not work. The only instrument that can ever answer it is M8's
> calibration record, years from now, and only if the rows say which way they were asked — which
> is the whole of why decision 8 stores a column that has exactly one value on the day it ships.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | What makes an estimate worth questioning ✅ *done* | M3 |
| 2 | Recording how a range was asked for ✅ *done* | M2 |
| 3 | One question at a time, in the order that stops them anchoring ✅ *done* | 1, 2 |
| 4 | The review, and the bet | 3 |
| 5 | Close out | 1–4 |

**M5 changes no stored number and no arithmetic.** `p10_hours`, `p50_hours` and `p90_hours` mean
exactly what they meant, `LogNormalFit` fits them exactly as it did, and `Engine.VERSION` does not
move. One column is added that describes *how the question was put*, and everything else in this
milestone is a screen. That is deliberate, and it is what makes the milestone auditable later:
if calibration improves, nothing but the question changed.

---

## The measurement this plan is built on

Before designing anything, the two checks `roadmap.md` proposes were run against the failure they
exist to catch. `consistency` is the stated middle over the middle the two ends imply
(`LogNormalFit.consistency`, already built); the overconfidence rule is the roadmap's own "flag a
P90 less than ~1.5× the P50".

| Estimate | Implied median | Consistency | P90/P50 | Flagged? |
|---|---|---|---|---|
| **3 / 5 / 8** | 4.90 | 1.02 | 1.60 | **no** |
| **2 / 3 / 5** | 3.16 | 0.95 | 1.67 | **no** |
| **5 / 8 / 13** | 8.06 | 0.99 | 1.63 | **no** |
| **1 / 2 / 3** | 1.73 | 1.16 | 1.50 | **no** |
| 8 / 16 / 40 | 17.89 | 0.89 | 2.50 | no |
| 2 / 4 / 12 | 4.90 | 0.82 | 3.00 | no |
| 5 / 10 / 40 | 14.14 | 0.71 | 4.00 | no |

**Every Fibonacci triple passes both checks comfortably.** Consistency lands within 5% of perfect
on three of the four, because a geometric-ish sequence is exactly what a log-normal fit expects —
the numbers agree with each other beautifully. And the ratio sits at about 1.6 on all of them,
which clears a 1.5 threshold. The canonical garbage this milestone exists to stop is **coherent
garbage**: internally consistent, plausibly shaped, and invisible to every test that can be run on
three numbers in isolation.

Two things follow, and they are the spine of this plan.

**The warnings are a backstop, not the defence.** Anybody who reads step 1 as the milestone has it
backwards. What the checks catch is somebody who typed carelessly in a *different* way — a middle
pasted between two ends that were thought about, or a range so tight it could not have been meant
— and those are worth catching. They are not what catches 3/5/8.

**The question is the only thing that can work**, because the fault is not in the numbers, it is
in the fact that they were never separately thought about. That is a property of *how they were
asked for*, and it leaves no trace in what was stored. Which is decision 8's whole argument.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What M5 replaces | **The questions, never the storage.** Three columns in, three columns out, same fit. |
| 2 | The order the three are asked in | **The unbounded end first, the middle last**, and the middle is never an anchor. |
| 3 | How many are on screen at once | **One.** A visible previous answer is the anchor the order exists to prevent. |
| 4 | Which framings ship | **Surprise** collects, **betting** confirms, two warnings advise. **Comparative waits for M8.** |
| 5 | Whether a warning can refuse | **Never.** A warning that blocks is a rule people learn to satisfy. |
| 6 | Where the thresholds live | **Once, in `forecast.model`, beside the function they bound.** |
| 7 | May `estimate` import `forecast.model`? | **Yes.** The one-way rule is between *features*; a package with no state is not one. |
| 8 | What gets stored about the asking | **The method, and not the warning.** One is unrecoverable, the other is arithmetic. |
| 9 | Does the three-box form survive | **No.** A fast path to the garbage is a fast path to the garbage. |
| 10 | What a colleague's estimate may show | **Names while you answer, numbers afterwards.** Two anchored estimates are one estimate. |

### Decision 1 — M5 replaces the question and nothing else

`estimates` keeps three `numeric(12, 2)` columns, immutably, with the estimator and the moment.
`LogNormalFit` keeps fitting from the two ends and reporting the middle as a signal. `Engine` does
not move and neither does its version.

This is worth stating because the temptation runs the other way. Better elicitation *feels* like
it should produce a richer answer — a fourth point, a confidence-in-the-confidence, a shape — and
every one of those is a schema change that would make every estimate written before M5
incomparable with every one written after. **The one thing this milestone must preserve is that
its own effect can be measured**, and M8 can only measure it by comparing like with like.

| Rejected | Why |
|---|---|
| A fourth point, or a stated distribution shape | `product-concept.md` settled the shape: log-normal from two ends, because the number nobody knows is the maximum. More points would be more boxes, which is the disease. |
| Per-point confidence ("how sure are you about the P90?") | A question about a question. If somebody is unsure of their P90, the answer is a wider P90, which is what the framing is for. |
| Storing the answers to the framing questions separately | They *are* the three numbers. A "surprise threshold" column and a `p90_hours` column holding the same value is one number stored twice, waiting to disagree. |

### Decision 2 — The unbounded end first, the middle last

**This is the decision this milestone exists to get right**, and it is the one that has no test.

Three numbers asked together anchor on whichever is answered first, and the form as it stands
invites the middle to be answered first — it is the easiest, it is in the centre of the row, and
it is the number people have an opinion about without thinking. Everything after it becomes "the
middle, plus a bit" and "the middle, minus a bit", which is precisely the shape of 3/5/8 and
precisely why that shape passes every consistency check: it *is* internally consistent, around an
anchor nobody examined.

So the order is:

1. **The bad case** — *"think of a version of this that goes badly. Not a disaster: a bad week.
   What number would make you genuinely surprised to have gone over?"*
2. **The good case** — *"now the version where everything goes right. What is the least this
   could take?"*
3. **The typical case** — *"and what do you actually expect?"*

**The high end goes first because it is the only one of the three that is unbounded.** The good
case has a floor — the work obviously takes something, and nobody's optimistic answer is zero —
so an anchor above it compresses it much less than an anchor below the bad case compresses that.
The bad case has no such floor above it: it can always be worse, which is the property
`product-concept.md` chose the log-normal for, and it is the number every team gets wrong in the
same direction. Asking it before any number exists on screen is the only moment it can be answered
cold.

**The middle goes last because the fit does not use it.** It is the consistency signal, and a
signal that was derived from the two ends by arithmetic in somebody's head is not a signal. Asking
it last means it is the one number that *may* be anchored, and it is the one number that can afford
to be.

| Rejected | Why |
|---|---|
| Low, then high, then middle | The natural reading order and the wrong one. The low end is the number people are most confident about, so it anchors hardest, and it anchors the end that most needs to be free. |
| Middle first, then the ends outward | What the current form invites, and the failure this milestone is named after. |
| Middle first, then "how much worse could it be?" as a multiplier | Superficially attractive — it is how M3b asks its team factor — and wrong here. M3b's multiplier has no natural anchor to corrupt; an estimate does, and asking for a multiple of it guarantees the tail is a function of the middle rather than of the work. |

### Decision 3 — One question on screen at a time

The order in decision 2 buys nothing if all three boxes are visible while any of them is being
answered, because the anchor is *seeing* the earlier number, not typing it. So the form is three
steps, one question each, and **a previous answer is not on screen while the next is asked**.

All three appear together at the review (step 4), which is where they are meant to be seen
together: that is the moment to notice that the bad case is only a little above the typical one,
and the moment the warnings mean anything.

**The cost is real and is not hidden.** Estimating forty items becomes forty times three steps
plus a review, where it was forty times one form. The mitigations are that revising pre-fills from
the estimator's current range (so a revision is three confirmations rather than three fresh
answers), and that steps are keyboard-navigable with the answer submitted on Enter. What is *not*
a mitigation is a way to skip it — see decision 9.

| Rejected | Why |
|---|---|
| Three boxes, revealed one at a time but staying visible | Half the change and none of the effect. The point is not pacing, it is that the second answer is given without the first in view. |
| Hiding earlier answers at the review too | Then nobody can see that their range is absurd, which is the one thing the review is for. |

### Decision 4 — Surprise collects, betting confirms, comparison waits

`roadmap.md` names four things. They are not four of a kind, and treating them as a list to
implement is how this milestone becomes three milestones.

- **Surprise framing is how the numbers are collected.** It is decision 2's wording, and it is
  already load-bearing elsewhere in this codebase: `m3b-plan.md`'s decision 2 asks its team factor
  as "in a bad stretch, how much longer does everything take" for exactly this reason — *it is the
  only form of the question a person can actually answer*.
- **Betting framing is a check on an answer, not a way to get one.** "Would you take 9-to-1 odds
  that this comes in under 40 hours?" needs an X to bet about, so it can only run after the high
  end exists. It lives on the review, where it does the job it is good at: making a number that
  was typed cheaply feel expensive.
- **Comparative framing needs a reference class this product does not have.** "Bigger or smaller
  than the auth migration in March?" is only reference-class forecasting if March's actual is
  known. Today the only comparison available is against *other estimates* — that is comparing a
  guess with a guess, and it would industrialise anchoring across a whole plan rather than within
  one item. **It moves to M8**, where actuals exist, and it becomes much better there than it
  could be here.
- **Overconfidence and consistency warnings** ship, as step 1, with the measurement above
  attached to them so that nobody mistakes them for the defence.

### Decision 5 — A warning never refuses

Both checks advise and neither blocks. The estimate is stored exactly as given, warned or not.

Two reasons, and the second is the one that matters. A tight range is sometimes **correct**: a
task done thirty times has a genuinely narrow band, and a rule that refused it would be a rule
about the tool rather than about the work. And a warning that blocks becomes a specification —
people would learn that a P90 of 1.5× the P50 is what the machine wants, and type it, which is
3/5/8 with an extra step. **The product would be teaching the failure it exists to detect.**

This is the same shape as `progress_not_applicable` seen from the other side: that refuses rather
than silently trimming, because the person must not be overruled without being told. Here there is
nothing wrong to refuse — the numbers are a claim somebody is entitled to make — so the answer is
to say what is odd about it and store what they said.

### Decision 6 — One threshold, stated once, beside the function it bounds

`ForecastService.CONSISTENT_ENOUGH` is a quarter either way, and it currently lives in a service
in the `forecast` feature. Step 1 needs the same number in `estimate`, and a copy would be two
rules that can disagree about one estimate — `PasswordRules` exists in `user` to prevent exactly
that, and `m3a-plan.md` names it by name when handing this problem over.

So both thresholds move to `forecast.model`, beside `LogNormalFit.consistency` and the arithmetic
they bound, and `ForecastService` reads them from there. The overconfidence ratio is new and is
born there.

**The frontend gets neither number.** It renders a warning the server sent, the way it renders a
`code` rather than prose. A browser that decided for itself whether a range was too tight would be
the third copy.

### Decision 7 — `estimate` may import `forecast.model`, and that does not reverse an arrow

`CLAUDE.md` states the domain packages depend in one direction only: `forecast` points at all
four, `estimate` points at `item`, nothing points back. Decision 6 has `estimate` importing
`LogNormalFit`, which lives under `forecast`.

**The rule being kept is "no cycles between features", and `forecast.model` is not a feature.** It
has no entity, no repository, no service and no controller; it holds no Spring, no JPA and no I/O;
it does not know what a work item is and cannot be made to. It is imported the way `java.lang.Math`
is, and a package that imports nothing from this codebase cannot participate in a cycle in it.
`forecast` the feature stays untouchable from below: `estimate` gains no visibility of a run, a
capacity or a seed.

| Rejected | Why |
|---|---|
| Move `LogNormalFit` and `Normal` to a shared package | Correct in the abstract, and it would relocate the most carefully tested arithmetic in the product to make a point about naming. `forecast.model`'s own doc already says it is separated by purity rather than by feature; this is that sentence being taken at its word. |
| Compute the ratio in the browser | Two rules about one estimate, which `m3a-plan.md` rejected in advance and by name. |
| Publish the threshold to the browser so it can decide | A worse version of the same thing: the number would be right and the *rule* would still be written twice. |

### Decision 8 — The method is stored; the warning is not

`estimates` gains `elicitation_method`. `V15` backfills every existing row with `three_point` and
then drops the default, which is `V13`'s move and deliberately not `V14`'s: that backfill is
**true**. Every estimate written before this milestone really was typed into three boxes.

**This is the only instrument that can ever say whether M5 worked.** M8 measures how often
somebody's band contained the truth; the question this milestone raises is whether *changing the
question* changed that, and answering it means partitioning the calibration record by how each
estimate was asked for. Without the column the only proxy is `created_at` against a deploy date
that lives nowhere in the database — which is the reconstruction these documents exist to avoid.

**The warning is not stored, and the contrast is the point.** "This estimate was flagged and
submitted anyway" is recomputable from three numbers and one constant, so it is derived rather
than kept — M4's decision 5, applied again. The method is not recoverable from anything at all,
which is exactly why it is a column. *Store what cannot be worked out later; derive what can.*

| Rejected | Why |
|---|---|
| Add the column when a second method exists | It exists now: this milestone creates the second one, and the rows on each side of it are the comparison. |
| An enum on the entity | `priority_rule` and `calendar_rule` are strings held by the class that implements the rule, and a stored value an enum has never heard of is unreadable rather than merely unknown. Same shape, same answer. |
| Store the warnings so M8 can ask "did people ignore them?" | M8 measures whether the band held, which is better evidence than whether a heuristic fired. And a stored flag would freeze today's threshold into rows that outlive it. |

### Decision 9 — The three-box form goes, and nothing replaces it as a fast path

`EstimateForm` is replaced rather than kept beside the new one.

An "advanced" or "I know what I'm doing" three-box path would be used by everybody, because it is
faster and because the people most confident they do not need the framing are the people the
framing is for. **A fast path to the garbage is a fast path to the garbage.** The milestone is not
"offer a better way to estimate", it is "stop asking the question that produces 3/5/8".

**The strongest objection is revision**, and it is answered rather than dismissed: changing an
estimate from 8 to 10 hours should not cost three fresh answers. The form pre-fills every step
from the estimator's current range, so a revision is three confirmations — and revision is
precisely the moment to look at the tails again, since something has been learned since.

The honest way to reopen this is the column from decision 8: if a second method is ever added, its
calibration can be compared with this one's, and the argument stops being about taste.

### Decision 10 — Names while you answer, numbers afterwards

The plan screen already shows *who* has estimated an item and shows only *your own* numbers on the
row. That was not built as an anti-anchoring measure and it is one, so it is now a rule rather
than an accident: **a colleague's numbers are not on screen while somebody is answering.**

This is what keeps multi-estimator meaningful. `m2-plan.md` kept several current estimates per
item because two people disagreeing is signal for the engine to reason about; two people who
anchored on each other are not two estimates, and the band they produce is confidently narrow for
a reason nothing downstream can see.

The numbers stay available after submitting, on the row and to the forecast, where the comparison
is the point rather than an input.

---

## Step 1 — What makes an estimate worth questioning ✅ *done*

**Goal.** The two checks exist as arithmetic, in one place, and an estimate carries what they say.

- `EstimateQuality` in `forecast.model` — pure, like everything beside it. It holds:
  - `CONSISTENT_ENOUGH` (a quarter either way) and `TIGHT_BAND` (a P90/P50 ratio of 1.5),
    moved out of `ForecastService` and born there respectively — decision 6.
  - `of(p10, p50, p90)` → the consistency ratio and two booleans, over primitives.
- `ForecastService` reads its threshold from there instead of holding one.
- `EstimateResponse` gains `consistency` (the ratio) and the two flags. Computed on the way out,
  never stored — decision 8.
- `estimate` imports `forecast.model` for the first time; nothing else about the package graph
  moves — decision 7.

**Tests.** 3/5/8 raises neither flag, asserted with the ratios in the assertion so that the
measurement above is in the suite rather than only in this document; the other three Fibonacci
triples do the same. A P90 of 1.4× the P50 is flagged as tight and 1.6× is not, either side of the
boundary. A middle a long way from its implied one is flagged and one within a quarter is not,
either side of that boundary. Equal ends — somebody claiming certainty — are consistent and tight
rather than an error, since M2 accepts them. `ForecastService` produces `inconsistent_estimates`
for exactly the estimates `EstimateQuality` flags, so the two can no longer drift.

**Done when** no two places in this application can disagree about whether one estimate is worth
questioning.

### As built — where it differs from the above

**The flags are named `inconsistent` and `overconfident`**, which the bullets left open, and the
second name is a claim about a *range* rather than about a person — the javadoc says so, because
"overconfident" is the roadmap's word and it describes a pattern that usually means nobody thought
about the bad case and sometimes means they know the work. `EstimateQuality` is a record of the
three derived values with a static `of`, matching `LogNormalFit`'s posture in the same package
rather than storing the three inputs and recomputing the fit once per accessor.

**The measurement is in the suite twice, at two altitudes, and that is deliberate.**
`EstimateQualityTests.theCanonicalGarbagePassesBothChecks` carries all four Fibonacci triples with
their ratios in the assertion, so anybody who "fixes" a threshold has to come and read why it is
where it is. `EstimateApiTests.theCanonicalGarbageIsReportedAsPerfectlyFine` asserts the same
thing at the seam where somebody would actually meet it — 3/5/8 comes back over HTTP saying
nothing is wrong with it. A test that asserts a failure is unusual enough to be worth naming: it
is the plan's central claim, and if it ever stops holding, the milestone's design has changed and
somebody should notice.

**One test the bullets asked for was written as a loop over both sides of the threshold rather
than as a fixed expectation.** `thePlanReportsAnInconsistentEstimateExactlyWhenTheEstimateItselfDoes`
asks `EstimateQuality` what it thinks and asserts the forecast's limitation matches, for two ranges
either side of the bound — so moving `CONSISTENT_ENOUGH` moves both together and the test does not
have to be edited to stay true. Asserting a hard-coded "this one is flagged" in both places would
have been two expectations to keep in step, which is the duplication decision 6 exists to remove,
reintroduced in the tests.

**The frontend `Estimate` type and its three fixtures were updated**, which step 1 did not ask for
and step 4 would otherwise have had to do. The type is a description of the wire and the wire
changed; leaving it behind would be a small lie for three steps. The fixtures carry what the server
would really compute for those ranges rather than convenient values — a double more opinionated
than the thing it stands in for is the failure `CLAUDE.md` already records about `fetch` and empty
bodies. Nothing renders them yet.

**`ForecastService` lost a constant and a `LogNormalFit` import** and reads both from
`EstimateQuality` now. That is the whole of decision 6 landing, and it is why the estimate suite
and the forecast suite can no longer disagree.

---

## Step 2 — Recording how a range was asked for ✅ *done*

**Goal.** Every estimate says which question produced it, so that M8 can eventually say whether
the question mattered.

- `V15__estimate_elicitation.sql`: `elicitation_method varchar(40) not null default 'three_point'`,
  then **drop the default** in a second statement — `V13`'s two-step exactly, and for `V13`'s
  reason: the backfill is true, and nothing after it may leave the question unanswered.
- `Elicitation` in `estimate` holds the names: `THREE_POINT` and `SURPRISE_FRAMED`. Strings, like
  `priority_rule` and `calendar_rule`.
- `RecordEstimateRequest` gains `method`, required and validated against the known names — a
  refusal with its own code rather than an unmapped constraint, since an unrecognised method is a
  client saying something this server cannot record truthfully.
- `EstimateResponse` reports it back.

**Tests.** A recorded estimate stores the method it was told and reports it. A method nothing
recognises is refused, and no row is written. A missing method is refused against its own box. A
row written before this milestone reads back as `three_point` — asserted against a row inserted
directly, since nothing can create one through the API any more. The column has no default, so an
insert that omits it fails rather than being assigned one.

**Done when** the calibration question M8 will ask is answerable from the rows rather than from a
deploy date.

### As built — where it differs from the above

**The backfill got a migration test, which the bullets did not ask for and `V13` never had.**
`EstimateElicitationMigrationTests` brings the database to `V14`, inserts an estimate of the shape
that version produced — three numbers and no word about how they were asked for — migrates to
`V15`, and asserts it reads back as `three_point`. It then asserts the two halves of the second
statement: an insert omitting the column now fails, and `information_schema` reports no default to
inherit. The reason it is worth the fixture chain (tenant → user → project → work item → estimate)
is that this column's entire value is its trustworthiness: a backfill that quietly missed rows, or
a default that let later rows be handed a method nobody stated, would **corrupt** the evidence
rather than lose it, and corrupted evidence still looks like data. `V13` made the same kind of
claim about every row in `forecast_runs` and nothing has ever checked it.

**That test also absorbed two of the step's own bullets**, which as written could not both be
true. "A row written before this milestone reads back as `three_point` — asserted against a row
inserted directly, since nothing can create one through the API any more" was written assuming
step 3 had landed: today the API still records `three_point`, because the form still asks three
boxes. And "an insert that omits it fails" cannot be asserted against the same directly-inserted
row that the backfill is supposed to have filled in. Splitting them across the migration boundary
is what makes both testable, and it is a better test than either bullet described.

**The frontend was kept working rather than broken until step 3.** M4's step 2 left the browser
sending a request the server refused, and said so. Here that was avoidable and avoiding it is more
honest: the existing three-box form genuinely *is* `three_point`, so it says so from today.
`EstimateForm` owns the constant — the component that asks is the one that knows how it asked, so
when the questions change in step 3 the name changes in the same edit. A caller that merely posts
could not have made that claim truthfully.

**The wire calls it `method` and the column calls it `elicitation_method`**, which the bullets left
open. A field inside an estimate document needs no qualifier; a column sitting beside `created_at`
in a table of estimates does. The request and the response use the same name as each other, which
mattered more than matching the column the way `priorityRule` does.

**`Elicitation.require` returns `void` and throws**, rather than returning a normalised value: the
service already holds the string, and a method that hands back what it was given invites somebody
to think it changed something.

**Three pre-existing tests needed their raw JSON bodies widened**, and one of them is worth
noticing: `refusesAnOutOfOrderEstimateWithoutSayingWhetherTheItemExists` began failing with
`validation_failed` rather than `estimate_out_of_order`, because a body missing the new required
field is refused by Bean Validation before the service runs at all. That is correct behaviour and
a reminder of the ordering: field validation, then document-level facts about the request, then
anything that looks a row up.

---

## Step 3 — One question at a time, in the order that stops them anchoring ✅ *done*

**Goal.** The form asks three questions in decision 2's order, one at a time, and sends
`surprise_framed`.

- `EstimateForm` becomes three steps and a submit, replacing the three boxes entirely — decision 9.
  Bad case, good case, typical case, in that order and no other.
- **One question on screen**, with no earlier answer visible — decision 3. Back and forward move
  between steps; going back shows that step's own answer, because that is revision rather than
  anchoring.
- Each step is its own wording from the catalogue, and each carries the framing rather than the
  percentile name: nothing on screen says "P90" while a question is being answered.
- Revising pre-fills every step from the estimator's current range.
- The unit line stays where it is — hours of effort, not a duration and not a date.
- A colleague's numbers are not rendered anywhere on this form — decision 10.

**Tests.** The three questions appear in decision 2's order, and only one at a time; the second
question's screen does not contain the first answer. Going back shows what was typed and going
forward again keeps it. A submitted estimate sends the three numbers under the names the server
takes plus `surprise_framed`. Revising starts each step from the current estimate. No step renders
the string "P90", "P50" or "P10" — asserted, because those labels reappearing is exactly how this
form regresses. A colleague's range is not on screen at any step. Every string comes from the
catalogue.

**Done when** nobody can answer the middle first.

### As built — where it differs from the above

**A refused submission navigates, which the bullets did not mention and the form cannot work
without.** On a one-question-at-a-time form the box a complaint belongs to is almost never the one
in view, and `useFormFailure` suppresses the banner precisely because the field *is* one this form
renders — so without this, a refused `p10Hours` would be rendered on a screen nobody is looking at
and nothing would appear at all. Two rules: a complaint about a field brings that field's own
question back, and `estimate_out_of_order` returns to the first question, because it belongs to
all three and reading them in order is the only way to see it. The second uses the `code`
`useFormFailure` hands back, which its own doc says exists for "the rarer case where one particular
failure has an *action* attached" — this is the second such case, after sign-in's confirmation
link.

**Every step submits, so Enter moves somebody on rather than sending a third of an answer.** The
"Next" button is a submit button and `handle` advances instead of posting until the last question.
The obvious alternative — a `type="button"` Next — leaves Enter posting one answer and two blanks
from question one, which the server would refuse for two fields the visitor has not been asked
about yet.

**`numberFrom` was split out of `numberField`.** A form that no longer renders the boxes it is
submitting cannot read them out of `FormData`, and re-deriving "an empty box is nothing, not zero"
at a second call site is exactly how the two come to disagree — which is the bug `numberField`'s
own comment exists to describe. One rule, two callers.

**The catalogue's `fields: { p10Hours: 'P10', … }` block was deleted rather than left unused**, and
that is decision 9 landing in the one place it can be enforced: the test setup fails any test that
renders a key with no translation, so a percentile label cannot quietly come back as a "clearer"
one. The assertion that no step renders `/\bP(10|50|90)\b/` is the belt to that brace.

**The tests write the three questions out as literals** rather than importing them from the
catalogue. That is deliberate duplication: a wording change has to be made in two places, and the
*order* is then asserted against text a reader can check by eye rather than against an array the
component also owns.

**A progress line was added** — "Question 1 of 3" — which the bullets did not ask for. With one
question on screen and no answers visible, there is otherwise nothing at all that says where
somebody is or how much is left.

**The three answers are not yet seen together anywhere**, and that is step 4's to fix. Decision 3
says they appear at the review; until it exists, the last question submits directly. That makes
the current state slightly worse than the end state in one specific way — a range whose good case
is above its bad case is refused by the server rather than caught on screen — which the navigation
rule above is what makes survivable in the meantime.

---

## Step 4 — The review, and the bet

**Goal.** The three answers seen together for the first time, with what is odd about them and one
question that makes the high end feel expensive.

- A fourth screen showing all three, in plain words rather than percentile names.
- **The bet**, from the number just given: *"you are saying nine times in ten this comes in under
  {p90}. Would you take that bet?"* — a prompt, not a control that gates anything. Answering "no"
  sends the estimator back to the bad-case step with their answer in it.
- The two warnings from step 1, rendered from the server's flags rather than re-derived — decision
  6 — and **neither blocks submission**, decision 5.
- Submitting writes the estimate and closes the form, as the current one does.

**Tests.** All three answers appear together here and nowhere earlier. A tight range shows the
overconfidence warning and submits anyway; a middle far from its ends shows the consistency
warning and submits anyway; a range with neither shows no warning. Declining the bet returns to
the bad-case step with the answer still in it, and changing it changes what the review shows. The
warnings are rendered from what the response carried, so a threshold change on the server needs no
change here. Every string comes from the catalogue.

**Done when** somebody has to look at their own range before it is stored.

---

## Step 5 — Close out

- `roadmap.md`: mark M5 done. Record that **comparative framing moved to M8** with decision 4's
  reason, since a reference class needs actuals and comparing guesses would industrialise the
  anchoring this milestone exists to remove.
- `roadmap.md`: record what M8 inherits — `elicitation_method` partitions the calibration record,
  which is the only way the question this milestone asks about itself can ever be answered; and
  the *estimate hygiene warnings* in the icebox now extend two checks that exist rather than one
  idea.
- `roadmap.md`: the *Reworking the interface* note says M5 replaces what is asked and the rework
  replaces how everything looks. Half of that is now spent, and the note should say which half.
- `product-concept.md`: *The input problem is harder than the maths* stops being design intent for
  surprise and betting framing, and keeps it for comparative framing. The *Distribution fitting*
  note's last sentence — pointing at *which* estimate is M5's — is discharged.
- `CLAUDE.md`: the estimate form asks one question at a time in a fixed order and why; warnings
  advise and never refuse; the thresholds are stated once in `forecast.model` and `estimate` may
  import it; `elicitation_method` is stored because it cannot be recovered, and the warning is not
  because it can.

---

## Migrations

| | |
|---|---|
| `V15__estimate_elicitation.sql` | one column on `estimates`, backfilled `three_point` and then stripped of its default |

**The backfill is true, which is what makes it `V13` and not `V14`.** M4's calendar columns were
left null because a run made before M4 assumed no working day — there was nothing honest to write.
Here there is: every estimate in the table was typed into three boxes, so writing `three_point`
records what happened rather than inventing it. The default then goes in a second statement, so
that nothing written afterwards can leave the question to the database.

---

## Sequencing and risk

**The risk in M5 is that it cannot fail visibly.** Every other milestone had an oracle: M3a had a
closed form, M3b had a byte-identical degenerate case, M4 had a calendar anybody can count on
their fingers. This one has a form that will be more pleasant to use and a hypothesis about human
judgement that no test in this repository can settle. **It will feel finished on the day it ships
and will not be answerable for a year.** That is not a reason to build it differently; it is the
reason decision 8 exists, and the reason step 2 is not the afterthought it looks like.

**The one that will actually go wrong** is the order quietly relaxing. Three separate screens are
more work to build and more work to use than three boxes revealed in sequence, and at some point
somebody will make the earlier answers visible "so people can see what they said" — which is the
whole mechanism removed while every test still passes and the form still has three steps. The
defence is decision 3, the assertion that the second screen does not contain the first answer, and
this paragraph.

**Two things that will look like bugs and are not.**

- **The warnings almost never fire.** The measurement above says why: the estimates most worth
  worrying about are internally coherent. A warning that fired often would be a warning nobody
  read.
- **Estimating got slower.** It did, by design, and the number that matters is whether coverage
  falls — a plan people stop estimating is worse than a plan estimated carelessly, because
  `unestimated_items` is a real limitation on a real forecast. **This is the thing to watch after
  shipping**, and the lever if it does fall is the revision path and keyboard flow, never a fast
  path back to three boxes.

**What this milestone must not absorb.** Making the product *look* good is the interface rework
under *Future*, and it will be more tempting here than anywhere, because this is the screen being
rebuilt and it is the ugliest one. They are different work: this replaces what is asked, that
replaces how everything looks, and a beautifully styled form eliciting the same garbage is the
outcome both notes exist to prevent. Planning poker and Delphi rounds are the icebox's, and they
are the multi-estimator *workflow* rather than the single-estimator question. Estimating in days
is M11's, since what a day is worth is a calendar question. The line to hold is that M5 changes
three questions and one column, and stops.
