package uk.gov.justice.digital.hmpps.personlocationapi.sync.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.sync.AtAndBy
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeriesMapping
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovementMapping
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncCustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncResponse
import java.util.UUID

@Transactional
@Service
class ExternalMovementsResync(
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
  private val msa: MigrationSystemAuditRepository,
) {
  fun all(personIdentifier: String, request: ResyncExternalMovementsRequest): ResyncResponse {
    RequestContext.get().copy(username = SYSTEM_USERNAME, source = DataSource.NOMIS, migratingData = true).set()
    val (legacySeriesIds, seriesIds) = request.seriesIds()
    val (legacyMovementIds, movementIds) = request.movementIds()
    val existingSeries = findAllSeries(personIdentifier, seriesIds, legacySeriesIds, movementIds, legacyMovementIds)
      .associateBy { it.id }
    val existingMovements =
      findAllMovements(personIdentifier, existingSeries.values.map { it.id }.toSet(), movementIds, legacyMovementIds)
        .associateBy { it.id }
    val maProvider = MigrationAuditProvider(msa.findAllById(existingSeries.keys + existingMovements.keys))

    val seriesProvider = { legacyId: Long, dpsId: UUID? ->
      dpsId?.let { existingSeries[it] } ?: existingSeries.values.firstOrNull { it.legacyId == legacyId }
    }

    val movementProvider = { legacyId: String, dpsId: UUID? ->
      dpsId?.let { existingMovements[it] } ?: existingMovements.values.firstOrNull { it.legacyId == legacyId }
    }

    val mappings = request.custodialSeries.map { it.resync(personIdentifier, seriesProvider, movementProvider, maProvider) }
    removeNotInResync(mappings, existingMovements.values, existingSeries.values)
    return ResyncResponse(mappings)
  }

  private fun findAllSeries(
    personIdentifier: String,
    seriesIds: Set<UUID>,
    seriesLegacyIds: Set<Long>,
    movementIds: Set<UUID>,
    movementLegacyIds: Set<String>,
  ): List<CustodialSeries> {
    val mappedBy = seriesRepository.findSeriesIds(personIdentifier, seriesLegacyIds, movementIds, movementLegacyIds)
    return seriesRepository.findAllById((seriesIds + mappedBy).toSet())
  }

  fun findAllMovements(
    personIdentifier: String,
    seriesIds: Set<UUID>,
    movementIds: Set<UUID>,
    movementLegacyIds: Set<String>,
  ): List<ExternalMovement> {
    val mappedBy = movementRepository.findMovementIds(personIdentifier, seriesIds, movementLegacyIds)
    return movementRepository.findAllById(((movementIds + mappedBy).toSet()))
  }

  private fun ResyncCustodialSeries.resync(
    personIdentifier: String,
    seriesProvider: (Long, UUID?) -> CustodialSeries?,
    movementProvider: (String, UUID?) -> ExternalMovement?,
    maProvider: MigrationAuditProvider,
  ): CustodialSeriesMapping {
    val series = with(custodialSeries) {
      seriesProvider(requireNotNull(legacyId), dpsId)?.updateFrom(personIdentifier, custodialSeries)
        ?: seriesRepository.save(asEntity(personIdentifier))
    }
    val moves = movements.map { it.resync(series, movementProvider, maProvider) }
    mergeMigrationAudit(series.id, created, modified, maProvider)
    return CustodialSeriesMapping(series.id, series.legacyId!!, moves)
  }

  private fun ResyncExternalMovement.resync(
    series: CustodialSeries,
    movementProvider: (String, UUID?) -> ExternalMovement?,
    maProvider: MigrationAuditProvider,
  ): ExternalMovementMapping {
    val move = with(movement) {
      movementProvider(requireNotNull(legacyId), dpsId)?.updateFrom(series, movement)
        ?: movementRepository.save(asEntity(series))
    }
    mergeMigrationAudit(move.id, created, modified, maProvider)
    return ExternalMovementMapping(move.id, movement.legacySequenceNumber!!)
  }

  private fun removeNotInResync(
    mappings: List<CustodialSeriesMapping>,
    movements: Collection<ExternalMovement>,
    series: Collection<CustodialSeries>,
  ) {
    val (seriesIds, movementIds) = mappings.fold(Pair(mutableSetOf<UUID>(), mutableSetOf<UUID>())) { (s, m), ser ->
      s.add(ser.dpsId)
      ser.movements.mapTo(m) { it.dpsId }
      s to m
    }
    movementRepository.deleteAll(movements.filter { it.id !in movementIds })
    seriesRepository.deleteAll(series.filter { it.id !in seriesIds })
  }

  private fun mergeMigrationAudit(
    id: UUID,
    created: AtAndBy,
    modified: AtAndBy?,
    maProvider: MigrationAuditProvider,
  ) {
    maProvider[id]?.also {
      it.createdBy = created.by
      it.createdAt = created.at
      it.modifiedBy = modified?.by
      it.modifiedAt = modified?.at
    } ?: run {
      maProvider.put(
        msa.save(
          MigrationSystemAudit(id, created.at, created.by, modified?.at, modified?.by),
        ),
      )
    }
  }
}

private class MigrationAuditProvider(audits: Iterable<MigrationSystemAudit>) {
  private val audits = audits.associateBy { it.id }.toMutableMap()

  operator fun get(id: UUID): MigrationSystemAudit? = audits[id]

  fun put(msa: MigrationSystemAudit) {
    audits[msa.id] = msa
  }
}
