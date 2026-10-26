drop index if exists uq_external_journey_external_reference;
create index if not exists external_journey_external_reference on external_journey (external_reference);