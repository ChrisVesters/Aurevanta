# The code review taken after the resource model

**Every work on `roadmap.md` is built, so this is the first pass over the codebase that
is not about a feature.** It looks at what the original tenancy design–the resource model left behind: how large the classes got, where
responsibilities drifted, and which packages grew faster than the reasons for them. It is the
structural twin of `security.md` — a standing record of what was examined, what was changed,
and what was examined and deliberately left alone.

**Measured first, and the measurement moved the list.** Java line counts are misleading here
because this codebase documents more densely than it codes: `ForecastService` was 989 lines and
520 of them were code, `problem` is 51 files and 571 code lines. Everything below is counted
with comments and blank lines stripped, which is what makes "this class is too big" a fact
rather than an impression.

---

## What was found, and what was done

### 1. `ForecastService` was five services in a trench coat — **split**

At 520 code lines it was 6% of the backend in one class, twice the size of the next one, and it
held five unrelated jobs: making a forecast, reading runs back, the contribution ranking's contribution ranking, the inverse query's
cuts, and the resource model's hires. The precedent for the fix was already in the same package and had been
since the throughput forecast — `MovementService` and `ThroughputService` are exactly these things, done right.

The rule the split follows: **this service makes forecasts and reads them; everything that
*explains* one is its own service and writes nothing.**

| new class | what it is | code lines |
|---|---|---|
| `ContributionService` | the contribution ranking — what widened the band | 41 |
| `CutService` | the inverse query — what to drop, and the greedy search | 170 |
| `HireService` | the resource model — what another unit would buy | 71 |
| `ForecastReplays` | the one place a stored run is re-run | 51 |
| `PlanTitles` | what a run's work is called *now* | 25 |

`ForecastService` came out at **233**. `ForecastReplays` is the load-bearing one: five features
now replay through a single function, where before there were two entry points and a rule in a
comment saying they must not drift. `PlanTitles` is the three-way naming rule — here,
here-but-archived, gone — which a contribution ranking and a list of cuts both need and which
"written twice is two chances for one copy to start rendering a missing item as a blank" had
already flagged as a hazard.

**`teamOf` moved into `ForecastResponse` rather than into any of them.** Naming a run's pools
is response-shaping, and the response is where it belongs; the controller now hands
`ForecastResponse.of` the organisation's pools and gets a described team back, one argument
shorter and with no static helper in between.

### 2. `ForecastRun`'s twenty-argument constructor — **collapsed to eleven**

Ten of the twenty were the engine version, the priority rule, the calendar rule, the working
day, the capacity, the resourcing, the two growth ends, the team factor and the start date —
which is exactly `ForecastTerms`, a record that already existed and was only ever used to read
them *back*. Now it writes them too, and `run.getTerms()` is the inverse of the constructor
rather than a re-listing of the same ten getters in a service.

**The bug this closes is not hypothetical**: `capacity` and `sampleCount` were adjacent `int`s
in the middle of that list, and nothing but argument order kept them apart.

### 3. `ForecastPanel.tsx` was 1,673 lines — **418**

One component held four other components, sixteen prose helpers, and seventeen pieces of state
across six independent server reads. Split along the seams that were already there:

| new module | what it is | lines |
|---|---|---|
| `forecastText.ts` | every sentence this panel says | 352 |
| `ForecastAsk.tsx` | the question, and the form that asks it | 329 |
| `BurnUp.tsx` | the table and the drawing of it | 179 |
| `ThroughputComparison.tsx` | the second forecast beside the first | 124 |
| `EarlierRuns.tsx` | the history, and why the date moved | 119 |
| `SpreadPanel.tsx` | what the band is made of | 95 |
| `MovementAsked.tsx` | one account, once asked for | 69 |
| `confidence.ts` | how a forecast is *read* | 46 |

**Two of these own a request, and that is why they are components rather than fragments.** The
breakdown costs a whole replay and the account of a movement costs seven; both are asked for
rather than loaded, and holding their state in the panel meant six of its seventeen variables
existed for questions most readers never ask. Each is now keyed on the run it is about, so a
new forecast clears them by remounting — which deleted a handler that had to remember to reset
six things by hand.

**`forecastText.ts` is the one worth arguing for.** the reporting work's whole subject is saying a forecast to
somebody who does not know what P90 means, and its sharpest rule — *one date, never a window* —
is a rule about a string. A function that builds one can be read and tested; the same rule
spread through JSX is a rule nobody can check.

### 4. `projects/types.ts` was two files — **split**

654 lines, of which two thirds described six forecast features and one third described a plan.
Now `types.ts` (157) and `forecastTypes.ts` (506). They share no type: a work item's identifier
is the only thing that crosses, and it crosses as a string.

### 5. `CurrentUser` was a component nothing used — **deleted**

A `@Component` in `security` reading the `SecurityContextHolder`, with no caller in `main/`
and one test of its own. That alone is the reporting work's precedent for `Comparison.identical()`. What made
it worse is that **`CLAUDE.md` named it as the rule** — "take the tenant from
`CurrentUser.requiredTenantId()`" — while every controller in the application takes it from
`@AuthenticationPrincipal AuthenticatedUser caller`. A document pointing at a mechanism nothing
uses is worse than no document. Both are gone, and the rule now describes what the code does.

### 6. `ratelimit` spelled an address two ways — **stated once**

`MailRateLimiter` and `SignInRateLimiter` each carried a private `normalise` and a private
refusal-thrower, character for character identical. The refusal is cosmetic duplication; the
normalisation is not. **It is the key a budget is stored under**, and two copies is two chances
for one to fold case differently from the other — after which a limiter counts
`Ada@acme.test` and `ada@acme.test` as two people and the budget the other one is enforcing
quietly stops being the budget anybody is subject to. Both now live on `RateLimiter`, beside
the counter they key. `MailRateLimitApiTests.countsAnAddressHoweverItIsSpelled` already pinned
the behaviour, which is why no new test was needed — checked before writing one.

### 7. Twenty copies of one effect in the frontend — **seven**

Every screen that reads from the server had written this by hand:

```tsx
let cancelled = false;
request<T>(url).then((loaded) => { if (!cancelled) setX(loaded); })
               .catch((e) => { if (!cancelled) setFailure(describeFailure(t, e)); });
return () => { cancelled = true; };
```

Twenty times, across sixteen files. The copies were not the problem — **the rule inside them
was**. Every one ends by setting `cancelled`, because an answer arriving after the component
has gone is a write into a screen nobody is looking at. That is the sort of rule that holds in
twenty places until it holds in nineteen, and the nineteenth is found by a user.

`api/useLoaded.ts` states it once: a path (or null for "not yet"), the deps a fresh read is
keyed on, and `{ data, failure }` back. The failure is *returned* rather than shown, because
whether a screen must explain a failed read or survive it silently is a judgement the screen
owns — what is not a judgement is whether the answer may still touch the page.

**Thirteen sites converted; seven left alone, each for a stated reason** — a POST with a body
(`EstimateForm`, `VerifyEmailPage`), two sequential reads (`WorkItems`, `MembersPage`), an
unauthenticated read (`InvitePage`), a value the screen writes back (`ProjectPage`), and the
session restore itself (`AuthProvider`). **Read-only is the boundary**, and it is in the hook's
own documentation: a screen that holds what the server answered after a rename is not doing a
read, and widening the hook to serve it would make it the state container for pages whose
state is not a read at all.

It also removed state that was only ever derived. `ForecastPanel` held `runs` and `drift` in
two variables filled from one response; they are two readings of `data` now. **125 fewer
statements**, at unchanged coverage.

---

## Examined and deliberately left alone

**`problem`, at 51 classes.** One class per failure this API can report, averaging eleven code
lines. That is the design and `CLAUDE.md` states it: every `code` this API publishes can be
read in one directory, and a `code` is what the browser translates. All 51 are referenced —
checked, not assumed.

**`WorkItems.tsx`, at 736 lines.** The obvious extraction is the row, and it makes things
worse: the row needs the item, its estimates, its needs, the organisation's pools, both
directions of its dependencies, the open-form state, six failure objects and six callbacks. A
component with twenty props is not an improvement on an inline block; it is the same coupling
with a longer signature. The screen is large because it holds one plan's whole editing surface,
and the state it holds is one screen's.

**Five service methods return `*Response` types**, all in `forecast`. Everywhere else a service
returns entities. It reads like a layering violation and is not: these five answers have no
entity behind them, and `CLAUDE.md` already places a feature's web types in the feature's own
package rather than in a layer. Introducing domain twins to map from would be more code
describing the same shape.

**`Schedule` at 350 code lines is now the largest class in the backend, and should be.** It is
the serial schedule generation scheme, its resource loop and its stopping rule; every part of
it is argued for in `design/simulation-engine.md` and `design/resources-and-people.md`, and splitting it would put the argument in
two files.

**`en.ts` at 1,388 lines.** One catalogue per locale is the design. Adding a locale is a new
file, not a restructuring of this one.

**`AuthenticationService.requireMembership` and `MembershipService.requireMember` are the same
query.** Same repository call, different exception — and the difference is the point, which the
javadoc already said before I went looking. `/api/auth/me` is the session-restore endpoint, so a
token naming an organisation the caller has been removed from must answer `invalid_credentials`
and end the session; every other tenant-scoped endpoint answers `not_a_member` and stays signed
in. Two names for one query, two meanings, both documented. **Nothing was changed here, and
reading the javadoc before merging them is the only reason.**

**`Comparison.sameStart()` has no caller but its own test** — the same shape the reporting work's review deleted
`Comparison.identical()` for, and the opposite conclusion. `Drift.sameQuestion` is
`comparable() && sameCalendar() && sameAssumptions()`: this type's list exactly, minus this one.
A reader checking that expression against the type has to be able to see what was left out.
Delete the method and the omission stops being visible — the expression reads as complete, and
the next person to "finish" it ends every drift window at one run, because a plan re-forecast
weekly is started from today every time. **It is kept, and its javadoc now says that is what it
is for**, which is what was actually missing.

**`ProjectsPage` and `ResourcesPage` are the same page twice** — list, archive toggle, create
form, the same five pieces of state. A generic `ArchivableListPage` would need render props for
the row and the form, which is the same coupling with indirection on top. The part they truly
shared was the effect, and that is now `useLoaded`.

**`test/render.tsx` at 543 lines** mixes the fetch double with every fixture the suite uses.
Splitting it would touch 28 test files to move constants between two files that are always
imported together.

---

## What it cost, and what it did not change

**No behaviour changed anywhere.** No migration, no column, no `Engine.VERSION` move, no
endpoint, no response shape, no wording. One deletion — `CurrentUser` — removed a component
nothing called.

The whole of the evidence is that the suites went through it unedited: **1,090 backend tests**
(1,100 before, less the ten that only exercised `CurrentUser`) and **500 frontend tests**, the
frontend at 100% of statements, branches, functions and lines, zero missed branches on every
backend class touched, and `ForecastApiTests`' stored-snapshot replay — the case that fails the
day somebody changes the model — passing throughout.

**Two test files changed, and only their imports**, where the forecast types moved. Nothing
else in either suite was edited, which is the only claim worth making about a refactor.
