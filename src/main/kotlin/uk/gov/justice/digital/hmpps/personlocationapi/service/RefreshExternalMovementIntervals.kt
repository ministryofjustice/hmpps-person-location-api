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
      .onEach { e -> e.resetStays(event.refreshFrom) }
      .toCollection(ArrayDeque())
    val st = stayRepository.findAllByPersonIdentifier(event.personIdentifier)
      .asSequence()
      .sortedBy { it.arrivedAt }
      .dropWhile { e -> event.refreshFrom != null && e.departedAt != null && e.departedAt!!.isBefore(event.refreshFrom) }
      .onEach { s -> s.resetMovements(event.refreshFrom) }
      .toCollection(ArrayDeque())
    val movementMap = movementRepository.findByPersonIdentifier(event.personIdentifier)
      .filter { event.refreshFrom == null || !it.occurredAt.isBefore(event.refreshFrom) }
      .sortedBy { it.occurredAt }
      .groupBy { it.series.id }
    val ejs = journeyRepository.findFindAllByMovementsIdIn(movementMap.values.flatMap { it.map { m -> m.id } })
      .asSequence()
      .flatMap { j -> j.movements().map { it to j } }
      .sortedBy { it.first.occurredAt }
      .map { it.second }
      .distinctBy { it.id }
      .onEach { j -> j.resetMovements(event.refreshFrom) }
      .toCollection(ArrayDeque())

    val episodes = mutableSetOf<CustodialEpisode>()
    val stays = mutableSetOf<PrisonStay>()
    val journeys = mutableSetOf<ExternalJourney>()
    movementMap.forEach { (_, movements) ->
      var currentJourney: ExternalJourney? = ejs.removeFirstOrNull()?.also { journeys.add(it) }
      var currentStay: PrisonStay? = st.removeFirstOrNull()?.also { stays.add(it) }
      var currentEpisode: CustodialEpisode? = ep.removeFirstOrNull()?.also { episodes.add(it) }
      movements.forEach { movement ->
        currentJourney = currentJourney?.takeIf { it.status != ExternalJourney.Status.COMPLETED }
        currentStay = currentStay?.takeIf { it.activeAfter(movement.occurredAt) }
        currentEpisode = currentEpisode?.takeIf { it.activeAfter(movement.occurredAt) }

        if (currentJourney == null) {
          currentJourney = (ejs.removeFirstOrNull() ?: movement.newJourney()).also { journeys.add(it) }
        }

        if (movement.movementType == ExternalMovement.Type.ARRIVAL && movement.destination is Prison) {
          if (currentEpisode == null) {
            currentEpisode = (ep.removeFirstOrNull()?.applySeries(movement.series) ?: movement.newEpisode()).also { episodes.add(it) }
          }
          if (currentStay == null) {
            currentStay = (st.removeFirstOrNull() ?: movement.newStay(currentEpisode)).also { stays.add(it) }
          }
        }

        currentJourney.applyMovement(movement)
        currentStay?.applyMovement(movement)?.also { currentEpisode?.applyStay(it) }
        currentJourney = currentJourney.takeIf { it.status != ExternalJourney.Status.COMPLETED }
      }
    }

    episodeRepository.saveAll(episodes)
    stayRepository.saveAll(stays)
    journeyRepository.saveAll(journeys)
    stayRepository.deleteAll(st + stays.filter { it.movements().isEmpty() })
    episodeRepository.deleteAll(ep + episodes.filter { it.stays().isEmpty() })
    journeyRepository.deleteAll(ejs + journeys.filter { it.movements().isEmpty() })
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
)

private fun ExternalMovement.newJourney() = ExternalJourney(
  personIdentifier,
  journeyType,
  ExternalJourney.Status.IN_TRANSIT,
  if (origin is Prison) (origin as Prison).code else null,
  if (destination is Prison) (destination as Prison).code else null,
  scheduleReference,
)
