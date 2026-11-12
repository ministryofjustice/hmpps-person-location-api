package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetInterval
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "movement_interval")
final class MovementInterval(
  personIdentifier: String,
  journeyType: ExternalJourney.Type,
  movementType: ExternalMovement.Type,
  validFrom: LocalDateTime,
  validTo: LocalDateTime?,
  origin: Location,
  destination: Location?,
  id: UUID = newUuid(),
) : Identifiable {
  @Id
  @Column(name = "id", nullable = false)
  override var id: UUID = id
    private set

  @Version
  @Column(name = "version", nullable = false)
  override var version: Int? = null
    private set

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = personIdentifier
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "journey_type", columnDefinition = "external_journey_type", nullable = false)
  var journeyType: ExternalJourney.Type = journeyType
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "movement_type", columnDefinition = "external_movement_type", nullable = false)
  var movementType: ExternalMovement.Type = movementType
    private set

  @Column(name = "valid_from", nullable = false)
  var validFrom: LocalDateTime = validFrom
    private set

  @Column(name = "valid_to")
  var validTo: LocalDateTime? = validTo
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "origin", nullable = false)
  var origin: Location = origin
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "destination")
  var destination: Location? = destination
    private set

  fun matchTarget(target: TargetInterval) {
    validFrom = target.validFrom
    validTo = target.validTo
    origin = target.origin
    destination = target.destination
    movementType = target.movementType
    journeyType = target.journeyType
  }
}

interface MovementIntervalRepository : JpaRepository<MovementInterval, UUID> {
  fun deleteAllByPersonIdentifier(personIdentifier: String)

  fun findAllByPersonIdentifierOrderByValidFromAsc(personIdentifier: String): List<MovementInterval>

  fun findAllByPersonIdentifierAndValidFromGreaterThanEqualOrderByValidFromAsc(
    personIdentifier: String,
    validFrom: LocalDateTime,
  ): List<MovementInterval>
}

fun MovementIntervalRepository.findFromCutoffOnward(
  personIdentifier: String,
  effectiveRefreshFrom: LocalDateTime?,
): List<MovementInterval> = effectiveRefreshFrom?.let {
  findAllByPersonIdentifierAndValidFromGreaterThanEqualOrderByValidFromAsc(personIdentifier, effectiveRefreshFrom)
} ?: findAllByPersonIdentifierOrderByValidFromAsc(personIdentifier)
