package uk.gov.justice.digital.hmpps.personlocationapi.controller

import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags.INTEGRATIONS
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodsOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.service.RetrieveEpisodes

@Tag(name = INTEGRATIONS)
@RestController
@RequestMapping("/integrations/people/{personIdentifier}")
@PreAuthorize("hasRole('${Roles.PERSON_PERIODS_RO}')")
class EpisodeController(private val retrieve: RetrieveEpisodes) {
  @GetMapping("/periods-of-custody")
  fun periodsOfCustodyFor(@PathVariable personIdentifier: String): PeriodsOfCustody = retrieve.periodsOfCustodyFor(personIdentifier)
}
