-- Progress: what has already happened, so a forecast can exclude it rather than predict it
-- again.
--
-- Without this there is no mid-project re-forecast, no burn-up (the reporting layer) and no calibration
-- (calibration) — the last of which needs the one column here nobody is obliged to fill in.
--
-- Dependencies were to share this migration; they have one of their own, so that each step
-- is a commit that stands up by itself.

alter table work_items
    -- Defaulted rather than backfilled: work that nobody has said anything about has not
    -- started, and a column that can never be null and always begins in one state is what
    -- a default is for.
    add column status varchar(20) not null default 'NOT_STARTED',
    -- Dates, not timestamps, and this is the one place in the schema where that is true.
    -- Everything else here records a moment the *server* observed — a row written, a token
    -- spent — and this records a day a *person* reports: "we finished it on the twelfth".
    -- There is no instant in that claim to be faithful to, and storing one invents a time
    -- of day that then reads back as the eleventh for anybody west of the meridian.
    add column started_on date,
    add column completed_on date,
    -- What it actually took, in the same hours an estimate is given in — the two are
    -- compared directly, and calibration's whole question is how often the band contained this.
    --
    -- Optional even when the work is done, deliberately. Most teams do not track it, and
    -- refusing to let somebody mark an item finished because they cannot say how long it
    -- took would be refusing the common case in order to serve a feature that is years
    -- away.
    add column actual_effort_hours numeric(12, 2);

-- Existing rows have no history to invent, and every one of them predates anybody being
-- able to record any: the default above is the whole of the backfill.
