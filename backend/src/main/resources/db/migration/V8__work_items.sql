-- Work items: the unit that carries an estimate.
--
-- A task rather than a story or an epic (docs/design/plans-and-estimates.md decision 1). Coarser units hide scope
-- growth inside the estimate, which the simulation engine's scope-uncertainty model would then count twice —
-- once in the range somebody widened to allow for the unknown, and again in the multiplier
-- that exists to model it.
--
-- Estimates arrive in V9 and hang off this row; progress and dependencies in V10. Nothing
-- here carries a number yet.

create table work_items (
    id          uuid          not null,
    -- Carried on the row rather than reached through the project, and this is the one
    -- piece of deliberate denormalisation in the schema. Isolation is enforced in
    -- application code, so it has to be *easy* to write a correctly scoped query and
    -- awkward to write a wrong one; a join away is far enough to forget.
    tenant_id   uuid          not null,
    project_id  uuid          not null,
    title       varchar(200)  not null,
    description varchar(2000),
    created_at  timestamp with time zone not null,
    -- Archive, never delete, exactly as projects do — and here the reason is sharper: an
    -- estimate is evidence calibration calibrates against, and deleting the item it hangs on would
    -- destroy that evidence years before the feature that reads it exists.
    archived_at timestamp with time zone,
    constraint pk_work_items primary key (id),
    constraint fk_work_items_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    -- An item belongs to one plan and has no meaning outside it.
    constraint fk_work_items_project foreign key (project_id) references projects (id) on delete cascade
);

-- Every listing is "the items of one project, in one state", read by a caller whose tenant
-- is already known — so the three together are the index rather than any one of them.
-- Leading with tenant_id also serves the cascade above, which is the only route by which a
-- row here is ever deleted: projects archive rather than disappear, so nothing needs an
-- index on project_id alone.
create index ix_work_items_tenant_project_archived on work_items (tenant_id, project_id, archived_at);
