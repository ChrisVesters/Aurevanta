-- Whether an address has been proved to belong to the person who registered it.
--
-- Null means unproved, and from here an unproved address cannot sign in: registering with
-- somebody else's address, or with a typo, now produces an account nobody can use rather
-- than a working one.

alter table users
    add column email_verified_at timestamp with time zone;

-- Accounts that already exist were created when no such requirement existed, so they are
-- grandfathered rather than locked out of a product they were legitimately using. Nothing
-- is deployed, so in practice this touches developer databases only — but leaving them
-- unverified would strand them with no way back in until Step 8 builds the screen that
-- asks for a new link.
update users
set email_verified_at = now()
where email_verified_at is null;
