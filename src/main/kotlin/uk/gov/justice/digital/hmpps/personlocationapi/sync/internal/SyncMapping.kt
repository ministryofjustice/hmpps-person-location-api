package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyDestination
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyJourneyDetails
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyNotes
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOccurredAt
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOrigin
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyReason
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyType
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement.Type.ARRIVAL
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement.Type.DEPARTURE
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalJourney.JourneyType
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement.MovementType
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
  applyLegacyId(request.legacyId)
}

fun SeriesEntity.toSyncModel() = CustodialSeries(
  legacyId,
  legacyBookingReference,
  id,
  when (status) {
    SeriesEntity.Status.OPEN -> CustodialSeries.Status.OPEN
    SeriesEntity.Status.CLOSED -> CustodialSeries.Status.CLOSED
  },
  isActive,
  openedAt,
  closedAt,
  notes,
)

fun MovementEntity.toSyncModel(): ExternalMovement {
  val legacyIdParts = syncIdsFromLegacyId()
  return ExternalMovement(
    id,
    series.id,
    legacyIdParts?.first,
    legacyIdParts?.second,
    when (movementType) {
      ARRIVAL -> MovementType.ARRIVAL
      DEPARTURE -> MovementType.DEPARTURE
    },
    reason,
    occurredAt,
    origin,
    destination,
    notes,
    ExternalJourney(JourneyType.valueOf(journeyType.name), scheduleReference),
  )
}

fun MovementEntity.syncIdsFromLegacyId(): Pair<Long, Int>? {
  val parts = legacyId?.split("_")
  return if (parts?.size != 2) {
    null
  } else {
    parts[0].toLong() to parts[1].toInt()
  }
}
