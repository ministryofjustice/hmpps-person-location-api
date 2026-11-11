package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.CodedReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Court
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
)

private fun Location?.priority(): Int = when (this) {
  is Prison -> 1
  is Court -> 2
  is Hospital -> 3
  is SimpleLocation -> 4
  null -> 5
}

internal fun List<ExternalMovement>.deduplicateAndPrioritize(): List<DeduplicatedMovement> = this.groupBy { Triple(it.occurredAt, it.movementType, it.journeyType) }
  .map { (key, duplicates) ->
    DeduplicatedMovement(
      occurredAt = key.first,
      movementType = key.second,
      journeyType = key.third,
      origin = duplicates.map { it.origin }.minByOrNull { it.priority() },
      destination = duplicates.map { it.destination }.minByOrNull { it.priority() },
      reason = duplicates.map { it.reason }.distinct().single(),
      externalReference = duplicates.firstOrNull { it.scheduleReference != null }?.scheduleReference,
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
  )
}

internal fun List<DeduplicatedMovement>.projectTimeline(): ProjectedTimeline {
  val episodes = mutableListOf<TargetEpisode>()
  val stays = mutableListOf<TargetStay>()
  val journeys = mutableListOf<TargetJourney>()

  var currentEpisode: TargetEpisode? = null
  var currentStay: TargetStay? = null
  var currentJourney: TargetJourney? = null

  for ((occurredAt, movementType, journeyType, origin, destination, reason, externalReference) in this) {
    when (movementType) {
      ExternalMovement.Type.DEPARTURE -> {
        if (origin is Prison) {
          if (journeyType == ExternalJourney.Type.RELEASE) {
            currentStay?.apply {
              departedAt = occurredAt
              status = PrisonStay.Status.RELEASED
            }
            currentStay = null

            currentEpisode?.apply {
              if (reason is CodedReason && (reason.isAbscondedOrEscaped())) {
                status = CustodialEpisode.Status.UNLAWFULLY_AT_LARGE
              } else {
                concludedAt = occurredAt
                status = if (reason is CodedReason && reason.isDeceased()) CustodialEpisode.Status.DECEASED else CustodialEpisode.Status.COMPLETED
                currentEpisode = null
              }
            }
          } else {
            if (currentJourney == null) {
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
          }
        }
        currentJourney?.apply {
          this.concludedAt = occurredAt
          this.destination = destination ?: this.destination
        }
        currentJourney = null

        if (destination is Prison) {
          if (currentEpisode == null) {
            currentEpisode = TargetEpisode(commencedAt = occurredAt).also { episodes.add(it) }
          } else {
            currentEpisode.status = CustodialEpisode.Status.ACTIVE
          }

          if (currentStay == null || currentStay.prisonCode != destination.code) {
            currentStay?.apply {
              departedAt = occurredAt
              status = PrisonStay.Status.TRANSFERRED_OUT
            }
            currentStay = TargetStay(
              arrivedAt = occurredAt,
              prisonCode = destination.code,
            ).also { stays.add(it) }
          }
        }
      }
    }
  }

  return ProjectedTimeline(episodes, stays, journeys)
}
