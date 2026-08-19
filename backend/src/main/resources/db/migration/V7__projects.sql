-- Projects: the first table that carries domain data rather than the machinery for
-- getting at it. A named container an organisation puts a plan in.
--
-- The name is deliberately not unique, and this time that is a decision rather than the
-- accident chosen handles spent a piece of work undoing. Two projects called "Q3 platform work" in one
-- organisation is ordinary — a team runs the same shape of work every quarter — and the id
-- is what addresses one. Nothing is derived from the name and nothing routes by it.

create table projects (
    id          uuid          not null,
    tenant_id   uuid          not null,
    name        varchar(200)  not null,
    -- Optional, and nullable rather than defaulted to an empty string: a project with
    -- nothing said about it and one described as "" are the same thing, and having two
    -- ways to spell it is how a query comes to miss half of them.
    description varchar(2000),
    created_at  timestamp with time zone not null,
    -- When it was put away, and null while it is not. The plan schema has no hard delete at all:
    -- every member may write plan data (decision 6), so a delete would be one person
    -- destroying a colleague's work with nothing to put back — and an estimate is
    -- evidence calibration calibrates against years after anyone would think to keep it.
    archived_at timestamp with time zone,
    constraint pk_projects primary key (id),
    -- There is nothing to plan for an organisation that is gone, and everything below
    -- this row hangs off it.
    constraint fk_projects_tenant foreign key (tenant_id) references tenants (id) on delete cascade
);

-- Every listing is "the projects of one organisation, the ones put away or the ones not",
-- so the pair is the index rather than tenant_id alone. It also serves the cascade above.
create index ix_projects_tenant_archived on projects (tenant_id, archived_at);
