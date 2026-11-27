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
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.normalizeForTimeline
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
    val allMovements = movementRepository.findByPersonIdentifierReadOnly(personIdentifier)
      .sortedWith(compareBy({ it.occurredAt }, { it.legacyIds?.first }, { it.legacyIds?.second }))
    if (allMovements.isEmpty()) {
      deleteAllProjectionsFrom(personIdentifier)
      return
    }

    val lastMovementBeforeRefresh = event.refreshFrom?.let { refreshFrom ->
      allMovements.lastOrNull { it.occurredAt.isBefore(refreshFrom) }
    }
    val effectiveRefreshFrom = lastMovementBeforeRefresh?.occurredAt ?: event.refreshFrom

    val initialEpisodes = episodeRepository.findActiveOrAfter(personIdentifier, effectiveRefreshFrom)
    val initialStays = stayRepository.findActiveOrAfter(personIdentifier, effectiveRefreshFrom)
    val initialJourneys = journeyRepository.findActiveOrAfter(personIdentifier, effectiveRefreshFrom)
    val initialIntervals = intervalRepository.findFromCutoffOnward(personIdentifier, effectiveRefreshFrom)

    val requiresFullRebuild = initialEpisodes.isEmpty() || initialStays.isEmpty()
    val effectiveCutoff = if (requiresFullRebuild) null else effectiveRefreshFrom

    val existingEpisodes = if (requiresFullRebuild) {
      episodeRepository.findActiveOrAfter(personIdentifier, null)
    } else {
      initialEpisodes
    }
    val existingStays = if (requiresFullRebuild) {
      stayRepository.findActiveOrAfter(personIdentifier, null)
    } else {
      initialStays
    }
    val existingJourneys = if (requiresFullRebuild) {
      journeyRepository.findActiveOrAfter(personIdentifier, null)
    } else {
      initialJourneys
    }
    val existingIntervals = if (requiresFullRebuild) {
      intervalRepository.findFromCutoffOnward(personIdentifier, null)
    } else {
      initialIntervals
    }

    val replayStartAt = if (requiresFullRebuild) {
      null
    } else {
      listOfNotNull(
        effectiveCutoff,
        existingEpisodes.minOfOrNull { it.commencedAt },
        existingStays.minOfOrNull { it.arrivedAt },
        existingJourneys.minOfOrNull { it.commencedAt },
        existingIntervals.minOfOrNull { it.validFrom },
      ).minOrNull()
    }

    val deduplicatedMovements = allMovements
      .deduplicateAndPrioritize()
      .filter { replayStartAt == null || !it.occurredAt.isBefore(replayStartAt) }

    val orderedMovements = deduplicatedMovements.normalizeForTimeline()
    val targetIntervals = orderedMovements.projectIntervals(effectiveCutoff)
    val targetTimeline = orderedMovements.projectTimeline()

    val activeTargetEpisodes = targetTimeline.episodes.filter {
      effectiveCutoff == null || it.concludedAt == null || !it.concludedAt!!.isBefore(effectiveCutoff)
    }
    val activeTargetStays = targetTimeline.stays.filter {
      effectiveCutoff == null || it.departedAt == null || !it.departedAt!!.isBefore(effectiveCutoff)
    }
    val activeTargetJourneys = targetTimeline.journeys.filter {
      effectiveCutoff == null || it.concludedAt == null || !it.concludedAt!!.isBefore(effectiveCutoff)
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
