package uk.gov.justice.digital.hmpps.personlocationapi.config

import io.sentry.SentryOptions
import jakarta.servlet.http.HttpServletRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
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
}
