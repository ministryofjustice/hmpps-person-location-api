package uk.gov.justice.digital.hmpps.personlocationapi.integration.config

import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.newId
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.prisonCode
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.word
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement.Type
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.CodedReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.SimpleLocation
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import java.time.LocalDateTime
import java.util.UUID

typealias ExternalMovementProvider = () -> ExternalMovement

interface ExternalMovementOperations {
  fun findMovement(uuid: UUID): ExternalMovement?
  fun givenMovement(emp: ExternalMovementProvider): ExternalMovement
}

class ExternalMovementOperationsImpl(
  private val movementRepository: ExternalMovementRepository,
) : ExternalMovementOperations {
  override fun findMovement(uuid: UUID): ExternalMovement? = movementRepository.findByIdOrNull(uuid)
  override fun givenMovement(emp: ExternalMovementProvider): ExternalMovement = movementRepository.save(emp())

  companion object {
    fun reason(code: String = word(6), description: String = word(40)) = CodedReason(code, description)
    fun prison(code: String = prisonCode(), description: String = word(25)) = Prison(code, description)
    fun outOfPrison(description: String = word(20)) = SimpleLocation(description)

    fun externalMovement(
      series: CustodialSeries,
      journeyType: ExternalJourney.Type = ExternalJourney.Type.entries.random(),
      movementType: Type = Type.entries.random(),
      reason: MovementReason = reason(),
      occurredAt: LocalDateTime = LocalDateTime.now(),
      origin: Location = if (movementType == Type.DEPARTURE) prison() else outOfPrison(),
      destination: Location? = if (movementType == Type.DEPARTURE) null else prison(),
      notes: String? = word(20),
      scheduleReference: ExternalReference? = null,
      legacyId: String? = "${newId()}_${newId()}",
      id: UUID = newUuid(),
    ): ExternalMovementProvider = {
      ExternalMovement(
        series,
        journeyType,
        movementType,
        reason,
        occurredAt,
        origin,
        destination,
        notes,
        scheduleReference,
        legacyId,
        id,
      )
    }
  }
}
