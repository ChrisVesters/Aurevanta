-- Invitations: the second way into an organisation, alongside registering one.
--
-- Deliberately not a row in user_tokens. That table's user_id is not null, and the whole
-- point of an invitation is that it goes to somebody who may hold no account yet — so it
-- carries a token_hash of its own. What it shares with user_tokens is how the token is
-- made and stored, which `LinkTokens` states once: 32 bytes from SecureRandom, base64url,
-- written down only as a hex SHA-256. A leaked backup must not be a list of working ways
-- into other people's organisations.

create table invitations (
    id          uuid         not null,
    tenant_id   uuid         not null,
    email       varchar(320) not null,
    -- The standing the invitee will hold once they accept, fixed when the invitation is
    -- sent so that what somebody agreed to join as cannot be changed under them.
    role        varchar(20)  not null,
    invited_by  uuid         not null,
    -- SHA-256, hex encoded, so exactly 64 characters.
    token_hash  varchar(64)  not null,
    status      varchar(20)  not null,
    expires_at  timestamp with time zone not null,
    created_at  timestamp with time zone not null,
    accepted_at timestamp with time zone,
    constraint pk_invitations primary key (id),
    -- An invitation is an offer to join one organisation; there is nothing to join once
    -- that organisation is gone.
    constraint fk_invitations_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    -- Cascading for the same reason: an invitation names the person who sent it, so the
    -- recipient can tell whether they were expecting one. An invitation outliving that
    -- account would have nothing to show.
    constraint fk_invitations_invited_by foreign key (invited_by) references users (id) on delete cascade
);

-- One live invitation per address per organisation. Partial, because the constraint is
-- about what is outstanding: an address that has already accepted, or whose invitation was
-- revoked, can be invited again, and without the `where` clause the first invitation ever
-- sent would block every later one for ever.
--
-- Matched on lower(email) to agree with uq_users_email: two invitations differing only in
-- case would reach one inbox, and the index has to see that.
create unique index uq_invitations_pending on invitations (tenant_id, lower(email))
    where status = 'PENDING';

-- Redemption looks an invitation up by its hash alone, so this index is the whole read
-- path. Unique as well, because two rows sharing a hash would make one link mean two
-- different things.
create unique index uq_invitations_hash on invitations (token_hash);

-- No plain index on tenant_id: every query that reads by organisation reads the pending
-- ones, which uq_invitations_pending already serves. This one exists for the cascade
-- above, which would otherwise scan the table each time an account is deleted.
create index ix_invitations_invited_by on invitations (invited_by);
