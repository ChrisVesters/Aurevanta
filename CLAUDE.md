# Aurevanta

Web application for planning work using **certainty interval estimations** (P10/P50/P90)
instead of single-point estimates. The domain centres on three-point estimates,
aggregation across tasks, and confidence bands.

Licensed GPL-3.0.

`docs/product-concept.md` holds the domain concepts and planned features — read it before
designing schema or domain logic. It is design intent, not a description of existing code.
`docs/roadmap.md` sequences that intent into milestones and records the decisions each one
depends on; only M0 (tenancy and identity) is built. `docs/m1-plan.md` breaks the next
milestone into implementation steps.

## Layout

- `backend/` — Spring Boot 4.1 REST API (Java 25, Maven), PostgreSQL 18 + Flyway.
  Package root `eu.sonetas.aurevanta`.
- `frontend/` — React 19 + TypeScript SPA built with Vite 8.

**Backend packages are by feature, not by layer** — `tenant`, `user`, `membership`,
`security` each hold their own entity, repository, service and web types. `auth` is large
enough to be split a second time, by use case: `auth.registration`, `auth.signin`, and
`auth.problem` for the failure vocabulary every use case reports through. The controller
and the response shapes all its endpoints share stay in `auth` itself. A new auth use case
— verifying an address, resetting a password — is a new subpackage, not more files at the
root.

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
  and `/app` sits behind `RequireAuth`. The forms never navigate themselves: they update
  the session and the guards move the visitor on, which is also what returns someone to
  the page they originally asked for after signing in.
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
  precisely the one that needs it. Unauthenticated and it sends mail, so Step 7's rate limit
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
  through its own refusals, the question the blanket `202` exists to refuse.
- **One budget per recipient, shared across endpoints.** Per-endpoint budgets would hand an
  attacker twice the allowance for alternating between reset and resend, and the person
  being written to cannot tell which endpoint sent what.
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
  `OWNER` membership in one transaction, and returns an access token. There is no
  invitation flow yet — additional members are the next piece of work.
- Authentication is a **stateless HMAC-signed JWT** presented as `Authorization: Bearer`,
  in **two kinds**, told apart by the `token_type` claim (required, never defaulted):
  - **access** — pins `tenant_id` and `role`; grants `SCOPE_TENANT` plus `ROLE_<role>`,
    and is the only kind any tenant-scoped endpoint accepts.
  - **identity** — names the person and no organisation; grants only `SCOPE_IDENTITY`,
    which reaches `GET /api/memberships` and `POST /api/auth/tenants/{tenantId}/token`
    and nothing else. Issued when sign-in cannot pick an organisation: several to choose
    between, or none at all.

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
  `email_already_registered`); `AuthExceptionHandler` is ordered ahead of Boot's own
  problem-detail advice so per-field validation messages survive.
- **A new failure needs a `code`, not just a message.** The frontend translates the code
  and ignores the prose.
- **Per-field validation errors carry a code too**, never Bean Validation's English:
  `errors: { password: { code: "size", min: 12, max: 72 } }`. The code names the
  *constraint*, so one catalogue entry per rule serves every form and the bounds come from
  the server rather than being repeated in the frontend. Adding a constraint to a request
  object means adding it to `AuthExceptionHandler.CONSTRAINT_CODES` and to
  `errors.validation` in `en.ts`; an unmapped one degrades to `invalid` rather than leaking
  its name. Only the constraint's *numeric* attributes go out — a regular expression or a
  message template is implementation detail, not something to render.
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
