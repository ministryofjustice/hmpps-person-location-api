package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReconciliationResponse
import java.time.LocalDateTime

class SyncReconciliationRetrievalIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps,
  ExternalMovementOperations by moveOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_URL, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getForReconciliation(personIdentifier(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 - empty response if nothing found`() {
    val res = getForReconciliation(personIdentifier()).successResponse<ReconciliationResponse>()
    assertThat(res.series).isEmpty()
  }

  @Test
  fun `200 - can retrieve content for reconciliation`() {
    val personIdentifier = personIdentifier()
    val prevSeries = givenSeries(
      custodialSeries(
        personIdentifier = personIdentifier,
        status = uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries.Status.CLOSED,
        isActive = false,
        openedAt = LocalDateTime.now().minusDays(7),
        closedAt = LocalDateTime.now().minusDays(5),
      ),
    )
    val series = givenSeries(custodialSeries(personIdentifier))
    val movement = givenMovement(externalMovement(series))
    val res = getForReconciliation(personIdentifier).successResponse<ReconciliationResponse>()
    assertThat(res.series).hasSize(2)

    val withMovement = res.series.single { it.series.dpsId == series.id }
    series verifyAgainst withMovement.series
    movement verifyAgainst withMovement.movements.single()

    val withoutMovement = res.series.single { it.series.dpsId == prevSeries.id }
    prevSeries verifyAgainst withoutMovement.series
  }

  private fun getForReconciliation(
    personIdentifier: String,
    role: String? = Roles.LEGACY_SYNC,
  ) = webTestClient
    .get()
    .uri(GET_URL, personIdentifier)
    .headers(setAuthorisation(username = SYSTEM_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val GET_URL = "/reconciliation/external-movements/{personIdentifier}"
  }
}
