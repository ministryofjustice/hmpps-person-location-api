package uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities

import jakarta.persistence.Column
import jakarta.persistence.Convert
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
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.TargetJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Prison
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceConverter
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "external_journey")
final class ExternalJourney(
  personIdentifier: String,
  type: Type,
  status: Status,
  commencedAt: LocalDateTime,
  concludedAt: LocalDateTime?,
  origin: String,
  destination: String?,
  externalReference: ExternalReference?,
  id: UUID = newUuid(),
) {
  @Id
  @Column(name = "id", nullable = false)
  var id: UUID = id
    private set

  @Version
  @Column(name = "version", nullable = false)
  var version: Int? = null
    private set

  @Size(max = 7)
  @Column(name = "person_identifier", nullable = false, length = 7)
  var personIdentifier: String = personIdentifier
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "type", columnDefinition = "external_journey_type", nullable = false)
  var type: Type = type
    private set

  @JdbcType(PostgreSQLEnumJdbcType::class)
  @Column(name = "status", columnDefinition = "external_journey_status", nullable = false)
  var status: Status = status
    private set

  @Column(name = "commenced_at", nullable = false)
  var commencedAt: LocalDateTime = commencedAt
    private set

  @Column(name = "concluded_at")
  var concludedAt: LocalDateTime? = concludedAt
    private set

  @Size(max = 6)
  @Column(name = "origin_prison_code", length = 6, nullable = false)
  var origin: String = origin
    private set

  @Size(max = 6)
  @Column(name = "destination_prison_code", length = 6)
  var destination: String? = destination
    private set

  @Convert(converter = ExternalReferenceConverter::class)
  @Column(name = "external_reference")
  var externalReference: ExternalReference? = externalReference
    private set

  fun matchTarget(target: TargetJourney) {
    type = target.journeyType
    status = if (concludedAt == null) Status.IN_TRANSIT else Status.COMPLETED
    commencedAt = target.commencedAt
    concludedAt = target.concludedAt
    origin = target.origin.code
    destination = (target.destination?.takeIf { it is Prison } as? Prison)?.code
  }

  enum class Type { ADMISSION, COURT_APPEARANCE, TEMPORARY_ABSENCE, TRANSFER, RELEASE }
  enum class Status { IN_TRANSIT, COMPLETED }
}

interface ExternalJourneyRepository : JpaRepository<ExternalJourney, UUID> {
  fun findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier: String): List<ExternalJourney>
  fun deleteAllByPersonIdentifier(personIdentifier: String)

  @Query(
    """
    select j from ExternalJourney j
    where j.personIdentifier = :personIdentifier
      and (j.concludedAt is null or j.concludedAt >= :cutoff)
    order by j.commencedAt asc
    """,
  )
  fun findActiveOrAfterCutoff(personIdentifier: String, cutoff: LocalDateTime): List<ExternalJourney>
}

fun ExternalJourneyRepository.findActiveOrAfter(personIdentifier: String, cutoff: LocalDateTime?) = cutoff?.let {
  findActiveOrAfterCutoff(personIdentifier, it)
} ?: findAllByPersonIdentifierOrderByCommencedAtAsc(personIdentifier)
