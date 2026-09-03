# A page for one task — the design record

> **Scope.** Every way of changing a piece of work moves off the plan's work list and onto
> a page addressed at `/app/items/{id}`. Explicitly excluded: any change to what those five
> forms *ask*, to the elicitation order, or to what the server accepts. This work moves
> where the forms live and adds nothing to what they do.
>
> **How to read this.** Decisions first, then what was built. The build stays green
> throughout: `./mvnw test` (format gate, 100% branch coverage) and
> `npm run lint && npm run build && npm run test`.

---

## The problem

A plan holds up to five hundred items. Every row of the work list carried five buttons —
estimate, progress, needs, order, edit — plus archive, and each of them opened its form
*inside the row*. Three consequences, in increasing order of how much they matter:

1. **The list stopped being readable as a list.** Six controls per row, repeated five
   hundred times, is most of the screen; what the plan actually *says* — what the work is,
   what it is expected to take, where each piece sits — was the minority of the pixels.
2. **Each form had a list item's width to say itself in.** The estimate form asks one
   question at a time and shows a review; the ordering panel renders both directions of
   every arrow and stays open; the progress form warns before discarding. All of them were
   competing with a row.
3. **A colleague's numbers could not be shown at all.** A row has one line for an estimate,
   so what it could say about somebody else's range was *that there is one*. Two people who
   disagree about a task are saying something, and it was invisible.

None of that is a defect anything would fail on, which is why it lasted.

---

## Decisions

| # | Question | Decision |
|---|---|---|
| 1 | What stays on the list? | **Adding and archiving, and nothing else.** The five forms all move. |
| 2 | What address does a task have? | **`/app/items/{id}`** — no plan in it. |
| 3 | How is the page reached? | **The row's title is the link.** No "Open" button. |
| 4 | Five forms open at once, or one at a time? | **One at a time, each in place of the summary it changes.** |
| 5 | Does the page show every estimator's numbers? | **Yes — and they leave the screen the moment somebody starts answering.** |
| 6 | What does a put-away task offer? | **Rewording and bringing it back, and nothing a forecast reads.** |

### Decision 1 — The forms move, they are not copied

The alternative was to add the page and leave the row forms alone, which is cheaper and
would have broken no test. It is also the thing this codebase argues against everywhere
else: two ways to do one thing become one that is told about a new field and one that is
not, and the one that is not looks entirely reasonable. The progress form is the clearest
case — it holds the rule about which boxes a status has room for, and a second copy of that
rule is a second chance to invite somebody to type something the server will refuse.

What stays on the list is what is about *the list*: writing a new task down, and putting one
away. Archiving is the action somebody performs while reading down a plan; estimating is not.

### Decision 2 — One identifier in the address, and it is the item's

`WorkItemController` already decided this for the API: an item is created and listed *within*
a plan, and once it exists it is addressed on its own at `/api/items/{id}`, because a path
repeating the project would be a second identifier the server has to check against the first
— and a disagreement between them is a refusal nobody could act on. The same argument holds
in the browser, so the route is `/app/items/{id}` and the plan comes back **on the row**,
which is what the back link is built from.

The cost is real and is paid deliberately: the page cannot ask about the plan until the task
has answered, so the read happens in **two waves**. That buys the one part of this page that
needs the plan at all — what else there is to come before, and what those things are called.
Everything else is either about the item (`/api/items/{id}/requirements`,
`/api/items/{id}/progress`) or about the organisation (`/api/resources`).

### Decision 3 — The title is the link

A row is about one task and its name is what somebody is scanning for. A separate control
would be a column of five hundred identical labels, each needing an `aria-label` to say
which task it opened — which is exactly the accessibility patch the four `openNamed`
catalogue entries were. Those entries are **deleted** rather than left unused: the test
setup fails any render with no translation, so a deleted key is what stops one coming back.

### Decision 4 — One form at a time, in place of what it changes

Five forms permanently on screen is the list's problem moved to a smaller page. So each part
of the task — its estimate, its progress, what it needs, where it sits — shows what the task
carries today and one control that opens the form **in place of that summary**. That is the
same rule the rows followed, and it is what *Forms that are asked for* already decided: a
form about something on screen opens in place; the four dialogs are for adding a new thing
to a list.

Opening one clears whatever the last one was told, so a complaint about a date cannot be the
first thing on a form about hours.

### Decision 5 — Every range is shown, and hidden while somebody answers

This is the one thing a page can do that a row could not, and it is also the one thing this
work could easily have got wrong. **A colleague's numbers must never be on screen while
somebody is answering the three questions.** Two people who anchored on each other are not
two estimates, and the band they produce is confidently narrow for a reason nothing
downstream can see — which is precisely what multi-estimator support exists to make visible.

So the estimate part renders *either* every current range *or* the form, never both. The
reader's own goes first, whatever order the server listed them in, because somebody comes
here to revise theirs and it must not have to be found among a column of colleagues.
`WorkItemPage.test.tsx` asserts the ranges leave the screen at every one of the three
questions; that case is what fails if they are ever put side by side.

### Decision 6 — A put-away task offers nothing a forecast reads

Archived work is out of the plan and out of every forecast, and the estimates endpoint says
nothing about it. The list already hid four buttons on the archived listing; the page states
that rule **once** rather than four times, and what remains is rewording the task and
bringing it back. It also says why, rather than showing four controls that would quietly
achieve nothing.

---

## What was built

**Backend — one endpoint.** `GET /api/items/{itemId}`, the missing member of a family that
already had create, list, reword, report, archive and unarchive. Nothing needed to read
exactly one item until a task had a page of its own. It answers for an archived item exactly
as for a live one: the *listing* splits the two because a screen showing a plan has to show
one or the other, but a caller holding an identifier is asking about that item, and refusing
would make the page a link that stops working the moment somebody tidies up.

**Frontend — `routes/WorkItemPage.tsx`**, holding the five forms, the progress history the
progress form already read, and the three summaries the row used to carry. `WorkItems.tsx`
lost about a third of its lines and every one of its five `useFormFailure` hooks bar the one
belonging to the add dialog.

**`projects/work.ts` gained three functions.** `describeProgress`, `byEnd` and
`describeShape` are said by both screens now, so they are stated once — beside `describeWork`,
which is there for the same reason. The rule is the one that file already carried: a fact
about a piece of work that two screens state is a fact that will eventually be stated
differently in two places.

### As built — where it differs from the above

- **The plan is read for its name as well as its work.** Decision 2 says the second wave
  exists for the ordering panel; it also fetches `/api/projects/{id}`, so the back link can
  say *← Q3 platform work* rather than *← Back*. Somebody arriving from a link has not been
  anywhere to go back to, and the plan is what they want next either way.
- **Two guards, not one, for a read that arrives late.** The two waves are two places an
  answer can land after the page has gone, and a cancellation check on only the second would
  let the first go on to ask a plan about a task nobody is looking at. Both are asserted, and
  the first case only exercises the branch it names because the fetch double holds its
  pending resolvers **by URL** — an earlier version kept one variable, silently resolved the
  last request rather than the first, and left that branch uncovered while appearing to test
  it. The coverage gate is what found it.
- **The estimates read stays plan-wide.** A per-item `GET /api/items/{id}/estimates` was
  considered and declined: the page already holds the plan for the ordering panel, and a
  second endpoint answering a subset of an existing one is a second way to read one thing.
  The page filters to the item it is about, which is the one line that would otherwise have
  been a whole endpoint.
- **The list's estimate line still names colleagues rather than quoting them.** It was
  tempting to put the numbers on the row too now that they exist in the catalogue. A row has
  one line and a plan has five hundred rows; naming is what fits, and the page is where the
  numbers belong.
