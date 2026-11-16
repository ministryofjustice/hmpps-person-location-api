package uk.gov.justice.digital.hmpps.personlocationapi.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "service")
data class ServiceConfig(
  val domainEvents: DomainEventConfig,
  val apiBaseUrl: String,
  val projection: ProjectionConfig,
) {
  data class DomainEventConfig(val pollInterval: Duration, val batchSize: Int)
  data class ProjectionConfig(val movementIntervals: MovementIntervalConfig)
  data class MovementIntervalConfig(val ignore: Boolean = false)
}
