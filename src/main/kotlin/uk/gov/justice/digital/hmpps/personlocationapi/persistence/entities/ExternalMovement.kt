package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorColumn
import jakarta.persistence.DiscriminatorType
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.Inheritance
import jakarta.persistence.InheritanceType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.envers.Audited
import org.hibernate.type.SqlTypes
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

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "journey_id", nullable = false)
  var journey: ExternalJourney,

  @NotNull
  @Column(name = "sequence", nullable = false)
  var sequence: Int,

  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "stay_id", nullable = false)
  var stay: PrisonStay,

  @Size(max = 7)
  @NotNull
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String,

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reason", nullable = false)
  var reason: MovementReason,

  @NotNull
  @Column(name = "occurred_at", nullable = false)
  var occurredAt: LocalDateTime,

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "origin", nullable = false)
  var origin: Location,

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "destination")
  var destination: Location?,

  @Column(name = "notes", length = Integer.MAX_VALUE)
  var notes: String?,

  @Column(name = "legacy_id")
  var legacyId: String?,

  @Id
  @Column(name = "id", nullable = false)
  override var id: UUID = newUuid(),
) : Identifiable,
  DomainEventProducer {
  @Version
  @Column(name = "version", nullable = false)
  override var version: Int? = null

  companion object {
    protected const val ARRIVAL = "ARRIVAL"
    protected const val DEPARTURE = "DEPARTURE"
  }
}

@Entity
@DiscriminatorValue(ExternalMovement.ARRIVAL)
class Arrival(
  journey: ExternalJourney,
  sequence: Int,
  stay: PrisonStay,
  personIdentifier: String,
  reason: MovementReason,
  occurredAt: LocalDateTime,
  origin: Location,
  destination: Location?,
  notes: String?,
  legacyId: String?,
) : ExternalMovement(journey, sequence, stay, personIdentifier, reason, occurredAt, origin, destination, notes, legacyId)

@Entity
@DiscriminatorValue(ExternalMovement.DEPARTURE)
class Departure(
  journey: ExternalJourney,
  sequence: Int,
  stay: PrisonStay,
  personIdentifier: String,
  reason: MovementReason,
  occurredAt: LocalDateTime,
  origin: Location,
  destination: Location?,
  notes: String?,
  legacyId: String?,
) : ExternalMovement(journey, sequence, stay, personIdentifier, reason, occurredAt, origin, destination, notes, legacyId)
