alter table movement_interval
    add column if not exists reason jsonb
;