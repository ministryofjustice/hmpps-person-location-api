package uk.gov.justice.digital.hmpps.personlocationapi.model.episode

import uk.gov.justice.digital.hmpps.personlocationapi.model.CodedDescription
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import java.time.LocalDateTime

data class PeriodOfCustody(
  val commencedAt: LocalDateTime,
  val concludedAt: LocalDateTime?,
  val status: CodedDescription,
)

data class PeriodsOfCustody(val periodsOfCustody: List<PeriodOfCustody>)

private fun CustodialEpisode.Status.asCodedDescription(): CodedDescription = CodedDescription(name, name.first().uppercase() + name.substring(1).lowercase().replace("_", " "))

fun CustodialEpisode.asPeriodOfCustody(): PeriodOfCustody = PeriodOfCustody(commencedAt, concludedAt, status.asCodedDescription())
