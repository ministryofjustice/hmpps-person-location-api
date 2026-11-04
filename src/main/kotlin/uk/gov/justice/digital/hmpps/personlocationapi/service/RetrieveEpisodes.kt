package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodsOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.asPeriodOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository

@Service
class RetrieveEpisodes(
  private val episodeRepository: CustodialEpisodeRepository,
) {
  fun periodsOfCustodyFor(personIdentifier: String): PeriodsOfCustody {
    val episodes = episodeRepository.findAllByPersonIdentifier(personIdentifier).sortedByDescending { it.commencedAt }
    val pocs = episodes.indices.map { index -> episodes[index].asPeriodOfCustody(episodes.getOrNull(index + 1)) }
    return PeriodsOfCustody(pocs)
  }

  fun latestPeriodFor(personIdentifier: String): PeriodOfCustody? {
    val episodes = episodeRepository.findMostRecentFor(personIdentifier).sortedByDescending { it.commencedAt }
    return episodes.firstOrNull()?.asPeriodOfCustody(episodes.lastOrNull())
  }
}
