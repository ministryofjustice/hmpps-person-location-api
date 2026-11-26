package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.CodedReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Court
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.HierarchicalReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Hospital
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.SimpleLocation
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import java.time.LocalDateTime

data class DeduplicatedMovement(
  val occurredAt: LocalDateTime,
  val movementType: ExternalMovement.Type,
  val journeyType: ExternalJourney.Type,
  val origin: Location?,
  val destination: Location?,
  val reason: MovementReason,
  val externalReference: ExternalReference?,
  val legacyBookingIds: Set<Long>,
)

private fun Location?.priority(): Int = when (this) {
  is Prison -> 1
  is Court -> 2
  is Hospital -> 3
  is SimpleLocation -> 4
  null -> 5
}

internal fun List<ExternalMovement>.deduplicateAndPrioritize(): List<DeduplicatedMovement> = this.groupBy { it.occurredAt to it.movementType }
  .map { (key, duplicates) ->
    DeduplicatedMovement(
      occurredAt = key.first,
      movementType = key.second,
      journeyType = duplicates.maxBy { it.journeyType.ordinal }.journeyType,
      origin = duplicates.map { it.origin }.minByOrNull { it.priority() },
      destination = duplicates.map { it.destination }.minByOrNull { it.priority() },
      reason = duplicates.map { it.reason }.distinct()
        .let { if (it.size > 1) HierarchicalReason(it.filterIsInstance<CodedReason>()) else it.single() },
      externalReference = duplicates.firstOrNull { it.scheduleReference != null }?.scheduleReference,
      legacyBookingIds = duplicates.mapNotNull { it.legacyIds?.first }.toSet(),
    )
  }.sortedBy { it.occurredAt }

internal fun List<DeduplicatedMovement>.projectIntervals(effectiveRefreshFrom: LocalDateTime?): List<TargetInterval> = mapIndexedNotNull { idx, current ->
  val next = getOrNull(idx + 1)

  if (effectiveRefreshFrom != null && current.occurredAt.isBefore(effectiveRefreshFrom)) {
    return@mapIndexedNotNull null
  }
  if (next != null && !next.occurredAt.isAfter(current.occurredAt)) {
    return@mapIndexedNotNull null
  }

  TargetInterval(
    validFrom = current.occurredAt,
    validTo = next?.occurredAt,
    movementType = current.movementType,
    origin = requireNotNull(current.origin),
    destination = current.destination,
    journeyType = current.journeyType,
    reason = current.reason,
  )
}

internal fun List<DeduplicatedMovement>.hasStrictAlternation(): Boolean {
  if (size <= 1) return true
  return zipWithNext().all { (first, second) -> first.movementType != second.movementType }
}

internal fun List<DeduplicatedMovement>.normalizeForTimeline(): List<DeduplicatedMovement> {
  val sorted = sortedBy { it.occurredAt }
  if (sorted.hasStrictAlternation()) {
    return sorted
  }

  val normalized = mutableListOf<DeduplicatedMovement>()
  var activePrisonCode: String? = null
  var sawArrivalAtDifferentPrisonSinceDeparture = false

  for (movement in sorted) {
    when (movement.movementType) {
      ExternalMovement.Type.ARRIVAL -> {
        val arrivalPrison = (movement.destination as? Prison)?.code
        val previous = normalized.lastOrNull()

        if (previous?.movementType == ExternalMovement.Type.ARRIVAL) {
          val previousPrison = (previous.destination as? Prison)?.code
          if (arrivalPrison != null && arrivalPrison == previousPrison) {
            normalized[normalized.lastIndex] = previous.mergeWith(movement)
            activePrisonCode = arrivalPrison
            sawArrivalAtDifferentPrisonSinceDeparture = false
            continue
          }
        }

        if (arrivalPrison != null && arrivalPrison == activePrisonCode) {
          val openArrivalIndex = normalized.indexOfLast {
            it.movementType == ExternalMovement.Type.ARRIVAL &&
              (it.destination as? Prison)?.code == arrivalPrison
          }
          val hasDepartureAfterOpenArrival = openArrivalIndex >= 0 &&
            (openArrivalIndex + 1 until normalized.size).any {
              normalized[it].movementType == ExternalMovement.Type.DEPARTURE
            }

          if (openArrivalIndex >= 0 && !hasDepartureAfterOpenArrival) {
            normalized[openArrivalIndex] = normalized[openArrivalIndex].mergeWith(movement)
            sawArrivalAtDifferentPrisonSinceDeparture = false
            continue
          }
        }

        if (arrivalPrison != null && activePrisonCode != null && arrivalPrison != activePrisonCode) {
          sawArrivalAtDifferentPrisonSinceDeparture = true
        }

        activePrisonCode = arrivalPrison
        normalized += movement
      }

      ExternalMovement.Type.DEPARTURE -> {
        val departurePrison = (movement.origin as? Prison)?.code
        val lastDepartureIndex = normalized.indexOfLast {
          it.movementType == ExternalMovement.Type.DEPARTURE &&
            (it.origin as? Prison)?.code == departurePrison
        }

        if (
          departurePrison != null &&
          lastDepartureIndex >= 0 &&
          !sawArrivalAtDifferentPrisonSinceDeparture
        ) {
          val earlierDeparture = normalized[lastDepartureIndex]
          val hasArrivalBetween = (lastDepartureIndex + 1 until normalized.size).any { idx ->
            val item = normalized[idx]
            item.movementType == ExternalMovement.Type.ARRIVAL &&
              (item.destination as? Prison)?.code != departurePrison
          }

          val keepBothForUalThenRelease =
            earlierDeparture.reason.isUal() && !movement.reason.isUal()

          val canCollapseReleases =
            !hasArrivalBetween &&
              !keepBothForUalThenRelease &&
              earlierDeparture.journeyType == ExternalJourney.Type.RELEASE &&
              movement.journeyType == ExternalJourney.Type.RELEASE

          if (canCollapseReleases) {
            normalized[lastDepartureIndex] = movement.mergeWith(earlierDeparture)
            if (departurePrison == activePrisonCode) {
              activePrisonCode = null
            }
            continue
          }
        }

        val hasPrecedingArrival = normalized.any { it.movementType == ExternalMovement.Type.ARRIVAL }
        if (!hasPrecedingArrival) {
          normalized += movement
          continue
        }

        if (departurePrison != null && departurePrison == activePrisonCode) {
          activePrisonCode = null
        }
        sawArrivalAtDifferentPrisonSinceDeparture = false
        normalized += movement
      }
    }
  }

  return normalized
}

private fun MovementReason.isUal(): Boolean = this is CodedReason && isAbscondedOrEscaped()

private fun DeduplicatedMovement.mergeWith(other: DeduplicatedMovement): DeduplicatedMovement = copy(
  journeyType = maxOf(journeyType, other.journeyType, compareBy { it.ordinal }),
  destination = destination ?: other.destination,
  origin = origin ?: other.origin,
  externalReference = externalReference ?: other.externalReference,
  legacyBookingIds = legacyBookingIds + other.legacyBookingIds,
)

internal fun List<DeduplicatedMovement>.projectTimeline(): ProjectedTimeline {
  val episodes = mutableListOf<TargetEpisode>()
  val stays = mutableListOf<TargetStay>()
  val journeys = mutableListOf<TargetJourney>()

  var currentEpisode: TargetEpisode? = null
  var currentStay: TargetStay? = null
  var currentJourney: TargetJourney? = null

  for (index in indices) {
    val (occurredAt, movementType, journeyType, origin, destination, reason, externalReference, legacyBookingIds) = this[index]

    when (movementType) {
      ExternalMovement.Type.DEPARTURE -> {
        if (origin is Prison) {
          val canRelease = if (journeyType == ExternalJourney.Type.RELEASE) {
            shouldProcessRelease(index, reason, currentStay)
          } else {
            true
          }

          if (journeyType == ExternalJourney.Type.RELEASE && canRelease) {
            currentStay?.apply {
              departedAt = occurredAt
              status = PrisonStay.Status.RELEASED
              if (arrivedAt.isEqual(departedAt)) {
                stays.remove(this)
              }
            }
            currentStay = null

            currentEpisode?.apply {
              if (reason is CodedReason && (reason.isAbscondedOrEscaped())) {
                status = CustodialEpisode.Status.UNLAWFULLY_AT_LARGE
              } else {
                concludedAt = occurredAt
                status =
                  if (reason is CodedReason && reason.isDeceased()) CustodialEpisode.Status.DECEASED else CustodialEpisode.Status.COMPLETED
                if (commencedAt.isEqual(concludedAt)) {
                  episodes.remove(this)
                }
                currentEpisode = null
              }
            }
          } else if (journeyType != ExternalJourney.Type.RELEASE) {
            if (currentJourney == null && (destination !is Prison || destination.isRealPrison())) {
              currentJourney = TargetJourney(
                commencedAt = occurredAt,
                journeyType = journeyType,
                origin = origin,
                destination = destination,
                externalReference = externalReference,
              ).also { journeys.add(it) }
            }
            currentStay?.apply {
              if (journeyType == ExternalJourney.Type.TRANSFER) {
                departedAt = occurredAt
                status = PrisonStay.Status.TRANSFERRED_OUT
                if (arrivedAt.isEqual(departedAt)) {
                  stays.remove(this)
                }
                currentStay = null
              } else {
                status = PrisonStay.Status.IN_TRANSIT_EXTERNAL
              }
            }
            currentEpisode?.status = CustodialEpisode.Status.TEMPORARY_ABSENCE
          }
        }
      }

      ExternalMovement.Type.ARRIVAL -> {
        currentStay?.apply {
          if (destination is Prison && destination.code == prisonCode) {
            departedAt = null
            status = PrisonStay.Status.RESIDENT
            this.legacyBookingIds.addAll(legacyBookingIds)
          }
        }
        currentJourney?.apply {
          this.concludedAt = occurredAt
          this.destination = destination ?: this.destination
          if (commencedAt.isEqual(concludedAt)) {
            journeys.remove(this)
          }
        }
        currentJourney = null

        if (destination is Prison) {
          if (currentEpisode == null && destination.isRealPrison()) {
            currentEpisode = TargetEpisode(commencedAt = occurredAt).also { episodes.add(it) }
          } else {
            currentEpisode?.status = CustodialEpisode.Status.ACTIVE
          }

          if (currentStay == null || currentStay.prisonCode != destination.code) {
            currentStay?.apply {
              departedAt = occurredAt
              status = PrisonStay.Status.TRANSFERRED_OUT
              if (arrivedAt.isEqual(departedAt)) {
                stays.remove(this)
              }
            }
            if (destination.isRealPrison()) {
              currentStay = TargetStay(
                arrivedAt = occurredAt,
                prisonCode = destination.code,
                legacyBookingIds = legacyBookingIds.toMutableSet(),
              ).also { stays.add(it) }
            }
          }
        }
      }
    }
  }

  return ProjectedTimeline(episodes, stays, journeys)
}

private fun List<DeduplicatedMovement>.shouldProcessRelease(
  index: Int,
  reason: MovementReason,
  currentStay: TargetStay?,
): Boolean {
  if (!reason.isForMerge()) {
    return true
  }

  val movement = this[index]
  val hasNoLaterMovement = index == lastIndex
  val hasOpenStay = currentStay != null && currentStay.departedAt == null
  val isSameBooking = currentStay?.legacyBookingIds?.size == 1 &&
    movement.legacyBookingIds.size == 1 &&
    currentStay.legacyBookingIds.single() == movement.legacyBookingIds.single()

  return hasOpenStay && isSameBooking && hasNoLaterMovement
}

private fun MovementReason.isForMerge() = (this is CodedReason && isMergeReason())
