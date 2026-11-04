package uk.gov.justice.digital.hmpps.personlocationapi.integration.config

import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode.Status
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

typealias CustodialEpisodeProvider = () -> CustodialEpisode

interface CustodialEpisodeOperations {
  fun findEpisode(uuid: UUID): CustodialEpisode?
  fun givenEpisode(ep: CustodialEpisodeProvider): CustodialEpisode
}

class CustodialEpisodeOperationsImpl(
  private val episodeRepository: CustodialEpisodeRepository,
) : CustodialEpisodeOperations {
  override fun findEpisode(uuid: UUID): CustodialEpisode? = episodeRepository.findByIdOrNull(uuid)
  override fun givenEpisode(ep: CustodialEpisodeProvider): CustodialEpisode = episodeRepository.save(ep())

  companion object {
    fun custodialEpisode(
      personIdentifier: String = personIdentifier(),
      status: Status = Status.ACTIVE,
      commencedAt: LocalDateTime = LocalDateTime.now().minusDays(1),
      concludedAt: LocalDateTime? = null,
      id: UUID = newUuid(),
    ): CustodialEpisodeProvider = {
      CustodialEpisode(
        personIdentifier,
        status,
        commencedAt.truncatedTo(ChronoUnit.SECONDS),
        concludedAt?.truncatedTo(ChronoUnit.SECONDS),
        id,
      )
    }
  }
}
