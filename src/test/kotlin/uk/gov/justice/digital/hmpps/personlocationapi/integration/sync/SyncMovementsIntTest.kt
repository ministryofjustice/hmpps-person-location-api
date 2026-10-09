package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReferenceId
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncExternalMovementRequest
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncUser

class SyncMovementsIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps,
  ExternalMovementOperations by moveOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(SYNC, personIdentifier())
      .bodyValue(syncMovementRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    sendMovement(personIdentifier(), syncMovementRequest(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok can create a new eternal movement`() {
    val series = givenSeries(custodialSeries())
    val request = syncMovementRequest(syncMovement(series.id))
    val res = sendMovement(series.personIdentifier, request).successResponse<ReferenceId>()

    val saved = requireNotNull(findMovement(res.dpsId))
    saved verifyAgainst request.movement

    verifyAudit(saved, RevisionType.ADD, setOf(ExternalMovement::class.simpleName!!), syncContext(request.syncUser))

    verifyEventPublications(saved, setOf())
  }

  @Test
  fun `200 ok can update an existing eternal movement`() {
    val series = givenSeries(custodialSeries())
    val movement = givenMovement(externalMovement(series))
    val request = syncMovementRequest(syncMovement(series.id, dpsId = movement.id))
    val res = sendMovement(series.personIdentifier, request).successResponse<ReferenceId>()

    val saved = requireNotNull(findMovement(res.dpsId))
    saved verifyAgainst request.movement

    verifyAudit(saved, RevisionType.MOD, setOf(ExternalMovement::class.simpleName!!), syncContext(request.syncUser))

    verifyEventPublications(saved, setOf())
  }

  @Test
  fun `200 can move movement to another series`() {
    val originalSeries = givenSeries(custodialSeries())
    val movement = givenMovement(externalMovement(originalSeries))
    val newSeries = givenSeries(custodialSeries())
    val request = syncMovementRequest(syncMovement(newSeries.id, dpsId = movement.id))
    val res = sendMovement(newSeries.personIdentifier, request).successResponse<ReferenceId>()

    val saved = requireNotNull(findMovement(res.dpsId))
    saved verifyAgainst request.movement

    verifyAudit(saved, RevisionType.MOD, setOf(ExternalMovement::class.simpleName!!), syncContext(request.syncUser))

    verifyEventPublications(saved, setOf())
  }

  private fun sendMovement(
    personIdentifier: String = personIdentifier(),
    request: SyncExternalMovementRequest = syncMovementRequest(),
    role: String? = Roles.LEGACY_SYNC,
  ) = webTestClient
    .put()
    .uri(SYNC, personIdentifier)
    .bodyValue(request)
    .headers(setAuthorisation(username = SYSTEM_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val SYNC = "/sync/external-movements/{personIdentifier}"
    fun syncContext(syncUser: SyncUser) = RequestContext(username = syncUser.username, caseloadId = syncUser.activeCaseloadId, source = DataSource.NOMIS)
  }
}
