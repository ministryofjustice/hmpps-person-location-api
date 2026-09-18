package uk.gov.justice.digital.hmpps.personlocationapi.sync

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags
import java.util.UUID

@Tag(name = OpenApiTags.SYNC)
@RestController
@RequestMapping("sync")
@PreAuthorize("hasRole('${Roles.LEGACY_SYNC}')")
class SyncController {
  @PutMapping("/custodial-series/{personIdentifier}")
  fun syncCustodialSeries(@PathVariable personIdentifier: String, @RequestBody request: SyncCustodialSeriesRequest): ReferenceId = TODO()

  @GetMapping("/custodial-series/{id}")
  fun getCustodialSeries(@PathVariable id: UUID): CustodialSeries = TODO()

  @DeleteMapping("/custodial-series/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteCustodialSeries(@PathVariable id: UUID) {}

  @PutMapping("/external-movements/{personIdentifier}")
  fun syncExternalMovement(@PathVariable personIdentifier: String, @RequestBody request: SyncExternalMovementRequest): ReferenceId = TODO()

  @GetMapping("/external-movements/{id}")
  fun getExternalMovement(@PathVariable id: UUID): ExternalMovement = TODO()

  @DeleteMapping("/external-movements/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  fun deleteExternalMovement(@PathVariable id: UUID) {}
}
