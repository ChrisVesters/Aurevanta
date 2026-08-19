-- The two assumptions the common-cause model added to the model, stored beside the answer they produced.
--
-- Real columns rather than fields in the `inputs` document, for the reason capacity and
-- priority_rule already have them: the reporting layer compares these across the runs of one plan, and a
-- comparison is a query. Two forecasts of the same plan made under different assumptions
-- are not a movement, and a detector that could not see the difference would report one.
--
-- numeric(6, 2) in percent, matching @Digits on the request: a value that rounded on the
-- way in would leave the run recording an assumption it did not use, and a replay would
-- then disagree with the numbers stored beside it.

alter table forecast_runs
    -- "In a bad stretch, how much longer does everything take?" — read as the P90 of one
    -- log-normal multiplier drawn per run and applied to every remaining duration.
    add column team_factor_worse_by_percent numeric(6, 2) not null default 0,
    -- "How much does a plan like this usually grow?" — the two ends of the range the item
    -- count is multiplied by, sampled per run.
    add column scope_growth_p10_percent numeric(6, 2) not null default 0,
    add column scope_growth_p90_percent numeric(6, 2) not null default 0;

-- The backfill above is true rather than convenient, which is the whole of what makes
-- decision 9 work: every run made before this migration had no team factor and no scope
-- growth, so zero is a correct record of what those runs assumed rather than a placeholder
-- for a value nobody stored. Version 1 of the engine *is* version 2 with these three at
-- zero, draw for draw, so those rows stay replayable rather than becoming history that can
-- only be read.
--
-- And then the default goes, which is the point of doing it in two statements. A default
-- left in place would let a future insert omit an assumption and be handed one by the
-- database — exactly the "a server that picked would be making a claim about a team it has
-- never met" this work refuses everywhere else. Existing rows get a true zero; nothing
-- after this may leave the question unanswered.
alter table forecast_runs
    alter column team_factor_worse_by_percent drop default,
    alter column scope_growth_p10_percent drop default,
    alter column scope_growth_p90_percent drop default;
