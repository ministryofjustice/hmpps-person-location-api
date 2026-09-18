create type custodial_series_status as enum ('OPEN', 'CLOSED')
;

create type episode_status as enum ('ACTIVE', 'TEMPORARY_ABSENCE', 'COMPLETED', 'UAL', 'DECEASED', 'CANCELLED')
;

create type prison_stay_status as enum ('RESIDENT', 'IN_TRANSIT_EXTERNAL', 'TRANSFERRED_OUT', 'RELEASED')
;

create type external_journey_type as enum ('ADMISSION', 'COURT_APPEARANCE', 'TEMPORARY_ABSENCE', 'TRANSFER', 'RELEASE')
;

create type external_journey_status as enum ('SCHEDULED','IN_TRANSIT','COMPLETED','CANCELLED')
;

create type external_movement_type as enum ('ARRIVAL', 'DEPARTURE')
;

create table if not exists custodial_series
(
    id                       uuid                    not null default uuidv7(),
    version                  int                     not null,
    person_identifier        varchar(7)              not null,
    status                   custodial_series_status not null,
    is_active                boolean                 not null,
    opened_at                timestamp               not null,
    closed_at                timestamp,
    notes                    text,
    legacy_booking_reference varchar(14),
    legacy_id                bigint,
    constraint pk_custodial_series primary key (id),
    constraint uq_custodial_series_legacy_id unique (legacy_id) deferrable initially deferred
)
;

create index if not exists uq_custodial_series_person_active on custodial_series (person_identifier) where is_active = true;
create index if not exists idx_custodial_series_person on custodial_series (person_identifier, opened_at desc);

create table if not exists custodial_episode
(
    id                uuid           not null default uuidv7(),
    version           int            not null,
    series_id         uuid           not null,
    person_identifier varchar(7)     not null,
    status            episode_status not null,
    commenced_at      timestamp      not null,
    concluded_at      timestamp,
    constraint pk_custodial_episode primary key (id),
    constraint fk_custodial_episode_series foreign key (series_id) references custodial_series (id)
)
;

create index if not exists idx_custodial_episode_series on custodial_episode (series_id, commenced_at desc);
create index if not exists idx_custodial_episode_person on custodial_episode (person_identifier, commenced_at desc);

create table if not exists prison_stay
(
    id                uuid               not null default uuidv7(),
    version           int                not null,
    episode_id        uuid               not null,
    person_identifier varchar(7)         not null,
    prison_code       varchar(6)         not null,
    status            prison_stay_status not null,
    is_active         boolean            not null,
    arrived_at        timestamp          not null,
    departed_at       timestamp,
    constraint pk_prison_stay primary key (id),
    constraint fk_prison_stay_episode foreign key (episode_id) references custodial_episode (id)
)
;

create index if not exists uq_prison_stay_person_active on prison_stay (person_identifier) where is_active = true;
create index if not exists idx_prison_stay_episode on prison_stay (episode_id, arrived_at desc);
create index if not exists idx_prison_stay_prison_active on prison_stay (prison_code) where is_active = true;
create index if not exists idx_prison_stay_person on prison_stay (person_identifier, arrived_at desc);

create table if not exists external_journey
(
    id                      uuid                    not null default uuidv7(),
    version                 int                     not null,
    person_identifier       varchar(7)              not null,
    type                    external_journey_type   not null,
    status                  external_journey_status not null,
    origin_prison_code      varchar(6),
    destination_prison_code varchar(6),
    external_reference      text,
    constraint pk_external_journey primary key (id),
    constraint ch_external_journey_origin_destination check ( origin_prison_code is not null or destination_prison_code is not null )
)
;

create unique index if not exists uq_external_journey_external_reference on external_journey (external_reference) where status != 'CANCELLED';
create index if not exists idx_external_journey_person_origin on external_journey (person_identifier, status, origin_prison_code) where origin_prison_code is not null;
create index if not exists idx_external_journey_person_status_type on external_journey (person_identifier, status, type);
create index if not exists idx_external_journey_origin on external_journey (origin_prison_code, status, type) where origin_prison_code is not null;
create index if not exists idx_external_journey_destination on external_journey (destination_prison_code, status, type) where destination_prison_code is not null;

create table if not exists external_movement
(
    id                uuid                   not null default uuidv7(),
    version           int                    not null,
    journey_id        uuid                   not null,
    stay_id           uuid                   not null,
    person_identifier varchar(7)             not null,
    sequence          int                    not null,
    type              external_movement_type not null,
    reason            jsonb                  not null,
    occurred_at       timestamp              not null,
    origin            jsonb                  not null,
    destination       jsonb,
    notes             text,
    legacy_id         text,
    constraint pk_external_movement primary key (id),
    constraint fk_external_movement_journey foreign key (journey_id) references external_journey (id),
    constraint fk_external_movement_stay foreign key (stay_id) references prison_stay (id),
    constraint uq_external_movement_sequence unique (journey_id, sequence) deferrable initially deferred,
    constraint uq_external_movement_legacy_id unique (legacy_id) deferrable initially deferred
)
;

create index if not exists idx_external_movement_stay on external_movement (stay_id, occurred_at desc);
create index if not exists idx_external_movement_person on external_movement (person_identifier, occurred_at desc);

create table if not exists hmpps_domain_event
(
    id         uuid    not null,
    version    int     not null,
    entity_id  uuid    not null,
    event_type text    not null,
    event      jsonb   not null,
    published  boolean not null,
    constraint pk_hmpps_domain_event primary key (id)
)
;

create index if not exists idx_hmpps_domain_event_unpublished on hmpps_domain_event (id) where (published = false);

create table if not exists audit_revision
(
    id                bigserial   not null,
    timestamp         timestamp   not null,
    source            varchar(6)  not null,
    affected_entities text[]      not null,
    username          varchar(64) not null,
    caseload_id       varchar(10),
    reason            text,
    constraint pk_audit_revision primary key (id),
    constraint ch_audit_revision_source check (source in ('DPS', 'NOMIS'))
)
;

create table if not exists hmpps_domain_event_audit
(
    rev_id     bigint   not null,
    rev_type   smallint not null,
    id         uuid     not null,
    version    int      not null,
    entity_id  uuid     not null,
    event_type text     not null,
    event      jsonb    not null,
    published  boolean  not null,
    constraint pk_hmpps_domain_event_audit primary key (rev_id, id),
    constraint fk_hmpps_domain_event_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_hmpps_domain_event_audit_event_type_entity_id on hmpps_domain_event_audit (event_type, entity_id);

create table if not exists custodial_series_audit
(
    rev_id                   bigint                  not null,
    rev_type                 smallint                not null,
    id                       uuid                    not null default uuidv7(),
    version                  int                     not null,
    person_identifier        varchar(7)              not null,
    status                   custodial_series_status not null,
    is_active                boolean                 not null,
    opened_at                timestamp               not null,
    closed_at                timestamp,
    notes                    text,
    legacy_booking_reference varchar(14),
    legacy_id                bigint,
    constraint pk_custodial_series_audit primary key (id, rev_id),
    constraint fk_custodial_series_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_custodial_series_audit_legacy_id on custodial_series_audit (legacy_id);

create table if not exists custodial_episode_audit
(
    rev_id            bigint         not null,
    rev_type          smallint       not null,
    id                uuid           not null default uuidv7(),
    version           int            not null,
    series_id         uuid           not null,
    person_identifier varchar(7)     not null,
    status            episode_status not null,
    commenced_at      timestamp      not null,
    concluded_at      timestamp,
    constraint pk_custodial_episode_audit primary key (id, rev_id),
    constraint fk_custodial_episode_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_custodial_episode_audit_person on custodial_episode_audit (person_identifier, commenced_at desc);
create index if not exists idx_custodial_audit_rev_id on custodial_episode_audit (rev_id);

create table if not exists prison_stay_audit
(
    rev_id            bigint             not null,
    rev_type          smallint           not null,
    id                uuid               not null,
    version           int                not null,
    episode_id        uuid               not null,
    person_identifier varchar(7)         not null,
    prison_code       varchar(6)         not null,
    status            prison_stay_status not null,
    is_active         boolean            not null,
    arrived_at        timestamp          not null,
    departed_at       timestamp,
    constraint pk_prison_stay_audit primary key (id, rev_id),
    constraint fk_prison_stay_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_prison_stay_audit_person on prison_stay_audit (person_identifier, arrived_at desc);
create index if not exists idx_prison_stay_audit_rev_id on prison_stay_audit (rev_id);

create table if not exists external_journey_audit
(
    rev_id                  bigint                  not null,
    rev_type                smallint                not null,
    id                      uuid                    not null,
    version                 int                     not null,
    person_identifier       varchar(7)              not null,
    type                    external_journey_type   not null,
    status                  external_journey_status not null,
    origin_prison_code      varchar(6)              not null,
    destination_prison_code varchar(6),
    external_reference      text,
    constraint pk_external_journey_audit primary key (id, rev_id),
    constraint fk_external_journey_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_external_journey_audit_person on external_journey_audit (person_identifier);
create index if not exists idx_external_journey_audit_external_reference on external_journey_audit (external_reference);
create index if not exists idx_external_journey_audit_rev_id on external_journey_audit (rev_id);

create table if not exists external_movement_audit
(
    rev_id            bigint                 not null,
    rev_type          smallint               not null,
    id                uuid                   not null,
    version           int                    not null,
    journey_id        uuid                   not null,
    sequence          int                    not null,
    stay_id           uuid                   not null,
    person_identifier varchar(7)             not null,
    type              external_movement_type not null,
    reason            jsonb                  not null,
    occurred_at       timestamp              not null,
    origin            jsonb                  not null,
    destination       jsonb,
    notes             text,
    legacy_id         text,
    constraint pk_external_movement_audit primary key (id, rev_id),
    constraint fk_external_movement_audit_revision foreign key (rev_id) references audit_revision (id)
)
;

create index if not exists idx_external_movement_audit_person on external_movement_audit (person_identifier, occurred_at desc);
create index if not exists idx_external_movement_audit_rev_id on external_movement_audit (rev_id);
create index if not exists idx_external_movement_audit_legacy_id on external_movement_audit (legacy_id);