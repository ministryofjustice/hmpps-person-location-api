package uk.gov.justice.digital.hmpps.personlocationapi.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags.INTEGRATIONS
import uk.gov.justice.digital.hmpps.personlocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodsOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.service.RetrieveEpisodes
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import java.time.LocalDateTime

@Tag(name = INTEGRATIONS)
@RestController
@RequestMapping("/integrations/people/{personIdentifier}")
@PreAuthorize("hasRole('${Roles.PERSON_PERIODS_RO}')")
class EpisodeController(private val retrieve: RetrieveEpisodes) {
  @Operation(summary = "Retrieve periods of custody ordered in reverse chronological order (most recent first).")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "All periods of custody for the provided identifier returned in reverse chronological order. If current, concludedAt will be null.",
        content = [Content(schema = Schema(implementation = PeriodsOfCustody::class))],
      ),
    ],
  )
  @GetMapping("/periods-of-custody")
  fun periodsOfCustodyFor(@PathVariable personIdentifier: String): PeriodsOfCustody = retrieve.periodsOfCustodyFor(personIdentifier)

  @Operation(summary = "Retrieve the most recent period of custody if one exists. The status and concluded date can be used to determine if it is current.")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No period of custody exists for the provided identifier",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "The most recent period of custody for the provided identifier. If current, concludedAt will be null.",
        content = [Content(schema = Schema(implementation = PeriodOfCustody::class))],
      ),
    ],
  )
  @GetMapping("/periods-of-custody/latest")
  fun latestPeriodFor(@PathVariable personIdentifier: String): PeriodOfCustody = retrieve.latestPeriodFor(personIdentifier)
    ?: throw NotFoundException("No period of custody found for the provided identifier.")

  @Operation(summary = "Retrieve the period of custody for a given date and time. The status and concluded date can be used to determine if it is current.")
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "404",
        description = "No period of custody exists for the provided identifier at the given date and time",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "The period of custody for the provided identifier covering the date and time provided. If current, concludedAt will be null.",
        content = [Content(schema = Schema(implementation = PeriodOfCustody::class))],
      ),
    ],
  )
  @GetMapping("/periods-of-custody/at")
  fun periodAt(@PathVariable personIdentifier: String, @RequestParam anchorDateTime: LocalDateTime): PeriodOfCustody = retrieve.periodAt(personIdentifier, anchorDateTime) ?: throw NotFoundException("No matching period of custody found for the provided identifier.")
}
