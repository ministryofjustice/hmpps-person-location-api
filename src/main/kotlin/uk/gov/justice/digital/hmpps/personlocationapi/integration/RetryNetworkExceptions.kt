package uk.gov.justice.digital.hmpps.personlocationapi.integration

import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.io.IOException
import java.time.Duration

private val retrySpec = Retry.backoff(3, Duration.ofMillis(200))
  .filter {
    it is IOException || it is WebClientRequestException || (it is WebClientResponseException && it.statusCode.is5xxServerError)
  }.onRetryExhaustedThrow { _, signal ->
    signal.failure()
  }

fun <T : Any> Mono<T>.retryOnTransientException(): Mono<T> = retryWhen(retrySpec)

fun <T : Any> Flux<T>.retryOnTransientException(): Flux<T> = retryWhen(retrySpec)
