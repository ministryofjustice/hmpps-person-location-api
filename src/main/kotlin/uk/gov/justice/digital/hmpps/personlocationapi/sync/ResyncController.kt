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
import uk.gov.justice.digital.hmpps.personlocationapi.sync.internal.ExternalMovementsResync
import java.util.UUID

@Tag(name = OpenApiTags.SYNC)
@RestController
@RequestMapping("/resync/external-movements")
@PreAuthorize("hasRole('${Roles.LEGACY_SYNC}')")
class ResyncController(private val resync: ExternalMovementsResync) {
  @PutMapping("/{personIdentifier}")
  fun resync(
    @PathVariable personIdentifier: String,
    @RequestBody request: ResyncExternalMovementsRequest,
  ): ResyncResponse = resync.all(personIdentifier, request)
}

data class ResyncExternalMovementsRequest(val custodialSeries: List<ResyncCustodialSeries>) {
  fun seriesIds(): Pair<Set<Long>, Set<UUID>> {
    val (legacyIds, ids) = custodialSeries.map { requireNotNull(it.custodialSeries.legacyBookingId) to it.custodialSeries.dpsId }.unzip()
    return legacyIds.toSet() to ids.filterNotNull().toSet()
  }
  fun movementIds(): Pair<Set<String>, Set<UUID>> {
    val (legacyIds, ids) = custodialSeries.flatMap {
      it.movements.map { m -> requireNotNull(m.movement.legacyId) to m.movement.dpsId }
    }.unzip()
    return legacyIds.toSet() to ids.filterNotNull().toSet()
  }
}

data class ResyncCustodialSeries(
  val custodialSeries: CustodialSeries,
  val movements: List<ResyncExternalMovement>,
  val created: AtAndBy,
  val modified: AtAndBy?,
)

data class ResyncExternalMovement(val movement: ExternalMovement, val created: AtAndBy, val modified: AtAndBy?)

data class ResyncResponse(val custodialSeries: List<CustodialSeriesMapping>)

data class CustodialSeriesMapping(
  val dpsId: UUID,
  val legacyBookingId: Long,
  val movements: List<ExternalMovementMapping>,
)

data class ExternalMovementMapping(val dpsId: UUID, val legacySequenceNumber: Int)
