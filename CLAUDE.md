# Aurevanta

Web application for planning work using **certainty interval estimations** (P10/P50/P90)
instead of single-point estimates. The domain centres on three-point estimates,
aggregation across tasks, and confidence bands.

Licensed GPL-3.0.

`docs/product-concept.md` holds the domain concepts and planned features — read it before
designing schema or domain logic. It is design intent, not a description of existing code.
`docs/roadmap.md` sequences that intent into milestones and records the decisions each one
depends on; only M0 (tenancy and identity) is built.

## Layout

- `backend/` — Spring Boot 4.1 REST API (Java 25, Maven), PostgreSQL 18 + Flyway.
  Package root `eu.sonetas.aurevanta`.
- `frontend/` — React 19 + TypeScript SPA built with Vite 8.

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

## Multi-tenancy and authentication

- **Every tenant-owned table carries a `tenant_id`**, and isolation is enforced in the
  application: take the tenant from `CurrentUser.requiredTenantId()`, never from a request
  parameter or path variable. A query that filters only by an id the caller supplied is a
  cross-tenant leak.
- Registering (`POST /api/auth/register`) creates a `tenants` row and its first `users`
  row with role `OWNER`, in one transaction, and returns an access token. There is no
  invitation flow yet — additional members are the next piece of work.
- Authentication is a **stateless HMAC-signed JWT** presented as `Authorization: Bearer`.
  The token pins `tenant_id` at issue time. `spring-boot-starter-oauth2-resource-server`
  verifies it; `AuthenticatedUserJwtConverter` turns it into an `AuthenticatedUser`
  principal. There are no sessions and no CSRF tokens.
- `aurevanta.security.jwt.secret` is unset by default, so each start generates a random
  key. Set it (32+ chars, e.g. `AUREVANTA_SECURITY_JWT_SECRET`) anywhere tokens must
  survive a restart or be accepted by more than one instance.
- Failures are RFC 9457 problem documents carrying a stable `code` (for example
  `email_already_registered`); `AuthExceptionHandler` is ordered ahead of Boot's own
  problem-detail advice so per-field validation messages survive.
- **A new failure needs a `code`, not just a message.** The frontend translates the code
  and ignores the prose. *Known gap:* per-field validation errors carry only Bean
  Validation's English text, so the frontend matches on field name instead. Giving each
  field error its own code would remove that guesswork.
