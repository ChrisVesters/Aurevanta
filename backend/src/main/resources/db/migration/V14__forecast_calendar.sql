-- The calendar a run was read under: when work starts, what one person's working day
-- holds, and which rule turns hours into a day.
--
-- Three columns and not five dates. A date costs a division and a walk over some weekends,
-- so it is derived on the way out — the mirror of the six hour figures beside it, which are
-- stored precisely because reproducing a percentile costs ten thousand simulations. Store
-- what is expensive or lossy to reproduce; derive what is cheap and deterministic. What
-- makes it deterministic across time is the rule below, not a stored copy of the answer.
--
-- Copied onto the run rather than read from a setting on the organisation, for the reason
-- capacity already is: somebody edits the working day, every historical date silently
-- moves, and the reporting layer reports a slide that never happened. This is the one table whose whole
-- purpose is to say what was actually assumed.

alter table forecast_runs
    -- The day work begins, stated by the caller. The server's clock does not know what day
    -- it is where they are — an Instant is a moment and a date needs a timezone, and the
    -- one a server would pick is its own. It is also often not today: a plan forecast in
    -- October for a January start should be forecast from January.
    add column starts_on date,
    -- What ONE worker's day holds, never the team's. The scheduler already ran `capacity`
    -- items at a time, so the hours this divides are a completion time with capacity
    -- inside them; dividing by a team's daily total ("four people at six hours each, so a
    -- working day is 24") counts capacity twice and produces a date wrong by exactly the
    -- factor a team is proudest of, with nothing on screen looking amiss.
    --
    -- numeric(4, 2) matching @Digits on the request, the way the percentages in V13 do: a
    -- value that rounded on the way in would leave the run recording an assumption it did
    -- not use, and a day boundary is a step function, so a hundredth is a whole day.
    add column working_hours_per_day numeric(4, 2),
    -- Which calendar turned the two into a date. A name and not a boolean, for the reason
    -- priority_rule is one: two defensible calendars give two different dates from
    -- identical data, so a run made under one must never be silently compared with a run
    -- made under another. `five_day_week` is Monday to Friday with no holidays. When real
    -- availability arrives it is a NEW name rather than an edit to this one, and every run
    -- made before it keeps resolving under the calendar it was actually read with.
    add column calendar_rule varchar(40);

-- All three nullable, and deliberately NOT backfilled — the exact opposite of V13, which
-- is the interesting part of this migration.
--
-- That one wrote zeros and could argue they were true: a run made before the common-cause model really did
-- assume no common cause and no unlisted work, so zero is a record rather than a
-- placeholder. There is nothing true to write here. A run made last week did not assume a
-- six-hour day; it assumed no calendar at all, because it produced no date. A default
-- would invent a claim on behalf of somebody who never made one, in the one table that
-- exists to say what was assumed — and it would then be indistinguishable from a claim
-- somebody did make.
--
-- So a run without these reports its hours, no dates, and says why. That is the same
-- thread as the common-cause model's retired limitation codes: history keeps saying what it actually said.
-- The cost is an optional field on the response and a branch in the frontend, and both are
-- the price of not inventing a record.
