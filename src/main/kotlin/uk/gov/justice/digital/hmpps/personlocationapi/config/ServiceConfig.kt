package uk.gov.justice.digital.hmpps.personlocationapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "service")
data class ServiceConfig(
  val domainEvents: DomainEventConfig,
  val apiBaseUrl: String,
) {
  data class DomainEventConfig(val pollInterval: Duration, val batchSize: Int)
}
