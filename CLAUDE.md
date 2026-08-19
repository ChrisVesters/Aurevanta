# Aurevanta

Web application for planning work using **certainty interval estimations** (P10/P50/P90)
instead of single-point estimates. The domain centres on three-point estimates,
aggregation across tasks, and confidence bands.

Licensed GPL-3.0.

`docs/product-concept.md` holds the domain concepts and planned features — read it before
designing schema or domain logic. It is design intent, not a description of existing code.
`docs/roadmap.md` sequences that intent into milestones and records the decisions each one
depends on; M0 (tenancy and identity), M1 (making it a team product), M1a (organisation names
are not unique), M2 (the estimation schema), M3 (the simulation engine), M4 (a date you can
commit to), M5 (elicitation), M6 (variance contribution), M7 (inverse queries), M8 (actuals and
calibration), M9 (throughput) and M10 (communicating it) are built. M4
completed **Tier 1** — the roadmap's own bar for beating a spreadsheet, being a Monte Carlo rollup
and a ship date at a confidence level — M5 then replaced the question the ranges feeding it are
collected by, M6 made the band say what it is made of, and M7 ran the question backwards.
**Tier 2 is complete**, and M8 is the first milestone that checks any of it against what happened —
M9 then answers the same question from the other side and needs no estimate at all, which is why it
is the one that can speak today. M10 is the one whose output is prose and pictures rather than
numbers, and so the first that can be wrong in a way no test catches. M11 (resources and people) is
next — planned in `docs/m11-plan.md`, and the largest complexity jump in the plan. **Read that
plan's measurements before arguing with its scope**: what `roadmap.md` warns loudest about there,
the scheduling heuristic, is worth 0–9%, and what it lists as an ordinary bullet — a team being
typed rather than pooled — is worth 14–59% in the optimistic direction.
`docs/m1-plan.md`, `docs/m1a-plan.md` and `docs/m2-plan.md` are the records of how each was
done and where each departed from its own brief — M1a most of all, since it corrected M0 by a
different route than the one it was written to take.

`docs/m3a-plan.md` and `docs/m3b-plan.md` are the two halves of M3, the simulation engine —
which `roadmap.md` calls the product and everything before it was built to feed. **Both are
built**, so a plan with ranges in it produces a band that models the common cause making good
and bad luck stop cancelling, and the work nobody has written down yet. Read both before
touching anything under `forecast`, because almost all of the risk is in decisions rather than
in code: what happens when two people estimate the same task differently, what an unestimated
item does to a graph it sits in the middle of, what "remaining work" means for a task already
under way, and where newly discovered work attaches. **The failure mode here is not a crash but
a plausible number**, which is why the engine is pure functions with an oracle behind every one
of them.

**They are two builds and one design**, split where a whole-plan closed form stops being
available: M3a is proved against arithmetic that exists outside this codebase, and M3b — the
shared team factor and scope growth — is proved in pieces, the strongest of which is that
setting its two parameters to zero reproduces an M3a run byte for byte. **`m3b-plan.md` is the
one to read for how a plan should be argued with**: its decision 6 predicted which of the two
effects would be the heavier, the build measured it, and the measurement pointed the other way
— so the `### As built` section says so and the decision came out stronger, because two effects
that load different bottlenecks are even less substitutable than two of different sizes.

`docs/m10-plan.md` is communicating any of it, and is **built**: a sentence anybody can read,
a burn-up, and the two questions somebody asks second — *has this been getting worse?* and *why
did the date move?* It added **no migration and no column**, and `Engine.VERSION` is still 2.
**Read its decision 2 before writing any sentence this product publishes**, and decision 5 before
touching the detector: the milestone measured both of the obvious designs and both are wrong. Its
close-out review is worth reading for what a review pass is for — it found the plan's own step 5
reintroducing the two-sided sentence decision 2 exists to keep out.

`docs/m9-plan.md` is the throughput cross-check, and is **built**: a second forecast from what a
plan has actually delivered, with no estimation in it. It added **no migration, no column and no
index** — a completion date is required on anything marked done, so the history was already there —
and `Engine.VERSION` is still 2. **Read its decision 6 before repeating what `roadmap.md` used to
say**: throughput does *not* absorb scope growth, and the error runs in the flattering direction.
Decision 5 is the one that decides whether the answer is honest.

`docs/m8-plan.md` is actuals and calibration, and is **built**: how often the ranges written here
contained what the work actually took. It is the first milestone whose headline number this
product does not control — everything before it can be made to look good by building it well, and
this one only by estimating well. **Read its decision 1 before touching anything that decides
which estimates count**, and decision 6 before rendering any of it. It added **one migration**,
`V16__work_item_progress.sql`, and no engine behaviour: `Engine.VERSION` is still 2.

`docs/m7-plan.md` is inverse queries, and is **built**: what to cut to hit a date at a confidence.
Like M6 it added **no column, no migration and no engine behaviour**, and `Engine.VERSION` is
still 2. **Read its decision 2 before touching anything about it.** A cut is modelled as a draw
taken and *discarded*, never as an item removed and never by emptying its estimates:
`ItemModel.sample` returns from `weighsNothing()` before it draws, so a weightless item takes no
draws and every later item is sampled from a different place in the stream. Measured, the noise
then lands in the same range as the effect being measured and the ranking becomes a coin flip that
looks exactly like an answer.

`docs/m6-plan.md` is variance contribution, and is **built**: ranking what a plan holds by how
much it widens the forecast. It added **no column and no migration** and did not move
`Engine.VERSION`. **Read its decision 3 before rendering any of it** — the obvious presentation
is percentages, and they sum to well over one, because M3b's shared factor makes everything move
with everything.

`docs/m5-plan.md` is elicitation, and is **built**: three boxes became three questions asked one
at a time, so that three numbers stop being 3/5/8. **Read the measurement at the top before
touching anything about estimate quality** — neither of the two checks the roadmap proposed
catches the failure they exist to catch, because every Fibonacci triple agrees with itself to
within a few percent and clears the ratio rule. The warnings are a backstop; the **order the
questions are asked in** is the defence, and it is the one decision in that milestone with no
test behind it. Its `### As built` for step 4 is worth reading for how a plan discovers a seam it
did not know it needed: the review has to show server-computed flags *before* the row exists,
which no amount of wanting could get from an endpoint that only answers submissions.

`docs/m4-plan.md` is the calendar, and is **built**: a confidence control resolving the engine's
hours into a date. **Read its decision 2 before touching anything that divides hours by a day** —
the engine's output already has capacity inside it, so the working day is one worker's and never
the team's, and getting that wrong produces a date wrong by exactly the capacity factor with
nothing on screen looking amiss. Its decision 7 is the other one worth knowing: the division is
exact `BigDecimal` because a day boundary is a step function, so a double is not off by a
rounding error but by a whole day.

**A plan is updated as its steps land, not at the end.** Mark the step `✅ *done*` on its
heading and in the *At a glance* table, and write its `### As built — where it differs from
the above` in the same change as the code. That section is the point of these documents:
`m1a-plan.md` is worth reading precisely because it records a milestone that did something
other than what it was written to do, and a departure is only honestly recalled while it is
still the thing you just decided. Leaving it to the close-out step turns a record into a
reconstruction.

`docs/security.md` is the security review taken after M1a — four open findings with the moment
each is cheapest to fix, what was deliberately accepted, and, at least as usefully, what was
checked and found sound. None of them is scheduled into a milestone, deliberately: they are
carried under *Cross-cutting* in `roadmap.md`, because a security list inside a milestone's
bullets is scope, and scope is what gets cut. **Read it before changing anything it names**:
two of the four are properties this codebase argues for elsewhere and does not keep, so a
change made without it can quietly widen one.

## Layout

- `backend/` — Spring Boot 4.1 REST API (Java 25, Maven), PostgreSQL 18 + Flyway.
  Package root `com.cvesters.aurevanta`.
- `frontend/` — React 19 + TypeScript SPA built with Vite 8.

**Backend packages are by feature, not by layer** — `tenant`, `user`, `membership`,
`invitation`, `security`, and the five domain packages `project`, `item`, `estimate`,
`dependency`, `forecast`, each hold their own entity, repository, service and web types.
`auth` is large enough to be split a second time, by use case: `auth.registration`,
`auth.signin`, `auth.verification`, `auth.reset`. The controller and the response shapes
all its endpoints share stay in `auth` itself. A new auth use case — verifying an address,
resetting a password — is a new subpackage, not more files at the root.

**`problem` is the exception**, and sits beside the features rather than in one: it holds
every failure the API can report and the single advice that renders them. It started under
`auth`, which stopped being true once `ratelimit` refused on behalf of whatever it guards
and invitations began reporting failures of their own. Every `code` this API publishes can
be read in that one directory.

**The domain packages depend in one direction only** — `forecast` points at all four,
`dependency` and `estimate` both point at `item`, `item` points at `project`, and nothing
points back. `forecast` is the only one that reads from every other, and it does so through
their services rather than their tables, so the arrows stay one-way and a forecast cannot
reach a plan without passing the membership check the plan's own service makes. That is why
whether a caller may reach a piece of work is asked of `WorkItemService.get` rather than
answered a second time inside `estimate`, and it is the reason coverage is counted in
`ProjectRepository` rather than published as a boolean on the item response: the boolean
would have made `item` depend on `estimate` while `estimate` already depends on `item`.

## Commands

Backend (from `backend/`):

```bash
./mvnw -B test              # runs tests; spins up PostgreSQL via Testcontainers (needs Docker)
./mvnw spring-boot:run      # starts on :8080; docker-compose support boots compose.yaml automatically
./mvnw spring-javaformat:apply  # reformats sources when the validate gate fails
```

Frontend (from `frontend/`):

```bash
npm run dev     # Vite dev server on :5173, proxies /api -> localhost:8080
npm run build   # tsc -b && vite build
npm run lint     # oxlint + prettier --check (fails on unformatted code)
npm run format   # prettier --write, fixes it
npm run test     # vitest run (jsdom + Testing Library)
npm run coverage # vitest run --coverage, report in coverage/
```

Both Docker and a JDK 25+ are required for the backend test suite.

## Conventions

- **Schema is owned by Flyway.** Hibernate is set to `ddl-auto=validate`, so every schema
  change is a new versioned migration in `backend/src/main/resources/db/migration/`.
  `V1__baseline.sql` is an empty baseline; the domain schema starts at `V2`.
- **All code must be covered by tests.** Every change ships with tests, and a class is not
  done until its behaviour is exercised — including the failure paths, not just the happy
  one. Rejection branches (invalid input, missing claims, denied access, conflicts) are
  the point of the test, not an optional extra: they are what the code exists to enforce.
  Judge this on *branch* coverage, since records and getters inflate instruction coverage
  and hide untested logic. `./mvnw test` writes a report to `target/site/jacoco/`.
  The only accepted exclusion is framework boilerplate with no behaviour of its own —
  `main()`, and entity accessors that no logic reads.
- **Backend tests use Testcontainers**, not an in-memory database — `TestcontainersConfiguration`
  provides a real `postgres:18` container via `@ServiceConnection`.
- `spring.jpa.open-in-view=false` — load what a request needs inside the service layer;
  do not rely on lazy loading in controllers or serialization.
- **Java is formatted by `spring-javaformat`**, validated in the `validate` phase, so
  `./mvnw test` fails on a violation. Tabs, Spring's import order, 120-column lines.
  Run `./mvnw spring-javaformat:apply` rather than hand-fixing.
- **Never hand-format code — run the formatter.** Both sides have one and both fail the
  build when ignored: `./mvnw spring-javaformat:apply` for Java, `npm run format` for the
  frontend. Run them before calling a change done; formatting judged by eye drifts.
- **TypeScript runs under `strict`**, and `npm run lint` is oxlint plus `prettier --check`.
  Prettier config is `.prettierrc.json`: `semi: true`, `singleQuote: true`,
  `trailingComma: "none"` — chosen to agree with the SonarQube rules reported in the IDE
  (S1438 semicolons, S1537 trailing commas), so the two never contradict each other.
  Change the config and reformat rather than editing files by hand.
- The frontend talks to the backend through the `/api` prefix and the Vite dev proxy,
  which keeps the browser same-origin so there is no CORS configuration in dev.
- **A form reports a failed submission through `useFormFailure`**, never by calling
  `describeFailure` on its own. It holds the rule that decides what the visitor sees — a
  complaint belonging to a field is shown against that field, and only a failure belonging
  to the whole form gets the banner. A form that handles just the banner answers an empty
  required field with "some fields need attention" and never says which, or why. Pass it
  the field names the form renders: suppressing the banner is only safe for complaints the
  visitor can actually see, so one about a field that is not on screen falls back to it.
- **No user-visible literal strings.** Every string a person can read comes from the
  catalogue in `src/i18n/en.ts` via `useTranslation()` / `<Trans>`. Keys are type-checked
  against that catalogue (`src/i18n/i18next.d.ts`), and the test setup fails any test that
  renders a key with no translation — so a hard-coded string is a build or test failure,
  not a review comment. English is the only locale; adding one is a new catalogue file,
  not a code change.
- **Server error prose is never displayed.** Problem documents carry a machine-readable
  `code`; `src/i18n/problems.ts` maps it to our own wording, falling back to a generic
  message rather than showing the backend's English. See the API note below for the one
  place this is still incomplete.
- **A success may carry no body, and 204 is not the only one that does.** Every `202` this
  API returns is empty, and `Response.json()` *rejects* on an empty body instead of
  resolving to `undefined`. `apiRequest` therefore reads the text and parses only what is
  there. Anything thrown that is not an `ApiError` reaches the visitor as "could not reach
  the server", so a parse failure here is indistinguishable from the network being down —
  which is exactly how it presented before it was fixed.
- **The `fetch` double in `src/test/render.tsx` mirrors that**, rejecting from `json()` on
  an empty body. It did not, once, and a mock more forgiving than the real thing is a test
  suite that passes while the browser fails: every body-less `202` reported a network
  failure for a request the server had accepted, and 138 green tests said nothing.
- **Routing is React Router** (`react-router` v8, `BrowserRouter` in `main.tsx`).
  `/` is a public landing page, `/register` and `/login` sit behind `RedirectWhenSignedIn`,
  and everything under `/app` sits behind `RequireAuth`, inside a shared `AppLayout` that
  owns the header and the organisation switcher. **A plan is `/app/projects/{id}`, and no
  route anywhere carries an organisation handle** — the organisation comes from the access
  token as it does on every request. M2 was expected to be what first put a handle in a URL
  and deliberately was not, so M1a's two deferrals (reserved handles, redirects for retired
  ones) are still deferred and still recorded in `roadmap.md`. `/app/settings` is owner-only: the nav
  entry is hidden and the page says so, with the server refusing either way — what is on
  screen is a courtesy, not the boundary. **`/app/members` is not**, and the difference is
  the point: any member may see who their colleagues are, so the page is reached by
  everybody and hides only the controls that administer somebody. The forms never navigate
  themselves: they update the session and the guards move the visitor on, which is also what
  returns someone to the page they originally asked for after signing in.
- **The pages an emailed link lands on are public and outside both guards** —
  `/verify-email`, `/reset-password`, `/invite/:token`. The last is outside
  `RedirectWhenSignedIn` as well, and deliberately: somebody already signed in is exactly
  who accepts an invitation with the account they have.
- **Only `AuthProvider` holds the token.** Anything that needs to call the API as the
  signed-in caller goes through `request` on the auth context. A component that had to be
  handed a token in order to ask for anything would be a component that could store it, log
  it, or pass it somewhere it does not belong.
- **A test double that answers every URL alike is a lying double.** A signed-in page
  restores the session *and* loads the switcher's options; answering both with one payload
  handed the switcher an account and crashed it. Mock by URL, not in bulk.
- **The frontend suite runs in `America/New_York`**, set as `test.env.TZ` in `vite.config.ts`,
  and it is deliberate rather than incidental. Every date this product shows is a *day* with no
  time of day in it, and the way that breaks is `new Date(iso)` reading a bare date as UTC
  midnight — which displays the day before for every reader west of the meridian and is
  invisible in a suite that runs in UTC. `formatDay` and `todayHere` in `src/projects/dates.ts`
  are the two halves of getting it right, one reading and one writing; running west is what
  makes a regression in either a failing test rather than a bug half the planet sees.
- Actuator exposes only `health` and `info`.

## The verification gate

- **An unconfirmed address cannot obtain a token by any route.** Registering returns `201`
  with the account and *no* session — it cannot hand one out, or the gate would be
  skippable by signing up. Sign-in refuses with `email_not_verified`.
- **The gate is checked only after the password is.** A wrong password on an unconfirmed
  account still returns `invalid_credentials`, identical to an address nobody registered.
  Checking verification first would turn sign-in into a way to ask who has an account.
- `POST /api/auth/verify-email` redeems the link; `POST /api/auth/verify-email/resend` asks
  for another. Both are unauthenticated — the person who needs them is exactly the person
  who cannot sign in — and resend **always** answers `202`, sending nothing for an unknown
  or already-confirmed address.
- **Mail failure never fails registration.** The account is created either way; what is
  lost is the link, which can be asked for again.
- Tests that drive anything sending mail import `RecordingEmailSenderConfiguration`, which
  replaces the whole port. Anything asserting that a request *survives* a broken mail
  server must instead use `FailingEmailSenderConfiguration`, which keeps the real
  asynchronous wrapper — the part that actually swallows the failure.

## Password reset

- **The gate's escape hatch, not a convenience.** An account whose confirmation mail never
  arrived cannot sign in, so it cannot be recovered by signing in. `POST
  /api/auth/password-reset` `{email}` is the only way back, and
  `POST /api/auth/password-reset/confirm` `{token, password}` spends the link.
- **A reset confirms the address as well**, stamping `email_verified_at` if it is unset —
  following the link proves the mailbox exactly as a confirmation link does. `markEmailVerified`
  keeps the *first* moment, so arriving by both routes does not rewrite history.
- **Request always answers `202`**, for an unknown address as much as a registered one, and
  unlike verification resend it does **not** skip an unconfirmed account: that account is
  precisely the one that needs it. Unauthenticated and it sends mail, so the rate limit
  applies to it.
- **Redeeming one reset link spends every other one that account holds**
  (`SingleUseTokenService.revokeAll`). Asking twice leaves two live links in an inbox, and
  the one that was not used must not stay a way in after the owner has already recovered.
  Purpose-scoped, so a reset does not cancel a confirmation link the same person still needs.
- **Sessions already issued survive a password change.** Access tokens are stateless and
  signed, so nothing can withdraw one before it expires. Closing that needs a token version
  on the user or a deny list, and neither is in M1.
- **`PasswordRules` in `user` states the bounds once.** Registration and reset both set a
  credential; if they disagreed, whichever asked for less would decide the rule for everyone,
  since anyone can reach the weaker endpoint.

## Rate limiting the endpoints that send mail

- **Two limits, because they stop different things.** Per *recipient* bounds what can be
  dumped in one inbox; per *source* stops one client sending a single message each to a
  great many addresses, which the per-recipient limit cannot see. Configured under
  `aurevanta.rate-limit.*`; defaults are 3 per address and 20 per source, both over 15
  minutes.
- **Applies to `/api/auth/register` too**, not just reset and resend. An address can only be
  registered once, so nobody can be buried this way — but one unsolicited message each to a
  million strangers is still this application sending it. Anything that emails an address
  the caller chose belongs behind the limit.
- **Claimed before anything is looked up**, so it counts requests rather than messages. A
  limit that only counted the addresses that turned out to have accounts would answer,
  through its own refusals, the question the blanket `202` exists to refuse. Invitation
  resend is the one exception and says why: its recipient comes from a row the caller can
  already list, not from anything they typed, so there is no enumeration to prevent.
- **One budget per recipient, shared across endpoints.** Per-endpoint budgets would hand an
  attacker twice the allowance for alternating between reset and resend, and the person
  being written to cannot tell which endpoint sent what.
- **A refusal that could never have sent a message gives the recipient's claim back**
  (`MailRateLimiter.refundRecipient`), and registration's `slug_taken` is the one that does.
  M1a made it a refusal the product *invites* people to retry — the form fills in the free
  alternative it carries — and a retry loop in front of three-per-quarter-hour locks
  somebody out of registering at all, and out of the password reset that shares the budget.
  **The source keeps its claim**, because that budget bounds what one client can make this
  application do and a refused registration still cost it a lookup and a bcrypt hash;
  refunding it would leave an endpoint that hashes passwords unlimited to anybody willing to
  collide on a handle every time. **Only a refusal decided without looking the address up may
  be refunded** — anything decided *by* the address would make the limit an answer to the
  question the blanket `202`s exist to refuse.
- **`429` carries `Retry-After`**, which is why it is the one failure with its own handler
  returning a `ResponseEntity` rather than a bare `ProblemDetail`. A client told only "too
  many" can do nothing but guess, and a bad guess is a retry loop.
- **The counts live in this process and nowhere else.** A second instance has its own, so
  the effective limit becomes the configured one times the number of instances — this is
  wrong the moment anything is scaled out, and the fix is shared state, not a bigger number.
- **`getRemoteAddr()` is the source**, so behind a proxy or load balancer every request
  appears to come from one address and the per-source limit would throttle everybody
  together. Deploying behind one means trusting a forwarded header first.
- **Sign-in is limited too, on *failures* only** (`SignInRateLimiter`). Guessing a password
  otherwise costs only bcrypt's time, which is a price per attempt rather than a bound on
  how many. A success clears the account's count, and reaching `email_not_verified` never
  counts — the password was right, so it is not a guess.
- **The per-account sign-in limit sits several times above the per-source one, and that
  ratio is the design.** A per-account limit is also a way to lock somebody out of their own
  account, so it must be beyond what any single source can reach; filling it takes several
  sources acting together, which is the distributed case it exists for. Change one number
  without the other and a defence becomes an attack.
- **The advice that renders a refusal names no package, and must not start to.**
  `ApiExceptionHandler` covers every controller. It was scoped to one class, then to
  `auth`, and each time a controller appeared outside that scope its refusals lost their
  `code` and their `Retry-After` and arrived as Boot's default error — silently, since a
  problem document is still a problem document. Invitations were the third occasion.
  Worse, a scope written as a package *string* goes stale without failing to compile:
  one did, when the root package was renamed, and every problem document in the
  application quietly became Boot's default while the build stayed green.
- **Tests that drive these endpoints must clear the limiter**, since one context is shared
  across cases — `MailRateLimiter.clear()` in `@BeforeEach`. Otherwise one test spends the
  allowance the next one needs, and the failure looks nothing like its cause.

## Single-use tokens (emailed links)

- **Not the same thing as an access token.** `security` mints stateless JWTs a client
  presents on every request; `token` backs one emailed link — confirm an address, reset a
  password — that works once and then never again.
- **Only a SHA-256 hash is stored**, never the raw value. These tokens grant account access
  without a password, so a leaked backup must not be a list of working password resets.
  SHA-256 rather than the `PasswordEncoder`: redemption looks a row up *by* the hash, which
  a salted, deliberately slow bcrypt would turn into a table scan, and 32 random bytes are
  not the guessable secret bcrypt exists to protect.
- `SingleUseTokenService.issue(user, purpose, ttl)` returns the raw token **once** — put it
  in the mail there or it is gone. `consume(rawToken, purpose)` returns the user and spends
  the token, or returns empty.
- **`consume` never says why it refused.** Unknown, expired, already spent and wrong-purpose
  are one answer, so a caller cannot be used to confirm which tokens exist.
- **Redemption is one conditional `UPDATE`, not a read then a write.** That is what makes
  "exactly once" hold when two clicks arrive together; a new purpose must keep it that way.
- Adding a purpose is a `TokenPurpose` constant. Redemption checks it, so a weaker token
  (proving someone reads an inbox) can never be spent as a stronger one (taking the account
  over).
- **`LinkTokens` states how a token is made and hashed, once.** `user_tokens` is not the
  only table holding one — invitations have a `token_hash` of their own — and two ways of
  minting a link would eventually be one strong way and one weak way. Anything putting a
  secret in an inbox goes through `LinkTokens.generate()` and `LinkTokens.hash()`.

## Members, and administering an organisation

- **`/api/members` and `/api/memberships` are the same table read the other way round.**
  The first answers "who else is in this one", once an organisation has been chosen; the
  second answers "which organisations am I in", before one has been. Only the second is
  reachable with an identity token, which is why they are not one endpoint.
- `GET /api/members` — any member, so colleagues can see who their colleagues are.
  `PATCH /api/members/{id}` `{role}` and `DELETE /api/members/{id}` — **OWNER only**.
- **An organisation always keeps at least one owner.** Demoting or removing the last one is
  refused with `last_owner`, whoever asks — an owner may remove themselves only if another
  remains. Everything else an owner gets wrong here, an owner can put right; an organisation
  with nobody able to administer it cannot be repaired from inside the product at all.
- **The owner count is taken under a write lock** (`MembershipRepository.lockOwners`). Two
  owners removing each other at the same moment would each count two and each conclude one
  remains. A conditional `UPDATE` cannot serve instead the way it can for spending a token:
  that makes a race on *one row* safe, and this is a race on a count across several.
- **Removal deletes the membership, never the identity.** Somebody removed keeps their
  account, their password and every other organisation they belong to.
- **`MembershipService.requireOwner` is the one rule every owner-only endpoint shares**,
  here and in `invitation`. It re-reads the membership rather than trusting the role pinned
  into a token up to twelve hours ago, so somebody demoted this morning stops administering
  at once. Two copies of that check would be two chances for one to drift.
- **`MembershipService.join` is the only place a membership is created** — registering,
  accepting an invitation and starting an organisation all go through it.
- **`POST /api/organisations` `{name, slug}` is the way out of belonging to nothing**, and
  takes an *identity* token for that reason: the caller who needs it has no organisation and
  so no access token to offer. Losing your last membership became reachable the moment an
  owner could remove people, and waiting to be invited is not something a person can do for
  themselves. `OrganisationService` is shared with registration, so both apply one set of
  rules about what an organisation may be called.

## Organisations: the name, and the handle

- **The name is not unique and never was in reality.** Two organisations may both be called
  Acme Consulting. M0 made the name unique by accident — it derived the slug from it and put
  a unique index on that — which produced the one refusal in the system nobody could act on,
  because a person cannot choose a different name for their own company. M1a is the
  correction; `docs/m1a-plan.md` is its record.
- **The handle is chosen, not derived.** `slug` is a required field on
  `RegistrationRequest` (as `organisationSlug`), `CreateOrganisationRequest` and
  `UpdateOrganisationRequest`. Nothing on the server derives one, and nothing allocates one:
  the caller brings a handle, and it is taken exactly as typed or refused.
- **A refusal is only ever raised against something somebody chose.** That is the whole
  property this milestone exists to establish, and it is why the field is required rather
  than optional. An omitted handle would have to be filled in by the server, and a server
  that picks cannot then refuse — so there would be two paths through creation answering
  differently, and the caller would be left holding a consequence they never chose.
- **`slug_taken` carries the next free alternative** (`suggested`), so the refusal arrives
  with its own remedy. That is what buys the absence of an availability endpoint: asking "is
  `acme` free?" would be a public surface anybody could walk to enumerate which
  organisations exist, bought for a case that arises rarely. `OrganisationService.nextFree`
  counts from `Slug.base`, which strips a trailing `-<digits>` first — somebody refused
  `acme-2` is offered `acme-3`, not `acme-2-2`.
- **The extra property rides on the problem document, not on `errors`.** `FieldProblem` is
  built from Bean Validation failures and publishes only a constraint's numeric attributes;
  "somebody else has this" is neither. The form knows what the code refers to and places the
  message against the handle input itself, the way sign-in places `email_not_verified`.
- **There is no lock, deliberately.** Once the handle arrives in the request there is no
  check-then-assign sequence to serialise: `existsBySlug` refuses a taken handle, and the
  pair who pass that check in the same instant meet `uq_tenants_slug`. Compare
  `MembershipRepository.lockOwners` above — that guards an invariant nothing can repair once
  broken, and this would guard a *message*. The race produces a rare, self-correcting,
  already-actionable refusal, which does not earn one.
- **The service never catches the constraint violation; `ApiExceptionHandler` reads it.**
  Catching it in the service would need a `saveAndFlush` to make it land somewhere catchable
  and would leave a branch only two colliding writers could reach, which nothing could cover.
  The advice maps three index names — `uq_tenants_slug`, `uq_users_email`,
  `uq_invitations_pending` — each to **the code its own pre-check produces**, so a caller
  cannot tell a race from the ordinary case. `slug_taken` from that route carries no
  suggestion: the transaction is already lost, so there is nothing left to ask the database.
  Anything unmapped degrades to a neutral `conflict` that names no field.
- **The one `slug_taken` without a suggestion is the one the field must not promise one
  for.** `SlugField` takes `suggested` alongside `taken` and says "choose another" rather
  than "we have suggested another", because in that path the input is still holding the
  handle that was just refused. `useProposedSlug.takeSuggestion` is what decides it, so
  whether an alternative arrived and whether the field was replaced with it stay one fact.
- **`ConstraintNamesTests` is what fails when a migration renames an index.** Those names
  belong to Flyway, not to the advice, and nothing else would notice them drifting apart — a
  specific refusal would quietly become a generic one with the suite still green. Adding an
  entry to the map is what adds it to the test.
- **`PATCH /api/organisations` `{name, slug}` — OWNER only**, with the organisation taken
  from the token and never named in the request, like every other tenant-scoped endpoint.
  Both fields are required: Jackson cannot tell an absent field from a null one, so an
  optional name would have to decide which of "leave it" and "clear it" a null meant, and
  both readings are wrong for a column that cannot be empty. **Keeping your own handle is not
  taking somebody's** — the collision check skips the caller's own, or renaming would be
  impossible without also re-addressing.
- **Changing a handle breaks every link to it.** No redirect table, no retired handles that
  keep resolving. That is free only while nothing routes by handle; M2 is the step that ends
  it, and both this and reserved handles are recorded under M2 in `roadmap.md` rather than
  left here to be rediscovered.
- **Deriving a handle from a name is the frontend's job** — `proposeSlug` in
  `src/tenant/slug.ts`, with accent folding, lowercasing and hyphenating. **`Slug.PATTERN` is
  the contract between the two and the server enforces it**, so a proposal the server would
  refuse is a bug in the proposer, not a disagreement about the rule. A name with nothing to
  derive from proposes nothing, and the visitor types one: an empty required field asks a
  question, where a default like `organisation` would invite somebody to accept it unread.
- **`useProposedSlug` is shared by both forms that create an organisation**, and taking over
  is one-way: the handle follows the name until somebody edits it, and then it is theirs and
  stops moving — including when they go back to fix a typo in the name. A field that kept
  following would silently undo a handle chosen one keystroke earlier. The settings form does
  *not* use it: that handle already has an owner.
- **`SLUG_TAKEN` is exported from `SlugField` so the form can keep the banner quiet.**
  `useFormFailure` cannot know to: a handle already taken is not a validation failure and
  never appears in `errors`, so the refusal was rendered twice, once beside the field and
  once in the banner. A test caught it, and the tests now assert it is said once.
- **The handle is shown on screen only where a choice is being made and the caller's own
  list repeats a name** — `ChooseOrganisationPage` and `OrganisationSwitcher`, via
  `sharedNames`. Names that differ only in case count as the same name, since that is not a
  difference anybody reliably notices in a list. Everywhere else needs nothing: an invitation
  preview already carries an inviter, the header is covered by the switcher, and a sentence
  that interpolates a name reads badly with a handle in it. Both screens already hold the
  list, so this costs no query and no endpoint.

## Invitations

- **An invitation is not a `user_tokens` row**, and cannot be: it is sent to an address
  that may hold no account, while `user_tokens.user_id` is not null. It shares the token
  mechanism (`LinkTokens`) and nothing else.
- `POST /api/invitations` `{email, role}` — **OWNER only**, and the organisation comes from
  the caller's access token, never the request.
- **The inviter's standing is re-read, not taken from their token.** An access token pins
  the role held when it was issued and lasts twelve hours, so an owner demoted or removed
  this morning would otherwise go on inviting people all day. `not_a_member` and
  `not_an_owner` are separate codes; both are safe, since somebody already inside an
  organisation learns nothing from which one they get.
- **The sender's own address must be confirmed.** Unreachable under the gate — an
  unconfirmed account holds no token — but somebody who has not shown they can read their
  own inbox should not be writing to a stranger's, and that rule belongs where it is
  enforced rather than resting on a gate somewhere else.
- **One live invitation per address per organisation**, held by a partial unique index on
  `(tenant_id, lower(email)) where status = 'PENDING'`. A second live one is refused with
  `invitation_already_pending`; an **expired** one is renewed in place instead, because it
  still occupies that slot while no longer being a way in — inserting alongside it would
  trip the constraint over a link that has already stopped working.
- **"Already a member" is scoped to this organisation.** An owner can list their own
  members anyway, so telling them one is already there discloses nothing; whether the
  address holds an account elsewhere is not something an invitation may be used to ask.
- **Behind `MailRateLimiter` like everything else that sends mail.** Needing credentials is
  not what makes an endpoint safe here: the inbox belongs to a stranger either way, and it
  cannot tell an invitation from a confirmation link — which is why they share one budget
  per recipient. Resend claims *after* its lookup rather than before, alone among the
  senders: the recipient is named by a row an owner can already see, not by anything they
  typed, so there is no enumeration for a "count requests" rule to prevent.
- **Two of the six endpoints need no credentials.** `GET /api/invitations/{token}` and
  `POST /api/invitations/{token}/accept` are reached by somebody being invited *into* an
  organisation, so a token scoped to it cannot be the price of entry. The other four —
  issue, list, revoke, resend — are OWNER-only and tenant-scoped.
- **The preview carries four fields and no more**: organisation name, inviter's display
  name, role, and `claimed`. It is served to anyone holding the link, so it must disclose
  nothing a member would have had to sign in for — no identifier, no handle, no member list.
- **`claimed` is the one thing the preview says about the invited address**, and it says it
  so that the page can ask the right thing before anything is typed. Without it the page had
  to guess from the *visitor's* session, which is a different question: an invited colleague
  who already had an account was shown the form for making one, and told to sign in instead
  only after inventing a display name and a twelve-character password. The disclosure is one
  bit, never the address, and reaching it takes the raw token — which was mailed to that
  address and is stored only as a hash, so the audience is the recipient and whoever they
  forwarded the message to. `sign_in_required` still exists and is still the authority: it is
  the same answer arriving late, for an account registered since the preview was fetched.
- **Accepting has two ways through, chosen by the address and not by the caller.** Nobody
  holds it, so an account is created and a session handed back at once — the link proved
  the address, so `email_verified_at` is stamped and no confirmation is sent. Somebody does
  hold it, and then they must *be* that somebody: `sign_in_required` if the caller is
  anonymous, `invitation_for_another_address` if they are signed in as someone else. **A
  token emailed to a mailbox proves control of the mailbox, never ownership of the account
  registered with it**, and treating the two as the same would make a forwarded message a
  way into somebody else's account.
- **Nothing is written until every refusal has been ruled out.** Being told to sign in must
  not cost the visitor the link they were told to come back with, so the invitation is spent
  only once the attempt is certain to succeed — and spent by a conditional `UPDATE`, so two
  clicks arriving together cannot both produce a membership.
- **A link that no longer works says which way it failed** — `invitation_expired`,
  `invitation_revoked`, or `invalid_token` for unknown and already-spent. The one-answer
  rule the single-use tokens follow is relaxed here on purpose: what the visitor should do
  next differs (ask for another, or do not), and a 256-bit token in an inbox cannot be
  probed for.
- **Revoking sets a status, never deletes the row.** The partial index counts only pending
  rows, so withdrawing one frees the address to be invited again; and a surviving row is
  what lets the person still holding the link be told it was withdrawn rather than that it
  never existed.
- **Resending mints a new token**, retiring the old link. Resending usually means the first
  message went astray, and a message that went astray is one somebody else may be holding.
  The resender becomes the inviter of record, because their name is what the message
  carries.

## Outbound mail

- **Depend on the `EmailSender` port, never on a transport.** `SmtpEmailSender` is the only
  thing that knows mail is SMTP; swapping in a provider's HTTP API later is one new
  implementation and no change to any caller.
- **Sending is asynchronous and delivery failure is logged, not thrown.** The bean wired
  into the context is `AsyncEmailSender`, so a slow or unreachable server cannot add
  seconds to a request — and registration cannot fail because mail did. A caller therefore
  learns nothing about delivery; that is the trade, and closing it needs an outbox table
  and a provider webhook, which M1 does not have.
- **Mail sent inside a transaction goes out after it commits**, and not at all if it rolls
  back. A message almost always describes a row the same transaction just wrote — a token,
  an invitation — so sending it eagerly would race the commit and could deliver a link to
  something that does not exist. Callers need do nothing; `AsyncEmailSender` waits.
- **Recipients are masked in logs** (`a***@acme.test`). Addresses are personal data and
  logs travel further than the database does; the mask is enough to match a lost message
  to a support request and no more.
- Transport is `spring.mail.*`, so adopting a provider is configuration. `aurevanta.mail.from`
  is the sender; `aurevanta.mail.base-url` is the origin links in mail are built against —
  it points at the *frontend*, and the backend cannot discover it behind a proxy, so it is
  stated rather than guessed. Build links with `MailProperties.link(path)`.
- `compose.yaml` runs **Mailpit**: SMTP on **2525**, and everything sent in dev is readable
  at <http://localhost:8025> and delivered to nobody. Not Mailpit's usual 1025, which is
  contended — macOS lets one process hold IPv4 `127.0.0.1:1025` while Docker holds IPv6
  `*:1025`, so nothing fails to start but a client resolving to IPv4 reaches the wrong
  listener. `AUREVANTA_SMTP_PORT` moves the published port and `spring.mail.port` together.
- Tests use `RecordingEmailSender`, which replaces the whole port — wrapper included — so
  assertions are immediate and nothing waits on a background thread. `SmtpEmailSenderTests`
  is the single exception, proving the adapter against a real SMTP server via GreenMail.

## Multi-tenancy and authentication

- **Every tenant-owned table carries a `tenant_id`**, and isolation is enforced in the
  application: take the tenant from `CurrentUser.requiredTenantId()`, never from a request
  parameter or path variable. A query that filters only by an id the caller supplied is a
  cross-tenant leak.
- **Identity is global; membership is per organisation.** `users` holds the person —
  email, password, display name — and carries no tenant and no role. A `memberships` row
  (`user_id` + `tenant_id` + `role`, unique together) is what grants standing in one
  organisation, so one address can belong to several with a different role in each.
- Registering (`POST /api/auth/register`) creates a `tenants` row, a `users` row and an
  `OWNER` membership in one transaction, and returns the account without a token. An
  organisation gains further members by invitation.
- Authentication is a **stateless HMAC-signed JWT** presented as `Authorization: Bearer`,
  in **two kinds**, told apart by the `token_type` claim (required, never defaulted):
  - **access** — pins `tenant_id` and `role`; grants `SCOPE_TENANT` plus `ROLE_<role>`,
    and is the only kind any tenant-scoped endpoint accepts.
  - **identity** — names the person and no organisation; grants only `SCOPE_IDENTITY`,
    which reaches exactly three things and nothing else: `GET /api/memberships` to see
    what there is, `POST /api/auth/tenants/{tenantId}/token` to choose one, and
    `POST /api/organisations` to start one when there is nothing to choose. Issued when
    sign-in cannot pick an organisation: several to choose between, or none at all.

  Endpoints are guarded on `SCOPE_TENANT`, never on the absence of `SCOPE_IDENTITY`, so a
  third kind of token would have to be granted access deliberately rather than inherit it.
  The handful of endpoints that are `permitAll` — confirming an address, resetting a
  password, previewing and accepting an invitation — are reached by people who have no
  token to present, which is the whole reason those endpoints exist.

  `spring-boot-starter-oauth2-resource-server` verifies the signature;
  `AuthenticatedUserJwtConverter` branches on the kind to build the `AuthenticatedUser`
  principal. There are no sessions and no CSRF tokens.
- **The exchange endpoint is the one place a tenant comes from the request**, and it is
  safe only because the membership is looked up by the caller's own user id *together
  with* the requested tenant. Widening that lookup to the tenant alone would turn it into
  cross-tenant escalation; `MembershipApiTests` guards it with a two-organisation fixture.
- `aurevanta.security.jwt.secret` is unset by default, so each start generates a random
  key. Set it (32+ chars, e.g. `AUREVANTA_SECURITY_JWT_SECRET`) anywhere tokens must
  survive a restart or be accepted by more than one instance.
- Failures are RFC 9457 problem documents carrying a stable `code` (for example
  `email_already_registered`); `ApiExceptionHandler` is ordered ahead of Boot's own
  problem-detail advice so per-field validation messages survive.
- **A new failure needs a `code`, not just a message.** The frontend translates the code
  and ignores the prose.
- **Per-field validation errors carry a code too**, never Bean Validation's English:
  `errors: { password: { code: "size", min: 12, max: 72 } }`. The code names the
  *constraint*, so one catalogue entry per rule serves every form and the bounds come from
  the server rather than being repeated in the frontend. Adding a constraint to a request
  object means adding it to `ApiExceptionHandler.CONSTRAINT_CODES` and to
  `errors.validation` in `en.ts`; an unmapped one degrades to `invalid` rather than leaking
  its name. Only the constraint's *numeric* attributes go out — a regular expression or a
  message template is implementation detail, not something to render.
- **`@Size(max = n)` reports a lower bound of zero, so it gets its own code.** "Use between
  0 and 200 characters" is not a sentence worth putting in front of anybody, so a `size`
  whose `min` is zero is published as `max_size` and the catalogue says "no more than"
  instead of inventing a range. Every optional description in the domain is one of these.
- **One field can break several rules at once**, and Bean Validation returns them in a set
  whose order varies between requests. `CODE_PRECEDENCE` decides which is reported, so the
  answer never depends on arrival order — presence (`not_blank`) before shape (`size`,
  `email`), and anything unmapped last. A new constraint that can fail *alongside* another
  on the same field belongs in that list, or the message will flicker.
- **Request records normalise before validation, in a compact constructor.** `@Email`
  rejects a padded address outright, so trimming in the service would be too late — someone
  pasting an address out of a password manager would be told it is invalid. Passwords are
  deliberately never stripped: spaces are legitimate in a passphrase, and trimming would
  store one credential and compare another.

## Plans, and the work in them

- **Any member may do everything to a plan**, and roles govern administration only —
  invitations, members, organisation settings. Estimation is a team activity, and
  multi-estimator support is meaningless if only an owner may estimate. So
  `MembershipService.requireMember` is the twin of `requireOwner`, and no method in the four
  domain services touches a row before it has passed — some call it themselves, and the rest
  reach it through the `ProjectService` or `WorkItemService` lookup they already need, which
  is the same rule reached rather than a second copy of it. It re-reads the membership rather
  than trusting the tenant pinned into a token up to twelve hours ago, and the organisation
  is taken **off the row that comes back**, never off the request.
- **Nothing in the domain is deleted, and that follows from the rule above.** Widening write
  access makes destroying things everybody's to do, and a member who deleted a project would
  delete a colleague's work with nothing to put back — unlike removing a member, which an
  owner undoes by inviting them again. Projects and work items carry `archived_at`;
  archiving hides them from the default listing and any member may bring one back. **There
  is a second, independent reason**, which is why the column is right rather than merely
  defensible: an estimate is evidence M8 reads years from now, and deleting the item it
  hangs on would destroy that evidence long before the feature that needs it exists.
- **Archiving and unarchiving are one service method with a boolean**, because they are one
  decision read in both directions and two would be two lookups and two membership checks to
  keep in step. `archive` keeps the *first* moment the way `User.markEmailVerified` does:
  archiving something already archived is a no-op arriving twice, not a fresh decision.
- **A listing asks for one state or the other** — `?archived=true` — rather than returning
  both and leaving every caller to filter. The caller that forgot would show work somebody
  had deliberately put away as though it were live.
- **A project's name is not unique, and this time that is a decision.** M1a spent a whole
  milestone removing an accidental uniqueness constraint on an organisation's name; two
  plans called "Q3 platform work" is ordinary, and the id is what addresses them. Listings
  order by name, then `created_at`, then id — a name alone is not a total order, and one
  that is nearly total rearranges itself between requests.
- **`WorkItemController` has no class-level path**, because its endpoints sit at two. An
  item is created and listed *within* a project (`/api/projects/{id}/items`), which is the
  only moment its plan has to be named; once it exists it is addressed at `/api/items/{id}`,
  and so are its estimates and its dependencies. A path that repeated the project would be a
  second identifier the server had to check agreed with the first, and a disagreement
  between them is a refusal nobody could act on.
- **Asking for the work in a plan that is not there is `project_not_found`, not an empty
  list.** "No such plan" and "a plan with nothing in it" are different answers and only one
  is worth acting on, so `WorkItemService` and `DependencyService` fetch the project through
  `ProjectService` rather than assuming it.
- **An archived project still accepts work.** Archiving says the plan is not being worked
  from, not that it is sealed, and refusing here would need a refusal nobody asked for aimed
  at somebody tidying up an old plan.
- **500 items per project is the scale target**, which is what makes "does this need
  pagination" answerable: no. It is also what lets the cycle check read a whole graph in one
  query instead of walking it a hop at a time under a lock.
- **`ProjectService` has two ways to read a project and the split is not accidental.** `get`
  hands back the entity for the other services; `planned` adds `itemCount` and
  `estimatedItemCount` for the API. Without it, typing a task into a plan would pay for two
  grouped counts nobody asked for.

### Progress is reported, not observed

- **Its own endpoint** — `PATCH /api/items/{id}/progress` — rather than more fields on the
  item. Rewording a task is planning and saying it finished on Tuesday is reporting, and
  keeping them apart stops a rename from overwriting the dates M8 reads.
- **`started_on` and `completed_on` are dates, and `forecast_runs.starts_on` is the only
  other one in this schema.** Everything else records a moment the *server* observed; these
  record a day a *person* reports or states. There is no time of day in "we finished it on the
  twelfth", and storing one invents the part nobody claimed — midnight UTC reads back as the
  eleventh for every reader west of the meridian. `<input type="date">` hands back exactly what
  the column holds, and `formatDay` builds a date from its parts rather than through
  `new Date(iso)`, which would reintroduce the same shift in the last step before a person sees
  it. **`todayHere` is that argument reaching the other way**: a forecast's start is stated by
  the caller because an instant is not a date without a timezone and the only one a server can
  pick is its own, and the browser's pre-fill builds from local parts because `toISOString()`
  reports tomorrow after seven in the evening in New York.
- **A state that needs a date and did not get one is refused, never stamped with the
  server's clock** (`progress_date_required`). M8 and M10 read these dates and neither can
  tell one somebody reported from one the server guessed while nobody was looking. Which box
  is missing depends on the state in another, so the refusal names no field.
- **A claim carrying what its own status cannot hold is refused, not trimmed**
  (`progress_not_applicable`). This shipped wrong once: it kept whatever fitted the status
  and dropped the rest, so hours typed against work marked not started were accepted,
  discarded and never mentioned. Silently dropping input is worse than refusing it, because
  the person is not told they have been overruled. The entity writes what it is given; the
  service is the only place that says what a status means.
- **`DONE` does not require a start.** Work is routinely ticked off by somebody who never
  marked it as begun. Where both dates are given they must agree about which came first —
  `progress_out_of_order`.
- **`actual_effort_hours` is optional even when done.** Most teams do not track it, and
  refusing to let somebody mark work finished because they cannot say how long it took would
  refuse the common case to serve a feature years away.
- **`requireConsistent` is deliberately not a `switch`.** One covering every constant of an
  enum still compiles to a default nothing can reach, and an unreachable branch is a hole in
  the coverage gate that no test can close.

## Estimates: written once, never rewritten

- **There is no update and no delete, and that is the whole design.** A revision is a new
  row; the first stays exactly where it is. M8 asks how often a person's band contained the
  truth, which is a question about what they *said at the time*, and only rows nothing
  rewrites can answer it. **Guard this with the test, not the intention** — an `UPDATE` will
  look simpler at some point, most likely when somebody fixes a typo in their own estimate.
- **Immutability is doing authorization work as well.** Because no estimate is ever updated,
  no member can rewrite a colleague's even though every member may write estimates. The
  strictest rule in this schema is the one nobody had to enforce.
- **The current estimate is the newest row per (item, estimator)**, which the
  `(work_item_id, estimator_user_id, created_at desc)` index answers directly. Several
  people may hold a current estimate on one item at once — that is not a conflict to refuse
  but the signal M3 gets to reason about.
- **The estimator is a `User`, not a `Membership`.** A membership is deletable — M1 made
  sure of it, because removing somebody must not delete their account — so hanging this off
  one would destroy calibration evidence as a side effect of a person leaving. The foreign
  key deliberately does not cascade, mirroring "removal deletes the membership, never the
  identity".
- **Effort in hours, `numeric(12, 2)`, and `@Digits(integer = 10, fraction = 2)` matches the
  column exactly.** Without it `0.005` passes `@Positive`, rounds to `0.00` on the way in and
  lands as an estimate of nothing — breaking the rule that had just admitted it, silently,
  after the check that enforces it. A "day" is a calendar word and calendars are M11's; the
  UI may show days, the column stores hours.
- **A band the wrong way round is `estimate_out_of_order`, a document-level code and not a
  field one.** Each of the three numbers is perfectly good and what is wrong is the
  relationship between them, so `FieldProblem` — which exists to say "this box is wrong" —
  would have to pick a culprit arbitrarily. The form shows it in the banner. It is checked
  before the item is looked up, because it is a fact about the request alone and a caller who
  sent nonsense learns nothing about which items exist by being told so.
- **`GET /api/projects/{id}/estimates` carries every estimator's current range for a whole
  plan**, rather than the item response carrying a boolean saying one exists. It subsumes the
  boolean, gives the form the numbers to fill itself in, and shows a colleague's estimate
  instead of merely admitting one is there — and it keeps the dependency arrow pointing one
  way. A plan's worth at a time, because asking per item would be five hundred requests to
  draw one page.
- **Coverage is counted in `ProjectRepository`, in JPQL naming `WorkItem` and `Estimate` and
  importing neither.** It is the one place a feature reaches into another's tables, and it is
  acceptable because Hibernate parses every query at startup — a renamed entity fails the
  context rather than the next reader. Compare the package name written as a string that M1
  lost silently. It counts *distinct* items, since three people estimating one task is one
  item covered, and it ignores archived items so the count and the screen agree.

## Dependencies, and the lock that no constraint can replace

- **Finish-to-start with a lag, and nothing else.** The other three edge types multiply the
  scheduler's complexity for cases most teams never draw.
- **The lock is the sharpest thing in the domain, and removing it will look like a
  simplification.** Acyclicity is a property of every edge at once, so two callers can each
  read a graph their own new edge leaves acyclic and close a loop together. `DELETE` needs no
  lock — taking an arrow away cannot close a loop — but a write takes
  `ProjectService.lockForGraphChange` **before the graph is read** and holds it until the
  edge lands. Postgres has no unique index for "acyclic", and a conditional `UPDATE` cannot
  serve either: that makes a race on *one row* safe, and this is a race on a property of all
  of them. It is the `MembershipRepository.lockOwners` situation, not the token-redemption
  one. `DependencyGraphLockTests` releases two callers together, and its second case is the
  one worth having — the loop closes only through an arrow the loser had to have *read* the
  winner write, so a serial test could not tell the lock from luck.
- **The locked row holds none of the graph, and that is the point**: the edges that would
  otherwise have to be locked are the ones that do not exist yet. It is taken in
  `ProjectService` rather than on the repository directly, so reaching a plan still goes
  through the method that re-reads the caller's membership — a lock is not a reason to skip
  the check that says somebody may be here at all. It **refuses nothing** and returns `void`;
  every caller has already found a work item inside that plan, so a `project_not_found` from
  that line would be a branch no request could reach and no test could cover.
- **An edge is deleted where everything else archives**, and the two rules do not conflict.
  A dependency carries no history anything downstream reads; it is a constraint the scheduler
  obeys until it is gone, and one drawn by mistake that merely went dormant would leave a
  plan quietly forecasting around a line nobody could see.
- **Both ends go in the body** — `POST /api/dependencies` `{predecessorItemId,
  successorItemId, lagHours}`. Neither end owns the other, so putting one in the path would
  read as though it did; the project is not in the request either, because the items answer
  it and a third identifier that could disagree with them would be a refusal nobody could
  act on. Listing is by plan (`GET /api/projects/{projectId}/dependencies`), because a screen
  showing a plan needs every edge at once.
- **A self-edge is `self_dependency` and is answered before any row is read** — the only
  cycle decidable from the request alone, so a caller who put one identifier in both boxes
  learns nothing about which items exist. The remedy differs too: a cycle is a plan to go and
  rethink, this is two boxes with the same thing in them.
- **`dependency_cycle` carries the loop as `path`**, the way `slug_taken` carries
  `suggested`. Item identifiers rather than titles, since server prose is never shown to
  anybody and the client already holds the plan it is drawing. It starts at the proposed
  predecessor, follows existing arrows back round, and does not repeat the closing item — the
  frontend adds it back, or the last arrow would be the one step of the route nobody is shown.
  Breadth-first, so the loop somebody is handed is the shortest one rather than whichever a
  depth-first walk wandered into.
- **`self_dependency` and `dependency_across_projects` are `400`; `dependency_already_exists`
  and `dependency_cycle` are `409`.** The first two are facts about what the request names,
  the second two are conflicts with what is already drawn.
- **A duplicate has both a pre-check and an index mapping.** `uq_dependencies_edge` is in
  `ApiExceptionHandler.CONSTRAINT_CONFLICTS`, so the pair who pass the check in the same
  instant get `dependency_already_exists` rather than a bare `conflict` — and
  `ConstraintNamesTests` picked it up for free, which is what that test is for.
- **`lag_hours` is required and `@PositiveOrZero`.** Zero is the ordinary answer and is a
  claim rather than a guess — there is no wait — so the server does not fill it in; a
  negative would be a lead, which is a different kind of edge than this models. The form does
  answer it, sending zero for an empty box, because its own hint says that is what an empty
  box means: the strictness is the API's contract with every other caller, not a question to
  push at somebody typing.

## The plan screens

- **`WorkItems` loads and writes on its own** rather than through `ProjectPage`, because a
  plan and its contents are separate resources on the server and answering for one another's
  failures would mean a plan that could not be renamed because its items would not load. It
  asks for the work and its estimates with one `Promise.all` — neither answer depends on the
  other — and reloads from the server after every action, since the order is the server's, so
  is what an item ends up called, and so is which estimate is current after a colleague
  recorded theirs a moment ago.
- **A row opens one of four small forms** — reword, estimate, report progress, order — and
  each has its own `useFormFailure` over the field names it *can* render. "Can" rather than
  "does" is load-bearing for the estimate form, which shows one question at a time: the
  banner stays quiet for a complaint about a field on another screen, which is only safe
  because the form navigates to it. `ProjectForm` and
  `WorkItemForm` are two components rather than one parameterised by a field name, because
  the field names are the server's and are what a per-field complaint is keyed by. What they
  share is `optionalField` — "an empty box means nothing, not `''`" — stated once because the
  version of that bug which gets written is the second copy.
- **`numberField` exists because `Number('')` is zero.** The obvious version sends an
  untouched box as an estimate of no hours, and the visitor is told their estimate must be
  more than zero about a field they never filled in. **`numberFrom` is the same rule for a box
  a component is holding itself**, since the estimate form no longer renders the answers it is
  submitting and so cannot read them out of a `FormData` — the bug is in the rule, not in where
  the string came from.
- **The progress form offers only the boxes a status has room for**, which is
  `progress_not_applicable` seen from the other end: a server refusing something a screen has
  just invited you to type would be a trap rather than a check. It warns before a status
  change discards something already recorded, because the boxes holding it have just
  disappeared and somebody not told would reasonably assume the values survive.
- **Ordering is asked from one end only** — "must finish before…", opened on the task that
  finishes first — so there is no control for which way the arrow points and no way to draw
  one backwards by misreading a label. The list offers neither the task itself nor anything
  it already comes before, both being refusals the server would give, but it does not try to
  hide a cycle: that is a property of the whole plan decided under a lock, and guessing at it
  here would hide options that are legal by the time the request lands.
- **That panel stays open once an arrow lands**, alone among these forms, which all close on
  a successful write. Ordering is plural where rewording and estimating are not, and the list
  it just joined is in the same panel. Each row says **both** directions — what it must
  finish before, and what it is waiting on — because either alone answers half the question
  somebody opened the plan with. An arrow pointing at work archived since is named as such
  rather than shown as a blank, since the archived listing is a different screen.
## The estimate form: one question at a time, and never the middle first

- **`EstimateForm` asks three questions in a fixed order and shows one at a time.** Bad case,
  good case, typical case, and then a review. **Reordering `STEPS` is not a cosmetic change**:
  three numbers asked together anchor on whichever is answered first, and three boxes invited
  the middle to go first — which is what 3/5/8 is, the middle plus a bit and minus a bit around
  an anchor nobody examined. The bad case goes first because it is the only one of the three
  with nothing above it; the good case has a floor and compresses far less under an anchor. The
  middle goes last because the fit does not use it, so it is the one number that can afford to
  be anchored.
- **No earlier answer is on screen while the next is asked**, and they are not in the DOM
  either — the answers live in state and only the current input is rendered. The anchor is
  *seeing* the previous number, not typing it, so a hidden input holding it would be no better.
- **Nothing on the form names a percentile.** "P90" asks somebody to reason about tail
  probability, which nobody can do; surprise is a thing people recognise. The catalogue entries
  for `P10`/`P50`/`P90` were deleted rather than left unused, so the test setup's
  missing-translation failure is what stops one coming back as a "clearer" label.
- **There is no fast path back to three boxes**, and adding one would undo the milestone: it
  would be used by everybody, because it is quicker and because the people most certain they do
  not need the framing are the people it is for. Revising is the case that objection is
  strongest for, and it is answered by pre-filling every step from the current estimate.
- **A refused submission navigates.** On a one-question form the box a complaint belongs to is
  almost never the one in view, and `useFormFailure` suppresses the banner *because* the field
  is one this form renders — so without this a refused `p10Hours` would appear nowhere at all.
  A field complaint brings its own question back; `estimate_out_of_order` returns to the first,
  since it belongs to all three.
- **The review is the first and only moment the three are seen together**, which is where the
  betting frame is asked and both warnings arrive. The bet gates nothing and has one control:
  saying yes is pressing save, and the only button is the way out.
- **A colleague's numbers are never on screen while somebody is answering.** Two people who
  anchored on each other are not two estimates, and the band they produce is confidently narrow
  for a reason nothing downstream can see — which is exactly what multi-estimator support exists
  to make visible.

## What is worth questioning about an estimate

- **Both checks were measured against the failure they exist to catch, and neither catches
  it.** 3/5/8 has a consistency of 1.02 and a P90 1.60× its P50; so do 2/3/5, 5/8/13 and 1/2/3.
  A Fibonacci triple is very nearly geometric, which is the shape a log-normal fit expects, so
  the canonical garbage is **coherent** garbage — invisible to anything looking at three numbers
  in isolation. `EstimateQualityTests.theCanonicalGarbagePassesBothChecks` asserts it. **The
  question order is the defence and these are a backstop**; raising a threshold until it catches
  them would fire on nearly every estimate any team writes.
- **They advise and never refuse.** A tight band is sometimes exactly right, and a rule that
  blocked one would become a specification people learn to type — 3/5/8 with an extra step, and
  the product teaching the failure it exists to detect.
- **Both thresholds are stated once, in `EstimateQuality`, beside the arithmetic they bound**,
  and read by the forecast and the estimate alike. One estimate cannot be worth questioning in a
  forecast and fine on the plan screen. The browser is told neither number: it renders a flag the
  server sent, the way it renders a `code`.
- **`estimate` may import `forecast.model`, and that reverses no arrow.** The rule the domain
  packages keep is that no *feature* depends on a feature that depends on it, and
  `forecast.model` is not one — no entity, no repository, no service, no Spring, no JPA, and it
  imports nothing from this codebase, so it cannot be part of a cycle in it. It is used the way
  `java.lang.Math` is.
- **`POST /api/estimates/quality` is the one POST in this API that writes nothing**, and the one
  method in `EstimateService` with no membership check — there is no row to reach. It exists
  because the warning has to arrive *before* the estimate does: an estimate is written once and
  never rewritten, so warning after saving would make "that is not what I meant" cost a second
  row. It shares `requireAscending` with `record`, which is not optional — a range that does not
  ascend has no fit, and without the check the endpoint would answer 500 to an ordinary typo.
- **`elicitation_method` is stored because it cannot be recovered; the warnings are not because
  they can.** How a question was *put* leaves no trace in the three numbers, and it is the only
  instrument that can ever say whether M5 worked — split M8's calibration record by it and the
  question answers itself. Whether a range is worth questioning is arithmetic over three columns
  and one constant, so keeping it would freeze today's threshold into rows that outlive it.
  `V15` backfilled `three_point` and that backfill is **true** (V13's move, not V14's): three
  boxes really were the only form this product ever had.

## Forecasting: the engine, and the decisions inside it

- **`forecast.model` is separated by *purity*, not by feature, and it is the only package in
  this application that is.** `Normal`, `LogNormalFit`, `ItemModel`, `TeamFactor`,
  `ScopeGrowth`, `Precedence`, `Schedule`, `Engine`, `Forecast`, `Histogram` and
  `WorkingCalendar` hold no Spring, no JPA and no I/O: they are functions over primitives. `forecast` beside it is an ordinary feature package — entity, repository,
  service, controller, responses — that reaches the other four domain packages through their
  services. **The failure mode here is not a crash but a plausible number**, and the seam is
  what makes the arithmetic checkable against sums that exist outside this codebase. The
  strongest test in the milestone is that one: a chain at capacity 1 is a sum of independent
  log-normals with an exact mean and variance, and forty tight tasks come out at 811.08
  sampled against 811.12 from the closed form and 811.1 from a measurement taken in
  `roadmap.md` before any of this existed.
- **Four modelling decisions look like bugs from the outside and are not.** Each is argued in
  `m3a-plan.md`; the risk is that one gets quietly *simplified* by somebody who did not read
  it, which no test failure would announce as such.
  - **A mixture over estimators, never an average.** One estimator is sampled per item per
    run, so when two people disagree the band gets wider. That is the disagreement being
    reported rather than a fault; averaging is the one change here that converts uncertainty
    into false confidence, and it will be proposed the first time a band comes out wide.
  - **An unestimated item stays in the graph as a zero-effort node.** Dropping it would
    silently delete the precedence running through it, so the plan would come out early for a
    reason nobody could see. Coverage is reported instead — `unestimated_items`, plus the
    counts on the run.
  - **Work already under way is drawn from the estimate *conditioned on what has been
    spent*** — a truncated draw, not a fresh one. A task that has run long has more left, not
    less. This is the defining property of the distribution this product chose, and a model
    without it flatters every late project.
  - **Capacity is required and has no default.** `roadmap.md` measured it moving the P90 by
    70%, and a default would be a claim about a team made by a server that has never met
    them. The box on screen is empty for the same reason: a box already filled in is a box
    nobody reads. **The two M3b assumptions are required on the same grounds and one sharper
    one**: they *have* a neutral value, zero, and zero is a claim — that nothing in this
    team's world has a common cause, and that no unlisted work will ever appear. Defaulting
    to it would ship M3a's model under M3b's name with the notices that admitted to it
    deleted.
- **The two M3b effects are different effects and merging them would count one twice.** A
  shared team factor makes each item longer; scope growth makes more of them. Under a summing
  aggregator those are the same thing — which is why modelling scope as a multiplier is
  rejected in `m3b-plan.md` decision 3, since two multipliers compose into one. **They
  separate only because the aggregator is a scheduler**: new items compete for slots, so they
  make the plan longer *and* make everything else wait. Measured, they load different
  bottlenecks — where capacity binds a multiplier is heavier, because more smaller pieces pack
  better; where capacity is plentiful scope is heavier, because it adds a step to a path.
  Whoever proposes collapsing them into "one uncertainty number" is proposing to delete that.
- **The team factor's median is pinned to exactly 1, and this is the quietest thing in the
  engine.** `TeamFactor` holds a `LogNormalFit` whose `mu` must be zero and refuses one that
  is not. A multiplier with a *mean* of 1 has a median below it, so it would drag the centre
  of every forecast down while claiming only to widen it — no test would fail and no number
  would look wrong. The estimates already carry the central case; a factor whose job is
  common-cause spread must leave the middle alone and pull the tails apart.
- **A parameter set to none must consume no randomness.** `TeamFactor.NONE` and
  `ScopeGrowth.NONE` take no draw at all rather than drawing and discarding. A draw that
  changed no number would still advance the generator, shift every subsequent number in the
  run, and silently unreplay every forecast stored before that parameter existed — with
  nothing failing to say so. It is invisible in review; the tests that guard it assert the
  generator's next value is untouched, and each has a mirror asserting a live parameter does
  spend a draw, so neither passes because `sample` does nothing.
- **Either a version bump keeps the old engine inside the new one, or every earlier run
  becomes a record that can be read and never replayed.** `Engine.VERSION` is 2, and version 1
  is version 2 with both parameters at zero — draw for draw, which is what the rule above
  buys. `V13` backfilled the three assumption columns with zeros, and that is a *true* record
  of what those runs assumed rather than a placeholder. A future change that cannot be reduced
  to a parameter setting — a different fit, a different scheduler — does not get to pretend:
  it bumps the version, old runs become read-only history, and M10 has to be told, because
  comparing runs across an incomparable bump is how a tool reports a date sliding when nothing
  moved.
- **A run is written once and never updated, like an estimate.** `forecast_runs` stores its
  resolved inputs, its seed, its sample count, its capacity, its priority rule and
  `Engine.VERSION`, so any run can be replayed exactly — which is where M6 gets its per-item
  contribution instead of storing a duration vector per item per run, and what M10 reads to see
  a date sliding. `ForecastApiTests` replays a stored snapshot and asserts it reproduces its
  stored percentiles: **that is the test that fails the day somebody changes the model without
  bumping the version** — and M6 made the same comparison a runtime guard, so a run that no
  longer reproduces is refused rather than explained.
- **The generator is `java.util.Random`, deliberately.** It is the only one in the JDK whose
  algorithms are written into its *contract* rather than only its implementation.
  `SplittableRandom` is faster and takes its Gaussian from a default method nothing promises
  to keep, so a JDK upgrade could silently move every old forecast — worse than a version
  bump, because no version would have changed.
- **Snapshots use their own `ObjectMapper`, pinned in `ForecastSnapshots`.** The
  application's is *configuration*: a naming strategy or inclusion rule set for the API's sake
  would change every snapshot written from that moment and stop every earlier one from being
  readable. The snapshot holds the ranges people typed rather than the fitted parameters, so
  the fit stays an implementation detail a version bump may redefine.
- **The seed goes out as a JSON *string*.** It is sixty-four bits and a JSON number is a
  double in a browser, so as a number nearly every seed published arrived silently rounded —
  and a seed that is nearly right reproduces nothing. `jsonPath().value` re-reads a document
  as the expected type, so `isString()` is what actually pins it.
- **Every forecast reports what it did not do, and the screen prints it beside the number.**
  Three codes are emitted, and they are all properties of the plan rather than of the model.
  The panel renders a code it has never heard of rather than dropping it: the server is what
  versions ahead here, and silently showing nothing is that rule failing through the back
  door. **A band without its caveats is this product's own failure mode with a chart on it**,
  which is why they are not behind a disclosure — and neither are the five assumptions, for
  the same reason.
- **`no_team_factor` and `no_scope_uncertainty` are retired, not deleted, and the difference
  is the history.** M3b models what they name, so nothing writes them; the constants stay in
  `ForecastLimitation` and their wording stays in the frontend catalogue, because every run
  made before M3b still carries them in its stored `outputs` — and an enum missing a value
  that exists in stored JSON is a forecast that cannot be deserialised at all. That is why
  limitations are stored on the run rather than worked out when it is read: a limitation
  derived at read time would have made every M3a run silently claim a model it never had.
- **Any member may forecast**, like everything else in the domain. A plan with nothing
  estimated is refused with `nothing_to_forecast` (`422`) and **no row is written** — a
  refusal that had stored a run would leave the history holding a forecast nobody received.
### The calendar: hours become a date, and the date is derived

- **The engine is in hours from end to end, and the calendar sits beside it rather than
  inside it.** `WorkingCalendar` is a pure function in `forecast.model`; nothing about a
  working day reaches `Engine`, `Schedule` or the `inputs` snapshot a replay is fed. That is
  what keeps a calendar change from being a model change — otherwise `Engine.VERSION` would
  have to move every time somebody adjusted a holiday, and a stored forecast's numbers would be
  invalidated by something that changed none of them.
- **The working day is one worker's, never the team's, and this is the sharpest thing in M4.**
  `Schedule.finish` already ran `capacity` items at a time, so the hours a date divides are a
  completion *time* with capacity inside them. Dividing by a team's daily total — "four people
  at six hours each, so a working day is 24" — counts capacity twice and produces a date four
  times too early, with the band unchanged, the assumption on screen and nothing anywhere
  looking wrong. The hint under the box says whose day it is, and
  `WorkingCalendarTests.convertingToDaysDoesNotUndoTheCapacityTheSchedulerAlreadyApplied` is
  the assertion; neither is optional, because every test that only checks *a date came out*
  passes against the bug.
- **The division is exact `BigDecimal`, because a day boundary is a step.** `ceil` turns a
  smooth quantity into a discrete one, so an error in the last bit of a double is a whole day
  rather than a rounding difference: 20.01 hours at 6.67 a day is exactly 3 in decimal and
  3.0000000000000004 in binary. Both ends arrive as `BigDecimal` already — a `numeric(14, 2)`
  percentile and a `numeric(4, 2)` working day — so `divide(..., 0, RoundingMode.CEILING)`
  costs nothing and needs no conversion.
- **Dates are derived and the *rule* is stored.** `forecast_runs` keeps `starts_on`,
  `working_hours_per_day` and `calendar_rule`; the five dates are computed in
  `ForecastResponse` and nowhere else. Store what is expensive or lossy to reproduce, derive
  what is cheap and deterministic — and what makes it deterministic across time is the rule's
  *name*, `five_day_week`, held for the reason `priority_rule` is: two defensible calendars
  give two different dates from identical data. A working day read from a *setting* instead
  would move every historical date the moment somebody edited it, and M10 would report a slide
  that never happened.
- **The stored rule is what decides whether a run has dates at all.** A run under a rule this
  code cannot resolve reports its hours, its own rule's name and no dates, rather than being
  read through today's calendar. M11's real availability arrives as a **new rule name**, never
  as an edit to this one.
- **A run made before a calendar existed has none, and the columns are nullable.** `V13`
  backfilled zeros and could argue they were true; `V14` deliberately backfilled nothing,
  because a run made last week did not assume a six-hour day — it assumed no calendar, since it
  produced no date. A default here would invent a claim on behalf of somebody who never made
  one, in the one table whose whole purpose is to say what was assumed. The screen says which
  of the two absences it is rather than showing a blank.
- **The date is the headline and the hours stay.** A band in hours advertises that it came out
  of a model; "Aug 25" does not, and it gets pasted into a plan with the assumption behind it
  left in the browser. Removing the hours would leave nothing on screen that came out of the
  engine, and would make the working day invisible in exactly the way this milestone warns
  about. The confidence control (50 / 80 / 95%) reads percentiles already in the response, so
  moving it sends **no request** — that is the feature rather than an optimisation, since the
  trade only reads as a trade when both numbers are two readings of one forecast.

### Variance contribution: a ranking, and never a share

- **The number is a squared correlation, and it is not a partition.** Each source's sampled
  value is correlated with the plan's completion across every run; the square of that is what
  the ranking is by. **They sum to exactly 1 only for a chain at capacity one with no common
  cause** — the summing model this product deliberately stopped using, and the closed-form
  oracle `ContributionsTests` is built on. In any real forecast M3b's team factor multiplies
  every item by the same draw, so everything moves with everything and the shares add to well
  over one. **Nothing may render them as percentages**: a bar per source, and the panel says in
  a line that they overlap and why. A screen showing a plan accounting for three hundred percent
  of its own uncertainty is this product's own failure mode with a chart on it.
- **Three kinds of row, and the two that are not items are what make the ranking honest.** The
  shared team factor and the work nobody has listed are both sources of spread, either can
  dominate, and when one does the true answer to "what should I spike" is that no estimate below
  it is the problem. A source nobody *modelled* gets **no row at all** rather than a row reading
  zero — it never varied so it measures as nothing either way, but zero invites a reader to
  conclude their team has no common cause when what they did was decline to model one. Decided
  from what the run stored, which is the rule a run made before there was a calendar follows.
- **Nothing is stored, and that is what makes it work on the past.** The per-item durations come
  from replaying the run out of its own seed, which is what M3a kept a seed for. The decisive
  argument is not the five million numbers a column would hold: a stored contribution would only
  ever explain runs made after it existed, and a derived one explains every forecast this
  product has ever produced. `V16` would be a mistake — see `m6-plan.md` decision 1.
- **A replay must prove itself before it explains anything.** `ForecastService.contributionsTo`
  compares the replay's six figures against the six on the row and refuses with
  `forecast_replay_mismatch` on any difference. That is `ForecastApiTests`' persistence assertion
  promoted into a runtime guard, and it needs no list of replayable engine versions: it asks the
  only question that matters — *does this still come out the same?* — so it catches a version
  bump, a JDK generator change and an accidental edit to the sampler alike. A ranking from a
  different model is not a rougher ranking of this plan, it is an exact ranking of a plan nobody
  forecast, and it would look entirely reasonable.
- **Watching the engine takes no draw and moves no version.** `Engine.run` has an overload taking
  a `RunObserver`, called after everything that draws; `RunObserver.NONE` is the no-op the
  ordinary form delegates with, the way `Schedule.finish(durations)` delegates with
  `NOTHING_FOUND`. The test that says the version need not move is byte-identity: the same seed
  produces an equal `Forecast` with an observer attached and without. Measured at five hundred
  items and ten thousand runs, the accumulator costs nothing — 489 ms against 491 ms.
- **Welford's co-moments, because the obvious formula returns NaN on a large plan.** Accumulating
  `Σx`, `Σx²` and `Σxy` and subtracting at the end is wrong in the third decimal on a plan of a
  million hours — enough to reorder a ranking whose whole purpose is the order — and on a plan of
  a billion the subtraction goes negative and `sqrt` gives `NaN`, which is not valid JSON. A
  million hours is reachable: `@Digits(integer = 10, fraction = 2)` lets one estimate be ten
  billion. `ContributionsTests` keeps the naive formula and asserts it fails, so putting it back
  is a failing test rather than a tidier-looking method.
- **A source with no variance contributes exactly zero, and that is the ordinary case** — an item
  nobody estimated, work already finished, and an estimate of three identical numbers all produce
  it. The correlation is `0/0`, and a `NaN` would sort unpredictably through a ranking and fail
  to serialise at all.
- **Titles come off the plan as it stands, because the snapshot never held one.** `ForecastInputs`
  stores identifiers and no title on purpose — M10 diffs those snapshots and a rename is not a
  thing that moved — so the response resolves names from the live and archived listings. Work put
  away since is named and marked; work the plan no longer holds at all says so rather than
  rendering a blank. `describeWork` on the frontend and `titleOf`/`isArchived` on the backend are
  that same three-way rule stated once on each side, shared by the ranking and the cuts.

### Inverse queries: what to cut, and the pairing that makes it an answer

- **A cut is a draw taken and *discarded*, and this is the sharpest thing in M7.** `ItemModel`
  carries a `cut` flag; `asCut()` sets it and `sample` still draws exactly as it would have, then
  returns zero. The two obvious alternatives are both wrong and the second is silent: **removing
  the item** shortens the loop and renumbers every edge, and **emptying its estimates** makes
  `sample` return from `weighsNothing()` *before* `random.nextInt`, so a weightless item takes no
  draws and the generator runs ahead by two per run. Measured, the resulting noise lands in the
  same range as the effect being measured — a cut worth having buys about five points, and an
  unpaired comparison moves by four — so the ranking becomes a coin flip that looks exactly like
  an answer. `CutTests.cuttingOneItemMovesNoOtherNumberInTheRun` is what says it does not: every
  other item, every run, byte for byte.
- **The priority order must not move either**, which is why `typicalEffortHours` ignores the flag.
  A cut that reordered the scheduler's queue would leave the counterfactual differing from its
  baseline in two ways at once, with nothing able to say which produced the difference.
- **The caller names the candidates, and the server proposes none.** Which work is negotiable is a
  judgement about value and nothing in this schema records any — a four-week task a regulator
  requires is not a candidate and a two-day nicety is. It also bounds the cost honestly, since
  every candidate is a whole simulation. **M6's ranking is not the shortlist**, and `roadmap.md`
  said it was until M7 corrected it: an item that never varies contributes nothing to the spread
  and is frequently the best thing to drop. **The same work named twice is one candidate** — a
  second mention asks no second question, and weighing it twice would rank one item in two rows
  and let the search cut it at two of its steps, reporting one sacrifice as two.
- **The numbers never add, and here that matters more than it did in M6.** Every figure is a
  percentage with a plus sign in front of it, so a column of them reads as arithmetic waiting to
  happen. Two cuts on one chain shorten the same path; two on separate branches leave the later
  one deciding. So the singles are labelled *what this buys on its own*, **the cumulative answer
  is searched for and measured at every step**, and nothing on screen puts the two in one column.
- **The search is greedy and says so.** Best single, then the best of what is left *with that one
  already cut*. Round one of it **is** the singles — run once and read twice, so the two can never
  drift. It stops for one of three reasons and the answer names which: the bar was met, the
  candidates ran out, or the simulation budget did. **`BUDGET_SPENT` is not a defensive branch**;
  twelve candidates that never reach the bar would be seventy-nine runs, `MOST_SIMULATIONS` is 40,
  and a search reporting the best thing it happened to look at without saying so is the failure
  mode of every heuristic.
- **Nothing is written, and that is what lets it answer about the past.** `cutsFor` is
  `readOnly`; every counterfactual is a replay of a stored run out of its own seed, and forty
  simulations can go past for one question without `forecast_runs` gaining a row. `roadmap.md`
  worried this milestone would fill that table with hypotheticals and turn M10's sliding-date
  detector into a history of things nobody planned; it did not, and M11 inherits the answer.
- **It weighs and never decides.** `POST /api/forecasts/{runId}/cuts` changes nothing; acting on
  the answer means archiving work on the plan screen, where somebody can see what else it is
  connected to. The tick list on `TargetDate` offers only work the run was about — compared by
  `createdAt`, since the snapshot holds no item list — because a refusal about a box the screen
  has just invited somebody to tick is a trap rather than a check.

### Calibration: how often the ranges contained the truth

- **`GET /api/calibration` stores nothing**, which is M6's decision 1 spent a third time: a
  stored hit rate would explain only the estimates written after somebody added the column,
  and a derived one explains every estimate this product has ever held. It is also the only
  honest shape — a calibration figure changes every time anybody finishes a task, so writing
  one down would freeze a number whose whole nature is that it moves. Organisation-wide and
  not per plan, because a single plan holds far too few completed items to tell 45% from 80%,
  and because calibration is a property of people rather than of plans.
- **A forecast is an estimate written before the reported start day, and the ambiguous day is
  excluded rather than split.** `estimates.created_at` is an instant the server observed and
  `started_on` is a day a person reported, so no timezone makes the comparison exact — the rule
  therefore has to err in the safe direction, and an estimate written at any hour of the start
  day counts as a report. That costs real forecasts, and the cost is *published* as
  `movedByTheStartDay` so it can never become a quietly better hit rate.
- **The boundary is the earliest start ever reported, never the column.** `work_item_progress`
  (`V16`) is what makes that possible: before it, the rule could be satisfied after the fact by
  editing the date it is measured against. Every progress write appends to it in the same
  transaction, so `work_items` may keep being written over — the item holds the latest state for
  the screen and the scheduler, and the log holds who claimed what and when. It backfills
  nothing, so an item older than the table falls back to its column, which is the only claim
  anybody ever made about it.
- **Three buckets and nothing may add them.** *Forecasts* is the headline; *reports* is what
  somebody wrote once they could see how the task was going, and says how large hindsight is on
  a team's own work; *unbounded* is every range on finished work nobody reported a start for,
  which cannot be told from a report. `DONE` needs no start date, so that third bucket is most
  of a real organisation's evidence — dropping it would discard the majority to exclude a
  minority. **Only the forecasts are split by estimator and by method.**
- **Archived work is scored, and so is a former member's.** Coverage counts ignore archived
  items so the number and the screen agree; this is the opposite case, because archiving must
  not be a way to lose a miss. Everything is scoped to one organisation, which corrects `V9`'s
  comment: a consultant with two clients has two records and the two never meet.
- **The hit rate never goes through the fit.** `p10 ≤ actual ≤ p90` is arithmetic on what
  somebody typed, so a change to how a range is modelled can move the corrections and can never
  move the headline. Only `medianPercentile` and `bandWidthMultiplier` use `LogNormalFit`, and
  a range with no width has no scale to land on — it counts in the rate and not in those two.
- **The rate is gameable and the pair is not, so neither ships alone.** Estimating one to a
  thousand hours contains every outcome and scores 100% forever; `bandWidthMultiplier` reports
  that as a number below one. The API makes it structural rather than conventional: `rate` and
  `corrections` are each **one nullable object** rather than loose fields, so there is no shape
  in which a client can hold a rate without its interval or a bias without a width.
- **A Wilson interval at 80%, everywhere a rate appears** — headline and every row alike, since
  six outcomes and ninety produce rates that look alike and mean nothing alike. 80% because
  every interval this product shows is a P10–P90 band and two conventions on one screen is one
  too many, which also means it is built from `Normal.P90_Z`. Both ends are clamped into
  `[0, rate]` and `[rate, 1]`: the algebra puts them there and binary arithmetic misses by one
  part in 10^16 in either direction. That is the opposite of clamping the normal approximation,
  which at four in five is genuinely three points past certainty — `CalibrationTests` keeps that
  form around to fail.
- **Nothing is applied and nothing is judged.** The correction is reported and never fed back:
  applying it closes a loop on M8's own evidence, so the record converges on 80% while the
  estimating does not change, and two runs of one plan would then differ for a reason stored on
  neither. It is **not** a `ForecastLimitation` either — those are frozen at run time and this
  moves every week — so the forecast panel carries it as a caveat about the inputs, read fresh
  and absent when nothing is scored. And **no threshold lives in the browser**: the page states
  what a well-judged set scores and shows what this one scored, because two rules about one
  estimate is what `EstimateQuality` exists to prevent.
- **People are named and never ranked.** Rows come out in name order, then by identifier, with
  the count and interval on each. This product ranks work — M6 ranks what widens a band, M7
  ranks what to cut — and a hit-rate leaderboard is won by writing one to a thousand.
- **Nothing on the screen names a percentile.** Where the truth typically lands inside somebody's
  own range is drawn as a *position* between **Good case** and **Bad case** — the two questions
  `EstimateForm` actually asks — with a tick at the middle. That is the same refusal M5 makes,
  and it is why the nav says "Track record" while the route and the API say `calibration`.
- **The empty state is the main screen for a year, and is designed as one.** Scoring needs
  finished work carrying both an estimate and a measured actual, and the actual is optional
  because most teams do not track it. So `coverage` publishes the two gaps as two different
  things to go and do, and `ProgressForm` says what the effort box is *for* rather than pressing
  anybody to fill it in.

### Throughput: a second forecast, with no estimate in it

- **Items completed per calendar week, never hours.** `completed_on` is required on anything
  marked `DONE`, so this history exists in full for every plan — where `actual_effort_hours` is
  optional and mostly absent, which is M8's own problem and the reason M9 can answer today and
  M8 cannot. Counting items also means **an unestimated item is ordinary evidence**, where the
  engine carries it at zero effort and reports a limitation: that is the one place this
  forecast is better informed than the engine's.
- **A week nobody finished anything in is part of the history, and it is the easiest thing here
  to get wrong.** Completion dates arrive as a list and grouping a list of dates yields only the
  weeks that had something in them — which inflates the rate by exactly the fraction of the time
  the team was not delivering, and those weeks are what "absorbs interruptions and holidays"
  means. Ten items in one week and nothing for three is 2.5 a week, not ten, and
  `ThroughputTests` says so.
- **The history begins at the first completion**, not at the plan's creation: counting the idle
  months before anybody began would make the rate a property of when somebody opened a form.
  That is a bias in the optimistic direction and it is named rather than hidden.
- **`Throughput.RULE` is a name for the same reason `WorkingCalendar.RULE` is.** A week
  beginning Sunday is a second name, never an edit to this one — and the bucket is keyed by the
  Monday on or before rather than by an ISO week *number*, which needs a year beside it and
  disagrees with the calendar for a few days every January.
- **A bootstrap over observed weeks, never a fitted distribution.** A Poisson fit asserts that
  weeks are independent draws of one rate, which is false of every real team; resampling asserts
  only that future weeks look like some multiset of past ones — and unlike a fit it has an
  oracle, because twenty weeks of exactly five must answer exactly eight weeks for forty items
  in every run with no spread.
- **It cannot draw a week worse than the worst one observed, and that is published rather than
  patched.** Simulated against a team losing one week in ten, 41% at two months of history have
  never seen their own bad week, and theirs is the answer that comes back early and confident.
  So `worst()` is on screen, the floor is a quarter (`WORTH_SHOWING`) and a year is where the
  warning stops (`WORTH_TRUSTING`). Inventing a tail nobody observed is the alternative, and it
  would be a number with no source inside a forecast whose whole claim is that it came from the
  team.
- **It does not absorb scope growth, whatever `roadmap.md` used to say.** It absorbs the *drag*
  of past discovered work, not the fact that more will appear, so projecting the listed items at
  a rate earned partly on unlisted work is optimistic by that share.
  `throughput_excludes_unlisted_work` goes out with every answer.
- **The plan's own history, not the organisation's.** An organisation-wide rate applied to one
  of three plans is optimistic by however much attention goes elsewhere, and nothing in this
  schema records how attention is split — that waits on M11. A young plan gets a window and no
  forecast, which is M8's empty state in a second place.
- **Elapsed weeks, and no working day anywhere.** A week of history already contains its
  holidays and its Friday afternoons; multiplying it by a working day would be M4's own error —
  capacity counted twice — from the other side. The as-of day is stated by the caller for
  `todayHere`'s reason.
- **Nothing is stored, and there is no run.** `GET /api/projects/{id}/throughput?asOf=…` writes
  nothing: the history is already dated, so an answer as of any day is reproducible from it, and
  a row would be a cached answer to a cheap question. It also keeps `forecast_runs` meaning one
  thing — somebody asked the engine — which is what M10's detector walks. The seed is derived
  from the question rather than random or configurable, so asking twice agrees.
- **`ThroughputLimitation` is its own enum and not `ForecastLimitation`'s.** That one is
  serialised into `forecast_runs.outputs` and read back years later, which is why nothing may be
  deleted from it; nothing here is stored at all. One enum would have handed the looser rules to
  the stricter.
- **Three ways to have no projection, and the window ships in all of them** — nothing left to
  deliver, too little history, or a rate that would not clear the backlog inside ten years. Each
  says which rather than answering with an absence, because the window is the half a reader can
  judge for themselves.
- **The gap is two dates and never one number.** Four things differ between the two forecasts
  and **two make the engine look slow while two make it look fast**, so a subtraction is not
  interpretable alone — the screen names all four, with scope growth and coverage filled in from
  the run. Nothing averages them, ranks them or resolves them: "six weeks against eleven" starts
  a conversation and a number in the middle ends it.
- **A refusal about the plan's own data is passed on, not swallowed.** Losing the read leaves the
  band alone; it does not leave the reader guessing. A task marked finished next week — which the
  progress form accepts, see *Dates the schema accepts and reality does not* in `roadmap.md` —
  takes the throughput answer away, and the screen says which task to go and fix.

### Saying it to somebody who does not know what P90 means

- **Every date this product publishes is one-sided, and no sentence may pair two of them.**
  "There is an 80% chance that Q3 platform work will be finished by 25 August" is the shape;
  "between 12 October and 20 November" is not, and it is the version `roadmap.md` and
  `product-concept.md` both used to carry. A two-sided interval invites *so not before the
  12th?* — a question nobody manages against, about the end of the distribution the model is
  worst at — and it quietly halves the confidence a reader thinks they have at the far end.
  M10's own plan reintroduced the wrong shape in a later step and its review pass caught it;
  The case named *says one date and never a window* is what fails if it comes back. **The band in hours stays**, for
  M4's reason: remove it and nothing on screen came out of the engine.
- **`Comparison` decides whether two runs may be set beside each other at all** — the engine
  version, the priority rule, the calendar rule, the working day, the five assumptions. It is `Comparable` in no
  file, deliberately: `java.lang.Comparable` is auto-imported into every one, so a class of that
  name in `forecast.model` shadows it for the whole package. The assumptions are `BigDecimal`s
  and are compared with `compareTo`, because `30` and `30.00` are the same assumption.
- **A difference in the *model* refuses; everything else is a term.** The engine version and
  the priority rule are the two, and the second is worth knowing about because there is only one
  rule today: a calendar is laid over an answer the engine has already given, so two calendars
  are one answer read twice, where a priority rule is *inside* the scheduler and two rules are
  two answers. The refusal and the reporting look contradictory and are not. M6 refuses when the *model* cannot reproduce a run — there is nothing to compare
  with. This reports when the *question* changed, which is the single most useful thing the
  feature says: *six of those eight days were you halving the capacity*.
- **The decomposition's terms add up because they are computed cumulatively**, one change at a
  time in a stated order, each measured with every earlier one already applied and the last
  state being the newer run itself. The obvious version — re-run once per change and report each
  difference — gives five numbers that do not account for the movement they claim to explain,
  because a simulation is not linear in its inputs. M6 and M7 both met this and both answered
  *do not add them*; here the sentence **is** the feature, so that answer was not available.
- **`Movement.RULE` names the order for `Schedule.PRIORITY_RULE`'s reason.** Reordering `ORDER`
  is not a refactor: a reader told scope cost them five days and estimates four will act on it,
  and swapping those two steps moves days between the lines. Two of the seven steps are not in
  the milestone's own plan and both are load-bearing — **`SAMPLING`**, because two runs never
  share a seed and without it the terms sum to the distance between two things nobody was
  shown, and **`CALENDAR`**, because a working day that changed moves the date without touching
  an hour and would otherwise land in a residual.
- **The detector measures cumulative drift against the band's own width, never direction.**
  Measured: a plan that is not slipping still moves out one week and in the next, so "out three
  times running" fires on 86% of plans re-forecast weekly for six months, and no run length
  fixes it. `Drift.WORTH_SAYING` is stated once beside the arithmetic the way
  `EstimateQuality.TIGHT_BAND` is, and the browser is told the flag rather than the number.
  **A band of no days is a short plan and not a confident one** — both ends are rounded up to a
  whole day on their own — so it gets no verdict rather than the strictest one in the product:
  the noise measurement this detector rests on was taken on a twelve-item chain, and on a plan
  that small re-running alone moves the date by days.
- **A run that answered a different question ends the window; a start date that moved does
  not.** That exception is the one thing here most likely to be "corrected" by somebody reading
  the comparability rule on its own. Every weekly re-forecast starts from today, so counting a
  moved start as a new question would end every window at one run and the detector could never
  fire — and a finish date that held still while the start moved a week is a plan that delivered
  a week's work, which is the opposite of a slide.
- **The text equivalent is the feature and the drawing is the enhancement**, which is the
  order the burn-up was built in and the order the next chart gets built in. The table is what
  the tests assert; the SVG is inline, has no library, is `aria-hidden`, and carries nothing the
  table does not — a picture and its equivalent saying the same thing twice to a screen reader
  is worse than either. It is coloured from the same variables as everything else, so the
  interface rework restyles it rather than fighting it.
- **The cone is M9's bootstrap and never the engine's band.** A burn-up's future is how many
  *items* are done by each week; the engine forecasts effort and has no notion of one, so
  inventing a trajectory from its finish distribution would assume a shape nothing measured.
  `Throughput.project` records the running total per week as it goes — no extra draw, so every
  seeded answer given before it existed is unchanged — and the cone narrows because the backlog
  is a **ceiling**, not because uncertainty falls away.
- **The cone's percentiles read the other way round from the dates beside them.** Every figure
  is the percentile of the quantity it names, so `p90Date` is the *late* end of a finish and
  `p90` of a delivery count is the *good* end. The edge to plan against is `p10`.
- **`GET /api/projects/{id}/forecasts` answers an object, not an array** — `{runs, drift}` —
  because whether the date keeps moving out is a property of the sequence and there is nowhere
  on a run to hang it. The account of *why* it moved is a separate request, because it costs six
  whole simulations and most readers never ask.
- **Nothing here is stored.** No migration, no column, no cached decomposition: `forecast_runs`
  already holds every run's inputs, seed, calendar and version, and `work_item_progress` holds
  the rest. M6's decision 1 a fourth time, and the strongest instance of it — a decomposition
  computed at read time explains every pair of runs this product has ever held.
