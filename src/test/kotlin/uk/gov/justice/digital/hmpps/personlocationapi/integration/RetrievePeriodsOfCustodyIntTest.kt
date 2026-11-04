package uk.gov.justice.digital.hmpps.personlocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.personlocationapi.access.Roles
import uk.gov.justice.digital.hmpps.personlocationapi.integration.DataGenerator.personIdentifier
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialEpisodeOperations
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.CustodialEpisodeOperationsImpl.Companion.custodialEpisode
import uk.gov.justice.digital.hmpps.personlocationapi.model.episode.PeriodsOfCustody
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisode
import java.time.LocalDateTime

class RetrievePeriodsOfCustodyIntTest(
  @Autowired episodeOps: CustodialEpisodeOperations,
) : IntegrationTest(),
  CustodialEpisodeOperations by episodeOps {

  @Test
  fun `401 unauthorised without a valid token`() {
    webTestClient
      .get()
      .uri(RETRIEVE_PERIODS_OF_CUSTODY, personIdentifier())
      .exchange()
      .expectStatus()
      .isUnauthorized
  }

  @Test
  fun `403 forbidden without correct role`() {
    retrievePeriodsOfCustody(personIdentifier(), "ROLE_ANY__OTHER__RW").expectStatus().isForbidden
  }

  @Test
  fun `200 ok no periods of custody for a given person`() {
    val personIdentifier = personIdentifier()
    val res = retrievePeriodsOfCustody(personIdentifier).successResponse<PeriodsOfCustody>()
    assertThat(res.periodsOfCustody).isEmpty()
  }

  @Test
  fun `200 ok can retrieve an active period of custody with no previous period`() {
    val episode = givenEpisode(custodialEpisode())
    assertThat(episode.status).isEqualTo(CustodialEpisode.Status.ACTIVE)
    val res = retrievePeriodsOfCustody(episode.personIdentifier).successResponse<PeriodsOfCustody>()
    assertThat(res.periodsOfCustody).hasSize(1)
    with(res.periodsOfCustody.first()) {
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

    val res = retrievePeriodsOfCustody(personIdentifier).successResponse<PeriodsOfCustody>()
    assertThat(res.periodsOfCustody).hasSize(2)
    with(res.periodsOfCustody.first()) {
      assertThat(commencedAt).isEqualTo(current.commencedAt)
      assertThat(concludedAt).isNull()
      assertThat(status.code).isEqualTo(current.status.name)
      assertThat(previousPeriod?.concludedAt).isEqualTo(prior.concludedAt)
    }
    with(res.periodsOfCustody.last()) {
      assertThat(commencedAt).isEqualTo(prior.commencedAt)
      assertThat(concludedAt).isEqualTo(prior.concludedAt)
      assertThat(status.code).isEqualTo(prior.status.name)
      assertThat(previousPeriod).isNull()
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
    val current = givenEpisode(
      custodialEpisode(
        personIdentifier,
        status = CustodialEpisode.Status.COMPLETED,
        concludedAt = LocalDateTime.now(),
      ),
    )
    assertThat(current.status).isEqualTo(CustodialEpisode.Status.COMPLETED)

    val res = retrievePeriodsOfCustody(personIdentifier).successResponse<PeriodsOfCustody>()
    assertThat(res.periodsOfCustody).hasSize(2)
    with(res.periodsOfCustody.first()) {
      assertThat(commencedAt).isEqualTo(current.commencedAt)
      assertThat(concludedAt).isEqualTo(current.concludedAt)
      assertThat(status.code).isEqualTo(current.status.name)
      assertThat(previousPeriod?.concludedAt).isEqualTo(prior.concludedAt)
    }
    with(res.periodsOfCustody.last()) {
      assertThat(commencedAt).isEqualTo(prior.commencedAt)
      assertThat(concludedAt).isEqualTo(prior.concludedAt)
      assertThat(status.code).isEqualTo(prior.status.name)
      assertThat(previousPeriod).isNull()
    }
  }

  private fun retrievePeriodsOfCustody(
    personIdentifier: String,
    role: String? = Roles.PERSON_PERIODS_RO,
  ) = webTestClient
    .get()
    .uri(RETRIEVE_PERIODS_OF_CUSTODY, personIdentifier)
    .headers(setAuthorisation(username = DEFAULT_USERNAME, roles = listOfNotNull(role)))
    .exchange()

  companion object {
    const val RETRIEVE_PERIODS_OF_CUSTODY = "/integrations/people/{personIdentifier}/periods-of-custody"
  }
}
