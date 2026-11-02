alter table custodial_episode
    drop column series_id
;

alter table external_journey
    add column commenced_at timestamp,
    add column concluded_at timestamp
;

truncate external_journey_movement, external_journey, prison_stay_movement, prison_stay, custodial_episode;

alter table external_journey
    alter column commenced_at set not null,
    alter column origin_prison_code set not null
;

create index if not exists idx_external_journey_person on external_journey (person_identifier, commenced_at desc);

