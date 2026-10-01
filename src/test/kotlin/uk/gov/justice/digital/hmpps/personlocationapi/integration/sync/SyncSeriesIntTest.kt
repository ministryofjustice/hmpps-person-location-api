package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.sync.ReferenceId
import uk.gov.justice.digital.hmpps.personlocationapi.sync.SyncCustodialSeriesRequest

class SyncSeriesIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .put()
      .uri(SYNC, personIdentifier())
      .bodyValue(syncSeriesRequest())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    sendSeries(personIdentifier(), syncSeriesRequest(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok can create a new custodial series`() {
    val request = syncSeriesRequest()
    val res = sendSeries(personIdentifier(), request).successResponse<ReferenceId>()

    val saved = requireNotNull(findSeries(res.dpsId))
    saved verifyAgainst request.custodialSeries
  }

  private fun sendSeries(
    personIdentifier: String = personIdentifier(),
    request: SyncCustodialSeriesRequest = syncSeriesRequest(),
    role: String? = Roles.LEGACY_SYNC,
  ) = webTestClient
    .put()
    .uri(SYNC, personIdentifier)
    .bodyValue(request)
    .headers(setAuthorisation(username = SYSTEM_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val SYNC = "/sync/custodial-series/{personIdentifier}"
  }
}
