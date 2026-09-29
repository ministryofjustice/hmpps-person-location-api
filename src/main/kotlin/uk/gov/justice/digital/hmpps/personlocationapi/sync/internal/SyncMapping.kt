package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.Arrival
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.Departure
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries as SeriesEntity

fun CustodialSeries.asEntity(personIdentifier: String) = SeriesEntity(
  personIdentifier,
  when (status) {
    CustodialSeries.Status.OPEN -> SeriesEntity.Status.OPEN
    CustodialSeries.Status.CLOSED -> SeriesEntity.Status.CLOSED
  },
  isActive,
  openedAt,
  closedAt,
  notes,
  legacyBookingReference,
  legacyId,
)

fun ExternalMovement.asEntity(series: SeriesEntity) = when (type) {
  ExternalMovement.MovementType.ARRIVAL -> Arrival(
    series.personIdentifier,
    reason,
    occurredAt,
    from,
    to,
    notes,
    legacyId,
  )

  ExternalMovement.MovementType.DEPARTURE -> Departure(
    series.personIdentifier,
    reason,
    occurredAt,
    from,
    to,
    notes,
    legacyId,
  )
}