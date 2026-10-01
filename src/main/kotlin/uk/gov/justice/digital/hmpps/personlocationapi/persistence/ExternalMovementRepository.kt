package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
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

  fun findByLegacyId(legacyId: String): ExternalMovement?
}
