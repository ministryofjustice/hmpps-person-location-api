package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodsOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.asPeriodOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository

@Service
class RetrieveEpisodes(
  private val episodeRepository: CustodialEpisodeRepository,
) {
  fun periodsOfCustodyFor(personIdentifier: String): PeriodsOfCustody = episodeRepository.findAllByPersonIdentifier(personIdentifier)
    .map(CustodialEpisode::asPeriodOfCustody)
    .sortedByDescending { it.commencedAt }
    .let { PeriodsOfCustody(it) }
}
