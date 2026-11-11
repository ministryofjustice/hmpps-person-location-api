package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import jakarta.validation.constraints.Size
import org.hibernate.annotations.JdbcType
import org.hibernate.dialect.type.PostgreSQLEnumJdbcType
import org.hibernate.envers.Audited
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetStay
import java.time.LocalDateTime
import java.util.UUID

@Audited
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
    select s from PrisonStay s
    where s.personIdentifier = :personIdentifier
      and (s.departedAt is null or s.departedAt >= :cutoff)
    order by s.arrivedAt asc
    """,
  )
  fun findActiveOrAfterCutoff(personIdentifier: String, cutoff: LocalDateTime): List<PrisonStay>
}

fun PrisonStayRepository.findActiveOrAfter(personIdentifier: String, cutoff: LocalDateTime?) = cutoff?.let {
  findActiveOrAfterCutoff(personIdentifier, it)
} ?: findAllByPersonIdentifierOrderByArrivedAtAsc(personIdentifier)
