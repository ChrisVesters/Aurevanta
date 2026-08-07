-- Single-use tokens for the links this application emails.
--
-- These are not the access tokens in `security`: those are stateless JWTs the holder
-- presents on every request. A row here backs one emailed link — confirm an address, reset
-- a password — which works once and then never again.
--
-- Only a hash is stored. A token in this table grants account access without a password,
-- so a database leak that handed over the raw values would hand over every pending
-- password reset with them. SHA-256 rather than the password encoder: bcrypt is
-- deliberately slow and salted per row, which would make redemption a table scan, and the
-- reason to be slow — guessing a low-entropy human secret — does not apply to 32 bytes
-- from a cryptographic generator.

create table user_tokens (
    id          uuid        not null,
    user_id     uuid        not null,
    purpose     varchar(40) not null,
    -- SHA-256, hex encoded, so exactly 64 characters.
    token_hash  varchar(64) not null,
    expires_at  timestamp with time zone not null,
    -- Set the moment the token is redeemed; a non-null value is what makes a second
    -- attempt fail.
    consumed_at timestamp with time zone,
    created_at  timestamp with time zone not null,
    constraint pk_user_tokens primary key (id),
    -- Cascading because a token is meaningless without the person it authenticates: there
    -- is no state worth keeping once the account is gone.
    constraint fk_user_tokens_user foreign key (user_id) references users (id) on delete cascade
);

-- Redemption looks a token up by its hash alone, so this index is the whole read path.
-- Unique as well because two rows sharing a hash would make "consumed exactly once"
-- meaningless.
create unique index uq_user_tokens_hash on user_tokens (token_hash);

create index ix_user_tokens_user on user_tokens (user_id);
