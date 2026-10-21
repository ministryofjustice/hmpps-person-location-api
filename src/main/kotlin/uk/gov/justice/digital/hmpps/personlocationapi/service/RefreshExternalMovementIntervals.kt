package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourneyRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison

@Transactional
@Service
class RefreshExternalMovementIntervals(
  private val movementRepository: ExternalMovementRepository,
  private val episodeRepository: CustodialEpisodeRepository,
  private val stayRepository: PrisonStayRepository,
  private val journeyRepository: ExternalJourneyRepository,
) {
  fun execute(event: RefreshPersonMovementIntervals) {
    val ep = episodeRepository.findAllByPersonIdentifier(event.personIdentifier)
      .asSequence()
      .sortedBy { it.commencedAt }
      .dropWhile { e -> event.refreshFrom != null && e.concludedAt != null && e.concludedAt!!.isBefore(event.refreshFrom) }
      .toCollection(ArrayDeque())
    val st = stayRepository.findAllByPersonIdentifier(event.personIdentifier)
      .asSequence()
      .sortedBy { it.arrivedAt }
      .dropWhile { e -> event.refreshFrom != null && e.departedAt != null && e.departedAt!!.isBefore(event.refreshFrom) }
      .toCollection(ArrayDeque())
    val movements = movementRepository.findByPersonIdentifier(event.personIdentifier)
      .filter { event.refreshFrom == null || !it.occurredAt.isBefore(event.refreshFrom) }
      .sortedBy { it.occurredAt }
    val ejs = journeyRepository.findFindAllByMovementsIdIn(movements.map { it.id })
      .asSequence()
      .flatMap { j -> j.movements().map { it to j } }
      .sortedBy { it.first.occurredAt }
      .map { it.second }
      .distinct()
      .toCollection(ArrayDeque())

    val episodes = mutableSetOf<CustodialEpisode>()
    val stays = mutableSetOf<PrisonStay>()
    val journeys = mutableSetOf<ExternalJourney>()
    var currentJourney: ExternalJourney? = null
    var currentStay: PrisonStay? = st.removeFirstOrNull()?.resetMovements(event.refreshFrom)
    var currentEpisode: CustodialEpisode? = ep.removeFirstOrNull()?.resetStays(event.refreshFrom)
    movements.forEach { movement ->
      if (currentJourney == null) {
        currentJourney = (ejs.removeFirstOrNull()?.resetMovements(movement.occurredAt) ?: movement.newJourney()).also { journeys.add(it) }
      }
      currentJourney.applyMovement(movement)
      currentJourney = currentJourney.takeIf { it.status != ExternalJourney.Status.COMPLETED }
      currentStay?.applyMovement(movement)?.also { currentEpisode?.applyStay(it) }
      currentStay = currentStay?.takeIf { it.activeAfter(movement.occurredAt) }?.also { stays.add(it) }
      currentEpisode = currentEpisode?.takeIf { it.activeAfter(movement.occurredAt) }?.also { episodes.add(it) }
      if (movement.movementType == ExternalMovement.Type.ARRIVAL && movement.destination is Prison) {
        if (currentEpisode == null) {
          currentEpisode = ep.removeFirstOrNull() ?: movement.newEpisode().also { episodes.add(it) }
        }
        if (currentStay == null) {
          currentStay = (st.removeFirstOrNull()?.resetMovements(event.refreshFrom) ?: movement.newStay(currentEpisode))
            .also {
              stays.add(it)
              currentEpisode?.applyStay(it)
            }
        }
      }
    }

    episodeRepository.saveAll(episodes)
    stayRepository.saveAll(stays)
    journeyRepository.saveAll(journeys)
    stayRepository.deleteAll(st)
    episodeRepository.deleteAll(ep)
    journeyRepository.deleteAll(ejs)
  }
}

private fun ExternalMovement.newEpisode() = CustodialEpisode(
  series,
  CustodialEpisode.Status.ACTIVE,
  occurredAt,
  null,
)

private fun ExternalMovement.newStay(episode: CustodialEpisode) = PrisonStay(
  episode,
  (destination as Prison).code,
  PrisonStay.Status.RESIDENT,
  occurredAt,
  null,
).applyMovement(this)

private fun ExternalMovement.newJourney() = ExternalJourney(
  personIdentifier,
  journeyType,
  ExternalJourney.Status.IN_TRANSIT,
  if (origin is Prison) (origin as Prison).code else null,
  if (destination is Prison) (destination as Prison).code else null,
  scheduleReference,
)
