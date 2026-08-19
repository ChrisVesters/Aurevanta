# Organisation names are not unique — the design record

> **Scope.** Chosen handles: stop `tenants.slug` making an organisation's *name* unique
> across the installation. Explicitly excluded: reserved handles, and any change to how
> tenants are addressed in URLs — nothing routes by handle yet, which is the whole reason
> this is cheap now.
>
> **How to read this.** Decisions first; the first one changes what the work *is*. Then
> five steps, each a reviewable commit that leaves the build green: `./mvnw test` (format
> gate, 100% branch coverage) and, where the frontend is touched,
> `npm run lint && npm run build && npm run test`.

---

## What changed about this work

The roadmap framed chosen handles as *"keep the slug unique, disambiguate on collision"* — the
application quietly appends a number and nobody is refused. That fixes the symptom.

**The decision taken instead is that the handle becomes a required field the organisation
owns**, proposed from its name and with a suffixed alternative offered when the proposal is
taken. This is a better answer to the problem chosen handles exists for, and it is worth being explicit
about why:

The original tenancy design's refusal was unactionable because it was aimed at the wrong thing. *"That organisation
name is already taken"* leaves a person nothing to do — it is their name. **"That handle is
already taken" leaves them everything to do**, because a handle is not their name; it is an
address, and picking a different address costs nothing. The refusal survives, and stops
being a wall.

It also dissolves the question the roadmap flagged as needing an answer first — *does the
slug follow the name?* Once the two are separate fields, neither follows the other, and
changing either is a deliberate act rather than a side effect.

---

## At a glance

| Step | | Depends on |
|---|---|---|
| 1 | The handle becomes a field ✅ *done* | — |
| 2 | Each conflict reports what it actually is ✅ *done* | 1 |
| 3 | Changing the name and the handle afterwards ✅ *done* | 1 |
| 4 | Two organisations, one name, on screen ✅ *done* | 1 |
| 5 | Close out ✅ *done* | 1–4 |

Step 1 is the work. Steps 2 and 4 are consequences it leaves behind; step 3 is the one
addition, and the one to strike if the work needs trimming.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | Derived handle, or a field? | **A field**, defaulted from the name and freely changed. |
| 2 | What if the caller chooses nothing? | **They cannot.** The handle is required; the form proposes one from the name, and accepting it is a choice. |
| 3 | How is a collision surfaced? | **On submit**, with the next free handle carried in the refusal. No availability endpoint. |
| 4 | Reserved handles? | **Not yet** — decided when the plan schema puts handles in URLs. |
| 5 | Can the handle change later? | **Yes, and the old one stops working.** |
| 6 | Telling two identically named organisations apart | **The handle, shown only where the caller's own list repeats a name.** |

### Decision 2 — Required, with a proposal

An optional handle would leave two paths through creation that have to answer differently — a
chosen handle can be refused, an omitted one cannot, because there would be nothing for the
caller to change. Two paths is one more than this needs.

**The field is required.** The form proposes a handle as the name is typed, and accepting the
proposal costs a keystroke fewer than changing it — so the common case is free and the person
has still chosen. Every refusal is then against something somebody typed, which is the whole
property chosen handles exists to establish, with no branch where the server picks and the caller is left
holding the consequence.

Three things fall out of it, and they are most of why this is the better answer:

- **No silent allocation.** Nothing walks `base-2`, `base-3` looking for a free handle to
  assign. The only suffixing left builds a *suggestion*, which is advisory — it cannot be
  wrong in a way that matters, only unhelpful.
- **The advisory lock goes with it.** Without a check-then-assign sequence there is nothing to
  serialise: the request carries a handle, the pre-check refuses it if taken, and
  `uq_tenants_slug` catches the pair who passed that check in the same instant. Worth
  comparing the stakes with the other pessimistic lock in this codebase —
  `MembershipService.lockOwners` guards an invariant nothing can repair once broken, and this
  would guard a *message*. A rare, self-correcting, already-actionable refusal does not earn a
  lock.
- **The proposal becomes the frontend's job**, and `Slug.of` moves with it. Deriving a handle
  from a name is what a form does while somebody types; once the field is required the server
  never needs it, and leaving it there would leave code nothing calls. Accent folding,
  lowercasing and hyphenating port to TypeScript with their own tests. **The shape regex is
  the contract between the two**, and it is the server that enforces it — a proposal the
  server would reject is a frontend bug, not a disagreement about the rule.

A name with nothing to derive from — `!!! ???` — proposes nothing, and the visitor types a
handle themselves. That is the right outcome rather than a fallback worth inventing: an empty
proposal in a required field asks a question, where `organisation` would invite somebody to
accept it without reading it.

### Decision 3 — On submit, with the suggestion in the refusal

An availability endpoint (`is acme-consulting free?`) would be the nicer form, and it is a
probe surface: anybody could walk it to enumerate which organisations exist. Handles become
semi-public once they are in URLs, so it would not be a disaster — but it is a new endpoint,
a new public surface, and a new thing to rate limit, bought for a case that arises rarely.

So the refusal carries the answer instead:

```
409  { "code": "handle_taken", "suggested": "acme-consulting-2" }
```

One round trip, only when it collides, and nothing to probe.

**The extra property rides on the problem document, not on a field error.** `FieldProblem`
deliberately publishes only a constraint's *numeric* attributes, and `errors` is built from
Bean Validation failures — a service-level "somebody else has this" is neither. The form
places the message against the handle input itself, knowing what the code refers to, exactly
as the sign-in form places `email_not_verified`.

### Decision 5 — The handle can change, and old links die

Chosen deliberately, so the cost belongs here in writing: **every link anyone has pasted to
an organisation stops working the moment its handle changes.** No redirect table, no retired
handles that keep resolving.

Two things make that survivable rather than reckless, and both are temporary:

- **Nothing routes by handle today.** Putting one in a URL is what would end that, so until then the cost of
  changing a handle is genuinely zero — there is no link to break.
- **It is the reversible direction.** Adding retired-handle redirects later is a table and a
  lookup; taking them away once people rely on them is not.

**This is the decision to revisit at the plan schema**, not because it will have become wrong, but because
that is the moment its cost stops being hypothetical. It is noted in `../roadmap.md` under the plan schema
rather than left in this plan for nobody to find.

### Decision 6 — Disambiguate where a choice is being made, and nowhere else

Once two organisations can share a name, a person who belongs to both sees the same word
twice. It only bites where a *choice* is being made between organisations, which is two
places:

| Where | After |
|---|---|
| `ChooseOrganisationPage` | the handle beside the name, **when the list repeats a name** |
| `OrganisationSwitcher` | the same, appended in the option |

Everywhere else needs nothing, and it is worth recording why rather than leaving it to be
rediscovered:

- **The invitation preview already carries its discriminator.** "Ada has invited you to join
  Acme Consulting" — if you do not know Ada, the organisation's name was never going to
  settle it. This is the case the roadmap worried about most, and it is the one already
  answered.
- **The app header is covered by the switcher** that sits in it.
- **Sentences that interpolate a name are left alone.** "Acme Consulting is ready" reads badly
  with a handle in it, and by then you are inside one organisation rather than choosing
  between two.

**Only on a repeat**, because putting `acme-consulting` under "Acme Consulting" for everybody
is noise for the overwhelming majority with no collision. It is a display rule over data
already on the wire — `MembershipSummary` has carried `organisation.slug` since the team model step 1 —
so it costs no query and no endpoint.

---

## Step 1 — The handle becomes a field ✅ *done*

**Goal.** Two organisations may share a name. Whoever creates one chooses its address, and is
refused only when they chose something somebody else already has.

### The field

`handle` joins `RegistrationRequest` and `CreateOrganisationRequest`, **required** in both.

- **Shape:** lowercase letters, digits and single hyphens, not leading or trailing —
  `^[a-z0-9]+(-[a-z0-9]+)*$` — and 2 to 80 characters, matching the column.
- **`Pattern` joins `CONSTRAINT_CODES`** as `pattern`, with a catalogue entry that is
  self-contained: only a constraint's *numeric* attributes are published, so the regular
  expression stays on the server and the message has to say "lowercase letters, numbers and
  hyphens" in its own words rather than interpolate one.
- **The name keeps `@NotBlank @Size(max = 200)`** and loses everything else. An empty name is
  still refused, because that *is* something the person filling in the form can fix — which is
  the distinction the whole work turns on.

### Accepting it, or refusing it

There is no allocator any more, only an answer:

- **Free** → taken exactly as given.
- **Taken** → `HandleTakenException`, carrying the first free suffixed form so the refusal
  arrives with its own remedy.
- **The suggestion strips a trailing `-<digits>` before suffixing**, so a refused
  `acme-consulting-2` proposes `acme-consulting-3` rather than `acme-consulting-2-2`.
- **Two callers choosing one handle in the same instant** get past the check together and meet
  `uq_tenants_slug`. The violation is caught and reported as `handle_taken` without a
  suggestion — the constraint name says which index was tripped, the same mechanism step 2
  uses — and a retry produces the ordinary refusal with one. This is rare, self-correcting,
  and already actionable, which is why it does not get a lock.

### What goes away

- `Slug.of` and `SlugTests` **move to the frontend**, per decision 2.
- `OrganisationNameUnavailableException` and `UnusableOrganisationNameException` are deleted.
  `handle_taken` replaces the first; nothing replaces the second, because a name that yields
  no handle is no longer a name that yields anything.
- `OrganisationService` stops deriving. It validates freeness, saves, and reports.

### The forms

Registration and "start an organisation" both gain the field, prefilled: as the name is typed
the handle follows it, until the moment the visitor edits the handle themselves, after which
it is theirs and stops moving. A refused handle replaces the field's value with the suggestion
and says why.

**Backend, frontend and catalogue move in one commit** — the rule the team model step 4 set, for the same
reason: splitting it leaves a form that can be shown a code nothing has wording for, or a
required field the server does not know about.

**Tests.** Two organisations registered with the same name and different handles both succeed.
A chosen handle is stored exactly as typed. A missing handle is a field error, and a malformed
one reports `pattern`. A taken handle is refused with a suggestion, and that suggestion is
accepted when submitted. A refused `acme-2` suggests `acme-3`. Two concurrent creations of one
handle leave one organisation and one `handle_taken`, released together as
`SingleUseTokenServiceTests` does for redemption — the index is what holds, and a serial test
cannot tell that from luck. Frontend: the proposal follows the name until the field is edited
and then stops; a refusal prefills the suggestion; a name with nothing to derive from proposes
nothing.

**Done when** two people can register "Acme Consulting", and the only thing either is ever
refused is a handle they typed.

### As built — where it differs from the above

- **The wire word is `slug`, not `handle`.** The API already publishes
  `organisation.slug`, and a request field called `handle` would have named the same value
  twice for anybody writing a client. "Handle" survives where it reads better than it
  stores: the UI label, this plan, and `../roadmap.md`. The refusal is `slug_taken`.
- **The service never catches the race; the advice reads it.** Catching the constraint
  violation inside `OrganisationService` needed a `saveAndFlush` to make the violation land
  somewhere catchable, and left a branch only two colliding writers could reach — so
  nothing could cover it. Mapping the violated index to a refusal moved into
  `ApiExceptionHandler`, which is the one place that already turns failures into answers,
  and which step 2 was going to extend the same way regardless. It reads the constraint
  name out of the exception, so both branches are a plain unit test away.
- **`ConstraintNamesTests` is what the risk section asked for.** It asserts every index
  name the advice reads still exists in the database. Renaming an index without following
  it would otherwise turn a specific, actionable refusal into a generic one with the whole
  suite still green; this is the test that fails instead. Step 2 adds names to the list it
  walks.
- **`Slug.base` lost a branch it could never take.** Guarding against an empty result was
  dead code: the pattern guarantees a leading letter or digit, so stripping a trailing
  count always leaves something.
- **The banner is suppressed while the handle field is speaking.** `useFormFailure` could
  not know to do it — a handle already taken is not a validation failure and never appears
  in `errors` — so the refusal was rendered twice, once beside the field and once in the
  banner. A test caught it. `SLUG_TAKEN` is exported from `SlugField` for exactly that,
  and the tests now assert the message is said once.
- **`useProposedSlug` is shared by both forms**, because "follows the name until its owner
  takes it over" is easy to get subtly wrong and invisible when you do: a field that kept
  following would silently undo a handle somebody had just chosen, one keystroke into the
  name above it. Taking over is one-way, so going back to fix a typo in the name does not
  move it either.

---

## Step 2 — Each conflict reports what it actually is ✅ *done*

**Goal.** Retire the last refusal that describes the wrong thing.

`registration_conflict` was written when a registration could trip two unique indexes and the
handler could not tell which. After step 1 it is down to one — and the handler it lives in
stopped being registration's during the team model step 9, when the advice went application-wide. It now
answers for an invitation race too, telling an owner that *"that email address or organisation
name was just taken"* when what happened is that a colleague invited the same person a moment
earlier. This is the item the Phase D review recorded and deliberately left; step 1 is what
makes it worth doing.

- `RegistrationService` catches the violation on the email index and rethrows
  `EmailAlreadyRegisteredException` — the code its own pre-check already produces, so the race
  and the ordinary case become indistinguishable to the client, which is what they should
  always have been.
- `InvitationService.write` does the same with `InvitationAlreadyPendingException`.
- What is left in `ApiExceptionHandler` is a genuine last resort, and takes a neutral code and
  wording that does not name a field it knows nothing about.

**Tests.** Both handlers keep reporting the code they do today when their pre-check fires. The
catch-all reports the neutral code and still does not echo the violation's own message, which
can name database objects.

**Done when** no problem document describes a constraint the caller did not touch.

### As built — where it differs from the above

- **Nothing was caught in a service.** The plan had `RegistrationService` and
  `InvitationService.write` each catching their own constraint violation and rethrowing.
  Step 1 had already moved that job into `ApiExceptionHandler` — for a reason that applies
  just as well here — so doing it the planned way would have left two mechanisms for one
  thing, and put a `saveAndFlush` in each service to make the violation land somewhere
  catchable. All three constraints are entries in one map instead.
- **`registration_conflict` is retired rather than narrowed.** The plan says it "narrows to
  email only"; in fact `uq_users_email` now answers with `email_already_registered`, the
  code its own pre-check produces, which leaves the old code with nothing to describe. What
  replaces it is `conflict` — deliberately naming no field, because a refusal that guesses
  at what the caller was doing is worse than one that admits it does not know.
- **`ConstraintNamesTests` grew without being touched.** It walks
  `ApiExceptionHandler.KNOWN_CONSTRAINTS`, which is now the map's key set, so adding a
  constraint to the map is what adds it to the test. That is the shape step 1 was aiming
  for and this step is the first evidence it works.

Worth knowing: **every entry in that map is a race nothing can provoke on demand**, so the
end-to-end suites never exercise them. The unit tests construct the violation directly and
`ConstraintNamesTests` pins the names to real indexes — between them that is the whole of
the coverage, and it is deliberate rather than a gap.

---

## Step 3 — Changing the name and the handle afterwards ✅ *done*

**Goal.** An organisation is not stuck with what it was called on the day it was created.

The one part of this work that adds rather than corrects, and the one to strike if it
needs trimming — nothing else here depends on it.

- `PATCH /api/organisations` `{name?, handle?}` — **OWNER only**, and the organisation comes
  from the caller's token rather than the path. The absent identifier is the point: it is the
  same rule every tenant-scoped endpoint follows, and it is what stops this becoming a way to
  rename somebody else's organisation.
- A changed handle goes through the same allocator, so it is refused with the same code and
  the same suggestion as at creation.
- `/app/settings` — a new owner-only page, and a nav entry beside Members. Members do not see
  it; the server enforces the same rule regardless.

**The name is included alongside the handle deliberately.** A settings screen that lets an
owner change their address but not their name would be an arbitrary place to stop, and after
step 1 the name derives nothing, so changing it is a plain column update with no consequence
anywhere. If that is unwanted, this is the seam to cut along.

**Changing a handle breaks every link to it**, per decision 5. The screen says so before it
saves rather than after.

**Tests.** An owner changes the name; an owner changes the handle; a member is refused both.
A handle already taken is refused with a suggestion. The organisation another token names is
untouched, with a second organisation in the fixture so leakage would fail.

**Done when** an organisation can be renamed and re-addressed without touching the database.

### As built — where it differs from the above

- **Both fields are required, not optional.** The plan wrote `{name?, handle?}`. Jackson
  cannot tell an absent field from one sent as null, so an optional name would have had to
  decide which of "leave it" and "clear it" a null meant — and both readings are wrong for
  a column that cannot be empty. `PATCH /api/members/{id}` already means "the parts you may
  change are the parts you send", so this follows it.
- **The tenant comes off the membership that proved the caller may change it.** Looking it
  up separately would have added a not-found branch for a tenant that cannot be missing
  once a membership in it has been found — a branch nothing could cover.
- **Keeping your own handle is not taking somebody's.** The collision check skips the
  caller's own, or renaming an organisation would be impossible without also moving its
  address. It has its own test, because it is the kind of thing that works until the first
  person renames without re-addressing.
- **The form is a component of its own, mounted with an organisation rather than before
  one.** `SettingsPage` initialised its handle field from a session that was still
  restoring, so the field came up empty — for a real visitor, not only in a test. Splitting
  the form out means it mounts once there is something to start from, which removes the
  question rather than answering it with an effect that would then have to be careful not
  to overwrite what had been typed since.
- **The handle does not follow the name here**, unlike the two forms that create an
  organisation. It already has an owner, and moving it because somebody fixed a typo above
  it is the bug `useProposedSlug` exists to prevent.

---

## Step 4 — Two organisations, one name, on screen ✅ *done*

**Goal.** A person who belongs to two organisations called Acme can tell them apart.

- A small helper — given the caller's memberships, which names occur more than once — used by
  `ChooseOrganisationPage` and `OrganisationSwitcher`. Both already hold the list; neither
  needs a new request.
- The handle renders beside the name only for those: as secondary text in the chooser, and
  appended in the switcher's option, where an `<option>` has nowhere to put a second line.
- A catalogue entry for the pairing, so the punctuation between name and handle is not a
  literal string.

**Tests.** Two organisations with the same name each show their handle; two with different
names show none. A repeat in one list does not put a handle beside an unrelated name.

**Done when** no screen offers a choice between two things a person cannot tell apart.

### As built — where it differs from the above

- **Names that differ only in case count as the same name.** "Acme" beside "acme" is two
  things nobody reliably tells apart in a list, which is the test this rule applies. The
  alternative — exact comparison — is defensible and leaves the one genuinely confusable
  case uncovered for nothing.
- **The handle goes *inside* the chooser's button**, not beside it. Two buttons that read
  the same are two buttons anybody navigating by their names cannot choose between, which
  is the same problem one layer down from the one being fixed.
- **A space between the name and the handle turned out to be load-bearing.** An accessible
  name is the button's text run together, so without it the two lines are announced as one
  word and the handle is no easier to hear than the name it exists to disambiguate. It is
  invisible on screen, where the two are separate rows of a column. A test caught it.
- **One catalogue entry, not two.** Only the switcher needs punctuation between the two —
  an `<option>` has nowhere to put a second line — so the chooser renders the handle as a
  plain second row and needs no wording of its own.

---

## Step 5 — Close out ✅ *done*

- `../roadmap.md`: strike the the original tenancy design debt row about organisation names being unique across the
  installation, and mark chosen handles done with what it actually did — which is not what chosen handles was
  written to do, and should say so.
- `../roadmap.md`: chosen handles's "decisions required" table has answers now, including the one it called
  unresolved. Record them where the question was asked.
- `../roadmap.md`, **under the plan schema**: reserved handles, and whether a changed handle should leave a
  redirect behind. Both are deferred here on the grounds that nothing routes by handle yet,
  and the plan schema is the step that ends that. They belong where the person doing the plan schema will read them.
- `CLAUDE.md`: the handle is chosen, not derived; a refusal is only ever raised against a
  choice; the allocation lock and why it is keyed the way it is.

### As built — where it differs from the above

- **There is no allocation lock to describe**, so `CLAUDE.md` records its absence instead —
  and why, since a missing lock beside `lockOwners` looks like an oversight rather than a
  judgement. This bullet was written before decision 2 removed the check-then-assign
  sequence the lock existed to serialise; the plan's own sequencing note says as much two
  screens further down, and this is the one place it was not followed through.
- **Two of the three `../roadmap.md` items were already done.** The decisions table and the the plan schema
  deferrals were written when the plan was, because both were answers this work needed
  *before* building rather than notes to leave after it. What was left is the part that can
  only be written afterwards: the the original tenancy design debt row, and an "As built" saying that this work
  did not do what it was written to do.
- **`CLAUDE.md` gained a section rather than a paragraph.** Steps 1–4 shipped without
  touching it, which was the plan — it is step 5's job — but four steps of decisions is more
  than three bullets can hold: the handle's shape and where it is enforced, the refusal and
  its remedy, the constraint map and the test that pins it, the two frontend rules that are
  easy to get subtly wrong, and where a handle is shown on screen at all.
- **`../roadmap.md`'s "What I would build next" was stale in a way nothing else caught**: it
  still sequenced the team model and chosen handles as work to come. It now says what is left, which is the plan schema's two
  open decisions.

---

## The review at the end, and what it found

Five things, all fixed before the work was called done. Worth recording, because four
of the five are the *same* mistake in different places: a rule that was got right where it
was written down and then not followed through to the one path that reaches it differently.

- **`Slug.withSuffix` could emit a handle its own `PATTERN` refuses.** The cut that makes
  room for the suffix can land on a hyphen, and `-` + `-2` is a doubled hyphen. The
  original `Slug.of` guarded it with a trailing `replaceAll("-+$", "")`, which went when
  derivation moved to the frontend — and the test that covers the cut uses `"a".repeat(80)`,
  which never reaches a hyphen to land on. The forms fill a suggestion in, so this arrived
  as a `pattern` field error against a value the visitor never typed.
- **The handle field promised a suggestion the race never sends.** `SlugTakenException(null)`
  is deliberate — the transaction is lost, so there is nothing left to look up — but the
  field said "we have suggested another" regardless, pointing at an input still holding the
  refused handle. It now takes `suggested` alongside `taken`, decided by
  `useProposedSlug.takeSuggestion`, which is also what applies the alternative: one fact,
  one place. `SettingsPage` adopted the hook to get it, which removed its own copy of the
  same three lines.
- **A taken handle spent the registering address's mail budget.** The claim goes before
  everything is looked up, which was written when `email_already_registered` was the only
  refusal that could reach it — a refusal nobody retries. `slug_taken` is one the product
  *invites* people to retry, and three collisions locked somebody out of registering at all
  for a quarter of an hour, and out of the password reset that shares the budget.
  `MailRateLimiter.refundRecipient` gives that share back; the **source** keeps its claim,
  because a refused registration still cost a lookup and a bcrypt hash.
- **The switcher went on offering an organisation's old name after a rename.** Its effect is
  keyed on the organisation's id, which a rename does not change. Step 3 added renaming and
  step 4 added the handle beside a repeated name without either noticing the other: a stale
  option can be the one thing that told two identically named organisations apart.
- **`CLAUDE.md` said `/app/members` is owner-only.** Step 3 widened a sentence to cover the
  settings page it was adding and swept the members page in with it. Only the *controls*
  there are owner-only; the page is for everybody, which is what `GET /api/members` exists
  for.

Each fix ships with the test that fails without it, and each of those was run against the
unfixed code to prove it does.

---

## Migrations

**None**, and that is worth stating rather than leaving as an absence.

Every slug already in the database is unique and stays valid — `uq_tenants_slug` is unchanged,
the column is unchanged, and no existing row needs a different handle than the one it has.
What changes is only where the *next* handle comes from. A work that alters what a column
means without altering the column is exactly the kind that gets a migration written for it out
of habit; there is nothing here for one to do.

---

## Sequencing and risk

**Step 1 is the whole work**, and it got considerably smaller when the handle became
required: no allocator, no allocation loop, no lock. What is left to get wrong is narrower and
worth naming.

**The sharpest edge is now mapping a constraint violation back to the right refusal.** Both
step 1 and step 2 read an index name out of a `DataIntegrityViolationException` to decide what
happened, which couples that code to names owned by a migration. Rename an index without
following it and a specific, actionable refusal silently degrades to a generic one — the same
shape of failure as the exception-advice scope that broke during the team model step 9, and just as quiet.
**Whatever reads a constraint name needs a test that fails if the name changes**, not just one
that passes today.

The remaining race — two people choosing one handle in the same instant — is deliberately left
to `uq_tenants_slug`. It is worth being clear that this is a judgement about stakes rather
than an oversight: it produces a rare, retryable refusal against something the caller typed,
where the original tenancy design's bug produced a certain refusal against something they could not change.

**Do it before the plan schema.** The roadmap's reason stands and gets stronger with decision 5: nothing
routes by handle today, so a handle that changes costs nothing. The moment the plan schema puts one in a
URL, both deferred questions — reserved handles, and redirects for retired ones — stop being
free to defer. Step 5 is what makes sure they are read at that moment rather than discovered.

**Step 3 is the one to disagree with.** It is the only part that adds a feature, and it brings
a settings screen and an editable name with it. Everything else in this work is the
removal of two refusals and the machinery that made them necessary.
