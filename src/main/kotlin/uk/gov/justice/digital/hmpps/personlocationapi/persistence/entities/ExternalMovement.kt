package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.envers.Audited
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import software.amazon.awssdk.services.sns.endpoints.internal.Value
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.DomainEventProducer
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "external_movement")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
abstract class ExternalMovement(
  personIdentifier: String,
  reason: MovementReason,
  occurredAt: LocalDateTime,
  origin: Location,
  destination: Location?,
  notes: String?,
  legacyId: String?,
  id: UUID = newUuid(),
) : Identifiable,
  DomainEventProducer {
  @Id
  @Column(name = "id", nullable = false)
  final override var id: UUID = id
    private set

  @Version
  @Column(name = "version", nullable = false)
  final override var version: Int? = null
    private set

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  final var personIdentifier: String = personIdentifier
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reason", nullable = false)
  final var reason: MovementReason = reason
    private set

  @Column(name = "occurred_at", nullable = false)
  final var occurredAt: LocalDateTime = occurredAt
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "origin", nullable = false)
  final var origin: Location = origin
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "destination")
  final var destination: Location? = destination
    private set

  @Column(name = "notes", length = Integer.MAX_VALUE)
  final var notes: String? = notes
    private set

  @Column(name = "legacy_id")
  final var legacyId: String? = legacyId
    private set

  companion object {
    protected const val ARRIVAL = "ARRIVAL"
    protected const val DEPARTURE = "DEPARTURE"
  }
}

@Entity
@DiscriminatorValue(ExternalMovement.ARRIVAL)
class Arrival(
  personIdentifier: String,
  reason: MovementReason,
  occurredAt: LocalDateTime,
  origin: Location,
  destination: Location?,
  notes: String?,
  legacyId: String?,
) : ExternalMovement(personIdentifier, reason, occurredAt, origin, destination, notes, legacyId)

@Entity
@DiscriminatorValue(ExternalMovement.DEPARTURE)
class Departure(
  personIdentifier: String,
  reason: MovementReason,
  occurredAt: LocalDateTime,
  origin: Location,
  destination: Location?,
  notes: String?,
  legacyId: String?,
) : ExternalMovement(personIdentifier, reason, occurredAt, origin, destination, notes, legacyId)

interface ExternalMovementRepository : JpaRepository<ExternalMovement, UUID> {
  fun findByLegacyId(legacyId: String): ExternalMovement?
}