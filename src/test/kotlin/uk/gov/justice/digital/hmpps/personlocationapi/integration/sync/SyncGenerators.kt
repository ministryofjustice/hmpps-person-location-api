package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.externalReference
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.newId
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.prisonCode
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.username
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.word
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.outOfPrison
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.prison
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.reason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries.Status
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement.MovementType
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncCustodialSeriesRequest
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncExternalMovementRequest
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncUser
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries as SyncCustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement as SyncExternalMovement

fun syncUser(username: String = username(), activeCaseloadId: String? = prisonCode()) = SyncUser(username, activeCaseloadId)

fun syncSeries(
  legacyBookingId: Long = newId(),
  legacyBookingReference: String = word(6).uppercase(),
  dpsId: UUID? = null,
  status: Status = Status.OPEN,
  isActive: Boolean = true,
  openedAt: LocalDateTime = LocalDateTime.now().minusDays(7),
  closedAt: LocalDateTime? = null,
  notes: String? = word(20),
) = SyncCustodialSeries(
  legacyBookingId,
  legacyBookingReference,
  dpsId,
  status,
  isActive,
  openedAt.truncatedTo(ChronoUnit.SECONDS),
  closedAt?.truncatedTo(ChronoUnit.SECONDS),
  notes,
)

fun syncMovement(
  dpsCustodySeriesId: UUID? = null,
  dpsId: UUID? = null,
  legacyBookingId: Long = newId(),
  legacySequenceNumber: Int = newId().toInt(),
  type: MovementType = MovementType.entries.random(),
  reason: MovementReason = reason(),
  occurredAt: LocalDateTime = LocalDateTime.now(),
  from: Location = if (type == MovementType.DEPARTURE) prison() else outOfPrison(),
  to: Location? = if (type == MovementType.DEPARTURE) null else prison(),
  notes: String? = word(30),
  journeyType: ExternalJourney.JourneyType = ExternalJourney.JourneyType.entries.random(),
  scheduleReference: ExternalReference? = externalReference(),
) = SyncExternalMovement(
  dpsId,
  dpsCustodySeriesId,
  legacyBookingId,
  legacySequenceNumber,
  type,
  reason,
  occurredAt.truncatedTo(ChronoUnit.SECONDS),
  from,
  to,
  notes,
  ExternalJourney(journeyType, scheduleReference),
)

fun syncSeriesRequest(
  request: CustodialSeries = syncSeries(),
  syncUser: SyncUser = syncUser(),
  occurredAt: LocalDateTime = LocalDateTime.now(),
) = SyncCustodialSeriesRequest(occurredAt, syncUser, request)

fun syncMovementRequest(
  request: SyncExternalMovement = syncMovement(),
  syncUser: SyncUser = syncUser(),
  occurredAt: LocalDateTime = LocalDateTime.now(),
) = SyncExternalMovementRequest(occurredAt, syncUser, request)
