package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison
import java.time.LocalDateTime
import java.util.Collections
import java.util.UUID

@Entity
@Table(name = "prison_stay")
final class PrisonStay(
  episode: CustodialEpisode,
  prisonCode: String,
  status: Status,
  arrivedAt: LocalDateTime,
  departedAt: LocalDateTime?,
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

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "episode_id", nullable = false)
  var episode: CustodialEpisode = episode
    private set(value) {
      field = value
      personIdentifier = value.personIdentifier
    }

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = episode.personIdentifier
    private set

  @Size(max = 6)
  @Column(name = "prison_code", nullable = false, length = 6)
  var prisonCode: String = prisonCode
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "status", columnDefinition = "prison_stay_status", nullable = false)
  var status: Status = status
    private set

  @Column(name = "arrived_at", nullable = false)
  var arrivedAt: LocalDateTime = arrivedAt
    private set

  @Column(name = "departed_at")
  var departedAt: LocalDateTime? = departedAt
    private set

  @OneToMany(cascade = [CascadeType.ALL])
  @JoinTable(
    name = "prison_stay_movement",
    joinColumns = [JoinColumn(name = "stay_id", referencedColumnName = "id")],
    inverseJoinColumns = [JoinColumn(name = "movement_id", referencedColumnName = "id")],
  )
  private var movements: MutableList<ExternalMovement> = mutableListOf()

  fun movements(): List<ExternalMovement> = Collections.unmodifiableList(movements.sortedBy { it.occurredAt })

  fun activeAfter(dateTime: LocalDateTime) = !arrivedAt.isAfter(dateTime) && departedAt?.isAfter(dateTime) != false

  fun resetMovements() = apply {
    movements.clear()
  }

  fun applyMovement(movement: ExternalMovement) = apply {
    if (
      (movement.origin is Prison && movement.movementType == ExternalMovement.Type.DEPARTURE) ||
      (movement.destination is Prison && movement.movementType == ExternalMovement.Type.ARRIVAL)
    ) {
      when (movement.movementType) {
        ExternalMovement.Type.DEPARTURE -> handleDeparture(movement)
        ExternalMovement.Type.ARRIVAL -> handleArrival(movement)
      }
    }
  }

  fun handleDeparture(movement: ExternalMovement) {
    if (movements.isEmpty()) {
      prisonCode = (movement.origin as Prison).code
      arrivedAt = movement.occurredAt
    }
    if ((movement.origin as Prison).code == prisonCode) {
      movements.add(movement)
      when (movement.journeyType) {
        ExternalJourney.Type.RELEASE -> {
          departedAt = movement.occurredAt
          status = Status.RELEASED
          episode.concludeEpisode(movement.occurredAt, CustodialEpisode.Status.COMPLETED)
        }

        else -> {
          status = Status.IN_TRANSIT_EXTERNAL
        }
      }
    }
  }

  fun handleArrival(movement: ExternalMovement) {
    if (movements.isEmpty()) {
      prisonCode = (movement.destination as Prison).code
      status = Status.RESIDENT
      arrivedAt = movement.occurredAt
      departedAt = null
    }
    if ((movement.destination as Prison).code == prisonCode) {
      movements.add(movement)
    } else {
      departedAt = movement.occurredAt
      status = Status.TRANSFERRED_OUT
    }
  }

  enum class Status { RESIDENT, IN_TRANSIT_EXTERNAL, TRANSFERRED_OUT, RELEASED }
}

interface PrisonStayRepository : JpaRepository<PrisonStay, UUID> {
  fun findAllByPersonIdentifier(personIdentifier: String): MutableList<PrisonStay>
}
