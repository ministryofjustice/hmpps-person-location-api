package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import io.sentry.time.Deadline.after
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshAllMovementIntervals.occurredAt
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceConverter
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID

@Entity
@Table(name = "external_journey")
final class ExternalJourney(
  personIdentifier: String,
  type: Type,
  status: Status,
  commencedAt: LocalDateTime,
  concludedAt: LocalDateTime?,
  origin: String,
  destination: String?,
  externalReference: ExternalReference?,
  id: UUID = newUuid(),
) {
  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = id
    private set

  @Version
  @Column(name = "version", nullable = false)
  var version: Int? = null
    private set

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = personIdentifier
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "type", columnDefinition = "external_journey_type", nullable = false)
  var type: Type = type
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "status", columnDefinition = "external_journey_status", nullable = false)
  var status: Status = status
    private set

  @Column(name = "commenced_at", nullable = false)
  var commencedAt: LocalDateTime = commencedAt
    private set

  @Column(name = "concluded_at")
  var concludedAt: LocalDateTime? = concludedAt
    private set

  @Size(max = 6)
  @Column(name = "origin_prison_code", length = 6, nullable = false)
  var origin: String = origin
    private set

  @Size(max = 6)
  @Column(name = "destination_prison_code", length = 6)
  var destination: String? = destination
    private set

  @Convert(converter = ExternalReferenceConverter::class)
  @Column(name = "external_reference")
  var externalReference: ExternalReference? = externalReference
    private set

  @OneToMany
  @JoinTable(
    name = "external_journey_movement",
    joinColumns = [JoinColumn(name = "journey_id", referencedColumnName = "id")],
    inverseJoinColumns = [JoinColumn(name = "movement_id", referencedColumnName = "id")],
  )
  private var movements: MutableSet<ExternalMovement> = linkedSetOf()

  fun movements(): List<ExternalMovement> = Collections.unmodifiableList(movements.sortedBy { it.occurredAt })

  fun resetMovements(after: LocalDateTime?) = apply {
    movements.removeIf { after == null || !it.occurredAt.isBefore(after) }
  }

  fun applyMovement(movement: ExternalMovement) = apply {
    if (movements.isEmpty()) {
      type = movement.journeyType
      commencedAt = movement.occurredAt
      origin = (movement.origin as Prison).code
      externalReference = movement.scheduleReference
    }
    if (movements().isEmpty() || concludedAt?.isBefore(movement.occurredAt) != false) {
      movements.add(movement)
    }
    updateStatus()
  }

  fun activeAfter(dateTime: LocalDateTime): Boolean = !commencedAt.isAfter(dateTime) && concludedAt?.isAfter(dateTime) != false

  private fun completingMovement() = movements.firstOrNull {
    (it.movementType == ExternalMovement.Type.ARRIVAL && it.destination is Prison) ||
      (it.movementType == ExternalMovement.Type.DEPARTURE && it.journeyType == Type.RELEASE)
  }

  private fun updateStatus() {
    val completingMovement = completingMovement()
    destination = (completingMovement?.destination?.takeIf { it is Prison } as? Prison)?.code
    concludedAt = completingMovement?.occurredAt
    status = if (completingMovement != null) Status.COMPLETED else Status.IN_TRANSIT
  }

  enum class Type { ADMISSION, COURT_APPEARANCE, TEMPORARY_ABSENCE, TRANSFER, RELEASE }
  enum class Status { SCHEDULED, IN_TRANSIT, COMPLETED, CANCELLED }
}

interface ExternalJourneyRepository : JpaRepository<ExternalJourney, UUID> {
  fun findFindAllByMovementsIdIn(movementIds: Collection<UUID>): List<ExternalJourney>

  @Modifying
  @Query("delete from external_journey_movement where movement_id in (:movementIds)", nativeQuery = true)
  fun removeMovementLinks(movementIds: Collection<UUID>)
}
