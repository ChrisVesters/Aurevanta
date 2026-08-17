-- How the three numbers in each row were asked for.
--
-- This is the only instrument that can ever say whether M5 worked. The milestone's claim is
-- that changing the *question* produces honester ranges, and the failure mode it has to
-- live with is that a better form feels better and changes nothing. Nothing in the test
-- suite can settle that: the only evidence is M8's calibration record — how often somebody's
-- P10–P90 band actually contained the truth — partitioned by how each estimate was
-- collected. Without this column that partition does not exist.
--
-- The alternative was `created_at` against the date this shipped, which lives nowhere in
-- the database and would make the product's own headline claim rest on somebody remembering
-- a deploy. That is the reconstruction these documents exist to avoid.
--
-- varchar(40) holding a name, like priority_rule and calendar_rule, and for the same reason
-- an enum is not used: a stored value the code has never heard of should be readable and
-- unrecognised rather than unreadable.

alter table estimates
    add column elicitation_method varchar(40) not null default 'three_point';

-- The backfill is TRUE, which is what makes this V13 and not V14.
--
-- M4's calendar columns were left null because a run made before M4 assumed no working day
-- — there was nothing honest to write, and a default would have invented a claim on behalf
-- of somebody who never made one. Here there is something honest to write: every estimate in
-- this table was typed into three boxes labelled P10, P50 and P90, because that is the only
-- form this product has ever had. `three_point` records what happened.
--
-- And then the default goes, which is the point of doing it in two statements. A default
-- left in place would let a future insert omit the method and be handed one by the database
-- — and the row would then claim to have been collected a way nobody said it was, in the one
-- column whose whole purpose is to say how it was. Existing rows get a true value; nothing
-- after this may leave the question unanswered.
alter table estimates
    alter column elicitation_method drop default;
