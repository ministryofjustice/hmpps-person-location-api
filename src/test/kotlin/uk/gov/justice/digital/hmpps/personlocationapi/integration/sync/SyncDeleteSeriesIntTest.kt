package uk.gov.justice.digital.hmpps.personlocationapi.integration.sync

import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.RevisionType
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext.Companion.SYSTEM_USERNAME
import uk.gov.justice.digital.hmpps.personlocationapi.integration.IntegrationTest
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialSeriesOperationsImpl.Companion.custodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.ExternalMovementOperationsImpl.Companion.externalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeries
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.DataSource
import java.util.UUID

class SyncDeleteSeriesIntTest(
  @Autowired seriesOps: CustodialSeriesOperations,
  @Autowired moveOps: ExternalMovementOperations,
) : IntegrationTest(),
  CustodialSeriesOperations by seriesOps,
  ExternalMovementOperations by moveOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .delete()
      .uri(DELETE_URL, newUuid())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    deleteSeries(newUuid(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `400 - bad request if id not a valid uuid`() {
    deleteSeries("invalid-uuid").errorResponse(HttpStatus.BAD_REQUEST)
  }

  @Test
  fun `409 - cannot delete series with movement`() {
    val series = givenSeries(custodialSeries())
    val movement = givenMovement(externalMovement(series))
    deleteSeries(series.id).errorResponse(HttpStatus.CONFLICT)

    assertThat(findSeries(series.id)).isNotNull
    assertThat(findMovement(movement.id)).isNotNull

    verifyAudit(series, RevisionType.ADD, setOf(CustodialSeries::class.simpleName!!))
    verifyAudit(movement, RevisionType.ADD, setOf(ExternalMovement::class.simpleName!!))
  }

  @Test
  fun `204 - uuid does not exist`() {
    deleteSeries(newUuid()).expectStatus().isNoContent
  }

  @Test
  fun `204 - can delete series without movement`() {
    val series = givenSeries(custodialSeries())
    deleteSeries(series.id).expectStatus().isNoContent

    assertThat(findSeries(series.id)).isNull()

    verifyAudit(series, RevisionType.DEL, setOf(CustodialSeries::class.simpleName!!), syncContext())

    verifyEventPublications(series, setOf())
  }

  private fun deleteSeries(
    id: UUID = newUuid(),
    role: String? = Roles.LEGACY_SYNC,
  ) = deleteSeries(id.toString(), role)

  private fun deleteSeries(
    id: String,
    role: String? = Roles.LEGACY_SYNC,
  ) = webTestClient
    .delete()
    .uri(DELETE_URL, id)
    .headers(setAuthorisation(username = SYSTEM_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val DELETE_URL = "/sync/custodial-series/{id}"
    fun syncContext() = RequestContext(source = DataSource.NOMIS)
  }
}
