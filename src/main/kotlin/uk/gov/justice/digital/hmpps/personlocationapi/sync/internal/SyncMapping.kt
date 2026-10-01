package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyDestination
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyJourneyDetails
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyNotes
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOccurredAt
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOrigin
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyReason
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyType
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries as SeriesEntity
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement as MovementEntity

fun CustodialSeries.asEntity(personIdentifier: String) = SeriesEntity(
  personIdentifier,
  status.asEntityStatus(),
  isActive,
  openedAt,
  closedAt,
  notes,
  legacyBookingReference,
  legacyId,
)

fun SeriesEntity.updateFrom(personIdentifier: String, request: CustodialSeries) = apply {
  applyPerson(personIdentifier)
  applySeries(request.isActive, request.openedAt, request.closedAt)
  applyNotes(request.notes)
  applyLegacyIdentifiers(request.legacyId, request.legacyBookingReference)
}

fun ExternalMovement.asEntity(series: SeriesEntity) = MovementEntity(
  series,
  journey.type.asEntityType(),
  type.asEntityType(),
  reason,
  occurredAt,
  from,
  to,
  notes,
  journey.scheduleReference,
  legacyId,
)

fun MovementEntity.updateFrom(series: SeriesEntity, request: ExternalMovement) = apply {
  applySeries(series)
  applyType(ApplyType(request.type.asEntityType()))
  applyJourneyDetails(ApplyJourneyDetails(request.journey.type.asEntityType(), request.journey.scheduleReference))
  applyReason(ApplyReason(request.reason))
  applyOccurredAt(ApplyOccurredAt(request.occurredAt))
  applyOrigin(ApplyOrigin(request.from))
  applyDestination(ApplyDestination(request.to))
  applyNotes(ApplyNotes(request.notes))
}
