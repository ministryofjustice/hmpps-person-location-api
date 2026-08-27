package uk.gov.justice.digital.hmpps.personlocationapi.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import org.hibernate.envers.Audited
import uk.gov.justice.digital.hmpps.personlocationapi.domain.IdGenerator.newUuid
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "custodial_career")
class CustodialCareer(
  @Size(max = 7)
  @NotNull
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String,

  @Column(name = "status", columnDefinition = "custodial_career_status", nullable = false)
  var status: Status,

  @NotNull
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean,

  @NotNull
  @Column(name = "opened_at", nullable = false)
  var openedAt: LocalDateTime,

  @Column(name = "closed_at")
  var closedAt: LocalDateTime?,

  @Column(name = "notes", length = Integer.MAX_VALUE)
  var notes: String?,

  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = newUuid(),
) {
  @Version
  @Column(name = "version", nullable = false)
  var version: Int? = null

  enum class Status { OPEN, CLOSED }
}
