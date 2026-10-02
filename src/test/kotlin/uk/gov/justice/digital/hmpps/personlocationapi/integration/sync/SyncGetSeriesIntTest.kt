package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.sync.CustodialSeries
import java.util.UUID

class SyncGetSeriesIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(GET_URL, newUuid())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    getSeries(newUuid(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `400 - bad request if id not a valid uuid`() {
    getSeries("invalid-uuid").errorResponse(HttpStatus.BAD_REQUEST)
  }

  @Test
  fun `404 - uuid does not exist`() {
    getSeries(newUuid()).expectStatus().isNotFound
  }

  @Test
  fun `200 - can retrieve movement`() {
    val series = givenSeries(custodialSeries())
    val res = getSeries(series.id).successResponse<CustodialSeries>()
    series verifyAgainst res
  }

  private fun getSeries(
    id: UUID = newUuid(),
    role: String? = Roles.LEGACY_SYNC,
  ) = getSeries(id.toString(), role)

  private fun getSeries(
    id: String,
    role: String? = Roles.LEGACY_SYNC,
  ) = webTestClient
    .get()
    .uri(GET_URL, id)
    .headers(setAuthorisation(username = SYSTEM_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val GET_URL = "/sync/custodial-series/{id}"
  }
}
