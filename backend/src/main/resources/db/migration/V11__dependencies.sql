-- Dependencies: the plan stops being a list and becomes a shape.
--
-- This is the table that decides what the simulation engine can ever model. The same ten items forecast at 51
-- or 86 days at the P90 depending only on how they depend on each other, so a schema
-- without this would not be a simpler product — it would be one whose answers are wrong by
-- a margin nobody can see.
--
-- Finish-to-start only, with lag (docs/design/plans-and-estimates.md decision 4). Start-to-start and the other two
-- multiply scheduling complexity for cases most teams never model.

create table dependencies (
    id                  uuid not null,
    tenant_id           uuid not null,
    -- Carried on the row for the same reason work items carry theirs: an edge is read by
    -- project, and the isolation rule is only as good as it is easy to follow.
    project_id          uuid not null,
    predecessor_item_id uuid not null,
    successor_item_id   uuid not null,
    -- How long after the predecessor finishes the successor may begin, in the same hours
    -- everything else here is measured in. Zero is the ordinary case and is not a guess:
    -- it is the absence of a lag rather than an unknown one.
    lag_hours           numeric(12, 2) not null,
    created_at          timestamp with time zone not null,
    constraint pk_dependencies primary key (id),
    constraint fk_dependencies_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    constraint fk_dependencies_project foreign key (project_id) references projects (id) on delete cascade,
    -- An edge describes two items and means nothing without either of them. Items are
    -- never deleted by anything the product offers, so this fires only with a whole
    -- organisation.
    constraint fk_dependencies_predecessor foreign key (predecessor_item_id) references work_items (id) on delete cascade,
    constraint fk_dependencies_successor foreign key (successor_item_id) references work_items (id) on delete cascade
);

-- One edge between any two items. A second would say nothing the first does not, and two
-- rows meaning one thing is how a graph walk comes to count a path twice.
--
-- This is also the index the pre-check reads, and the constraint that holds when two
-- callers get past that check in the same instant — `ApiExceptionHandler` maps it back to
-- the refusal the check would have given.
create unique index uq_dependencies_edge on dependencies (predecessor_item_id, successor_item_id);

-- Every graph walk reads one project's edges at once, which is the whole of the read path.
-- Leading with tenant_id also serves the cascade above.
create index ix_dependencies_tenant_project on dependencies (tenant_id, project_id);

-- Serves the successor foreign key, which the index above does not: removing an item's
-- edges reads them from both ends.
create index ix_dependencies_successor on dependencies (successor_item_id);
