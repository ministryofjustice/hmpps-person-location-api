package uk.gov.justice.digital.hmpps.personlocationapi.integration.config

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.HmppsDomainEventRepository

@TestConfiguration
class TestConfig(
  private val transactionTemplate: TransactionTemplate,
  private val hmppsDomainEventRepository: HmppsDomainEventRepository,
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
) {
  @Bean
  fun hmppsDomainEventOperations(): HmppsDomainEventOperations = HmppsDomainEventOperationsImpl(transactionTemplate, hmppsDomainEventRepository)

  @Bean
  fun custodialSeriesOperations() = CustodialSeriesOperationsImpl(seriesRepository)

  @Bean
  fun externalMovementOperations() = ExternalMovementOperationsImpl(movementRepository)
}
