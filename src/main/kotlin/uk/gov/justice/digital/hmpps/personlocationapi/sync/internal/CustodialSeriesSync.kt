package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.exception.ConflictException
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReferenceId
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncCustodialSeriesRequest
import java.util.UUID

@Transactional
@Service
class CustodialSeriesSync(
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
) {
  fun sync(personIdentifier: String, request: SyncCustodialSeriesRequest): ReferenceId = with(request) {
    RequestContext(username = syncUser.username, requestAt = occurredAt, caseloadId = syncUser.activeCaseloadId, source = DataSource.NOMIS)
      .set()

    val series = (
      custodialSeries.dpsId?.let { seriesRepository.findByIdOrNull(it) }
        ?: seriesRepository.findByLegacyId(requireNotNull(custodialSeries.legacyId))
      )?.updateFrom(personIdentifier, custodialSeries)
      ?: seriesRepository.save(custodialSeries.asEntity(personIdentifier))

    ReferenceId(series.id)
  }

  fun delete(id: UUID) {
    seriesRepository.findByIdOrNull(id)?.let { series ->
      RequestContext(source = DataSource.NOMIS).set()
      if (movementRepository.countBySeriesId(series.id) > 0) {
        throw ConflictException("Custodial series cannot be deleted with movements")
      } else {
        seriesRepository.delete(series)
      }
    }
  }
}
