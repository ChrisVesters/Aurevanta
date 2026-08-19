-- Resources: the first table that says a team is not four interchangeable people.
--
-- Everything the engine has scheduled until now has been bounded by one number, and
-- m11-plan.md measures what that costs: the same plan, the same durations, six slots — read
-- as six interchangeable people it finishes 14% to 59% earlier than as two pools of three
-- with work that cannot cross between them. Pooling is a relaxation, so the error only ever
-- runs one way. **A capacity number is not an approximation of a team, it is a lower bound
-- on when they finish**, and it gets worse exactly as a team specialises.
--
-- One concept and not two. `roadmap.md` describes resources *and* types — people, then
-- environments and licences, then type-level requirements over them — and the measurement
-- needed none of that hierarchy: what moved the answer was work being unable to cross from
-- one pool to another, and nothing in it depended on which individual did what. So a
-- resource is a named pool with a number of units, and every case that section lists is one
-- row here: "Backend engineers x 3", "Staging environment x 1", "Ada x 1". A parallel
-- hierarchy is the cheap modelling choice M1a spent a whole milestone unpicking, in another
-- form.

create table resources (
    id          uuid         not null,
    tenant_id   uuid         not null,
    name        varchar(200) not null,
    -- How many of this thing there are. Whole, because units are whole things: half a
    -- person is an availability question wearing an integer's clothes, and availability is
    -- the next milestone rather than this one (m11-plan.md decision 1).
    units       integer      not null,
    -- Optionally the person this pool is. A convenience for finding them and never a
    -- permission or a report — nothing in this product tells anybody how busy anybody is,
    -- which is M8's "people are named and never ranked" arriving in a second place.
    --
    -- Deliberately no cascade, mirroring estimates.estimator_user_id: removing somebody
    -- from an organisation must not delete the model of the team they were in, and a
    -- resource outliving its person's membership is the point rather than an accident.
    user_id     uuid,
    created_at  timestamp with time zone not null,
    -- Put away rather than deleted, like every other domain row. A pool a forecast was made
    -- against has to stay readable: the run stored the declaration it was scheduled under,
    -- and a name that had vanished would leave that snapshot describing an identifier.
    archived_at timestamp with time zone,
    constraint pk_resources primary key (id),
    constraint fk_resources_tenant foreign key (tenant_id) references tenants (id) on delete cascade,
    constraint fk_resources_user foreign key (user_id) references users (id)
);

-- The name is not unique, for the reason a project's is not: two pools called "Designers"
-- is somebody's business and the id is what addresses one. M1a is the milestone that paid
-- for learning this; nothing here derives anything from a name.

-- Every listing is "the pools of one organisation, the ones in use or the ones put away",
-- so the pair is the index rather than tenant_id alone. It also serves the cascade above.
create index ix_resources_tenant_archived on resources (tenant_id, archived_at);

-- Serves the user foreign key, which has no index of its own from the constraint.
create index ix_resources_user on resources (user_id);
