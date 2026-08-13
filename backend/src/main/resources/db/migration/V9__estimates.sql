-- Estimates: the product's actual content, and the one table here that is written once and
-- never rewritten.
--
-- There is no updated_at and there will be no update endpoint. A revision is a new row,
-- because M8 measures how often a person's range contained the truth — which is a question
-- about what they said *at the time*. An UPDATE would answer it with what they think now,
-- and the evidence for the old answer would simply be gone.

create table estimates (
    id                uuid not null,
    tenant_id         uuid not null,
    work_item_id      uuid not null,
    -- The estimator is a user, not a membership. A membership can be deleted — M1 made
    -- sure of that, because removing somebody must not delete their account — and hanging
    -- this off one would destroy years of calibration evidence as a side effect of a
    -- person leaving. M8 calibrates per estimator across everything they ever estimated,
    -- in whichever organisation.
    estimator_user_id uuid not null,
    -- Effort in hours, never duration: duration is effort divided by what is assigned to
    -- it, and that division is M11's. Two decimal places, matched by @Digits on the
    -- request so nothing is silently rounded on the way in — an estimate that arrived as
    -- 0.005 and landed as 0.00 would break the "greater than zero" rule after the check
    -- that enforces it.
    p10_hours         numeric(12, 2) not null,
    p50_hours         numeric(12, 2) not null,
    p90_hours         numeric(12, 2) not null,
    created_at        timestamp with time zone not null,
    constraint pk_estimates primary key (id),
    constraint fk_estimates_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    -- Cascades because an estimate is *of* an item; without the item there is nothing it
    -- describes. Nothing in the product deletes an item — they archive — so this fires
    -- only when a whole organisation goes.
    constraint fk_estimates_work_item foreign key (work_item_id) references work_items (id) on delete cascade,
    -- Deliberately no cascade: an estimate outliving its estimator's membership is the
    -- point, and the account itself is never deleted by anything this product offers.
    constraint fk_estimates_estimator foreign key (estimator_user_id) references users (id)
);

-- The current estimate is the newest row per (item, estimator), which is exactly what this
-- index answers: every read here either asks for one item's estimators or walks a project's
-- items. Descending on created_at so "the latest" is the first row rather than a sort.
create index ix_estimates_item_estimator_created
    on estimates (work_item_id, estimator_user_id, created_at desc);

-- Coverage counts distinct items with an estimate, per organisation, which reads by tenant
-- and joins back to work_items. This also serves the tenant cascade above.
create index ix_estimates_tenant on estimates (tenant_id);

-- Serves the estimator foreign key, which has no index of its own from the constraint —
-- and M8 will read exactly this way: everything one person ever estimated.
create index ix_estimates_estimator on estimates (estimator_user_id);
