-- The team a run was scheduled against, copied onto it the way the calendar and the five
-- assumptions already are.
--
-- **It is in the snapshot already, and that is not enough.** `forecast_runs.inputs` holds the
-- whole declaration, so nothing is lost — but reading it costs parsing a document that also
-- holds five hundred items and every range anybody typed, and two readers need this on every
-- run of a plan at once: the panel that prints what a forecast assumed, and M10's detector,
-- which walks a whole history to decide whether a date has been drifting. A column is what
-- makes both of those a read rather than a page of JSON per row.
--
-- **The second reason is the one that decides it.** `Comparison` is built from the columns of
-- this table, and until now it could see the *size* of a team — capacity is derived from the
-- pools — and not its *shape*. Three backend and three frontend becoming two and four is the
-- same capacity and a different question, and the date it moves would have been read as a
-- plan sliding, which is `roadmap.md`'s slide that never happened arriving through the one
-- feature written to prevent it.

alter table forecast_runs
    -- The pools and their units, in declaration order, as the snapshot's own mapper writes
    -- them. Order is part of the model rather than of a listing: work that names no resource
    -- takes one unit of the first pool with one free.
    --
    -- **Null on every run made before there was a team to describe, and nothing is
    -- backfilled.** This is V14's decision and not V13's: a run made in July did not assume
    -- an empty team, it assumed no such concept — and writing `[]` would put a claim into the
    -- one table whose whole purpose is to record what was actually assumed. A run made today
    -- by an organisation that has declared nothing stores `[]`, which is a different fact and
    -- a true one.
    add column resourcing text;
