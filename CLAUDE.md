# Aurevanta

Web application for planning work using **certainty interval estimations** (P10/P50/P90)
instead of single-point estimates. The domain centres on three-point estimates,
aggregation across tasks, and confidence bands.

Licensed GPL-3.0.

`docs/product-concept.md` holds the domain concepts and planned features — read it before
designing schema or domain logic. It is design intent, not a description of existing code.
`docs/roadmap.md` sequences that intent into milestones and records the decisions each one
depends on; M0 (tenancy and identity), M1 (making it a team product), M1a (organisation names
are not unique) and M2 (the estimation schema) are built. `docs/m1-plan.md`,
`docs/m1a-plan.md` and `docs/m2-plan.md` are the records of how each was done and where each
departed from its own brief — M1a most of all, since it corrected M0 by a different route than
the one it was written to take.

`docs/m3a-plan.md` and `docs/m3b-plan.md` are the two halves of M3, the simulation engine —
which `roadmap.md` calls the product and everything before it was built to feed. **M3a is
built and M3b is not**: a plan with ranges in it produces a band, and the two effects that
band is missing are named on every forecast it makes. Read both before touching
anything under `forecast`, because almost all of the risk is in decisions rather than in code:
what happens when two people estimate the same task differently, what an unestimated item does
to a graph it sits in the middle of, what "remaining work" means for a task already under way,
and where newly discovered work attaches. **The failure mode here is not a crash but a plausible
number**, which is why the engine is pure functions with an oracle behind every one of them.

**They are two builds and one design**, split where a whole-plan closed form stops being
available: M3a is proved against arithmetic that exists outside this codebase, and M3b — the
shared team factor and scope growth — is proved in pieces, the strongest of which is that
setting its two parameters to zero must reproduce an M3a run byte for byte.

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
- **`started_on` and `completed_on` are dates, and they are the only ones in this schema.**
  Everything else records a moment the *server* observed; these record a day a *person*
  reports. There is no time of day in "we finished it on the twelfth", and storing one
  invents the part nobody claimed — midnight UTC reads back as the eleventh for every reader
  west of the meridian. `<input type="date">` hands back exactly what the column holds, and
  `formatDay` builds a date from its parts rather than through `new Date(iso)`, which would
  reintroduce the same shift in the last step before a person sees it.
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
  each has its own `useFormFailure` over the field names it renders. `ProjectForm` and
  `WorkItemForm` are two components rather than one parameterised by a field name, because
  the field names are the server's and are what a per-field complaint is keyed by. What they
  share is `optionalField` — "an empty box means nothing, not `''`" — stated once because the
  version of that bug which gets written is the second copy.
- **`numberField` exists because `Number('')` is zero.** The obvious version sends an
  untouched box as an estimate of no hours, and the visitor is told their estimate must be
  more than zero about a field they never filled in.
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
- **The estimate form is three boxes and is deliberately not good.** `product-concept.md` is
  explicit that three boxes labelled P10/P50/P90 produce 3/5/8 without thinking, which is
  worse than no tool because the garbage now carries a probability. Making it good is M5, and
  it is a question-design problem rather than a styling one — so styling it is the one change
  that cannot help.

## Forecasting: the engine, and the four decisions inside it

- **`forecast.model` is separated by *purity*, not by feature, and it is the only package in
  this application that is.** `Normal`, `LogNormalFit`, `ItemModel`, `Precedence`, `Schedule`,
  `Engine`, `Forecast` and `Histogram` hold no Spring, no JPA and no I/O: they are functions
  over primitives. `forecast` beside it is an ordinary feature package — entity, repository,
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
    nobody reads.
- **A run is written once and never updated, like an estimate.** `forecast_runs` stores its
  resolved inputs, its seed, its sample count, its capacity, its priority rule and
  `Engine.VERSION`, so any run can be replayed exactly — which is what M6 gets its per-item
  contribution from instead of storing a duration vector per item per run, and what M10 reads
  to see a date sliding. `ForecastApiTests` replays a stored snapshot and asserts it
  reproduces its stored percentiles: **that is the test that fails the day somebody changes
  the model without bumping the version.**
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
  Five codes today, two of which (`no_team_factor`, `no_scope_uncertainty`) M3b exists to
  delete. The panel renders a code it has never heard of rather than dropping it: the server
  is what versions ahead here, and silently showing nothing is that rule failing through the
  back door. **A band without its caveats is this product's own failure mode with a chart on
  it**, which is why they are not behind a disclosure.
- **Any member may forecast**, like everything else in the domain. A plan with nothing
  estimated is refused with `nothing_to_forecast` (`422`) and **no row is written** — a
  refusal that had stored a run would leave the history holding a forecast nobody received.
- **Hours of effort, everywhere, and no calendar dates anywhere.** A date needs a working-day
  assumption; M4 is where that gets made, where somebody can see it.
