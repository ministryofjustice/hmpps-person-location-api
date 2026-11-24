package uk.gov.justice.digital.hmpps.personlocationapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.extension.Parameters
import org.springframework.http.HttpStatus

class NomisMigrationMockServer(options: WireMockConfiguration) : WireMockServer(options) {
  fun requestRepairCallsRefresh(personIdentifier: String) {
    stubFor(
      put(urlMatching("/migrate/person-location/repair/$personIdentifier"))
        .withBearerToken()
        .willReturn(aResponse().withStatus(HttpStatus.OK.value()))
        .withServeEventListener(
          "resync-callback-event-listener",
          Parameters.from(mapOf("personIdentifier" to personIdentifier)),
        ),
    )
  }
}
