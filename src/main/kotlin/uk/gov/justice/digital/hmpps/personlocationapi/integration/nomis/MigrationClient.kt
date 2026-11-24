package uk.gov.justice.digital.hmpps.personlocationapi.integration.nomis

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import uk.gov.justice.digital.hmpps.personlocationapi.integration.retryOnTransientException

@Component
class MigrationClient(@Qualifier("nomisMigrationWebClient") private val webClient: WebClient) {

  fun requestRepair(personIdentifier: String) {
    webClient
      .put()
      .uri(MIGRATION_REPAIR_URL, personIdentifier)
      .retrieve()
      .toBodilessEntity()
      .retryOnTransientException()
      .block()
  }

  companion object {
    const val MIGRATION_REPAIR_URL = "/migrate/person-location/repair/{personIdentifier}"
  }
}
