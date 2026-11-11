package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.MovementInterval
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.PrisonStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import java.time.LocalDateTime

data class ProjectedTimeline(
  val episodes: List<TargetEpisode>,
  val stays: List<TargetStay>,
  val journeys: List<TargetJourney>,
)

data class TargetInterval(
  val validFrom: LocalDateTime,
  val validTo: LocalDateTime?,
  val movementType: ExternalMovement.Type,
  val journeyType: ExternalJourney.Type,
  val origin: Location,
  val destination: Location?,
)

data class TargetJourney(
  val journeyType: ExternalJourney.Type,
  val commencedAt: LocalDateTime,
  val origin: Prison,
  var concludedAt: LocalDateTime? = null,
  var destination: Location? = null,
  var externalReference: ExternalReference? = null,
)

data class TargetStay(
  val prisonCode: String,
  val arrivedAt: LocalDateTime,
  var departedAt: LocalDateTime? = null,
  var status: PrisonStay.Status = PrisonStay.Status.RESIDENT,
)

data class TargetEpisode(
  val commencedAt: LocalDateTime,
  var concludedAt: LocalDateTime? = null,
  var status: CustodialEpisode.Status = CustodialEpisode.Status.ACTIVE,
)

internal fun TargetInterval.asEntity(personIdentifier: String) = MovementInterval(
  personIdentifier = personIdentifier,
  journeyType = journeyType,
  movementType = movementType,
  validFrom = validFrom,
  validTo = validTo,
  origin = origin,
  destination = destination,
)

internal fun TargetStay.asEntity(personIdentifier: String) = PrisonStay(personIdentifier, prisonCode, status, arrivedAt, departedAt)

internal fun TargetEpisode.asEntity(personIdentifier: String) = CustodialEpisode(personIdentifier, status, commencedAt, concludedAt)

internal fun TargetJourney.asEntity(personIdentifier: String) = ExternalJourney(
  personIdentifier = personIdentifier,
  type = journeyType,
  commencedAt = commencedAt,
  concludedAt = concludedAt,
  status = if (concludedAt == null) ExternalJourney.Status.IN_TRANSIT else ExternalJourney.Status.COMPLETED,
  origin = origin.code,
  destination = (destination?.takeIf { it is Prison } as? Prison)?.code,
  externalReference = externalReference,
)
