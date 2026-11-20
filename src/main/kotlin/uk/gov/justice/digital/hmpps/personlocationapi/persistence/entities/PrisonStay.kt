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
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetStay
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "prison_stay")
final class PrisonStay(
  personIdentifier: String,
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

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = personIdentifier
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

  fun matchTarget(target: TargetStay) {
    prisonCode = target.prisonCode
    status = target.status
    arrivedAt = target.arrivedAt
    departedAt = target.departedAt
  }

  enum class Status { RESIDENT, IN_TRANSIT_EXTERNAL, TRANSFERRED_OUT, RELEASED }
}

interface PrisonStayRepository : JpaRepository<PrisonStay, UUID> {
  fun findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier: String): List<PrisonStay>
  fun deleteAllByPersonIdentifier(personIdentifier: String)

  @Query(
    """
    select ps from PrisonStay ps
    where ps.personIdentifier = :personIdentifier
      and (ps.departedAt is null or ps.departedAt >= :cutoff)
    order by ps.arrivedAt asc
    """,
  )
  fun findActiveOrAfterCutoff(personIdentifier: String, cutoff: LocalDateTime): List<PrisonStay>

  @Query(
    """
    select ps from PrisonStay ps
    where ps.personIdentifier = :personIdentifier
    order by ps.arrivedAt desc
    limit 1
  """,
  )
  fun findMostRecentFor(personIdentifier: String): PrisonStay?

  @Query(
    """
    select ps.prisonCode as prisonCode, ps.arrivedAt as arrivedAt, ps.departedAt as departedAt, arr.reason as arrivedReason, dep.reason as departedReason 
    from PrisonStay ps
    join MovementInterval arr on ps.personIdentifier = arr.personIdentifier and ps.arrivedAt = arr.validFrom
    left join MovementInterval dep on ps.personIdentifier = dep.personIdentifier and ps.arrivedAt = dep.validFrom
    where ps.personIdentifier = :personIdentifier
    order by ps.arrivedAt
    """,
  )
  fun findAllByPersonIdentifierWithReasons(personIdentifier: String): List<StayWithReasons>
}

fun PrisonStayRepository.findActiveOrAfter(personIdentifier: String, cutoff: LocalDateTime?) = cutoff?.let {
  findActiveOrAfterCutoff(personIdentifier, it)
} ?: findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)

data class StayWithReasons(
  val prisonCode: String,
  val arrivedAt: LocalDateTime,
  val departedAt: LocalDateTime?,
  val arrivedReason: MovementReason,
  val departedReason: MovementReason?,
)
