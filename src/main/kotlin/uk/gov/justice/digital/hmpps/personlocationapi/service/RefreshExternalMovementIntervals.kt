package uk.gov.justice.digital.hmpps.personlocationapi.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetInterval
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.asEntity
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.deduplicateAndPrioritize
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourneyRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.MovementInterval
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.MovementIntervalRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStayRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.findActiveOrAfter
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.findFromCutoffOnward
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.mergeEntities
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.projectIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.projectTimeline

@Transactional
@Service
class RefreshExternalMovementIntervals(
  private val movementRepository: ExternalMovementRepository,
  private val intervalRepository: MovementIntervalRepository,
  private val stayRepository: PrisonStayRepository,
  private val episodeRepository: CustodialEpisodeRepository,
  private val journeyRepository: ExternalJourneyRepository,
) {
  fun execute(event: RefreshPersonMovementIntervals): Unit = with(event) {
    val allMovements = movementRepository.findByPersonIdentifier(personIdentifier)
      .sortedWith(compareBy({ it.occurredAt }, { it.legacyIds?.first }, { it.legacyIds?.second }))
    if (allMovements.isEmpty()) {
      deleteAllProjectionsFrom(personIdentifier)
      return
    }

    val lastMovementBeforeRefresh = event.refreshFrom?.let { refreshFrom ->
      allMovements.lastOrNull { it.occurredAt.isBefore(refreshFrom) }
    }
    val effectiveRefreshFrom = lastMovementBeforeRefresh?.occurredAt ?: event.refreshFrom

    val existingEpisodes = episodeRepository.findActiveOrAfter(personIdentifier, effectiveRefreshFrom)
    val existingStays = stayRepository.findActiveOrAfter(personIdentifier, effectiveRefreshFrom)
    val existingJourneys = journeyRepository.findActiveOrAfter(personIdentifier, effectiveRefreshFrom)
    val existingIntervals = intervalRepository.findFromCutoffOnward(personIdentifier, effectiveRefreshFrom)

    val replayStartAt = listOfNotNull(
      effectiveRefreshFrom,
      existingEpisodes.minOfOrNull { it.commencedAt },
      existingStays.minOfOrNull { it.arrivedAt },
      existingJourneys.minOfOrNull { it.commencedAt },
      existingIntervals.minOfOrNull { it.validFrom },
    ).minOrNull()

    val deduplicatedMovements = allMovements
      .filter { replayStartAt == null || !it.occurredAt.isBefore(replayStartAt) }
      .deduplicateAndPrioritize()

    val targetIntervals = deduplicatedMovements.projectIntervals(effectiveRefreshFrom)
    val targetTimeline = deduplicatedMovements.projectTimeline()

    val activeTargetEpisodes = targetTimeline.episodes.filter {
      effectiveRefreshFrom == null || it.concludedAt == null || !it.concludedAt!!.isBefore(effectiveRefreshFrom)
    }
    val activeTargetStays = targetTimeline.stays.filter {
      effectiveRefreshFrom == null || it.departedAt == null || !it.departedAt!!.isBefore(effectiveRefreshFrom)
    }
    val activeTargetJourneys = targetTimeline.journeys.filter {
      effectiveRefreshFrom == null || it.concludedAt == null || !it.concludedAt!!.isBefore(effectiveRefreshFrom)
    }

    mergeIntervals(personIdentifier, existingIntervals, targetIntervals)
    mergeStays(personIdentifier, existingStays, activeTargetStays)
    mergeEpisodes(personIdentifier, existingEpisodes, activeTargetEpisodes)
    mergeJourneys(personIdentifier, existingJourneys, activeTargetJourneys)
  }

  private fun mergeIntervals(
    personIdentifier: String,
    existing: List<MovementInterval>,
    targets: List<TargetInterval>,
  ) {
    mergeEntities(
      existing,
      targets,
      MovementInterval::validFrom,
      TargetInterval::validFrom,
      MovementInterval::matchTarget,
      { target -> target.asEntity(personIdentifier) },
      intervalRepository::deleteAll,
      intervalRepository::saveAll,
    )
  }

  private fun mergeStays(
    personIdentifier: String,
    existing: List<PrisonStay>,
    targets: List<TargetStay>,
  ) {
    mergeEntities(
      existing,
      targets,
      PrisonStay::arrivedAt,
      TargetStay::arrivedAt,
      PrisonStay::matchTarget,
      { target -> target.asEntity(personIdentifier) },
      stayRepository::deleteAll,
      stayRepository::saveAll,
    )
  }

  private fun mergeEpisodes(
    personIdentifier: String,
    existing: List<CustodialEpisode>,
    targets: List<TargetEpisode>,
  ) {
    mergeEntities(
      existing,
      targets,
      CustodialEpisode::commencedAt,
      TargetEpisode::commencedAt,
      CustodialEpisode::matchTarget,
      { target -> target.asEntity(personIdentifier) },
      episodeRepository::deleteAll,
      episodeRepository::saveAll,
    )
  }

  private fun mergeJourneys(
    personIdentifier: String,
    existing: List<ExternalJourney>,
    targets: List<TargetJourney>,
  ) {
    mergeEntities(
      existing,
      targets,
      ExternalJourney::commencedAt,
      TargetJourney::commencedAt,
      ExternalJourney::matchTarget,
      { target -> target.asEntity(personIdentifier) },
      journeyRepository::deleteAll,
      journeyRepository::saveAll,
    )
  }

  private fun deleteAllProjectionsFrom(personIdentifier: String) {
    intervalRepository.deleteAllByPersonIdentifier(personIdentifier)
    journeyRepository.deleteAllByPersonIdentifier(personIdentifier)
    stayRepository.deleteAllByPersonIdentifier(personIdentifier)
    episodeRepository.deleteAllByPersonIdentifier(personIdentifier)
  }
}
