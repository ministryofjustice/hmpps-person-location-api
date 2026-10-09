package uk.gov.justice.digital.hmpps.personlocationapi

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
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
) {
  fun execute(event: RefreshPersonMovementIntervals) {
    val ep = episodeRepository.findAllByPersonIdentifier(event.personIdentifier)
      .sortedBy { it.commencedAt }
      .dropWhile { e -> event.refreshFrom == null && e.concludedAt == null && e.concludedAt!!.isBefore(event.refreshFrom) }
      .toCollection(ArrayDeque())
    val st = stayRepository.findAllByPersonIdentifier(event.personIdentifier)
      .sortedBy { it.arrivedAt }
      .dropWhile { e -> event.refreshFrom == null && e.departedAt == null && e.departedAt!!.isBefore(event.refreshFrom) }
      .toCollection(ArrayDeque())
    val movements = movementRepository.findByPersonIdentifier(event.personIdentifier)
      .filter { event.refreshFrom == null || !it.occurredAt.isBefore(event.refreshFrom) }
      .sortedBy { it.occurredAt }

    val episodes = mutableSetOf<CustodialEpisode>()
    val stays = mutableSetOf<PrisonStay>()
    var currentStay: PrisonStay? = st.removeFirstOrNull()?.resetMovements()
    var currentEpisode: CustodialEpisode? = currentStay?.episode?.also { ep.removeIf { it.id == currentStay?.episode?.id } } ?: ep.removeFirstOrNull()
    movements.forEach { movement ->
      currentStay?.applyMovement(movement)
      currentStay = currentStay?.takeIf { it.activeAfter(movement.occurredAt) }?.also { stays.add(it) }
      currentEpisode = currentEpisode?.takeIf { it.activeAt(movement.occurredAt) }?.also { episodes.add(it) }
      if (movement.movementType == ExternalMovement.Type.ARRIVAL && movement.destination is Prison) {
        if (currentEpisode == null) {
          currentEpisode = movement.newEpisode().also { episodes.add(it) }
        }
        if (currentStay == null) {
          currentStay = (st.removeFirstOrNull()?.resetMovements() ?: movement.newStay(currentEpisode)).also { stays.add(it) }
        }
      }
    }
    episodeRepository.saveAll(episodes)
    stayRepository.saveAll(stays)
    stayRepository.deleteAll(st)
    episodeRepository.deleteAll(ep)
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
