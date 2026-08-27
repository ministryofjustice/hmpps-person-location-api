package uk.gov.justice.digital.hmpps.personlocationapi.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.envers.Audited
import org.hibernate.type.SqlTypes
import uk.gov.justice.digital.hmpps.personlocationapi.domain.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.domain.values.Location
import uk.gov.justice.digital.hmpps.personlocationapi.domain.values.MovementLegReason
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "external_leg")
class ExternalLeg(

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
  @Enumerated(EnumType.STRING)
  @Column(name = "type", columnDefinition = "external_leg_type", nullable = false)
  var type: Type,

  @NotNull
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "reason", nullable = false)
  var reason: MovementLegReason,

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

  @Id
  @Column(name = "id", nullable = false)
  override var id: UUID = newUuid(),
) : Identifiable,
  DomainEventProducer {
  @Version
  @Column(name = "version", nullable = false)
  override var version: Int? = null

  enum class Type { ARRIVAL, DEPARTURE }
}
