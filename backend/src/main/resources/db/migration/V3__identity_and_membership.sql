-- Identity split from membership.
--
-- V2 put tenant_id and role on users, which made an account and a membership the same
-- thing: one address could never belong to a second organisation, so anyone consulting
-- for two clients hit a wall. Credentials stay on users — one person, one password,
-- however many organisations they belong to — and the role moves to a membership row per
-- organisation, so the same person can own one and merely belong to another.

create table memberships (
    id               uuid        not null,
    user_id          uuid        not null,
    tenant_id        uuid        not null,
    role             varchar(20) not null,
    created_at       timestamp with time zone not null,
    -- When this membership was last chosen, so sign-in can offer the organisation the
    -- person was most recently working in. Null until they first select it.
    last_accessed_at timestamp with time zone,
    constraint pk_memberships primary key (id),
    constraint fk_memberships_user foreign key (user_id) references users (id),
    constraint fk_memberships_tenant foreign key (tenant_id) references tenants (id),
    -- One membership per person per organisation; also the index that serves every
    -- lookup by user_id, so no separate index on that column is needed.
    constraint uq_memberships_user_tenant unique (user_id, tenant_id)
);

create index ix_memberships_tenant on memberships (tenant_id);

-- Every account written under V2 already was a membership, so carry it across rather than
-- leaving existing users belonging to nothing.
insert into memberships (id, user_id, tenant_id, role, created_at, last_accessed_at)
select gen_random_uuid(), u.id, u.tenant_id, u.role, u.created_at, null
from users u;

drop index ix_users_tenant;

-- uq_users_email on lower(email) deliberately stays: an address now identifies a person
-- across the whole installation, which is exactly what lets one identity span
-- organisations.
alter table users
    drop column tenant_id,
    drop column role;
