package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PostLoad
import jakarta.persistence.Table
import jakarta.persistence.Transient
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.hibernate.envers.Audited
import org.hibernate.type.SqlTypes
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyDestination
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyJourneyDetails
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyNotes
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOccurredAt
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyOrigin
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyReason
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.ApplyType
import uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement.MovementAction
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.DomainEventProducer
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceConverter
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "external_movement")
final class ExternalMovement(
  series: CustodialSeries,
  journeyType: ExternalJourney.Type,
  movementType: Type,
  reason: MovementReason,
  occurredAt: LocalDateTime,
  origin: Location,
  destination: Location?,
  notes: String?,
  scheduleReference: ExternalReference?,
  legacyId: String?,
  id: UUID = newUuid(),
) : Identifiable,
  DomainEventProducer {
  @Id
  @Column(name = "id", nullable = false)
  override var id: UUID = id
    private set

  @Version
  @Column(name = "version", nullable = false)
  override var version: Int? = null
    private set

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "series_id", nullable = false)
  var series: CustodialSeries = series
    private set(value) {
      field = value
      personIdentifier = value.personIdentifier
    }

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = series.personIdentifier
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "journey_type", columnDefinition = "external_journey_type", nullable = false)
  var journeyType: ExternalJourney.Type = journeyType
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "movement_type", columnDefinition = "external_movement_type", nullable = false)
  var movementType: Type = movementType
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reason", nullable = false)
  var reason: MovementReason = reason
    private set

  @Column(name = "occurred_at", nullable = false)
  var occurredAt: LocalDateTime = occurredAt
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "origin", nullable = false)
  var origin: Location = origin
    private set

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "destination")
  var destination: Location? = destination
    private set

  @Column(name = "notes", length = Integer.MAX_VALUE)
  var notes: String? = notes
    private set

  @Convert(converter = ExternalReferenceConverter::class)
  @Column(name = "schedule_reference")
  var scheduleReference: ExternalReference? = scheduleReference
    private set

  @Column(name = "legacy_id")
  var legacyId: String? = legacyId
    private set

  @Transient
  private var appliedActions: List<MovementAction> = listOf()

  @PostLoad
  private fun load() {
    appliedActions = listOf()
  }

  fun applySeries(series: CustodialSeries) = apply {
    this.series = series
  }

  fun applyType(action: ApplyType) = apply {
    if (action changes this) {
      movementType = action.type
      appliedActions += action
    }
  }

  fun applyJourneyDetails(action: ApplyJourneyDetails) = apply {
    if (action changes this) {
      journeyType = action.journeyType
      scheduleReference = action.scheduleReference
      appliedActions += action
    }
  }

  fun applyReason(action: ApplyReason) = apply {
    if (action changes this) {
      reason = action.reason
      appliedActions += action
    }
  }

  fun applyOccurredAt(action: ApplyOccurredAt) = apply {
    if (action changes this) {
      occurredAt = action.occurredAt
      appliedActions += action
    }
  }

  fun applyOrigin(action: ApplyOrigin) = apply {
    if (action changes this) {
      origin = action.origin
      appliedActions += action
    }
  }

  fun applyDestination(action: ApplyDestination) = apply {
    if (action changes this) {
      destination = action.destination
      appliedActions += action
    }
  }

  fun applyNotes(action: ApplyNotes) = apply {
    if (action changes this) {
      notes = action.notes
      appliedActions += action
    }
  }

  fun applyLegacyId(legacyId: String?) = apply {
    this.legacyId = legacyId
  }

  enum class Type { ARRIVAL, DEPARTURE }
}
