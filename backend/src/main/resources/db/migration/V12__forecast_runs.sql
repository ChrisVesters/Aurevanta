-- Forecast runs: every answer this product has ever given, kept.
--
-- docs/design/plans-and-estimates.md deliberately did *not* build this table, on the grounds that its columns are
-- the engine's inputs and outputs and designing them before the engine existed would have
-- been guessing. The obligation it handed over was that the simulation engine must persist every run from its
-- first commit rather than adding persistence once the engine worked — because this history
-- cannot be reconstructed later, and the drift detector and the movement
-- decomposition both read it.
--
-- Written once and never updated, like estimates and for the same reason: a run is the
-- record of what was said on a date, and a forecast that could be edited afterwards would
-- answer "has this date been slipping" with whatever somebody most recently believed.

create table forecast_runs (
    id                   uuid not null,
    tenant_id            uuid not null,
    project_id           uuid not null,
    -- The person who asked, not their membership — the same reasoning estimates use. They
    -- may have left the organisation by the time anybody reads this, and they still ran it.
    requested_by_user_id uuid not null,
    created_at           timestamp with time zone not null,

    -- The assumptions. Every one of these moves the answer, so every one of them is stored
    -- beside it: a number without the assumptions that produced it is not a forecast, it is
    -- a rumour.
    --
    -- The seed is what makes a stored run reproducible. Together with the inputs below and
    -- the engine version, it means anything this run knew and did not store can still be
    -- recomputed exactly — which is why there is no need to hoard ten thousand sampled
    -- vectors against a feature that might want them.
    seed                 bigint not null,
    sample_count         integer not null,
    -- How many items could be under way at once. There is no default anywhere in the
    -- product: it moved the same ten items from 51 to 86 days at the P90 in the measurement
    -- roadmap.md carries, so somebody has to say it.
    capacity             integer not null,
    -- Which ready item the scheduler picks up first. Two defensible rules give two
    -- different forecasts from identical data, so a run made under one must never be
    -- silently compared with a run made under another.
    priority_rule        varchar(40) not null,
    -- Bumped whenever the model changes. Without it a replay would reproduce today's model
    -- rather than the one that answered, and would agree with the stored numbers only by
    -- luck.
    engine_version       integer not null,

    -- Coverage as it was at the moment of the run, not as it is now. docs/design/plans-and-estimates.md decision 5:
    -- a forecast covers the work that carries an estimate and says how much it left out.
    item_count           integer not null,
    estimated_item_count integer not null,

    -- numeric(14, 2) in hours: a hundredth of an hour is thirty-six seconds, which is finer
    -- than any forecast means anything to. Real columns rather than buried in the document
    -- below, because the reporting layer compares these across runs and a comparison is a query.
    mean_hours           numeric(14, 2) not null,
    p10_hours            numeric(14, 2) not null,
    p50_hours            numeric(14, 2) not null,
    p80_hours            numeric(14, 2) not null,
    p90_hours            numeric(14, 2) not null,
    p95_hours            numeric(14, 2) not null,

    -- The resolved inputs, as a value rather than as rows. Live rows will not do: items get
    -- reworded, edges get rubbed out and progress changes daily, so a run that referenced
    -- them would describe something that no longer exists within a week. The reporting work's movement
    -- decomposition — "out 8 days: +5 new scope, +4 re-estimates, -1 progress" — is a diff
    -- of two of these, and is impossible against rows that moved.
    --
    -- Not normalised into child tables, because nobody queries into it: it is read whole by
    -- whatever replays or diffs it, and a second schema to migrate in lockstep with the
    -- first would be a cost with no reader.
    inputs               jsonb not null,
    -- The shape of the answer, and what the model did not do (docs/design/simulation-engine.md decision 12).
    outputs              jsonb not null,

    constraint pk_forecast_runs primary key (id),
    constraint fk_forecast_runs_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    constraint fk_forecast_runs_project foreign key (project_id) references projects (id) on delete cascade,
    -- Deliberately no cascade: a run outliving the person who asked for it is the point.
    constraint fk_forecast_runs_requester foreign key (requested_by_user_id) references users (id)
);

-- Every read is "this plan's runs, newest first" — the history screen, and the drift detector
-- comparing each run with the one before it.
create index ix_forecast_runs_tenant_project_created
    on forecast_runs (tenant_id, project_id, created_at desc);

-- Serves the requester foreign key, which the constraint does not index on its own.
create index ix_forecast_runs_requester on forecast_runs (requested_by_user_id);

-- No unique index and nothing to conflict with. Two identical forecasts of one plan are two
-- forecasts rather than a duplicate: the whole point of the table is that somebody asked
-- twice, and what changed in between.
