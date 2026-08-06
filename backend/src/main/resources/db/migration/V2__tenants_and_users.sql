-- Tenancy and identity.
--
-- Aurevanta is multi-tenant with a shared schema: every tenant-owned table carries a
-- tenant_id foreign key and the application scopes all queries by it. Registration
-- creates a tenant and its first user, who owns it.

create table tenants (
    id         uuid         not null,
    name       varchar(200) not null,
    slug       varchar(80)  not null,
    created_at timestamp with time zone not null,
    constraint pk_tenants primary key (id),
    constraint uq_tenants_slug unique (slug)
);

create table users (
    id            uuid         not null,
    tenant_id     uuid         not null,
    email         varchar(320) not null,
    password_hash varchar(100) not null,
    display_name  varchar(200) not null,
    role          varchar(20)  not null,
    created_at    timestamp with time zone not null,
    constraint pk_users primary key (id),
    constraint fk_users_tenant foreign key (tenant_id) references tenants (id)
);

-- Login is by email alone, with no tenant selector, so an address identifies exactly one
-- account across the whole installation. Case-insensitive: addresses are compared folded.
create unique index uq_users_email on users (lower(email));

create index ix_users_tenant on users (tenant_id);
