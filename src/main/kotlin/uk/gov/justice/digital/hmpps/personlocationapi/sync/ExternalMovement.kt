package uk.gov.justice.digital.hmpps.personlocationapi.sync

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import java.time.LocalDateTime
import java.util.UUID

@Schema(name = "SyncExternalMovement")
data class ExternalMovement(
  val dpsId: UUID?,
  val dpsCustodySeriesId: UUID?,
  val legacyBookingId: Long?,
  val legacySequenceNumber: Int?,
  val type: MovementType,
  val reason: MovementReason,
  val occurredAt: LocalDateTime,
  val from: Location,
  val to: Location?,
  val notes: String?,
  val journey: ExternalJourney?,
) : StringLegacyIdRequest {
  @JsonIgnore
  override val legacyId: String? =
    if (legacyBookingId == null || legacySequenceNumber == null) null else "${legacyBookingId}_$legacySequenceNumber"

  @Schema(name = "SyncExternalMovementType")
  enum class MovementType { ARRIVAL, DEPARTURE }
}
