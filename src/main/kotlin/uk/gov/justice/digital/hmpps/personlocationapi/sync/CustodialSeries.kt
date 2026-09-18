package uk.gov.justice.digital.hmpps.personlocationapi.sync

import com.fasterxml.jackson.annotation.JsonIgnore
import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime
import java.util.UUID

@Schema(name = "SyncCustodialSeries")
data class CustodialSeries(
  val legacyBookingId: Long?,
  val legacyBookingReference: String?,
  val dpsId: UUID?,
  val status: Status,
  val isActive: Boolean,
  val openedAt: LocalDateTime,
  val closedAt: LocalDateTime?,
  val notes: String?,
) : NumericLegacyIdRequest {
  @JsonIgnore
  override val legacyId: Long? = legacyBookingId

  @Schema(name = "SyncCustodialSeriesStatus")
  enum class Status { OPEN, CLOSED }
}
