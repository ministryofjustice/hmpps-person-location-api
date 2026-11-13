package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import jakarta.persistence.QueryHint
import org.hibernate.jpa.HibernateHints
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.personlocationapi.exception.NotFoundException
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import java.time.LocalDateTime
import java.util.UUID

interface ExternalMovementRepository : JpaRepository<ExternalMovement, UUID> {
  @Query(
    """
    select em.id from ExternalMovement em
    where em.personIdentifier = :personIdentifier
    union
    select em.id from ExternalMovement em
    where em.series.id in (:seriesIds)
    union
    select em.id from ExternalMovement em
    where em.legacyId in (:movementLegacyIds)
  """,
  )
  fun findMovementIds(
    personIdentifier: String,
    seriesIds: Set<UUID>,
    movementLegacyIds: Set<String>,
  ): Set<UUID>

  fun countBySeriesId(seriesId: UUID): Int

  fun findBySeriesId(seriesId: UUID): List<ExternalMovement>

  fun findByLegacyId(legacyId: String): ExternalMovement?

  @Query("select em from ExternalMovement em where em.personIdentifier = :personIdentifier")
  @QueryHints(QueryHint(name = HibernateHints.HINT_READ_ONLY, value = "true"))
  fun findByPersonIdentifierReadOnly(personIdentifier: String): List<ExternalMovement>

  fun findByPersonIdentifier(personIdentifier: String): List<ExternalMovement>

  @Query(
    """
      select em.occurredAt from ExternalMovement em
      where em.personIdentifier = :personIdentifier
      and em.occurredAt < :dateTime
      order by em.occurredAt desc
      limit 1
    """,
  )
  fun findPreviousOccurredAt(personIdentifier: String, dateTime: LocalDateTime): LocalDateTime?
}

fun ExternalMovementRepository.getMovement(id: UUID): ExternalMovement = findByIdOrNull(id) ?: throw NotFoundException("External Movement not found")
