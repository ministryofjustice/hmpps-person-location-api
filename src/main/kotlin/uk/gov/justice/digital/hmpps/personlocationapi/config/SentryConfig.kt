package uk.gov.justice.digital.hmpps.personlocationapi.config

import io.sentry.SentryOptions
import jakarta.persistence.OptimisticLockException
import jakarta.servlet.http.HttpServletRequest
import org.hibernate.StaleStateException
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.OptimisticLockingFailureException
import java.util.regex.Pattern.matches

@Configuration
class SentryConfig {
  @Bean
  fun ignoreHealthRequests() = SentryOptions.BeforeSendTransactionCallback { transaction, _ ->
    transaction.transaction?.let { if (it.startsWith("GET /health") or it.startsWith("GET /info")) null else transaction }
  }

  @Bean
  fun transactionSampling() = SentryOptions.TracesSamplerCallback { context ->
    context.customSamplingContext?.let {
      val request = it["request"] as HttpServletRequest
      when (request.method) {
        "GET" if (request.requestURI.isHighUsage()) -> {
          0.001
        }

        else -> {
          0.02
        }
      }
    }
  }

  private fun String.isHighUsage(): Boolean = matches("/reconciliation(.*?)", this)

  @Bean
  fun ignoreOptimisticLockExceptions(): SentryOptions.BeforeSendCallback = SentryOptions.BeforeSendCallback { event, _ ->
    event.takeUnless { it.throwable.hasOptimisticLockCause() }
  }

  private tailrec fun Throwable?.hasOptimisticLockCause(visited: Set<Throwable> = emptySet()): Boolean = when {
    this == null || this in visited -> false
    isOptimisticLockException() -> true
    else -> cause.hasOptimisticLockCause(visited + this)
  }

  private fun Throwable.isOptimisticLockException(): Boolean = this is OptimisticLockingFailureException || this is OptimisticLockException || this is StaleStateException
}
