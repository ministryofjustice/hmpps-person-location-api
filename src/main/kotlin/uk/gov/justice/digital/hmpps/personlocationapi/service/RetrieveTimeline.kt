package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.TimeLineActivity
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.Timeline
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.TimelinePeriod
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.TimelinePrison
import uk.gov.justice.digital.hmpps.personlocationapi.model.timeline.TimelineStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository

@Service
class RetrieveTimeline(
  private val stayRepository: PrisonStayRepository,
) {
  fun periodsFor(personIdentifier: String): Timeline {
    val periods = stayRepository.findAllByPersonIdentifierWithReasons(personIdentifier)
      .groupBy { it.episodeCommencedAt to it.episodeConcludedAt }
      .map { (ep, stays) ->
        TimelinePeriod(
          ep.first,
          ep.second,
          stays.map { stay ->
            TimelineStay(
              prison = TimelinePrison(stay.prisonCode),
              arrival = TimeLineActivity(stay.arrivedAt, stay.arrivedReason.description),
              departure = stay.departedAt?.let {
                TimeLineActivity(it, stay.departedReason?.description ?: "Unknown")
              },
            )
          },
        )
      }
    return Timeline(periods)
  }
}
