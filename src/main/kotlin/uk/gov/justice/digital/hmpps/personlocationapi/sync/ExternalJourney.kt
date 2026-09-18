package uk.gov.justice.digital.hmpps.personlocationapi.sync

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference

@Schema(name = "SyncExternalJourney")
data class ExternalJourney(
  val type: JourneyType,
  val scheduleReference: ExternalReference?,
) {
  @Schema(name = "SyncExternalJourneyType")
  enum class JourneyType { ADMISSION, COURT_APPEARANCE, TEMPORARY_ABSENCE, TRANSFER, RELEASE }
}
