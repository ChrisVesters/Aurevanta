# Security review — 12 August 2026, at `31296d0`

> **Scope.** The whole application as built through the original tenancy design, the team model and chosen handles: authentication and JWT
> handling, the verification gate, password reset, emailed single-use tokens, invitations,
> tenant isolation, member administration, outbound mail, the problem-document surface, and
> the frontend's handling of tokens and errors. Not a review of a diff — everything, as it
> stands.
>
> **How to read this.** Four findings, none of them HIGH, each with the moment it stops being
> deferrable. Then what was *accepted* and why, and then what was checked and found sound —
> that last section is the point of writing this down at all, because a review that records
> only its findings makes the next one re-derive everything it ruled out.
>
> **Rate limiting, denial of service and dependency currency were out of scope** by
> instruction, not because they do not matter.

---

## At a glance

| # | | Severity | Cheapest moment |
|---|---|---|---|
| 1 | [The credential cannot be withdrawn, and sits where a script can read it](#1--the-credential-cannot-be-withdrawn-and-sits-where-a-script-can-read-it) | Medium | Any time — but its two halves are one migration, so do them together |
| 2 | [Registration is a free, repeatable account-existence oracle](#2--registration-is-a-free-repeatable-account-existence-oracle) | Medium | Before a registration screen is redesigned; the ordering fix is free today |
| 3 | [An invitation token is interpolated into an API path unencoded](#3--an-invitation-token-is-interpolated-into-an-api-path-unencoded) | Low | Before the plan schema — its blast radius is the endpoint set |
| 4 | [Raw invitation tokens travel in the request line](#4--raw-invitation-tokens-travel-in-the-request-line) | Low | Before the API is public; fixing 4 removes 3 |

**"Cheapest moment", not "deadline", and the distinction is the point.** Only 3 and 4 are made
worse by a piece of work — 3 is bounded by which body-less `POST` endpoints exist and the plan schema adds
endpoints, 4 is a wire format that is cheap to change while nothing external depends on it. 1
and 2 cost the same to fix whenever they are fixed; what changes is only how long the exposure
ran. Attaching those two to a piece of work would make them look like that work's scope, and
scope is what gets cut.

**3 and 4 are the same secret, one hop apart.** Fix 4 and 3 goes with it.

`roadmap.md` lists these under *Cross-cutting, not a piece of work* for the same reason, with a
table that points back here. **This file is the record**; that one exists so the debt is
visible from the plan.

---

## 1 — The credential cannot be withdrawn, and sits where a script can read it

**Medium.** `backend/…/auth/reset/PasswordResetService.java:94`,
`frontend/src/auth/session.ts:24`

Two halves of one thing, written as one item because they are one piece of work and doing
them separately means migrating the same code twice. **First, that a stolen token cannot be
taken back. Second, that it is kept somewhere any script on the origin can read.** Either
alone is arguable; together they describe a credential that is comparatively easy to obtain
and impossible to withdraw once obtained.

**Worth stating up front: there is no refresh token.** A grep of both sides finds none — the
only credentials are the twelve-hour access token and the identity token. So there is no
short-lived/long-lived split where the sensitive half could be hidden and the disposable half
left in reach of JavaScript. The twelve-hour credential is the whole of it.

### It cannot be withdrawn

`confirm` replaces the credential and revokes every other *reset link*, and that is all.
Access tokens are stateless HS256 JWTs living twelve hours
(`aurevanta.security.jwt.access-token-ttl`), `users` carries no `token_version` or
`credentials_changed_at`, `AuthenticatedUserJwtConverter` performs no such check, and there
is no deny list — `token_version`, `jti` and `denyList` appear nowhere in the source.

**The asymmetry is what makes this worth fixing rather than merely noting.** *Membership*
changes are already effectively immediate, because every tenant-scoped path re-reads the row
through `MembershipService.requireOwner` / `requireMember` — somebody demoted this morning
stops administering at once, which is a property this codebase went to some trouble for.
Credentials get no equivalent re-check. So the one remediation the product offers a person
whose account is compromised does not remediate.

**How it plays out.** An attacker holds a victim's access token — an unlocked shared machine,
a token pasted into a bug report, a proxy log (see finding 4). The victim notices and does
the only thing available: requests a reset, follows the link, chooses a new password. The
attacker goes on calling `GET /api/auth/me`, `GET /api/members`, `POST /api/invitations` and
`PATCH /api/organisations` with the old bearer token for the rest of its twelve hours — and
`POST /api/invitations` turns that window into permanent access, by inviting a second address
in as `OWNER`.

**The fix is one column, one claim and one comparison.** A monotonic `token_version` on
`users`, carried as a JWT claim, checked in `AuthenticatedUserJwtConverter`, bumped in
`User.changePassword`. It reuses the re-read discipline the membership checks already follow.
A `jti` deny list holding entries for the token's TTL is the alternative and is strictly more
machinery. Shortening the TTL narrows the window without closing it.

CLAUDE.md already records this as a deferred the team model trade-off, and the deferral was defensible
when nothing had been built on top of it. What has changed is that the reset endpoint is now
the *only* way back for an account behind the verification gate, so "recovery" is a claim the
product makes and does not keep.

### It sits where a script can read it

`session.ts` keeps `{token, kind}` in `localStorage`, which is readable by any script on the
origin. The module's own docstring says so plainly and names the remedy, so this is a stated
trade-off rather than an oversight — but it was a trade-off made when nothing depended on it,
and it is the half of this item most likely to be argued about, so the accounting belongs here
in full.

**Separate the response body from the storage; they are not the same concern.** Returning the
token in a JSON body is what OAuth2's own token endpoint does: it is TLS-protected, and bodies
are not recorded by proxies, load balancers and APM agents the way request lines are. That
distinction is exactly what makes finding 4 a finding and this not one. The exposure is what
the client does with the token afterwards.

**What an `HttpOnly` cookie would fix**: an XSS could no longer *exfiltrate* the token. That
is the main event and it is worth having.

**What it would not fix, and is routinely oversold**: an attacker with script execution on
this origin can still make authenticated requests, because the cookie is attached
automatically. It converts *"steal it and use it from anywhere, offline, for twelve hours"*
into *"abuse it from the victim's browser while they are on the page"*. A real reduction in
blast radius, not immunity.

**What it would cost**: CSRF comes back. CLAUDE.md's reasoning — *"CSRF disabled is correct
here: bearer-only, `STATELESS`, no cookie ever set"* — becomes false the moment one is set,
and that comment will be read and trusted by somebody who does not know it changed.

**It fits this application better than most**, because the SPA is same-origin with `/api`, so
`SameSite` does most of the work without a token per form. **Use `Lax`, not `Strict`.** Under
`Strict` the cookie is withheld on a top-level cross-site navigation, so every emailed link —
verify, reset, invite — would land the visitor apparently signed out. `Lax` sends it on
top-level GETs, which is precisely that case, and this API has no state-changing GETs for the
looser rule to expose.

### Why one item

Adding `token_version` means introducing a per-request server-side check of the credential.
Once that check exists, moving the credential into an `HttpOnly` cookie is a small further
step against the same code — `session.ts` is the only frontend module that changes, by its own
docstring, and the backend work is a cookie on the sign-in and exchange responses plus a
bearer-token resolver that reads it. Done together they close the exfiltration path and the
revocation gap as one migration. Done apart they are two.

**The residual risk today is genuinely low**, and that is worth recording rather than
inflating: the review found no unsafe sink anywhere in `src/` — no `dangerouslySetInnerHTML`,
no `innerHTML`, no `eval`, and no catalogue entry that renders a server string as HTML. This
is a one-mistake-away property, not a present hole. What makes it worth scheduling is that the
mistake it is one away from guards a credential nothing can withdraw.

---

## 2 — Registration is a free, repeatable account-existence oracle

**Medium.** `backend/…/auth/registration/RegistrationService.java:63`, with
`backend/…/tenant/OrganisationService.java:85`

`register` checks the address at line 63 and only reaches the handle check inside
`createFor`. Because `organisationSlug` is a required field the caller fully controls,
submitting a handle *known to be taken* yields two distinguishable `409`s for one request
shape: `email_already_registered` when the address has an account, `slug_taken` when it does
not. Both branches roll back — nothing is created, nothing is mailed.

**Registration always disclosed something here**, as most products do, and that on its own is
not what this finding is about. What chosen handles's required handle added is a probe that is
*repeatable* and *free of side effects*: before it, testing an address meant either being
told it was taken or actually creating an account for it, which is destructive, visible, and
usable once. Now the answer costs nothing. The `refundRecipient` added on 12 August — which
exists so that handle collisions cannot lock somebody out of registering — also returns the
per-recipient budget on the negative branch, so the probe now spends only the per-source
allowance.

There is a second oracle in the same pair of paths, and it survives unifying the codes: the
"address exists" branch returns *before* `passwordEncoder.encode`, so it answers in
milliseconds while the "address free" branch pays a full bcrypt.

**Why this is a finding here and not a shrug.** The rest of this API works hard for exactly
the property being broken. `AuthenticationService` carries a decoy hash so a missing account
costs the same time as a wrong password. Verification resend and password reset both answer a
blanket `202`, including for addresses nobody has registered. `MailRateLimiter` is claimed
before anything is looked up *specifically* so its own refusals cannot answer this question.
One endpoint answering it plainly makes the other three ceremony.

**The cheap fix is an ordering.** Check the handle before the address, so a taken handle
always wins and cannot serve as a control condition. It costs nothing, and it also means a
genuine collision is refused before a bcrypt is paid — which closes the timing channel in the
same move. Answering an already-registered address with the same non-committal `202` the
reset and resend endpoints use, plus a "you already have an account" mail, closes it fully at
the cost of a slower, stranger registration form. **Decide which of those two is wanted before
the plan schema**, because the second one changes a screen.

---

## 3 — An invitation token is interpolated into an API path unencoded

**Low**, and the severity is about today's endpoints rather than the mechanism.
`frontend/src/auth/AuthProvider.tsx:158`, `frontend/src/routes/InvitePage.tsx:52`

The token comes off the route parameter and goes straight into the path; `apiRequest` then
builds `` `/api${path}` ``. **React Router does let a `/` through a parameter** — this was
checked in the router source rather than assumed: `decodePath` decodes each segment and
re-encodes a decoded slash as `%2F`, and `matchPathImpl` converts it straight back
(`replace(/%2F/g, "/")`). So `/invite/..%2Fx` yields `token === "../x"`, and the browser's
URL parser normalises the dot segment away.

These are the only two attacker-controlled interpolations into an API path in the codebase.
Every other one carries a server-supplied UUID, and the verification and reset tokens travel
in a JSON body.

**What it buys an attacker today**: a link that renders as an ordinary invitation and, when
the victim clicks accept, issues some *other* `POST` under `/api` carrying their
`Authorization` header. They cannot read the response — it is the victim's own same-origin
JS — the method is fixed by the call site, and the body is fixed to `undefined` or
`{displayName, password}`. The best available primitive is making an owner resend an
invitation whose UUID the attacker already knows. That is why it is Low, and also why it will
not stay Low by itself: **the blast radius is whichever body-less `POST` endpoints happen to
exist**, and the plan schema adds endpoints.

**Fix:** `encodeURIComponent` at both call sites. Better, have `apiRequest` take path
segments rather than a pre-joined string, so no future call site can forget. Finding 4 makes
it moot.

---

## 4 — Raw invitation tokens travel in the request line

**Low**, and entirely deployment-dependent.
`backend/…/invitation/InvitationController.java:116` and `:136`,
`backend/…/mail/EmailTemplates.java:88`

Every other emailed secret in this application is redeemed by posting it in a JSON body —
`POST /api/auth/verify-email {token}`, `POST /api/auth/password-reset/confirm {token,
password}`. The invitation token is the exception: a path segment on both the unauthenticated
preview and the unauthenticated accept, from a link that is itself `/invite/<rawToken>`.

The request line is the part of an HTTP request that intermediaries record by default —
reverse proxies, load balancers, WAFs, CDN edge logs, APM spans. CLAUDE.md states the
property as *"only a SHA-256 hash is stored, never the raw value… so a leaked backup must not
be a list of working password resets"*. A path segment reintroduces that exposure through a
channel usually retained longer, and read by more people, than the database is. The credential
is worth a membership for seven days.

**This application's own handling is clean** — the four `log.*` sites in `main/` log no
secrets, `AsyncEmailSender` masks recipients, and `index.html` already sets `no-referrer`,
whose comment anticipates dropping the token from the address bar after redemption. The gap
is one hop out, in infrastructure this code does not own and cannot audit.

**How it plays out.** Behind an ALB with ordinary access logging: the invitee clicks, the SPA
calls `GET /api/invitations/<rawToken>`, and the token lands in the proxy log and everything
downstream of it. Anyone with read access takes it within seven days and accepts. If the
address holds no account, `register()` creates a *pre-verified* one and hands back a session —
so the taker gets both the membership and a verified identity at the invitee's address, and
the invitee's own link stops working.

**Fix:** move the token off the request line — `POST /api/invitations/preview {token}` and
`POST /api/invitations/accept {token, displayName?, password?}`, matching the two endpoints
that already work that way, and update the `permitAll` matchers in `SecurityConfiguration`.
The frontend keeps `/invite/:token` client-side, or uses a fragment so it never reaches a
server at all. **Do it while the API is unreleased**: it is a breaking change to a wire format,
which is free now and stops being free the moment anything outside this repository sends that
request.

---

## Accepted, with the reason

Recorded so they are not rediscovered as findings.

- **`SlugTakenException.suggested` discloses handle occupancy.** Being offered `acme-7` says
  `acme` through `acme-6` exist. Handles are public URL identifiers by design — the plan schema puts them
  in URLs — so this discloses something intended to be public, and the alternative is the
  availability endpoint chosen handles deliberately did not build.
- **`management.endpoint.health.show-details=when-authorized` with no `roles` set** means any
  authenticated caller, including a member of an unrelated tenant, sees datasource and
  disk-space details. Trivial, and `never` would cost nothing — worth doing when the actuator
  is next touched.
- **Registration discloses that an address is taken**, in the ordinary case where the handle
  is free. That is inherent to a registration form and is not what finding 2 is about.

---

## Checked and found sound

The value of a review is partly in what it rules out, and this is the half that stops the
next one starting from nothing.

- **The two token kinds.** `SecurityConfiguration` guards `.anyRequest()` on
  `hasAuthority(TENANT_SCOPED)` — a positive requirement, never "not identity" — so a third
  kind of token would have to be granted access deliberately. `SCOPE_IDENTITY` reaches
  exactly `GET /api/memberships`, `POST /api/auth/tenants/*/token` and
  `POST /api/organisations`, all keyed only off the caller's own user id. The `permitAll`
  patterns are single-segment, so `/api/invitations/*` cannot reach the owner-only pending
  list or the resend endpoint beside it.
- **`token_type` is genuinely required.** The converter throws `InvalidBearerTokenException`
  on its default branch — no defaulting to access. `sub`, `email`, `tenant_id` and `role` are
  rejected rather than guessed.
- **JWT crypto.** `MacAlgorithm.HS256` is pinned on decoder and encoder, so `alg: none` and
  an algorithm swap are both refused; `exp`/`nbf` and `iss` are validated; a configured secret
  under 32 bytes fails startup; unset generates 32 `SecureRandom` bytes with a warning. One
  symmetric key, so there is no asymmetric-confusion surface.
- **No IDOR and no cross-tenant leak.** Every id taken from a request is looked up *together
  with* the tenant from the token — `findInTenant`, `findPendingInTenant`,
  `findForUserInTenant`. The only two `findById` calls in the codebase take the caller's own
  user id. An id belonging to another tenant returns the same 404 as one that never existed.
- **No privilege escalation.** The `role` claim is never read for an authorisation decision:
  `AuthenticatedUser.role()` has no call site outside `security/`. Every owner check re-reads
  the membership. Invitation acceptance takes the role from the stored row. The last-owner
  invariant is checked on removal *and* demotion, under `PESSIMISTIC_WRITE`.
- **No SQL injection is reachable.** Every query is JPQL with named parameters; there are zero
  occurrences of `nativeQuery`, `createQuery`, `EntityManager`, `Specification`, `Sort` or
  `Pageable` in `main/`, and order clauses are literal.
- **Link tokens.** 32 bytes of `SecureRandom`, base64url, stored only as a hex SHA-256 against
  a unique index. Redemption is one conditional `UPDATE` predicated on hash, purpose,
  unconsumed and unexpired *together*, so expiry cannot slip between check and write and a
  second concurrent click gets zero rows. Purpose scoping holds: a confirmation link cannot be
  spent as a reset.
- **Invitation acceptance does not treat mailbox control as account ownership.** The signed-in
  branch loads the caller by their own JWT subject and requires the invitation address to match
  the *database* record; the anonymous branch refuses with `sign_in_required` if any account
  holds the address. Neither can hand back a session for somebody else's identity. Nothing is
  written until every refusal is ruled out, and the spend is a conditional `UPDATE`.
- **Mail header injection — checked and negative.** The subject is built from an inviter name
  and an organisation name, neither of which rejects CR/LF. Angus Mail's `MimeUtility.fold`
  ends in `makesafe()`, which drops blank lines and prefixes a space to any continuation, so an
  injected `Bcc:` folds into the subject rather than becoming a header; recipients go through
  `InternetAddress` parsing, which rejects CRLF. **This rests on `mail.mime.foldtext` remaining
  `true`** — rejecting CR/LF in display and organisation names would make it not matter, and is
  worth doing if either field ever reaches another header.
- **Frontend.** No `dangerouslySetInnerHTML`, `innerHTML`, `eval` or `document.write` anywhere
  in `src/`. i18next runs `escapeValue: false`, which is correct for React, and no catalogue
  entry combines interpolation with markup — so no `<Trans>` renders a server string as HTML.
  The raw token is never in the context value and is cleared on sign-out — **though where it is
  kept between page loads is finding 1, not this bullet**, which was the original wording's
  mistake: "no component but `AuthProvider` can reach it" answers a narrower question than "can
  a script on this origin read it". No CORS configuration exists at all, which is right for a
  same-origin SPA, and CSRF-disabled is correct *given* bearer-only, `STATELESS` and no cookie —
  a conditional that stops holding if finding 1 is fixed with a cookie.
- **Problem documents.** No handler echoes a database message, a constraint name or a stack
  trace; `handleConflict` reads the cause only for a `contains()` test against three known
  index names. `include-message` and `include-stacktrace` are at their `never` defaults. No
  response type serialises an entity, and no password hash or token hash appears in any
  response.

---

## How this was produced

Four reviews run in parallel over separate areas — authentication and JWT handling; emailed
tokens, invitations and mail; tenancy, authorization and data access; the frontend and the
HTTP surface — with every finding then verified against the source before it was written down
here. That last step removed one: a report that `InvitationPreview.claimed` contradicts
CLAUDE.md was quoting wording replaced earlier the same day.

**The second half of finding 1 — token storage — came from a question afterwards, not from
the sweep**, and the sweep had it in the sound column. That is recorded because it says
something about where this kind of review is weak: `localStorage` was documented, reasoned
about in a docstring, and deliberate, and every one of those made it read as settled rather
than as a decision still open to being taken again.

Worth repeating after the plan schema, which is the first work to carry domain data and the first to
put an organisation handle in a URL.
