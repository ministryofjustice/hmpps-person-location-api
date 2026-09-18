package uk.gov.justice.digital.hmpps.personlocationapi.sync

import java.time.LocalDateTime

interface SyncRequest

interface NumericLegacyIdRequest : SyncRequest {
  val legacyId: Long?
}

interface StringLegacyIdRequest : SyncRequest {
  val legacyId: String?
}

data class SyncUser(val username: String, val activeCaseloadId: String?)

data class SyncCustodialSeriesRequest(
  val occurredAt: LocalDateTime,
  val syncUser: SyncUser,
  val custodialSeries: CustodialSeries,
)

data class SyncExternalMovementRequest(
  val occurredAt: LocalDateTime,
  val syncUser: SyncUser,
  val movement: ExternalMovement,
)
