package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.getSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.getMovement
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReconciliationResponse
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReconciliationSeries
import java.util.UUID
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries as SyncCustodialSeries

@Transactional(readOnly = true)
@Service
class RetrieveForSync(
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
) {
  fun series(id: UUID): SyncCustodialSeries = seriesRepository.getSeries(id).toSyncModel()
  fun movement(id: UUID) = movementRepository.getMovement(id).toSyncModel()
  fun all(personIdentifier: String): ReconciliationResponse {
    val series = seriesRepository.findByPersonIdentifier(personIdentifier)
    val movements = movementRepository.findByPersonIdentifier(personIdentifier).groupBy { it.series.id }
    return series.map { it.forReconciliation { seriesId -> movements[seriesId] ?: emptyList() } }.asResponse()
  }
}

private fun List<ReconciliationSeries>.asResponse() = ReconciliationResponse(this)
private fun CustodialSeries.forReconciliation(movementProvider: (UUID) -> List<ExternalMovement>) = ReconciliationSeries(toSyncModel(), movementProvider(id).map { it.toSyncModel() })
