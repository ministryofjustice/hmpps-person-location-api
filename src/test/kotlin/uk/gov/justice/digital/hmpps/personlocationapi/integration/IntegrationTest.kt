package uk.gov.justice.digital.hmpps.personlocationapi.integration

import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.TestConfig
import uk.gov.justice.digital.hmpps.personlocationapi.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.personlocationapi.integration.container.LocalStackContainer.setMiniStackProperties
import uk.gov.justice.digital.hmpps.personlocationapi.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.personlocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper

@Import(TestConfig::class)
@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTest {

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  internal fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected final inline fun <reified T : Any> WebTestClient.ResponseSpec.successResponse(status: HttpStatus = HttpStatus.OK): T = expectStatus().isEqualTo(status)
    .expectBody<T>()
    .returnResult().responseBody!!

  protected final fun WebTestClient.ResponseSpec.errorResponse(status: HttpStatus): ErrorResponse = expectStatus().isEqualTo(status)
    .expectBody<ErrorResponse>()
    .returnResult().responseBody!!

  companion object {
    private val pgContainer = PostgresContainer.instance
    private val localStackContainer = LocalStackContainer.instance
    const val DEFAULT_USERNAME = "P3r50nL0c4710n"

    @JvmStatic
    @DynamicPropertySource
    @Suppress("unused")
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.also {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
      }

      System.setProperty("aws.region", "eu-west-2")
      localStackContainer?.also { setMiniStackProperties(it, registry) }
    }
  }
}
