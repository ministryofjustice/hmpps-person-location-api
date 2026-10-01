create table if not exists migration_system_audit
(
    id          uuid        not null,
    created_at  timestamp   not null,
    created_by  varchar(64) not null,
    modified_at timestamp,
    modified_by varchar(64),
    constraint pk_migration_system_audit primary key (id)
)
;

drop table if exists custodial_episode_audit;
drop table if exists prison_stay_audit;
drop table if exists external_journey_audit;
drop table if exists external_movement_audit;
drop table if exists external_movement;

create table if not exists external_movement
(
    id                 uuid                   not null default uuidv7(),
    version            int                    not null,
    person_identifier  varchar(7)             not null,
    series_id          uuid                   not null,
    journey_type       external_journey_type  not null,
    movement_type      external_movement_type not null,
    reason             jsonb                  not null,
    occurred_at        timestamp              not null,
    origin             jsonb                  not null,
    destination        jsonb,
    notes              text,
    schedule_reference text,
    legacy_id          text,
    constraint pk_external_movement primary key (id),
    constraint fk_external_movement_series foreign key (series_id) references custodial_series (id),
    constraint uq_external_movement_legacy_id unique (legacy_id) deferrable initially deferred
)
;

create index if not exists idx_external_movement_series on external_movement (series_id, occurred_at desc);
create index if not exists idx_external_movement_person on external_movement (person_identifier, occurred_at desc);
create index if not exists idx_external_movement_schedule_reference on external_movement (schedule_reference);

create table if not exists external_movement_audit
(
    rev_id             bigint                 not null,
    rev_type           smallint               not null,
    id                 uuid                   not null,
    version            int                    not null,
    person_identifier  varchar(7)             not null,
    series_id          uuid                   not null,
    journey_type       external_journey_type  not null,
    movement_type      external_movement_type not null,
    reason             jsonb                  not null,
    occurred_at        timestamp              not null,
    origin             jsonb                  not null,
    destination        jsonb,
    notes              text,
    schedule_reference text,
    legacy_id          text,
    constraint pk_external_movement_audit primary key (id, rev_id),
    constraint fk_external_movement_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_external_movement_audit_series on external_movement_audit (series_id, occurred_at desc);
create index if not exists idx_external_movement_audit_person on external_movement_audit (person_identifier, occurred_at desc);
create index if not exists idx_external_movement_audit_rev_id on external_movement_audit (rev_id, occurred_at desc);
create index if not exists idx_external_movement_audit_legacy_id on external_movement_audit (legacy_id);
create index if not exists idx_external_movement_audit_schedule_reference on external_movement_audit (schedule_reference);

create table if not exists external_journey_movement
(
    movement_id uuid not null,
    journey_id  uuid not null,
    constraint pk_external_journey_movement primary key (movement_id),
    constraint uq_external_journey_movement unique (journey_id, movement_id) deferrable initially deferred,
    constraint fk_external_journey_movement_movement foreign key (movement_id) references external_movement (id) on delete cascade,
    constraint fk_external_journey_movement_journey foreign key (journey_id) references external_journey (id)
);

create table if not exists prison_stay_movement
(
    movement_id uuid not null,
    stay_id     uuid not null,
    constraint pk_prison_stay_movement primary key (movement_id),
    constraint uq_prison_stay_movement unique (stay_id, movement_id) deferrable initially deferred,
    constraint fk_prison_stay_movement_movement foreign key (movement_id) references external_movement (id) on delete cascade,
    constraint fk_prison_stay_movement_stay foreign key (stay_id) references prison_stay (id)
);