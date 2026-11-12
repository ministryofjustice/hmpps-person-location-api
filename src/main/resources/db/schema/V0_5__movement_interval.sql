create extension if not exists btree_gist;

drop table if exists prison_stay_movement;
drop table if exists prison_stay;
drop table if exists custodial_episode;
drop table if exists external_journey_movement;
drop table if exists external_journey;
drop type if exists episode_status;
drop type if exists external_journey_status;

create type episode_status as enum ('ACTIVE', 'TEMPORARY_ABSENCE', 'COMPLETED', 'UNLAWFULLY_AT_LARGE', 'DECEASED')
;

create type external_journey_status as enum ('IN_TRANSIT','COMPLETED')
;

create table if not exists movement_interval
(
    id                uuid                   not null default uuidv7(),
    version           int                    not null,
    person_identifier varchar(7)             not null,
    journey_type      external_journey_type  not null,
    movement_type     external_movement_type not null,
    valid_from        timestamp              not null,
    valid_to          timestamp,
    period            tsrange generated always as (tsrange(valid_from, valid_to, '[)')) stored,
    origin            jsonb                  not null,
    destination       jsonb,
    constraint pk_movement_interval primary key (id),
    constraint movement_interval_no_overlap_per_prisoner exclude using gist (person_identifier with =, period with &&) deferrable initially deferred,
    constraint ch_movement_interval_valid_from_to check (valid_to is null or valid_to > valid_from)
)
;

create index if not exists idx_movement_interval_period_gist on movement_interval using gist (person_identifier, period);

create table if not exists custodial_episode
(
    id                uuid           not null default uuidv7(),
    version           int            not null,
    person_identifier varchar(7)     not null,
    status            episode_status not null,
    commenced_at      timestamp      not null,
    concluded_at      timestamp,
    period            tsrange generated always as (tsrange(commenced_at, concluded_at, '[)')) stored,
    constraint pk_custodial_episode primary key (id),
    constraint custodial_episode_no_overlap_per_prisoner exclude using gist (person_identifier with =, period with &&) deferrable initially deferred,
    constraint ch_custodial_episode_commenced_concluded check ( concluded_at is null or concluded_at > commenced_at )
)
;

create index if not exists idx_custodial_episode_person on custodial_episode (person_identifier, commenced_at desc);

create table if not exists prison_stay
(
    id                uuid               not null default uuidv7(),
    version           int                not null,
    person_identifier varchar(7)         not null,
    prison_code       varchar(6)         not null,
    status            prison_stay_status not null,
    arrived_at        timestamp          not null,
    departed_at       timestamp,
    period            tsrange generated always as (tsrange(arrived_at, departed_at, '[)')) stored,
    is_active         boolean generated always as (departed_at is null) stored,
    constraint pk_prison_stay primary key (id),
    constraint prison_stay_no_overlap_per_prisoner exclude using gist (person_identifier with =, period with &&) deferrable initially deferred,
    constraint ch_prison_stay_arrived_departed check ( departed_at is null or departed_at > arrived_at )
)
;

create index if not exists idx_prison_stay_person on prison_stay (person_identifier, arrived_at desc);
create index if not exists idx_prison_stay_prison_active on prison_stay (prison_code) where is_active = true;

create table if not exists external_journey
(
    id                      uuid                    not null default uuidv7(),
    version                 int                     not null,
    person_identifier       varchar(7)              not null,
    type                    external_journey_type   not null,
    status                  external_journey_status not null,
    commenced_at            timestamp               not null,
    concluded_at            timestamp,
    period                  tsrange generated always as (tsrange(commenced_at, concluded_at, '[)')) stored,
    origin_prison_code      varchar(6)              not null,
    destination_prison_code varchar(6),
    external_reference      text,
    constraint pk_external_journey primary key (id),
    constraint external_journey_no_overlap_per_prisoner exclude using gist (person_identifier with =, period with &&) deferrable initially deferred,
    constraint ch_external_journey_commenced_concluded check ( concluded_at is null or concluded_at > commenced_at )
)
;

create index if not exists idx_external_journey_person_origin on external_journey (person_identifier, status, origin_prison_code);
create index if not exists idx_external_journey_origin on external_journey (origin_prison_code, status, type);
create index if not exists idx_external_journey_destination on external_journey (destination_prison_code, status, type) where destination_prison_code is not null;
create index if not exists idx_external_journey_external_reference on external_journey (external_reference);

create table if not exists custodial_episode_audit
(
    rev_id            bigint         not null,
    rev_type          smallint       not null,
    id                uuid           not null,
    version           int            not null,
    person_identifier varchar(7)     not null,
    status            episode_status not null,
    commenced_at      timestamp      not null,
    concluded_at      timestamp,
    constraint pk_custodial_episode_audit primary key (id, rev_id),
    constraint fk_custodial_episode_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create table if not exists prison_stay_audit
(
    rev_id            bigint             not null,
    rev_type          smallint           not null,
    id                uuid               not null,
    version           int                not null,
    person_identifier varchar(7)         not null,
    prison_code       varchar(6)         not null,
    status            prison_stay_status not null,
    arrived_at        timestamp          not null,
    departed_at       timestamp,
    constraint pk_prison_stay_audit primary key (id, rev_id),
    constraint fk_prison_stay_audit_revision foreign key (rev_id) references audit_revision (id)
)
;