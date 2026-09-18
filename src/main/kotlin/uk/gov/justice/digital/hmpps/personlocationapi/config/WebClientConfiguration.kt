package uk.gov.justice.digital.hmpps.personlocationapi.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager
import org.springframework.security.oauth2.client.endpoint.WebClientReactiveClientCredentialsTokenResponseClient
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClient.Builder
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.util.retry.Retry
import uk.gov.justice.hmpps.kotlin.auth.authorisedWebClient
import java.io.IOException
import java.time.Duration
import java.time.Duration.ofSeconds

@Configuration
class WebClientConfiguration {
  @Bean
  fun tokenResponseClient(): WebClientReactiveClientCredentialsTokenResponseClient {
    val client = WebClientReactiveClientCredentialsTokenResponseClient()
    val webClient = WebClient.builder().filter { request, next ->
      next.exchange(request)
        .retryWhen(Retry.backoff(3, Duration.ofMillis(50)).filter { it.isRetryableException() })
    }.build()

    client.setWebClient(webClient)
    return client
  }

  private fun Throwable.isRetryableException(): Boolean = this is IOException || this is WebClientRequestException || (this is WebClientResponseException && this.statusCode.is5xxServerError)

  fun authorisedWebClient(
    url: String,
    builder: Builder,
    authorizedClientManager: OAuth2AuthorizedClientManager,
    timeout: Duration = Companion.timeout,
    registrationId: String = DEFAULT_REGISTRATION_ID,
  ): WebClient = builder.authorisedWebClient(authorizedClientManager, registrationId, url, timeout)

  companion object {
    const val DEFAULT_REGISTRATION_ID = "default"
    private val timeout: Duration = ofSeconds(2)
  }
}
