package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.Timeline
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository

@Service
class RetrieveTimeline(
  private val episodeRepository: CustodialEpisodeRepository,
  private val stayRepository: PrisonStayRepository,
) {
  fun periodsFor(personIdentifier: String): Timeline {
    TODO()
  }
}
