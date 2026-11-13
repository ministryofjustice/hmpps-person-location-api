package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetEpisode
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "custodial_episode")
final class CustodialEpisode(
  personIdentifier: String,
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

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = personIdentifier
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

  fun applyPerson(personIdentifier: String) {
    this.personIdentifier = personIdentifier
  }

  fun matchTarget(target: TargetEpisode) {
    commencedAt = target.commencedAt
    concludedAt = target.concludedAt
    status = target.status
  }

  enum class Status { ACTIVE, TEMPORARY_ABSENCE, COMPLETED, UNLAWFULLY_AT_LARGE, DECEASED }
}

interface CustodialEpisodeRepository : JpaRepository<CustodialEpisode, UUID> {
  fun findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier: String): MutableList<CustodialEpisode>
  fun deleteAllByPersonIdentifier(personIdentifier: String)

  @Query(
    """
    select e from CustodialEpisode e
    where e.personIdentifier = :personIdentifier
      and (e.concludedAt is null or e.concludedAt >= :cutoff)
    order by e.commencedAt asc
    """,
  )
  fun findActiveOrAfterCutoff(personIdentifier: String, cutoff: LocalDateTime): List<CustodialEpisode>

  @Query(
    """
    select ce from CustodialEpisode ce
    where ce.personIdentifier = :personIdentifier
    order by ce.commencedAt desc
    limit 2
  """,
  )
  fun findMostRecentFor(personIdentifier: String): List<CustodialEpisode>
}

fun CustodialEpisodeRepository.findActiveOrAfter(personIdentifier: String, cutoff: LocalDateTime?) = cutoff?.let {
  findActiveOrAfterCutoff(personIdentifier, it)
} ?: findAllByPersonIdentifierOrderByCommencedAtDesc(personIdentifier).reversed()
