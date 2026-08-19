-- What a piece of work needs before it can be under way.
--
-- Units of any number of pools, held for the whole of its duration — which is the ordinary
-- resource-constrained scheduling model, and follows the non-preemption `Schedule` already
-- commits to: once something starts it runs to completion, so there is no moment at which
-- half of what it holds could be given back.
--
-- **Occupancy is not speed.** Two units means the item ties up two, not that it goes twice
-- as fast. `roadmap.md` says that with an allocation "the schema's stored effort finally converts to
-- duration honestly", and docs/design/resources-and-people.md decision 5 disagrees: an estimate is what somebody
-- said the *task* would take and already implies whoever does it, effort divided by
-- headcount is linear speed-up with no communication cost, and — the reason that decides it
-- — there is no oracle. Every modelling decision in the simulation engine is checkable against arithmetic that
-- exists outside this codebase, and "two people finish this in 60% of the time" is checkable
-- against nothing.
--
-- An item with no row here is not unconstrained: it takes one unit of whichever pool has
-- one free, in declaration order (decision 6). That is what an unannotated item *is* —
-- generic work anybody can pick up — and it is what makes a plan with one pool and no
-- requirements identical to today's capacity model, which is the whole of the version-bump
-- containment this work rests on.

create table requirements (
    id           uuid    not null,
    -- Carried on the row the way work items and dependencies carry theirs. Isolation is
    -- enforced in application code, and that rule is only as good as it is easy to follow.
    tenant_id    uuid    not null,
    work_item_id uuid    not null,
    resource_id  uuid    not null,
    units        integer not null,
    created_at   timestamp with time zone not null,
    constraint pk_requirements primary key (id),
    constraint fk_requirements_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    -- A requirement describes an item and means nothing without it. Items are never deleted
    -- by anything the product offers, so this fires only with a whole organisation.
    constraint fk_requirements_item foreign key (work_item_id) references work_items (id) on delete cascade,
    -- The same for the pool, which archives rather than being deleted.
    constraint fk_requirements_resource foreign key (resource_id) references resources (id) on delete cascade
);

-- One row per item and pool. Two would be two spellings of one number, and a scheduler
-- adding them up would be reading a data-entry mistake as a claim about a team.
--
-- This is also the constraint that holds when two callers replace one item's requirements in
-- the same instant — `ApiExceptionHandler` maps it back to the refusal the pre-check gives,
-- so a caller cannot tell a race from an ordinary duplicate. It serves the read path too:
-- every requirement of one item is a prefix scan of it.
create unique index uq_requirements_item_resource on requirements (work_item_id, resource_id);

-- A screen shows a whole plan's requirements at once, which is the estimates rule: asking
-- per item would be five hundred requests to draw one page. The join is through the item, so
-- what this index serves is the tenant scope and the cascade above.
create index ix_requirements_tenant on requirements (tenant_id);

-- Serves the resource foreign key, and answers "is anything still asking for this pool".
create index ix_requirements_resource on requirements (resource_id);
