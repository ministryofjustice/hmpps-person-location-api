package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

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
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import java.time.LocalDateTime
import java.util.UUID

@Audited
@Entity
@Table(name = "custodial_episode")
class CustodialEpisode(
  @NotNull
  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "series_id", nullable = false)
  var series: CustodialSeries,

  @Size(max = 7)
  @NotNull
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String,

  @Column(name = "status", columnDefinition = "episode_status", nullable = false)
  var status: Status,

  @NotNull
  @Column(name = "commenced_at", nullable = false)
  var commencedAt: LocalDateTime,

  @Column(name = "concluded_at")
  var concludedAt: LocalDateTime?,

  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = newUuid(),
) {
  @Version
  @Column(name = "version", nullable = false)
  var version: Int? = null

  enum class Status { ACTIVE, TEMPORARY_ABSENCE, COMPLETED, UAL, DECEASED, CANCELLED }
}
