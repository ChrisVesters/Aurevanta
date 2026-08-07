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
- **Routing is React Router** (`react-router` v8, `BrowserRouter` in `main.tsx`).
  `/` is a public landing page, `/register` and `/login` sit behind `RedirectWhenSignedIn`,
  and `/app` sits behind `RequireAuth`. The forms never navigate themselves: they update
  the session and the guards move the visitor on, which is also what returns someone to
  the page they originally asked for after signing in.
- Actuator exposes only `health` and `info`.

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
- `compose.yaml` runs **Mailpit**: SMTP on 1025, and everything sent in dev is readable at
  <http://localhost:8025> and delivered to nobody.
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
