package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.springframework.data.jpa.repository.JpaRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "custodial_episode")
final class CustodialEpisode(
  series: CustodialSeries,
  status: Status,
  commencedAt: LocalDateTime,
  concludedAt: LocalDateTime?,
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
  @Column(name = "status", columnDefinition = "episode_status", nullable = false)
  var status: Status = status
    private set

  @Column(name = "commenced_at", nullable = false)
  var commencedAt: LocalDateTime = commencedAt
    private set

  @Column(name = "concluded_at")
  var concludedAt: LocalDateTime? = concludedAt
    private set

  internal fun concludeEpisode(dateTime: LocalDateTime, status: Status) = apply {
    this.concludedAt = dateTime
    this.status = status
  }

  fun activeAt(dateTime: LocalDateTime): Boolean = !commencedAt.isAfter(dateTime) && concludedAt?.isAfter(dateTime) != false

  enum class Status { ACTIVE, TEMPORARY_ABSENCE, COMPLETED, UAL, DECEASED, CANCELLED }
}

interface CustodialEpisodeRepository : JpaRepository<CustodialEpisode, UUID> {
  fun findAllByPersonIdentifier(personIdentifier: String): MutableList<CustodialEpisode>
}
