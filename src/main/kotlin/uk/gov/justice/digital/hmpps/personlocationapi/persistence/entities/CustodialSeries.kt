package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.envers.Audited
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "custodial_series")
class CustodialSeries(
  personIdentifier: String,
  status: Status,
  isActive: Boolean,
  openedAt: LocalDateTime,
  closedAt: LocalDateTime?,
  notes: String?,
  legacyBookingReference: String?,
  legacyId: Long?,
  id: UUID = newUuid(),
) : Identifiable {

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

  @Column(name = "status", columnDefinition = "custodial_series_status", nullable = false)
  final var status: Status = status
    private set

  @Column(name = "is_active", nullable = false)
  final var isActive: Boolean = isActive
    private set

  @Column(name = "opened_at", nullable = false)
  final var openedAt: LocalDateTime = openedAt
    private set

  @Column(name = "closed_at")
  final var closedAt: LocalDateTime? = closedAt
    private set

  @Column(name = "notes", length = Integer.MAX_VALUE)
  final var notes: String? = notes
    private set

  @Column(name = "legacy_booking_reference")
  final var legacyBookingReference: String? = legacyBookingReference
    private set

  @Column(name = "legacy_id")
  final var legacyId: Long? = legacyId
    private set

  enum class Status { OPEN, CLOSED }
}

interface CustodialSeriesRepository : JpaRepository<CustodialSeries, UUID> {
  fun findByLegacyId(legacyId: Long): CustodialSeries?
}