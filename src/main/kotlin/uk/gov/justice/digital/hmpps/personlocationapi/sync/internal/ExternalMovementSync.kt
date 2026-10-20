package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.exception.ConflictException
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.getSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReferenceId
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncExternalMovementRequest
import java.util.UUID

@Transactional
@Service
class ExternalMovementSync(
  private val aep: ApplicationEventPublisher,
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
) {
  fun sync(personIdentifier: String, request: SyncExternalMovementRequest): ReferenceId = with(request) {
    RequestContext(
      username = syncUser.username,
      requestAt = occurredAt,
      caseloadId = syncUser.activeCaseloadId,
      source = DataSource.NOMIS,
    ).set()

    val series = seriesRepository.getSeries(requireNotNull(movement.dpsCustodySeriesId))
    if (series.personIdentifier != personIdentifier) {
      throw ConflictException("Person identifier mismatch")
    }

    val original = (
      movement.dpsId?.let { movementRepository.findByIdOrNull(it) }
        ?: movementRepository.findByLegacyId(requireNotNull(movement.legacyId))
      )
    val originalOccurredAt = original?.occurredAt

    val move = original?.updateFrom(series, movement)
      ?: movementRepository.save(movement.asEntity(series))

    aep.publishEvent(
      RefreshPersonMovementIntervals(
        personIdentifier,
        listOfNotNull(originalOccurredAt, move.occurredAt).min(),
      ),
    )

    ReferenceId(move.id)
  }

  fun delete(id: UUID) {
    movementRepository.findByIdOrNull(id)?.let { mov ->
      RequestContext(source = DataSource.NOMIS).set()
      movementRepository.delete(mov)
    }
  }
}
