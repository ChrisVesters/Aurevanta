# Aurevanta — what it does today

> **This document describes the product as built.** `product-concept.md` says what Aurevanta
> is *for* and why the idea is worth building; `roadmap.md` says what is left. This one says
> what exists, and it is the place to start. Where a decision looks arbitrary, the reasoning
> is in `design/` — one document per subject, written while it was being built.

---

## The idea, in one page

Every planning tool asks *how long will this take?* and takes a number back. The number is
always wrong, and the plan built from it inherits a false precision that nobody can see and
everybody acts on.

Aurevanta asks for a **range** instead — a good case, a bad case and a typical case — and
never adds those ranges up. Adding them is the error the whole product exists to remove:
percentiles do not sum, so a plan of forty tasks each "P50: two days" does not take eighty
days at any confidence anybody can name.

Instead it **simulates**. Ten thousand times it draws a duration for every piece of work from
the range somebody gave it, schedules them against the team's real capacity and the order the
work has to happen in, and records when the plan finished. The answer is not a date. It is a
distribution of dates, and from it you can ask the only question worth asking:

> **On what date am I 80% likely to be finished?**

Everything below is either how that number is produced, how it is made honest, or how it is
made readable.

---

## Accounts, organisations and teams

- **Identity is global; standing is per organisation.** One address, one password, one
  account — and a membership row for each organisation it belongs to, with a role in each. A
  consultant with three clients has one account and three memberships.
- **Registering creates an organisation and hands back no session.** An address must be
  confirmed before it can hold a token, and registration cannot hand one out or the gate would
  be skippable by signing up.
- **Organisations gain members by invitation.** An owner invites an address; the link works
  once, and whether the recipient already has an account decides what they are asked for. A
  link mailed to a mailbox proves control of the mailbox, never ownership of the account
  registered with it, so somebody signed in as another person is refused rather than joined.
- **Roles govern administration only** — invitations, members, the organisation's name and
  handle. Every member may do everything to a *plan*, because estimation is a team activity
  and multi-estimator support is meaningless if only an owner may estimate.
- **An organisation always keeps at least one owner.** Demoting or removing the last one is
  refused, whoever asks.
- **Passwords can be reset and addresses re-confirmed**, both by emailed single-use links,
  both rate-limited per recipient and per source.

## Plans, and the work in them

- **A plan is a project**: a name, an optional description, and up to five hundred items.
- **A work item is a task**: a title, an optional description, and a status — not started, in
  progress, or done.
- **Progress is reported, not observed.** Its own endpoint, separate from renaming, so that
  editing a title cannot overwrite the dates a forecast reads. A state that needs a date and
  did not get one is refused rather than stamped with the server's clock, and a claim carrying
  what its status cannot hold is refused rather than trimmed.
- **Dependencies are finish-to-start with a lag.** Both ends go in the body; the graph is
  checked for cycles under a lock, and a refusal carries the loop it would have closed so
  somebody can go and look at it.
- **Nothing is deleted.** Plans and items are archived, and any member can bring one back. An
  estimate is evidence the track record reads years later, and deleting the item it hangs on
  would destroy that evidence.

## Estimates

- **Three numbers, asked one at a time, in a fixed order**: the bad case, then the good case,
  then the typical case — and no earlier answer is on screen while the next is asked. Three
  boxes side by side invite the middle to be answered first, which is what 3/5/8 is: a number
  nobody examined, plus a bit and minus a bit.
- **Nothing on the form names a percentile.** "P90" asks somebody to reason about tail
  probability, which nobody can do. Surprise is a thing people recognise.
- **An estimate is written once and never rewritten.** A revision is a new row. The question
  the track record asks is *what did you say at the time*, and only rows nothing rewrites can
  answer it. It also means no member can rewrite a colleague's estimate, without anybody
  having to enforce it.
- **Several people may hold a current estimate on one item.** That is not a conflict to
  resolve — it is the disagreement, and the forecast reports it by coming out wider.
- **Two warnings, and they advise rather than refuse**: a band that is suspiciously tight, and
  three numbers that disagree with each other about the shape they imply. Both are measured
  against the failure they exist to catch, and neither catches a Fibonacci triple — which is
  why the *order the questions are asked in* is the real defence and these are a backstop.

## The forecast

- **A Monte Carlo simulation over the whole plan.** Each item's range is fitted to a
  log-normal; ten thousand runs draw a duration for every item and schedule them.
- **The aggregator is a scheduler, not a sum.** Work waits for what it depends on and competes
  for the team, so the answer reflects the plan's shape rather than its total.
- **Work already under way is drawn from what is *left*** — a truncated draw, conditioned on
  the hours already spent. A task that has run long has more remaining, not less.
- **Two effects the arithmetic cannot see are modelled explicitly.** A shared team factor, so
  that good and bad luck stop cancelling across a plan; and scope growth, so that the work
  nobody has written down yet is in the answer. Both are stated by the person asking, and
  neither has a default — zero is a claim, not a neutral setting.
- **Every run is stored with its seed, its inputs and its engine version**, so any forecast
  this product has ever given can be reproduced exactly. That is what lets every question
  below be answered about the past rather than only about new runs.
- **Every forecast reports what it did not do.** Unestimated items, arrows into archived work,
  requirements on resources the team no longer has — printed beside the number, never behind a
  disclosure.

## Reading a forecast

- **A date, at a confidence you choose.** 50%, 80% or 95%, and moving between them sends no
  request: all five percentiles are already in the answer, so the trade is immediate.
- **A sentence anybody can read.** *"There is an 80% chance that Q3 platform work will be
  finished by 25 August."* One date, never a window — a two-sided interval invites *so not
  before the 12th?*, which is a question nobody manages against.
- **The band in hours stays on screen.** Remove it and nothing visible came out of the engine.
- **Every assumption that produced the number is printed beside it** — the capacity or the
  team, the working day, the two uncertainty parameters, the start date and the calendar rule.
- **A burn-up**, with a cone drawn from the plan's own delivery history rather than from the
  engine's band. The table is the feature; the drawing carries nothing the table does not.

## Questions you can ask of a forecast

Each of these replays a stored run and writes nothing, so all of them work on forecasts made
months ago.

- **What is making this uncertain?** Every item ranked by how much it widened the band, with
  the shared team factor and the unlisted work ranked alongside them — because when one of
  those tops the list, the honest answer is that no estimate is the problem. Shown as bars,
  never percentages: they overlap and would sum to well over one.
- **What do I cut to hit a date?** Name the work that is negotiable and each candidate is
  weighed against the same random numbers as the baseline, so the difference is the cut rather
  than the luck. It also searches for a set that reaches the bar, and says whether it got
  there, ran out of candidates, or ran out of budget.
- **What would another person buy?** One pool at a time, one unit at a time, so the
  diminishing return is visible rather than inferred. It can come back *later* — more people
  can be slower when the extra unit lets low-priority work tie up what the critical path
  needed — and it says so rather than showing a negative.
- **Why did the date move?** Two runs of one plan, and the distance between them split into
  what moved it: re-running, progress reported, estimates revised, scope changed, the team
  changed, assumptions changed, the calendar, and time simply passing. The terms add up,
  because each is measured with every earlier one already applied.
- **Is this plan drifting?** Measured as cumulative movement against the band's own width, not
  as a direction — a plan that is fine still moves out one week and in the next, and "out three
  times running" fires on 86% of healthy plans.

## A second opinion, with no estimate in it

- **A forecast from what the plan has actually delivered.** Items completed per calendar week,
  bootstrapped — no estimation anywhere in it, which is why it can answer on a plan nobody has
  estimated and why it is the honest check on one that has been.
- **Weeks with nothing finished are part of the history.** They are what "absorbs interruptions
  and holidays" means, and dropping them inflates the rate by exactly the fraction of the time
  the team was not delivering.
- **It cannot draw a week worse than the worst one it has seen**, and it says so: the window it
  used and its worst week are on screen, with a warning under a year of history.
- **The two forecasts are shown as two dates and never as one number.** Four things differ
  between them and two make each look slow, so a subtraction is not interpretable. "Six weeks
  against eleven" starts a conversation; a number in the middle ends one.

## The track record

- **How often the ranges written here contained what actually happened.** Organisation-wide,
  because one plan holds far too few finished items to tell 45% from 80%, and because
  calibration is a property of people rather than of plans.
- **Only estimates written before work started count as forecasts.** Anything written later is
  reported separately as a *report* — what somebody said once they could see how it was going —
  and everything on finished work with no reported start is a third bucket. The three are never
  added.
- **The hit rate ships with a correction and never alone.** Estimating one to a thousand hours
  contains every outcome and scores 100% forever; the band-width multiplier reports that as a
  number below one. The two are structurally inseparable in the API.
- **Nothing is applied and nobody is ranked.** The correction is reported, never fed back into
  a forecast — that would close a loop on its own evidence. People are named in name order,
  with their counts and intervals, because a hit-rate leaderboard is won by estimating badly.

## Resources

- **A resource is a named pool with a unit count** — *Backend engineers × 3*, *Staging
  environment × 1*, *Ada × 1*. A person is a pool of one; there is no second concept.
- **Work says how many units of which pools it ties up** while it is being done. Units are
  occupancy, never speed: two units means the work holds two, not that it goes twice as fast.
- **Work that names nothing takes one unit of whichever pool is free.** It follows that a team
  nobody has annotated behaves exactly like the capacity it adds up to — so describing a team
  changes nothing on its own, and one requirement is enough for it to start mattering.
- **A capacity number is a lower bound, not an approximation.** The same six people read as six
  interchangeable slots finish 14–59% earlier than the same six split into pools work cannot
  cross between, and the error only ever runs one way.
- **Nothing here reports on anybody.** A pool may name a person, and that is a label for
  finding them. There is no screen that says what anybody is working on.

---

## What it deliberately does not do

- **No boards, no workflow, no states work moves through.** It is not a tracker, and it does
  not want to be the place work is managed.
- **No single-point estimates anywhere.** There is no box to type "5 days" into.
- **No suggested schedule.** It evaluates the plan implied by the priority rule; it does not
  propose a better one.
- **No date without its assumptions.** Every number that leaves this product carries what
  produced it.
- **No forecast corrected by its own track record.** The record is reported and never applied.

---

## Shape of the thing

- **Backend** — Spring Boot on Java, PostgreSQL with Flyway migrations, a REST API under
  `/api` returning RFC 9457 problem documents with machine-readable codes. Multi-tenant, with
  isolation enforced in the application and every tenant-owned table carrying a tenant id.
- **Frontend** — a React single-page app. Every string a person can read comes from a
  translation catalogue; no server prose is ever displayed.
- **Authentication** — stateless signed JWTs in two kinds: an *access* token that names an
  organisation and a role, and an *identity* token that names only the person and reaches
  exactly three endpoints — enough to see which organisations you belong to, choose one, or
  start one.
- **The simulation engine is pure functions** with no framework, no database and no I/O, held
  apart from everything else on purpose: its failure mode is not a crash but a plausible
  number, and the seam is what lets the arithmetic be checked against closed forms that exist
  outside this codebase.

## Where to read further

| Subject | Document |
|---|---|
| What Aurevanta is for, and why | `product-concept.md` |
| What is left to build | `roadmap.md` |
| The simulation engine | `design/simulation-engine.md` |
| Common cause and scope growth | `design/common-cause-and-scope-growth.md` |
| Turning hours into a date | `design/calendar-and-dates.md` |
| How estimates are asked for | `design/elicitation.md` |
| What widened the band | `design/variance-contribution.md` |
| What to cut to hit a date | `design/what-to-cut.md` |
| The track record | `design/calibration.md` |
| The throughput cross-check | `design/throughput.md` |
| Saying a forecast plainly | `design/communicating-a-forecast.md` |
| Resources and people | `design/resources-and-people.md` |
| Plans, work items and estimates | `design/plans-and-estimates.md` |
| Teams, invitations and accounts | `design/teams-and-invitations.md` |
| Organisation handles | `design/organisation-handles.md` |
| Security review | `security.md` |
| Structural review of the codebase | `code-review.md` |
