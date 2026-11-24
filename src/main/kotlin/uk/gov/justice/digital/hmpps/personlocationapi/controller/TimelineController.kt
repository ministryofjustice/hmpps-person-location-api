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
import org.springframework.web.bind.annotation.RestController
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags.INTEGRATIONS
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.Timeline
import uk.gov.justice.digital.hmpps.personlocationapi.service.RetrieveTimeline
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse

@Tag(name = INTEGRATIONS)
@RestController
@RequestMapping("/integrations/people/{personIdentifier}")
@PreAuthorize("hasRole('${Roles.PERSON_PERIODS_RO}')")
class TimelineController(private val retrieve: RetrieveTimeline) {
  @Operation(
    summary = "Retrieves the timeline of a given person in chronological order. If there is no timeline history for the person, `periods` will be empty.",
  )
  @ApiResponses(
    value = [
      ApiResponse(
        responseCode = "401",
        description = "Unauthorised, requires a valid Oauth2 token.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "403",
        description = "Forbidden, requires an appropriate role.",
        content = [Content(schema = Schema(implementation = ErrorResponse::class))],
      ),
      ApiResponse(
        responseCode = "200",
        description = "All periods of custody, with nested stays for the provided identifier returned in chronological order.",
        content = [Content(schema = Schema(implementation = Timeline::class))],
      ),
    ],
  )
  @GetMapping("/timeline")
  fun timelineFor(@PathVariable personIdentifier: String): Timeline = retrieve.periodsFor(personIdentifier)
}
