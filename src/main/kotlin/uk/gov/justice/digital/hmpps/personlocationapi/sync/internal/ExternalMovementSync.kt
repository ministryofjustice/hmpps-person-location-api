package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.getSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReferenceId
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncExternalMovementRequest
import java.util.UUID

@Transactional
@Service
class ExternalMovementSync(
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
) {
  fun sync(personIdentifier: String, request: SyncExternalMovementRequest): ReferenceId = with(request) {
    RequestContext.get()
      .copy(username = syncUser.username, requestAt = occurredAt, caseloadId = syncUser.activeCaseloadId).set()

    val series = seriesRepository.getSeries(requireNotNull(movement.dpsCustodySeriesId)).applyPerson(personIdentifier)
    val move = (
      movement.dpsId?.let { movementRepository.findByIdOrNull(it) }
        ?: movementRepository.findByLegacyId(requireNotNull(movement.legacyId))
      )?.updateFrom(series, movement)
      ?: movementRepository.save(movement.asEntity(series))

    ReferenceId(move.id)
  }

  fun delete(id: UUID) {
    movementRepository.findByIdOrNull(id)?.let { mov ->
      RequestContext.get().copy(username = SYSTEM_USERNAME).set()
      movementRepository.delete(mov)
    }
  }
}
