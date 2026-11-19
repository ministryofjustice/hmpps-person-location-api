package uk.gov.justice.digital.hmpps.personlocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialEpisodeOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialEpisodeOperationsImpl.Companion.custodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import java.time.LocalDateTime

class RetrievePeriodOfCustodyAtIntTest(
  @Autowired episodeOps: CustodialEpisodeOperations,
) : IntegrationTest(),
  CustodialEpisodeOperations by episodeOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(RETRIEVE_PERIOD_AT, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    retrievePeriodOfCustodyAt(personIdentifier(), role = "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `404 not found - no period of custody for a given person`() {
    val personIdentifier = personIdentifier()
    val res = retrievePeriodOfCustodyAt(personIdentifier).errorResponse(HttpStatus.NOT_FOUND)
    assertThat(res.status).isEqualTo(HttpStatus.NOT_FOUND.value())
    assertThat(res.userMessage).isNull()
    assertThat(res.developerMessage).isNull()
  }

  @Test
  fun `200 ok can retrieve an active period of custody with no previous period`() {
    val episode = givenEpisode(custodialEpisode())
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
    val res = retrievePeriodOfCustodyAt(episode.personIdentifier).successResponse<PeriodOfCustody>()
    with(res) {
      assertThat(commencedAt).isEqualTo(episode.commencedAt)
      assertThat(concludedAt).isNull()
      assertThat(status.code).isEqualTo(episode.status.name)
      assertThat(previousPeriod).isNull()
    }
  }

  @Test
  fun `200 ok can retrieve an active period of custody with the previous period`() {
    val personIdentifier = personIdentifier()
    val prior = givenEpisode(
      custodialEpisode(
        personIdentifier,
        status = CustodialEpisode.Status.COMPLETED,
        commencedAt = LocalDateTime.now().minusDays(30),
        concludedAt = LocalDateTime.now().minusDays(10),
      ),
    )
    assertThat(prior.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    val current = givenEpisode(custodialEpisode(personIdentifier))
    assertThat(current.status).isEqualTo(CustodialEpisode.Status.ACTIVE)

    val res = retrievePeriodOfCustodyAt(personIdentifier).successResponse<PeriodOfCustody>()
    with(res) {
      assertThat(commencedAt).isEqualTo(current.commencedAt)
      assertThat(concludedAt).isNull()
      assertThat(status.code).isEqualTo(current.status.name)
      assertThat(previousPeriod?.concludedAt).isEqualTo(prior.concludedAt)
    }
  }

  @Test
  fun `200 ok can retrieve a completed period of custody with the previous period`() {
    val personIdentifier = personIdentifier()
    val prior = givenEpisode(
      custodialEpisode(
        personIdentifier,
        status = CustodialEpisode.Status.COMPLETED,
        commencedAt = LocalDateTime.now().minusDays(30),
        concludedAt = LocalDateTime.now().minusDays(10),
      ),
    )
    assertThat(prior.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    val previous = givenEpisode(
      custodialEpisode(
        personIdentifier,
        status = CustodialEpisode.Status.COMPLETED,
        commencedAt = LocalDateTime.now().minusDays(7),
        concludedAt = LocalDateTime.now().minusDays(3),
      ),
    )
    assertThat(previous.status).isEqualTo(CustodialEpisode.Status.COMPLETED)
    val current = givenEpisode(custodialEpisode(personIdentifier))
    assertThat(current.status).isEqualTo(CustodialEpisode.Status.ACTIVE)

    val res1 = retrievePeriodOfCustodyAt(personIdentifier, LocalDateTime.now().minusDays(5))
      .successResponse<PeriodOfCustody>()
    with(res1) {
      assertThat(commencedAt).isEqualTo(previous.commencedAt)
      assertThat(concludedAt).isEqualTo(previous.concludedAt)
      assertThat(status.code).isEqualTo(previous.status.name)
      assertThat(previousPeriod?.concludedAt).isEqualTo(prior.concludedAt)
    }
    val res2 = retrievePeriodOfCustodyAt(personIdentifier, LocalDateTime.now().minusDays(15))
      .successResponse<PeriodOfCustody>()
    with(res2) {
      assertThat(commencedAt).isEqualTo(prior.commencedAt)
      assertThat(concludedAt).isEqualTo(prior.concludedAt)
      assertThat(status.code).isEqualTo(prior.status.name)
      assertThat(previousPeriod?.concludedAt).isNull()
    }
  }

  private fun retrievePeriodOfCustodyAt(
    personIdentifier: String,
    anchorDateTime: LocalDateTime = LocalDateTime.now(),
    role: String? = Roles.PERSON_PERIODS_RO,
  ) = webTestClient
    .get()
    .uri {
      it.path(RETRIEVE_PERIOD_AT)
        .queryParam("anchorDateTime", anchorDateTime)
        .build(personIdentifier)
    }
    .headers(setAuthorisation(username = DEFAULT_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val RETRIEVE_PERIOD_AT = "/integrations/people/{personIdentifier}/periods-of-custody/at"
  }
}
