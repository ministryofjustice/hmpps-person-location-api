package uk.gov.justice.digital.hmpps.personlocationapi.model.episode

import io.swagger.v3.oas.annotations.media.Schema
import uk.gov.justice.digital.hmpps.personlocationapi.model.CodedDescription
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import java.time.LocalDateTime

@Schema(description = "Period of custody")
data class PeriodOfCustody(
  @Schema(description = "Date of admission for the period of custody.")
  val commencedAt: LocalDateTime,
  @Schema(description = "Optional date of release for the period of custody. When not provided, the period of custody is deemed active.")
  val concludedAt: LocalDateTime?,
  @Schema(description = "The status of the period of custody. Code and human readable description.")
  val status: CodedDescription,
)

data class PeriodsOfCustody(val periodsOfCustody: List<PeriodOfCustody>)

private fun CustodialEpisode.Status.asCodedDescription(): CodedDescription = CodedDescription(name, name.first().uppercase() + name.substring(1).lowercase().replace("_", " "))

fun CustodialEpisode.asPeriodOfCustody(): PeriodOfCustody = PeriodOfCustody(commencedAt, concludedAt, status.asCodedDescription())
