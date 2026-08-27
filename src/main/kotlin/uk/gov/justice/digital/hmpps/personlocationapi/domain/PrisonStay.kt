package uk.gov.justice.digital.hmpps.personlocationapi.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
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
@Table(name = "prison_stay")
class PrisonStay(
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "episode_id", nullable = false)
  var episode: CustodialEpisode,

  @Size(max = 7)
  @NotNull
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String,

  @Size(max = 6)
  @NotNull
  @Column(name = "prison_code", nullable = false, length = 6)
  var prisonCode: String,

  @Column(name = "status", columnDefinition = "prison_stay_status", nullable = false)
  var status: Status,

  @NotNull
  @Column(name = "is_active", nullable = false)
  var isActive: Boolean,

  @NotNull
  @Column(name = "admitted_at", nullable = false)
  var admittedAt: LocalDateTime,

  @Column(name = "released_at")
  var releasedAt: LocalDateTime?,

  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = newUuid(),
) {
  @Version
  @Column(name = "version", nullable = false)
  var version: Int? = null

  enum class Status { RESIDENT, IN_TRANSIT_EXTERNAL, TRANSFERRED_OUT, RELEASED }
}
