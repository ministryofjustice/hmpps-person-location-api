package uk.gov.justice.digital.hmpps.personlocationapi.sync

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags
import uk.gov.justice.digital.hmpps.personlocationapi.sync.internal.RetrieveForSync

@Tag(name = OpenApiTags.SYNC)
@RestController
@RequestMapping("reconciliation/external-movements")
@PreAuthorize("hasRole('${Roles.LEGACY_SYNC}')")
class ReconciliationController(private val retrieve: RetrieveForSync) {
  @GetMapping("/{personIdentifier}")
  fun reconcile(@PathVariable personIdentifier: String): ReconciliationResponse = retrieve.all(personIdentifier)
}

data class ReconciliationResponse(val series: List<ReconciliationSeries>)
data class ReconciliationSeries(val series: CustodialSeries, val movements: List<ExternalMovement>)
