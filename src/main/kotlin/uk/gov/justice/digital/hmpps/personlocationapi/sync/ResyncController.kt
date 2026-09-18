package uk.gov.justice.digital.hmpps.personlocationapi.sync

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags
import java.util.UUID

@Tag(name = OpenApiTags.SYNC)
@RestController
@RequestMapping("/resync/external-movements")
@PreAuthorize("hasRole('${Roles.LEGACY_SYNC}')")
class ResyncController {
  @PutMapping("/{personIdentifier}")
  fun resync(
    @PathVariable personIdentifier: String,
    @RequestBody request: ResyncExternalMovementsRequest,
  ): ResyncResponse = TODO()
}

data class ResyncExternalMovementsRequest(val custodialSeries: List<ResyncCustodialSeries>)

data class ResyncCustodialSeries(
  val custodialSeries: CustodialSeries,
  val movements: List<ExternalMovement>,
  val created: AtAndBy,
  val updated: AtAndBy?,
)

data class ResyncExternalMovement(val movement: ExternalMovement, val created: AtAndBy, val updated: AtAndBy?)

data class ResyncResponse(val custodialSeries: List<CustodialSeriesMapping>)

data class CustodialSeriesMapping(
  val dpsId: UUID,
  val legacyBookingId: Long,
  val movements: List<ExternalMovementMapping>,
)

data class ExternalMovementMapping(val dpsId: UUID, val legacySequenceNumber: Int)
