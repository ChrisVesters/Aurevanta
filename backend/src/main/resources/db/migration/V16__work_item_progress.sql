-- Progress stops being written over: who said what about a piece of work, and when.
--
-- V10 added four columns to work_items and every report since has written straight over
-- the last one. There is no updated_at on that table and nothing anywhere records who
-- reported it, so a progress claim is the third kind of evidence in this schema and the
-- only one that is not evidence: an estimate is immutable and names an estimator, a
-- forecast run is immutable and names a requester, and this was neither. That asymmetry
-- was never a decision anybody took — docs/design/plans-and-estimates.md argues at length for the other two and
-- this simply never came up.
--
-- The concrete cost is calibration's own exclusion rule. An estimate written after the work began
-- is a report by somebody who could already see how the task was going, and counting it
-- flatters the one number in this product whose entire value is that it is unflattering.
-- That rule compares estimates.created_at against started_on — and started_on can be moved
-- afterwards by anybody, with no trace that it ever said something else. So the hit rate,
-- as things stood, could be improved by editing the date it is measured against.
--
-- This is a log *beside* the four columns and not instead of them. The item keeps its
-- latest state for the screen and the scheduler; this holds every claim ever made about
-- it. That is the estimates pattern exactly, and it is the reason an estimate costs
-- nothing to reason about.

create table work_item_progress (
    id                  uuid not null,
    -- Denormalised the way work_items and estimates carry theirs. Isolation is enforced in
    -- application code, and the rule is only as good as it is easy to follow: calibration reads this
    -- table for a whole organisation at once and must be able to scope that read without
    -- reaching through anything.
    tenant_id           uuid not null,
    work_item_id        uuid not null,
    -- Who made the claim, which is the half of this that no column on work_items has ever
    -- held. A user rather than a membership, following estimates.estimator_user_id: a
    -- membership is deletable — the team model made sure of it, because removing somebody must not
    -- delete their account — and a report is a thing a person said, which outlives their
    -- standing in the organisation they said it in.
    reported_by_user_id uuid not null,
    -- When the server heard it, and so a moment rather than a day. This is not the same
    -- kind of thing as the two dates below and the distinction is V10's: those are days a
    -- person reports, and this is a moment the server observed.
    reported_at         timestamp with time zone not null,
    -- The claim itself, exactly as it was written to work_items in the same transaction.
    -- Nullable in the same places, because a report of NOT_STARTED carries nothing and one
    -- of DONE need carry no start.
    status              varchar(20) not null,
    started_on          date,
    completed_on        date,
    actual_effort_hours numeric(12, 2),
    constraint pk_work_item_progress primary key (id),
    constraint fk_work_item_progress_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    -- Cascades because a report is *of* an item; without the item there is nothing it
    -- describes. Nothing in the product deletes an item — they archive — so this fires only
    -- when a whole organisation goes.
    constraint fk_work_item_progress_item foreign key (work_item_id) references work_items (id) on delete cascade,
    -- Deliberately no cascade, mirroring estimates.estimator_user_id: a report outliving
    -- its reporter's membership is the point, and the account itself is never deleted by
    -- anything this product offers.
    constraint fk_work_item_progress_reporter foreign key (reported_by_user_id) references users (id)
);

-- No unique constraint, and none is missing. Two identical reports are two people saying
-- so, or one person saying so twice, and both are true records of a claim being made —
-- deduplicating them would be this table deciding which claims were worth keeping, which is
-- the behaviour it exists to replace.

-- Both readers walk one item's reports in the order they arrived: calibration takes the earliest
-- start ever claimed, and the progress form shows the latest line.
create index ix_work_item_progress_item_reported on work_item_progress (work_item_id, reported_at);

-- calibration reads a whole organisation's reports at once, and this also serves the tenant cascade.
create index ix_work_item_progress_tenant on work_item_progress (tenant_id);

-- Serves the reporter foreign key, which has no index of its own from the constraint.
create index ix_work_item_progress_reporter on work_item_progress (reported_by_user_id);

-- NOTHING IS BACKFILLED, and that is a decision rather than an omission.
--
-- This follows V14 and not V13. V13 wrote zeros into the forecast assumption columns and
-- could argue they were true — a run made before the common-cause model really did assume no team factor. V14
-- deliberately wrote nothing, because a run made before the calendar did not assume a six-hour day;
-- it assumed no calendar at all, and a default would have invented a claim on behalf of
-- somebody who never made one.
--
-- This is V14's case and more so. A work item already holding started_on = 2026-07-02 has
-- no reporter, no report instant and no history: three of the six columns here would have
-- to be guessed, in the one table whose entire purpose is to say who said what and when.
-- Inventing a report is worse than having none, because a fabricated row is indistinguishable
-- from a real one to every reader downstream — and corrupted evidence still looks like data.
--
-- What that costs is bounded and is handled where it is read: an item with no report in this
-- table falls back to the column on work_items, which is the only claim that exists for it.
-- WorkItemProgressMigrationTests asserts this table comes out empty, because "we deliberately
-- wrote nothing" and "the backfill silently missed every row" are otherwise the same thing.
