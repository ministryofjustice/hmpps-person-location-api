package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.repository.findByIdOrNull
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.newId
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.username
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.word
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.sync.AtAndBy
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncCustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncExternalMovementsRequest
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ResyncResponse
import uk.gov.justice.digital.hmpps.personlocationapi.sync.internal.MigrationSystemAuditRepository
import java.time.LocalDateTime
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries as SyncCustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement as SyncExternalMovement

class ResyncIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired movementOps: ExternalMovementOperations,
  @Autowired private val msaRepository: MigrationSystemAuditRepository,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps,
  ExternalMovementOperations by movementOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(RESYNC, personIdentifier())
      .bodyValue(resyncRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    resync(personIdentifier(), resyncRequest(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok can migrate data`() {
    val request = resyncRequest(
      listOf(
        resyncSeries(
          updated = AtAndBy(LocalDateTime.now(), username()),
          movements = listOf(resyncMovement(updated = AtAndBy(LocalDateTime.now(), username()))),
        ),
      ),
    )
    val res = resync(personIdentifier(), request).successResponse<ResyncResponse>()
    assertThat(res.custodialSeries).hasSize(1)

    val cs = requireNotNull(findSeries(res.custodialSeries.single().dpsId))
    val csRequest = request.custodialSeries.single()
    cs verifyAgainst csRequest.custodialSeries
    val csMsa = requireNotNull(msaRepository.findByIdOrNull(cs.id))
    assertThat(csMsa.createdBy).isEqualTo(csRequest.created.by)
    assertThat(csMsa.modifiedBy).isEqualTo(csRequest.modified!!.by)
    val mov = requireNotNull(findMovement(res.custodialSeries.single().movements.single().dpsId))
    val movRequest = csRequest.movements.single()
    mov verifyAgainst movRequest.movement
    val moMsa = requireNotNull(msaRepository.findByIdOrNull(mov.id))
    assertThat(moMsa.createdBy).isEqualTo(movRequest.created.by)
    assertThat(moMsa.modifiedBy).isEqualTo(movRequest.modified!!.by)

    verifyAudit(
      cs,
      RevisionType.ADD,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )
    verifyEventPublications(cs, setOf())
  }

  @Test
  fun `200 ok can remove data`() {
    val series = givenSeries(custodialSeries())
    val movement = givenMovement(externalMovement(series))
    val request = resyncRequest(listOf())
    val res = resync(series.personIdentifier, request).successResponse<ResyncResponse>()
    assertThat(res.custodialSeries).isEmpty()

    assertThat(findSeries(series.id)).isNull()
    assertThat(findMovement(movement.id)).isNull()

    verifyAudit(
      series,
      RevisionType.DEL,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )
    verifyAudit(
      movement,
      RevisionType.DEL,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )
    verifyEventPublications(series, setOf())
  }

  @Test
  fun `200 ok can merge data`() {
    val personIdentifier = personIdentifier()
    val series = givenSeries(custodialSeries(personIdentifier))
    val seriesWithMovement = givenSeries(custodialSeries(personIdentifier))
    val movement = givenMovement(externalMovement(seriesWithMovement))

    val request = resyncRequest(
      listOf(
        resyncSeries(
          syncSeries(dpsId = seriesWithMovement.id),
          movements = listOf(resyncMovement(syncMovement(dpsId = movement.id))),
        ),
        resyncSeries(),
      ),
    )
    val res = resync(series.personIdentifier, request).successResponse<ResyncResponse>()
    assertThat(res.custodialSeries).hasSize(2)

    assertThat(findSeries(series.id)).isNull()

    val modRequest = request.custodialSeries.first()
    val modSeries = requireNotNull(findSeries(seriesWithMovement.id))
    modSeries verifyAgainst modRequest.custodialSeries
    val modMove = requireNotNull(findMovement(movement.id))
    modMove verifyAgainst modRequest.movements.single().movement

    val newSeries = requireNotNull(findSeries(res.custodialSeries.first { it.dpsId != modSeries.id }.dpsId))
    newSeries verifyAgainst request.custodialSeries.last().custodialSeries

    verifyAudit(
      series,
      RevisionType.DEL,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )
    verifyAudit(
      seriesWithMovement,
      RevisionType.MOD,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )
    verifyAudit(
      movement,
      RevisionType.MOD,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )
    verifyAudit(
      newSeries,
      RevisionType.ADD,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )

    verifyEventPublications(series, setOf())
  }

  @Test
  fun `200 ok can re-migrate data without dps ids`() {
    val series = givenSeries(custodialSeries(legacyId = newId(), legacyBookingReference = word(6).uppercase()))
    val movBookingId = newId()
    val movSeq = newId().toInt()
    val movement = givenMovement(externalMovement(series, legacyId = "${movBookingId}_$movSeq"))

    val request = resyncRequest(
      listOf(
        resyncSeries(
          syncSeries(
            legacyBookingId = series.legacyId!!,
          ),
          movements = listOf(resyncMovement(syncMovement(legacyBookingId = movBookingId, legacySequenceNumber = movSeq))),
        ),
      ),
    )

    val res = resync(series.personIdentifier, request).successResponse<ResyncResponse>()
    assertThat(res.custodialSeries).hasSize(1)

    val modRequest = request.custodialSeries.single()
    val modSeries = requireNotNull(findSeries(series.id))
    modSeries verifyAgainst modRequest.custodialSeries
    val modMovement = requireNotNull(findMovement(movement.id))
    modMovement verifyAgainst modRequest.movements.single().movement

    verifyAudit(
      modSeries,
      RevisionType.MOD,
      setOf(
        CustodialSeries::class.simpleName!!,
        ExternalMovement::class.simpleName!!,
      ),
      RESYNC_CONTEXT,
    )

    verifyEventPublications(series, setOf())
  }

  private fun resync(
    personIdentifier: String,
    request: ResyncExternalMovementsRequest,
    role: String = Roles.LEGACY_SYNC,
  ) = webTestClient
    .put()
    .uri(RESYNC, personIdentifier)
    .bodyValue(request)
    .headers(setAuthorisation(username = DEFAULT_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val RESYNC = "/resync/external-movements/{personIdentifier}"
    private val RESYNC_CONTEXT = RequestContext(source = DataSource.NOMIS)

    private fun resyncMovement(
      movement: SyncExternalMovement = syncMovement(),
      created: AtAndBy = AtAndBy(at = LocalDateTime.now(), by = username()),
      updated: AtAndBy? = null,
    ) = ResyncExternalMovement(movement, created, updated)

    private fun resyncSeries(
      series: SyncCustodialSeries = syncSeries(),
      movements: List<ResyncExternalMovement> = listOf(resyncMovement()),
      created: AtAndBy = AtAndBy(at = LocalDateTime.now(), by = username()),
      updated: AtAndBy? = null,
    ) = ResyncCustodialSeries(series, movements, created, updated)

    private fun resyncRequest(series: List<ResyncCustodialSeries> = listOf(resyncSeries())) = ResyncExternalMovementsRequest(series)
  }
}
