# M1 — Make it a team product: implementation plan

> **Scope.** `roadmap.md` M1: invitations, member management, password reset, email
> verification, per-field error codes — plus the identity/membership reshape that decision 3
> pulled forward. Explicitly excluded: SSO, granular permissions, organisation settings
> beyond a name.
>
> **How to read this.** Decisions first, because three of them change what the steps are.
> Then thirteen steps in five phases. Each step is a reviewable commit that leaves the build
> green: `./mvnw test` (format gate, 100% branch coverage) and, where the frontend is
> touched, `npm run lint && npm run build && npm run test`.

---

## At a glance

| Phase | Step | | Depends on |
|---|---|---|---|
| **A — Reshape** | 1 | Split identity from membership ✅ *done* | — |
| **B — Foundations** | 2 | Email infrastructure ✅ *done* | — |
| | 3 | Single-use token infrastructure ✅ *done* | 1 |
| | 4 | Per-field error codes ✅ *done* | — |
| **C — Account lifecycle** | 5 | Email verification, and the sign-in gate ✅ *done* | 1, 2, 3 |
| | 6 | Password reset ✅ *done* | 2, 3 |
| | 7 | Rate limiting on mail-sending endpoints ✅ *done* | 5, 6 |
| | 8 | Frontend: verification, reset, gate handling ✅ *done* | 5, 6 |
| **D — Team** | 9 | Invitations: schema and issuing ✅ *done* | 1, 2, 3 |
| | 10 | Invitations: preview, accept, revoke ✅ *done* | 9 |
| | 11 | Member management ✅ *done* | 1 |
| | 12 | Frontend: members, acceptance, organisation switcher ✅ *done* | 10, 11 |
| **E — Close out** | 13 | Documentation and debt retirement | all |

Phases A and B are invisible to users. C and D are each a shippable release.

---

## Decisions

Decisions 1, 2 and 3 are settled and worked through below; 4–6 are recommendations I will
implement unless told otherwise.

| # | Question | Decision |
|---|---|---|
| 1 | How is mail sent? | **SMTP transport, provider-agnostic.** Mailpit in dev, a transactional provider in production, differing only by configuration. |
| 2 | Does an unverified address block use? | **Yes — hard gate.** Sign-in is refused until the address is verified. |
| 3 | One person, several organisations? | **Yes — identity split from membership,** in Step 1, before invitations exist. One account, one password, one verified address; a role *per organisation*. |
| 4 | Who may invite? | **OWNER only.** Granular permissions are out of scope, and one rule is easy to explain and to change. |
| 5 | Token lifetimes | Invitation **7 days**, password reset **1 hour**, email verification **3 days**. |
| 6 | The error-response shape changes | Accepted breaking change, coordinated in Step 4 across backend, frontend and catalogue. Nothing is deployed, so there is no migration burden. |

### Decision 1 — SMTP now, provider API only on a trigger

"SMTP or Mailgun" is not the fork it appears to be: Mailgun, Postmark, SES and Resend all
publish an SMTP endpoint alongside their HTTP API. The choice is *transport*, with the same
provider either way, so production adoption is a configuration change:

```properties
spring.mail.host=smtp.eu.mailgun.org
spring.mail.username=postmaster@mg.example.eu
spring.mail.password=${MAILGUN_SMTP_PASSWORD}
spring.mail.properties.mail.smtp.starttls.enable=true
```

Move to the HTTP API when one of these appears, not before:

- **Outbound SMTP ports are blocked.** Some PaaS environments restrict 587/465 to limit
  spam. The most common real reason teams switch, and an environment fact rather than a
  design choice.
- **Provider features are needed** — tags, per-message metadata, a returned message ID to
  correlate with webhooks. Partly reachable over SMTP via `X-Mailgun-*` headers, but
  awkwardly.
- **Volume grows.** JavaMail opens a connection per send by default: irrelevant at
  invitation-and-reset volumes, expensive at thousands.

A `MailgunEmailSender` then implements the same `EmailSender` port and no caller changes.
That is the entire reason the port exists.

**Selection criteria that matter more than transport.** Deliverability is a function of
domain setup — SPF, DKIM, DMARC, sender reputation — and is identical either way. Given
`sonetas.eu`, **EU data residency is likely a requirement rather than a preference**, since
recipient addresses are personal data. Mailgun has an EU region; check the same for anything
compared against it.

### Decision 2 — A hard gate, and what it drags with it

An unverified account cannot sign in. This stops typo'd and throwaway addresses becoming
working accounts, at the cost of making mail delivery load-bearing. Four consequences, none
optional:

**Registration stops signing you in.** `POST /api/auth/register` currently returns an access
token — incoherent under a hard gate, since it would hand a session to an account that
cannot sign in. It returns `201` with the account and **no token**, and the frontend shows a
"check your email" screen. This edits M0 code rather than extending it: `AuthController`,
the response type shared with login, `AuthProvider.register`, and the registration
assertions in `AuthApiTests`.

**Resend must be unauthenticated,** since the person needing it cannot sign in. An
unauthenticated endpoint that sends mail is an email-bombing and enumeration vector, so it
takes the same treatment as password reset: **always `202`**, and rate limited. Step 7 is a
requirement of this decision, not hardening.

**Reveal the unverified state only after correct credentials.** A wrong password returns the
usual undifferentiated `invalid_credentials`. The *right* password on an unverified account
returns a distinct `email_not_verified` — safe, because the caller has already proven they
hold the password, so nothing leaks that they did not know. Any other arrangement either
strands a legitimate user on a confusing error or lets an attacker probe which addresses
exist.

**Invited users skip the gate.** Accepting an invitation stamps `email_verified_at`, since
following the emailed link already proves control of the address. Password reset does the
same, which makes "forgot password" the recovery route for a lost verification email.

### Decision 3 — Identity is global, membership is per tenant

M0 put `tenant_id` and `role` on `users`, so an account *was* a membership. That leaves one
address unable to join a second organisation — a wall for anyone consulting for two clients.
Step 1 separates the two concepts:

```
users        identity: email, password_hash, display_name, email_verified_at
memberships  user_id + tenant_id + role        unique (user_id, tenant_id)
tenants      unchanged
```

**One identity, one password, one verified address.** Credentials, verification and reset
live on `users`, so a person has a single sign-in however many organisations they belong to.
**Role lives on the membership**, so the same person can own one organisation and be a
member of another.

Doing this before invitations is the point: Step 10's accept flow becomes "add a membership"
for a known identity, instead of an error apologising for the limitation.

#### What it does to authentication

`tenant_id` is pinned into the access token and every tenant-scoped query reads it from
`CurrentUser`. That design survives — it needs a way to choose *which* tenant, and a state
for a caller who has not chosen one. Two token types:

| Token | Claims | Permits |
|---|---|---|
| **Identity** | `sub`, no tenant | `/auth/me`, list memberships, choose a tenant, create an organisation |
| **Access** | `sub`, `tenant_id`, `role` | everything tenant-scoped, exactly as today |

Sign-in returns an access token directly when the identity has exactly one membership —
which is every existing user, so the common path is unchanged. With several, it returns the
membership list and an identity token to exchange for the chosen one. With **none** — a real
state, reached by being removed from your only organisation — it returns an identity token
and the client offers to create an organisation or wait for an invitation.

`AuthenticatedUserJwtConverter` already rejects a token with no `tenant_id`; it gains a
branch on token type rather than a rewrite, and `CurrentUser.requiredTenantId()` is
untouched. That is the part worth protecting: **tenant isolation logic does not change.**

#### Rights, later

Roles differing per organisation is the schema change; *rights* differing per organisation
is a policy question M1 leaves alone. The membership row is the natural home for
finer-grained permissions, so granting them later is a new column or a side table — not
another reshape.

### Out of scope, but decided about: bounces and complaints

Whatever the transport, the application has no way to learn a message was never delivered.
Invite a colleague at a mistyped address and the inviter sees a pending invitation for ever.

Closing that needs a provider webhook endpoint and somewhere to record delivery state, which
argues for an **outbox table** rather than fire-and-forget sending, since a webhook needs a
row to attach its verdict to. Out of scope for M1, the natural step after it, and worth
knowing before the sending code is written.

---

# Phase A — Reshape the model

## Step 1 — Split identity from membership ✅ *done*

**Goal.** One address can hold memberships in several organisations, with a different role
in each, and a single password.

The largest step, the only one that rewrites M0 rather than extending it, and first because
every later step touches `users`.

### Schema — `V3__identity_and_membership.sql`

A forward migration, not an edit to `V2`. Nothing is deployed, but rewriting an applied
migration breaks Flyway's checksum for anyone who ran it, and it is not a habit worth
starting.

- Create `memberships` (id, user_id, tenant_id, role, created_at, last_accessed_at), unique
  on (user_id, tenant_id), indexed on both foreign keys.
- Copy every existing `users` row into a membership, preserving `tenant_id` and `role`.
- Drop `users.tenant_id` and `users.role`.
- `uq_users_email` on `lower(email)` **stays** — email now identifies a person globally,
  which is exactly what lets one identity span organisations.

### Domain

- `User` loses `tenant` and `role`; a new `Membership` entity owns both.
- `MembershipRepository`: find by user, find by (user, tenant), count owners in a tenant —
  the last enforces Step 11's invariant.
- `RegistrationService` writes three rows: tenant, user, owner membership.
- `AuthenticationService` resolves memberships after checking the password.

### Tokens

- `TokenClaims` gains a token-type claim; `AccessTokenService` issues both kinds.
- `AuthenticatedUserJwtConverter` branches on type: identity tokens yield a principal with
  no tenant and authority `SCOPE_IDENTITY`; access tokens are unchanged.
- `SecurityConfiguration` permits identity tokens only on membership and tenant-selection
  endpoints.
- `POST /api/auth/tenants/{tenantId}/token` exchanges an identity token — or a valid access
  token — for one scoped to another organisation the caller belongs to.
- `GET /api/memberships` lists the caller's organisations and role in each.
- `last_accessed_at` records the most recent choice, so sign-in can default to it.

### Frontend

- `AuthProvider` handles all three sign-in outcomes: one membership, several, none. All
  three are testable now against a mocked API even though only the first is reachable in the
  running app until Steps 10 and 11 exist.
- Empty state for zero memberships. The organisation *switcher* waits for Step 12, when a
  second membership first becomes reachable.

**Tests.** Migration preserves every existing user's tenant and role. Sign-in with one
membership returns an access token; with several returns the list; with none returns an
identity token and no access. Exchange succeeds for a membership the caller holds and is
**refused for one they do not** — with two tenants in the fixture. An identity token is
rejected on every tenant-scoped endpoint. One user holds OWNER in one organisation and
MEMBER in another simultaneously.

**Done when** one address holds two memberships with different roles, and no tenant-scoped
query reads a tenant from anywhere but `CurrentUser`.

### As built — where it differs from the above

Every bullet and every named test landed. Four things a later step should know:

- **An identity token cannot reach `/auth/me`**, contrary to the token table in decision 3.
  `AccountResponse` names an organisation and a role, so there is no coherent tenant-less
  answer for it to give. Session restore for an identity token uses `GET /api/memberships`
  instead, and the frontend stores the *kind* of token alongside it so it knows which to
  call. Reaching `/auth/me` without a tenant would mean reshaping its response, not
  relaxing a guard.
- **There is no "create an organisation" endpoint**, also promised by that table. Nothing in
  the product can produce a zero-membership identity until Step 11 lets an owner remove
  someone, so this costs nothing yet — but **Step 11 must not ship without it**, or removing
  someone's last membership strands them on an empty state with no way out. The empty state
  currently says to wait for an invitation.
- **`memberships` has one index, not two.** `uq_memberships_user_tenant` is a btree on
  `(user_id, tenant_id)` whose leading column already serves every lookup by user, so a
  separate `ix_memberships_user` would only cost writes.
- **A chooser screen exists**, slightly ahead of the plan, which names only the empty state.
  Without it `status === 'choosing'` renders nothing. It is unreachable in the running app
  until Step 10, but tested against a mocked API. It is *not* the Step 12 switcher: it
  stands in for the route inside `RequireAuth` rather than sitting in the dashboard header.

One point of wording: the "Done when" clause says `CurrentUser`, and neither caller uses it —
`AuthController` takes the principal via `@AuthenticationPrincipal`, as M0 already did.
Same verified source, different accessor; `CurrentUser` is for code below the web layer and
gets its first production caller in M2.

---

# Phase B — Foundations

Nothing user-visible. Steps 2 and 4 are independent of everything; Step 3 needs Step 1.

## Step 2 — Email infrastructure ✅ *done*

**Goal.** Anything can send mail; nothing does yet.

- Add `spring-boot-starter-mail`; add **Mailpit** to `compose.yaml` (SMTP 1025, UI 8025) so
  dev mail is captured, never delivered.
- Outbound port `EmailSender` with `send(EmailMessage)`, plus an `EmailMessage` record (to,
  subject, body). Application code depends on the port only.
- `SmtpEmailSender` over `JavaMailSender`; `RecordingEmailSender` as the test double.
- `aurevanta.mail.*`: `from`, `base-url` (links in mail — the backend cannot know its own
  public URL).
- Plain-text templates via a small `EmailTemplates` component. No HTML, no template engine,
  until there is a reason.
- **Send off the request thread.** A slow or unreachable SMTP server must not add seconds to
  registration, so the port is invoked asynchronously and delivery failure is logged, not
  propagated. This is what makes "registration does not fail if mail fails" true in practice
  rather than only in intent.

**Tests.** `RecordingEmailSender` everywhere else; one GreenMail test proving the SMTP
adapter transmits recipient, subject and body. The executor runs inline under test, so
nothing waits on a timer.

**Done when** a test can assert an email was sent, `docker compose up` shows mail in
Mailpit, and pointing `spring.mail.*` at a real provider needs no code change.

### As built — where it differs from the above

- **`EmailTemplates` is deferred to Step 5**, which writes the first template. A component
  holding no templates is code no test can exercise, and this project's bar is that a class
  is not done until its behaviour is, so it is better created with its first real body than
  created empty now. What Step 5 needs from Step 2 already exists: `MailProperties.link()`
  builds the absolute links a template embeds, and is tested against both spellings of the
  origin and the path.
- **The executor is a bounded pool, so submission can be refused.** A mail server that
  stops responding fills the queue rather than growing it without limit, and a refused
  submission is a lost message logged exactly like a failed delivery. Both paths are tested.
- The `mail` package exports only `EmailSender`, `EmailMessage` and `MailProperties`.
  `SmtpEmailSender`, `AsyncEmailSender` and `MailConfiguration` are package-private, so no
  caller can reach past the port to the transport or to the threading.

## Step 3 — Single-use token infrastructure ✅ *done*

**Goal.** One mechanism for verification, reset and invitation tokens.

- Migration `V4__user_tokens.sql`: `user_tokens` (id, user_id, purpose, token_hash,
  expires_at, consumed_at, created_at). Unique index on `token_hash`, index on `user_id`.
- **Store only a hash.** These tokens grant account access; a database leak must not hand
  over password resets. SHA-256 rather than the `PasswordEncoder`, because bcrypt is
  deliberately slow and lookup is by hash.
- `SingleUseTokenService`: `issue(user, purpose, ttl)` returning the raw token once;
  `consume(rawToken, purpose)` returning the user and atomically marking it consumed.
- Raw tokens: 32 bytes from `SecureRandom`, base64url.

**Tests.** Round trip; expired, already-consumed, wrong-purpose and unknown tokens each
rejected; the raw token is never persisted.

**Done when** a token can be issued and redeemed exactly once, and nothing in the database
can be replayed.

### As built — where it differs from the above

- **Invitations will not use `user_tokens`.** The goal line says "verification, reset and
  invitation", but Step 9 gives `invitations` its own `token_hash` column — and it has to,
  because an invitee may have no account yet while `user_tokens.user_id` is not null. What
  the two share is the *mechanism*, not the table: 32 bytes from `SecureRandom`, base64url,
  stored as hex SHA-256, redeemed by a conditional update. `TokenPurpose` therefore has two
  constants, not three. Step 9 should lift the generation and hashing rather than
  reimplement them.
- **Redemption is one conditional `UPDATE`**, `where consumed_at is null and expires_at >
  now`, and the affected row count is what decides success. Reading the row and then
  writing it would let two simultaneous clicks both succeed; there is a test that releases
  four redemptions together and asserts exactly one wins.
- **`consume` returns `Optional<User>` and does not distinguish why it refused.** If Step 5
  or 6 wants an "this link has expired, request another" message distinct from a generic
  failure, that is a deliberate widening of this signature — the current one is silent on
  purpose so it cannot be used to probe which tokens exist.
- **The foreign key cascades on delete.** A token is meaningless without the person it
  authenticates, and without the cascade every future test that deletes a user would trip a
  constraint. There is a test for the cascade itself, since a missing one would only surface
  that way.

## Step 4 — Per-field error codes ✅ *done*

**Goal.** Retire the field-name guessing in M0's debt table, before more forms exist.

- `AuthExceptionHandler.handleInvalidRequest` currently emits Bean Validation's English:
  `errors: { password: "size must be between 12 and 72" }`. Emit the constraint instead:
  `errors: { password: { code: "size", min: 12, max: 72 } }`, from `FieldError.getCode()`
  (`NotBlank`, `Size`, `Email`) and its arguments.
- Map constraint names to lower-case codes; unknown constraints fall back to `invalid`.
- Frontend `ApiError.fieldErrors` becomes `Record<string, FieldProblem>`;
  `describeFieldErrors` translates `field + code` instead of matching on field name.
- Catalogue gains `errors.validation.<code>` with interpolation
  (`Use at least {{min}} characters.`).

**Tests.** Backend: each constraint type produces its code and arguments. Frontend: every
code renders, and an unknown code falls back rather than showing nothing.

**Done when** no frontend code matches on a field *name* to choose a message.

### As built — where it differs from the above

- **Codes are snake_case**, not lower-cased constraint names: `not_blank` rather than
  `notblank`, so they read like every other code this API publishes. `Size` → `size`,
  `Email` → `email`, anything unmapped → `invalid`.
- **The catalogue is keyed by constraint alone**, with no field dimension. The plan's
  "translates `field + code`" and its "no code matches on a field *name*" pull in opposite
  directions; a per-field key would put field names back in the catalogue. The cost is that
  `not_blank` reads "This cannot be empty." for every field rather than "Enter the name of
  your organisation." — acceptable, because each input carries a visible label, and the
  gain is that a new form needs no catalogue entries at all. `RegisterForm` still indexes
  `fieldErrors.organisationName`, but that *places* an already-chosen message next to its
  input; it does not choose one.
- **A field that breaks several rules reports the most telling one, by an explicit
  precedence.** An empty password fails `@NotBlank` and `@Size(min = 12)` together, and
  Hibernate Validator returns violations in a set whose iteration order varies *within a
  single run* — so the original "keep the first one" rule made the message flicker between
  "this cannot be empty" and "use between 12 and 72 characters". Presence now outranks
  shape. This was a latent M0 bug that survived into step 4; register's `password` is the
  only field affected, since every other constraint pair cannot fail on the same input.
- **A constraint that only bounds length above reports `max_size`, not `size`.**
  `@Size(max = 320)` reports `min: 0`, and "use between 0 and 320 characters" is not a
  sentence worth showing; `max_size` lets the client say "no more than" instead of
  inventing a range.
- **Attributes are read from the constraint by name, not from `FieldError.getArguments()`
  by position.** That array is ordered by attribute name, so `@Size` yields `[field, max,
  min]` and a positional read would silently publish min as max.
- **Only numeric attributes are published.** Bounds are all a message interpolates today; a
  constraint's regular expression, message template and groups stay on the server.
- **One deliberate cast in `problems.ts`.** i18next reads `{{min}}`/`{{max}}` out of the
  catalogue and requires them as *named* arguments, which no runtime-supplied dictionary
  can satisfy. The generic dispatch is kept and the compiler given up for that one call;
  the `renders every constraint code` test replaces it, failing on any placeholder left
  unfilled. Choosing per-code messages in a `switch` would have type-checked, but would
  make `describeFieldErrors` a file to edit for every new constraint.

---

# Phase C — Account lifecycle

Steps 5–8 ship together. Shipping 5 without 6 and 8 strands anyone whose mail is lost;
shipping any of them without 7 leaves an unauthenticated sending endpoint unthrottled.

## Step 5 — Email verification, and the sign-in gate ✅ *done*

**Goal.** Verified addresses only. Where decision 2 lands, so it changes M0 behaviour.

- Migration `V5__users_email_verified.sql`: `users.email_verified_at timestamptz null`.
- Registration issues a verification token and sends the mail, and does **not** fail if
  delivery fails — log and carry on.
- **Registration no longer returns an access token**: `201` with the account only. The
  response type currently shared with login splits in two.
- **Sign-in refuses an unverified account** with `email_not_verified`, but only once the
  password has been checked; a wrong password still returns `invalid_credentials`.
- `POST /api/auth/verify-email` → stamps `email_verified_at`.
- `POST /api/auth/verify-email/resend` `{email}` — **unauthenticated**, always `202`, sends
  nothing for an unknown or already-verified address, rate limited in Step 7.
- `/api/auth/me` gains `emailVerified`. Always true in practice once the gate exists, but it
  keeps the client honest if the gate is ever relaxed.

**Tests.** Verification stamps the user; a second use fails; expired fails; a mail failure
does not break registration. For the gate: registration returns no token; sign-in before
verification is refused with `email_not_verified`; a *wrong* password on an unverified
account still returns `invalid_credentials` and never reveals the account exists; sign-in
succeeds after verification. Resend returns `202` for unknown and already-verified addresses
and sends nothing.

**Done when** an unverified account cannot obtain an access token by any route.

### As built — where it differs from the above

- **`EmailTemplates` arrived here**, as Step 2 recorded it would: a component holding no
  templates was not worth creating empty.
- **The exception advice is now scoped by package**, not by class. Step 4 flagged that a
  second controller in an `auth` subpackage would silently lose it; `VerificationController`
  is that controller, so `@RestControllerAdvice(basePackages = "eu.sonetas.aurevanta.auth")`
  replaces the single-class selector.
- **Existing accounts are grandfathered as confirmed** by the migration. Nothing is
  deployed, so this touches developer databases only — but leaving them unconfirmed would
  strand them with no way back in until Step 8 builds the screen that asks for a new link.
- **Part of Step 8 came forward, because Step 5 makes it non-optional.** Registration no
  longer returns a token, so `AuthProvider.register` had to stop establishing a session and
  `RegisterForm` had to end somewhere — it now shows a "confirm your address" screen. The
  `/verify-email` route came with it: the step emails a link, so the page that link lands on
  is not separable from the step that sends it. Deferring it shipped a confirmation email
  pointing at a 404 — the whole journey the gate depends on, broken. `/forgot-password` and
  `/reset-password` remain Step 8's, alongside Step 6.
- **`/verify-email` also serves without a token**, showing a form that asks for a new link.
  A link that has expired or been used is precisely when somebody needs another one, so it
  must not be a dead end; the sign-in form links to the same page for anyone refused by the
  gate.

## Step 6 — Password reset ✅ *done*

**Goal.** A way back in, which the hard gate makes essential rather than routine.

- `POST /api/auth/password-reset` `{email}` → **always 202**, whether or not the address
  exists. Same reasoning as the login decoy hash: it must not reveal which addresses hold
  accounts.
- `POST /api/auth/password-reset/confirm` `{token, password}` → re-hashes and consumes.
- Reset also stamps `email_verified_at` if unset — following the link proves control of the
  address. Under the hard gate this is deliberately the **recovery route for a lost
  verification email**.
- Consume any outstanding reset tokens for that user on success.

**Tests.** Happy path; unknown email returns 202 and sends nothing; expired and reused
tokens fail; the old password stops working and the new one works.

**Done when** someone who never received a verification email can still reach their account.

### As built — where it differs from the above

Every bullet and every named test landed, in a new `auth.reset` subpackage. Five things a
later step should know:

- **"Consume any outstanding reset tokens" became `SingleUseTokenService.revokeAll`**, a
  second conditional bulk update alongside `consume`. It is scoped by *purpose* as well as
  by user: cancelling a confirmation link somebody is still waiting to use, because they
  happened to reset their password, would be a new way to strand an account rather than a
  tidy-up. Step 9 will want the same method when an invitation is superseded.
- **Password bounds moved into `user.PasswordRules`.** Reset and registration both set a
  credential, and the plan's `{token, password}` said nothing about what a password may be
  — repeating `@Size(min = 12, max = 72)` would have let the two drift, and the weaker
  endpoint would then decide the rule for everybody. `RegistrationRequest` was edited to
  read the same constants; the values are unchanged, so the published `min`/`max` attributes
  are too.
- **Nothing ends a session that is already running.** A JWT is stateless and signed, so a
  password change cannot withdraw one before it expires: somebody who resets because they
  fear their account is compromised has not evicted whoever was in it. Closing that needs a
  token version on `users` or a deny list, which is a decision for M2, not a bug in this
  step.
- **`EmailTemplates` gained a pluraliser**, because a reset link lasts a single hour and
  "stops working after 1 hours" reads like a phishing message — which is exactly the wrong
  impression for the one link in the system that hands over an account. `verifyEmail` uses
  it too, so the two messages cannot phrase the same thing differently. The templates now
  have direct tests (`EmailTemplatesTests`) rather than only being asserted through the
  endpoints that send them.
- **The frontend came with it after all**, so the rest of Step 8's password-reset work is
  done here rather than deferred. The backend-only reading was defensible — nothing in the
  running app could *send* a reset mail, so no journey was left dangling — but it shipped a
  feature no user could reach, which is not worth a commit on its own. `/forgot-password`,
  `/reset-password` and the "Forgotten your password?" link on the sign-in form landed
  together. See Step 8 for what that leaves.

## Step 7 — Rate limiting on mail-sending endpoints ✅ *done*

**Goal.** Close the vector decision 2 opened. Placed here, immediately after the endpoints
exist, so an unthrottled unauthenticated sender is never left in `main`.

- Per-address and per-IP throttle on password reset, verification resend and (from Step 10)
  invitation resend.
- In-memory bucket per instance is enough for now; `CLAUDE.md` records that it becomes wrong
  the moment there are two instances.
- `429` with `Retry-After` and its own error code.

**Tests.** The nth request in a window is refused; the window expires; unrelated addresses
are unaffected.

**Done when** no unauthenticated endpoint can be made to send unbounded mail.

### As built — where it differs from the above

- **Registration is limited too**, which the bullet list did not name. It is a weaker vector
  than the other two — an address can only be registered once, so nobody can be buried — but
  it will send one unsolicited message to any address somebody types, and the step's own
  "done when" says *no* unauthenticated endpoint. Only the per-source limit bites there; a
  per-address one would be meaningless for something that can only fire once per address.
- **One budget per recipient, shared by every endpoint**, rather than a bucket per endpoint.
  Alternating between reset and resend would otherwise buy twice the allowance, and the
  inbox on the receiving end cannot tell the two apart.
- **The source is checked before the address.** If it were the other way round, one machine
  flooding would burn through the budget of every address it named, and the refusals would
  land on the victims instead of the sender.
- **A sliding window, not a fixed one.** A window that resets on a boundary lets twice the
  limit through either side of it, which for a limit whose whole purpose is to bound what
  reaches an inbox is the case worth getting right.
- **Tests had to gain a `clear()` call.** The limiter is one bean in a context shared across
  a whole test class, so without it the fourth case in a class spends what the fifth needs.
  Four existing classes now reset it in `@BeforeEach`; the alternative was configuring the
  limits away in tests, which would have meant nothing outside this step ever exercised them.

**Sign-in was brought under the same roof afterwards**, though the step names only
mail-sending endpoints. Leaving `/api/auth/login` unthrottled meant the sole cost of
guessing a password was bcrypt's time — a price per attempt, not a bound on how many — and
having just built the machinery it would have been odd to leave the one unauthenticated
endpoint that guards credentials outside it. It counts failures rather than attempts, and
the per-account limit sits five times the per-source one so that a single machine cannot
fill it: a per-account limit is also a way to lock somebody out of their own account, and
that ratio is what keeps it from becoming one.

**Two things this does not do**, both worth knowing before deployment:

- **Counts are per process.** Two instances mean twice the limit. Recorded in `CLAUDE.md`
  as the plan asked.
- **The source is `getRemoteAddr()`.** Behind a proxy or load balancer that is one address
  for everybody, so the per-source limit would throttle all users together while giving an
  attacker no trouble at all. Whatever terminates TLS in front of this has to be trusted for
  a forwarded header before the per-source limit means anything in production.

## Step 8 — Frontend: verification, reset and the gate ✅ *done*

- Routes `/verify-email`, `/forgot-password`, `/reset-password`, all public.
- **Registration ends on a "check your email" screen**, not the dashboard — the biggest
  visible change from the hard gate. `AuthProvider.register` stops establishing a session,
  so `RedirectWhenSignedIn` no longer carries the user onward after sign-up.
- **Sign-in handles `email_not_verified`** with its own message and an inline resend, rather
  than the generic failure banner. This is the screen that rescues anyone whose verification
  mail went astray, so it matters more than it looks.
- Reuse `AuthLayout`, `Field` and the existing form-error patterns. Catalogue additions; no
  literal strings.

**Tests.** Each route renders; success and failure paths; registration lands on
check-your-email and does not authenticate; sign-in surfaces `email_not_verified` with a
working resend. Coverage stays at 100%.

**Done when** the whole verification and recovery journey can be completed in the browser.

### As built — spread across three steps

Each piece came forward with the step that made it reachable, on the same reasoning every
time: an endpoint that emails a link is not finished until the link lands somewhere, and a
feature no user can reach is not worth a commit of its own.

| | Where it landed |
|---|---|
| `/verify-email`, registration ending on "check your email" | Step 5 |
| `email_not_verified` gets its own message, not the generic banner | Step 5 |
| `/forgot-password`, `/reset-password`, the sign-in link to them | Step 6 |
| The inline resend on the sign-in form | Step 8 |

Three things worth knowing about the inline resend, which is all Step 8 itself had left:

- **`useFormFailure` now hands back the failure's `code`.** A form still must not choose
  its own wording — that rule is what keeps a field's complaint off the banner — but
  offering an *action* attached to one particular refusal means knowing which refusal
  arrived. Branching on the code the hook already parsed is a smaller change than letting
  forms near `describeFailure` again.
- **The resend has failure state of its own**, a second `useFormFailure`. Sharing one would
  mean a refused resend overwriting the very message that explains why a resend is on offer
  — and, because the offer is conditional on that message, removing the button along with
  it. Step 7 made this reachable rather than theoretical: the resend is rate limited, so
  `too_many_requests` is a refusal a real visitor will meet.
- **The standing "ask for a new one" link hides while the inline resend is showing.** Two
  ways to ask for the same thing, side by side, is a choice nobody needs. It stays for
  anyone who has not tried to sign in yet.

---

# Phase D — Team

## Step 9 — Invitations: schema and issuing ✅ *done*

- Migration `V6__invitations.sql`: `invitations` (id, tenant_id, email, role, invited_by,
  token_hash, status, expires_at, created_at, accepted_at). Partial unique index on
  (tenant_id, lower(email)) `where status = 'PENDING'` — one live invitation per address per
  organisation.
- `POST /api/invitations` `{email, role}` — OWNER only, sender must be verified.
- Rejects: the address is already a member of *this* organisation; a live invitation already
  exists (offer resend instead).
- Sends the invitation mail with `base-url` and the raw token.

**Tests.** Owner can invite; member cannot (403); unverified owner cannot; duplicate live
invitation rejected; existing member rejected; mail sent carrying the raw token.

**Before writing the endpoint:** invitation sending and resend go behind `MailRateLimiter`
like every other mail-sending endpoint — and `/api/invitations` sits outside
`eu.sonetas.aurevanta.auth`, which is where `AuthExceptionHandler` is scoped. A `429` raised
there would lose its `code` and its `Retry-After` and arrive as Boot's default error, so the
advice has to be widened in the same commit. Step 5 hit this one level down and fixed it by
replacing `assignableTypes` with `basePackages`; this is that lesson again, and it is silent
when got wrong.

**Done when** an owner can put a pending invitation in someone's inbox.

### As built — where it differs from the above

Every bullet and every named test landed, in a new `invitation` package. Seven things a
later step should know:

- **The advice was not widened — it stopped naming a scope at all.** The warning above was
  right about the consequence and too modest about the cause. Each previous widening
  replaced one *string* with a longer one, and a package name written as a string does not
  fail to compile when it goes stale: the "Fix package name" commit renamed the root and
  left `basePackages = "eu.sonetas.aurevanta.auth"` behind, after which the advice matched
  no controller and **every problem document in the application silently became Boot's
  default**. Seventeen tests were failing on `main` before this step began. A bare
  `@RestControllerAdvice` covers every controller and has nothing left to keep in step.
- **`auth.problem` moved to a root `problem` package**, `AuthProblemException` becoming
  `ApiProblemException` and `AuthExceptionHandler` becoming `ApiExceptionHandler`. An
  application-wide advice living inside `auth` is a lie about what it does, and the
  dependency was already inverted before this step — `ratelimit` imported its refusal from
  `auth.problem`, and `invitation` would have too. Every `code` this API publishes is now
  readable in one directory. Pure import churn across nineteen files; no behaviour beyond
  the fix above.
- **Ownership is enforced in the service, not the filter chain.** `SecurityConfiguration`
  already requires an access token for anything under `/api/invitations`, but the role in
  that token was pinned twelve hours ago. The service re-reads the membership, so somebody
  demoted or removed since cannot keep inviting for the rest of the day — and the refusal
  gets a `code`, which `hasRole` in the filter chain would not have given it. Two
  enforcement points that could drift would be worse than one that is right, so there is
  only the one.
- **An expired invitation is renewed in place rather than blocking the next one.** The
  partial unique index counts a `PENDING` row as occupying the slot whether or not it has
  timed out, so "reject a live invitation" and "reject any invitation" are different rules
  and only the first is wanted — the second would make an address permanently un-invitable
  once its first invitation lapsed. `Invitation.renew` restates the role and the inviter,
  since whoever is asking now may not be who asked last.
- **Token generation and hashing moved to `token.LinkTokens`**, as Step 3 said Step 9
  should. `SingleUseTokenService` now calls it too, so there is one definition of what a
  link's secret is rather than a second one that happens to agree today.
- **`@NotNull` needed a code**, since `role` is the first field that can be absent without
  being blank. `not_null` joins `CONSTRAINT_CODES`, the precedence list beside `not_blank`,
  and `errors.validation` in the catalogue — the only frontend change in this step. The
  invite form itself is Step 12's.
- **`EmailNotVerifiedException` is reused for an unconfirmed inviter.** Its detail still
  says "before signing in", which is wrong prose for this context and harmless: the client
  translates the code and never renders the detail. Splitting it would mean a second code
  for the same fact about the same caller.

Two things this step does **not** do, both for Step 10:

- **Nothing can accept an invitation yet**, so `ACCEPTED` and `REVOKED` exist as constants
  and nothing sets them. They are not speculative: the partial index is scoped to
  `PENDING`, so the distinction is load-bearing from the first row written.
- **There is no list, revoke or resend**, which means an owner who invites the wrong
  address must wait seven days for it to lapse. Acceptable only because Phase D ships as
  one release.

## Step 10 — Invitations: preview, accept, revoke ✅ *done*

- `GET /api/invitations/{token}` — **unauthenticated preview** returning organisation name,
  inviter's display name and role, so the invitee knows what they are joining before typing
  a password. Nothing else about the organisation is exposed.
- `POST /api/invitations/{token}/accept`:
  - **No identity for that address** → create the user, add the membership, stamp
    `email_verified_at` (the link proves the address), return an access token. Signed in
    immediately, as registration used to be.
  - **Identity exists** → the normal case now, thanks to Step 1: add a membership. **The
    caller must authenticate first.** The invitation token proves control of the mailbox,
    which is not the same as proving they are the account holder; attaching a membership to
    somebody else's identity on the strength of an emailed link would be a real hole.
  - Accepting while signed in as a *different* identity is refused with its own code rather
    than silently attaching the membership to the wrong person.
  - An invitation to an organisation the identity already belongs to is rejected.
- `GET /api/invitations` (pending, tenant-scoped, OWNER), `DELETE /api/invitations/{id}` to
  revoke, `POST /api/invitations/{id}/resend`.

**Tests.** Accept creates a membership with the right role in the right organisation; the
token is single-use; expired and revoked invitations fail; preview leaks nothing beyond the
three fields. For the multi-organisation case: an existing identity gains a *second*
membership with a different role and keeps the first; accepting as another identity is
refused; an unauthenticated accept for an existing address creates no duplicate account.

**Done when** one person holds two memberships, reached entirely through the product.

### As built — where it differs from the above

Every bullet and every named test landed. Eight things a later step should know:

- **A link that no longer works says which way it failed.** The plan asks Step 12 for
  distinct messages for expired and revoked, which the one-answer rule the single-use tokens
  follow cannot supply — so `invitation_expired` and `invitation_revoked` are their own
  codes, and only "unknown" and "already spent" share `invalid_token`. Relaxing the rule is
  deliberate and narrow: what the visitor should do next genuinely differs (ask for another,
  or do not), and the enumeration it guards against needs a guessable token, which 32 random
  bytes in somebody's inbox is not.
- **Nothing is written until every refusal has been ruled out**, and only then is the
  invitation spent. Getting this backwards would mean `sign_in_required` costing the visitor
  the very link it tells them to come back with.
- **The accept body is optional, and needed on only one of the two paths.** Somebody with no
  account sends a name and a password; somebody who has one signs in and sends nothing.
  `credentials_required` covers the request that is neither. The alternative — two endpoints,
  or fields validated conditionally — would have split one decision the address already makes.
- **The invitation is spent by a conditional `UPDATE`,** as `user_tokens` redeems its links.
  There is a test that releases four accepts together and asserts exactly one membership
  comes out; without it, the losers would each be told they had joined and then trip the
  unique index on `memberships`.
- **Ownership is enforced in the service for all four owner-only endpoints**, re-reading the
  membership rather than trusting the twelve-hour-old role in the token. Step 9 established
  this for issuing; listing, revoking and resending now share the same helper, and the two
  that send mail share a second one that also requires the caller's own address to be
  confirmed.
- **Resend claims the mail limit after its lookup, not before** — the only sender that does.
  The "count requests, not messages" rule exists so an unauthenticated endpoint's refusals
  cannot be used to ask which addresses exist; here the recipient comes from a row the owner
  can already list. `InvitationService` therefore holds a `MailRateLimiter`, which no other
  service does.
- **`AuthenticationResponse.of` and `AccountResponse.of` became public.** Accepting ends in a
  session like any other, and a client should not have to parse a second shape to learn it
  now holds one.
- **The token travels in the URL path**, as the plan specifies, so it reaches access logs on
  the way. That is inherent to the frontend route `/invite/:token` carrying it already; a
  body would have hidden it from the API's own logs and nowhere else. Worth revisiting only
  alongside how the frontend route is shaped.

Two states the product still cannot reach on its own, both checked anyway:

- **`already_a_member` on accept** is defensive. Issuing refuses an address that is already
  a member, and accepting is the only way to gain one, so the state has to be seeded
  directly — but it is what keeps the rule true once Step 11 can add and remove people.
- **`invalid_credentials` on accept**, for a token naming an account that has since been
  deleted. There is no account-deletion feature; the check is the same one `/auth/me` makes.

## Step 11 — Member management ✅ *done*

- `GET /api/members` — tenant-scoped list, any member.
- `PATCH /api/members/{id}` `{role}` and `DELETE /api/members/{id}` — OWNER only.
- **Invariant: an organisation always keeps at least one OWNER.** Demoting or removing the
  last one is rejected; an organisation with no owner is unadministrable and unrecoverable
  without database surgery. An owner may remove themselves only if another remains.
- **Removal deletes the membership, never the identity.** Someone removed keeps their
  account, password and other memberships. Losing their *last* membership leaves them on the
  zero-membership empty state from Step 1 — not a deleted account.

**Tests.** The list is scoped to the caller's organisation, with a second organisation in
the fixture so leakage would fail. Last-owner demotion and removal both rejected. A member
cannot change roles. Removing someone who belongs to two organisations leaves the other
membership and the identity intact.

**Done when** an organisation can be administered without touching the database, and cannot
be left ownerless.

### As built — where it differs from the above

Every bullet and every named test landed. Six things a later step should know:

- **`POST /api/organisations` came with it**, as Step 1's "as built" note said it must. That
  note is why: removing somebody's last membership is exactly what this step makes possible,
  and without a way to start one under your own power the only route back would be waiting
  for another person to invite you. It takes an *identity* token, since whoever needs it has
  no organisation to be scoped to. **It has no screen yet** — see Step 12 below.
- **The owner count is taken under a write lock.** An optimistic count is wrong for the one
  invariant here that nothing can repair: two owners removing each other in the same moment
  would each count two and each conclude one remains. The conditional-`UPDATE` trick the
  tokens use does not apply — that makes a race on one row safe, and this is a race on a
  count across several — so `lockOwners` does `select … for update` and the second
  transaction waits and counts again.
- **`countByTenantIdAndRole` is gone**, replaced by that locking query. Step 1 added it
  for this step; keeping both would have left an unlocked count in easy reach of the next
  person to need one.
- **`MembershipService.requireOwner` is now shared with `invitation`**, which had its own
  copy from Step 9. Who may administer an organisation is not a rule worth holding twice.
  `MembershipService.join` likewise became the only place a membership is created, so
  registering, accepting an invitation and starting an organisation cannot drift apart.
- **`RegistrationService` shrank.** Deriving the handle, refusing an unusable or taken name
  and writing the tenant with an owner beside it all moved to `OrganisationService`, which
  is the whole of what registering and starting a second organisation have in common. One
  ordering changed with it: the email is now checked before the organisation name, where it
  used to be second. Nothing depends on which of two simultaneous complaints is reported.
- **`tenant` now depends on `membership`**, which is a cycle at package level since
  `Membership` names a `Tenant`. It is inherent rather than accidental: "an organisation
  with an owner" cannot be created without something knowing about both, and putting the
  orchestration anywhere else would only move the cycle. `OrganisationService` reaches
  `membership` through its service, not its repository, so the direction at least stays
  above the data layer.

## Step 12 — Frontend: members, acceptance and switching ✅ *done*

- `/invite/:token` — public; preview then accept, handling expired, revoked, and
  already-signed-in-as-someone-else with distinct messages.
- **The zero-membership empty state gains a way to start an organisation**, calling
  `POST /api/organisations`. Added in Step 11 with no screen behind it, and it is the only
  way out of that state a person can take by themselves — the empty state currently says to
  wait for an invitation and nothing more.
- `/app/members` — list with roles, invite form, pending invitations with revoke and resend,
  role changes, removal with confirmation.
- **Organisation switcher** in the dashboard header, now that a second membership is
  reachable. Uses the exchange endpoint from Step 1.
- Owner-only controls hidden from members, with the backend still enforcing it.

**Tests.** Accept flow end to end against a mocked API; each failure case renders its own
message; a member sees no owner controls; switching organisations re-scopes the dashboard.

**Done when** the whole invitation journey works in the browser, for a new and an existing
account.

### As built — where it differs from the above

Every bullet landed, including the create-an-organisation button Step 11 left without a
screen. Seven things worth knowing:

- **`AppLayout` is new, and the header moved into it.** Two signed-in pages both need it,
  and the organisation switcher in particular has to live in exactly one place or switching
  would mean something different depending on where you did it. `DashboardPage` is now the
  page it always was, minus a header it should never have owned.
- **Only `AuthProvider` holds the token.** Everything that calls the API as the signed-in
  caller goes through a new `request` on the auth context. The alternative — exposing the
  token so components could pass it to `apiRequest` — would put it in reach of anything
  that imported the context.
- **The switcher loads its own list and hides itself below two organisations.** The session
  does not carry one: an access token names a single organisation, and what a person
  belongs to changes underneath it. A control offering one choice reads as broken, and one
  choice is what everybody has until an invitation has been accepted. If the list fails to
  load, the header falls back to the organisation's name rather than taking the page down.
- **Accepting ends on a screen, not a redirect.** `/invite/:token` is outside both guards,
  so nothing would have moved the visitor on — and the codebase's rule is that forms do not
  navigate themselves. Confirming what happened and offering a link is better than a silent
  jump anyway.
- **Signing in from the invitation comes back to the invitation**, by passing
  `state={{ from }}` to `/login`. Otherwise "that address already has an account" would
  mean going to find the email again.
- **Removal asks inline rather than through `window.confirm`.** A browser modal is
  untestable and unstyleable; the two-step prompt is neither. It is the one action on that
  page that cannot be undone from it, since putting somebody back means inviting them again.
- **A test double that answers every URL alike is a lying double.** `App.test.tsx` mocked
  whole scenarios with one payload, which was fine while a page made one request. It now
  handed the switcher an account and crashed the header — silently, since the assertions had
  already passed. Those doubles answer by URL now, and `CLAUDE.md` records why.

One thing this step does **not** do: nothing auto-signs-out when a token expires
mid-session. A refused request reports whatever the server said and the page stays put. It
was true before this step and no screen here made it worse, but a members page left open is
where somebody will first notice.

---

# Phase E — Close out

## Step 13 — Documentation and debt retirement

- `CLAUDE.md`: mail configuration, the two token types and what each permits, single-use
  token conventions, the identity-versus-membership model, and the per-instance
  rate-limiting caveat.
- `roadmap.md`: strike the three retired M0 debt rows — one-user tenants, English-prose
  field errors, missing reset and verification. Leave the landing-page row.
- `roadmap.md`: replace the "one account, one organisation" constraint with the model that
  replaced it, since decision 3 reversed it.

---

## Migrations at a glance

Version numbers are assigned here so two steps cannot claim the same one.

| Version | Step | Contents |
|---|---|---|
| `V3__identity_and_membership.sql` | 1 | `memberships`; drop `users.tenant_id`, `users.role` |
| `V4__user_tokens.sql` | 3 | `user_tokens` |
| `V5__users_email_verified.sql` | 5 | `users.email_verified_at` |
| `V6__invitations.sql` | 9 | `invitations` |

Step 9 also records what a *scope written as a string* costs: the exception advice named
its own package, the package was renamed, and nothing failed to compile. Prefer a selector
the compiler checks, or no selector at all.

---

## Sequencing and risk

**Do Step 1 first.** It rewrites M0's core model, so every day it waits is another day of
code written against the shape it replaces — and once invitations exist, the migration has
live invitation state to carry too.

**Steps 2 and 4 are independent** of everything and of each other. Either can be done in
parallel with Step 1 by a second person.

**Step 4 is a breaking API change.** Backend, frontend and catalogue move in one commit;
splitting it leaves the sign-up form showing raw constraint names.

**Phase C ships as one release** (steps 5–8), for the reasons at the head of that phase.
**Phase D ships as one release** (steps 9–12): invitations that cannot be accepted, or
members who cannot be managed, are not worth shipping separately.

**The riskiest step is 1**, and it is risky differently from the rest: it changes
authentication, the one place a mistake is a security bug rather than a broken feature. The
specific hazard is the tenant-exchange endpoint, which must refuse a tenant the caller does
not belong to or it becomes cross-tenant escalation. That case has a dedicated two-tenant
test, and it is the test to review hardest in the whole milestone.
