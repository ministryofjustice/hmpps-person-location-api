package uk.gov.justice.digital.hmpps.personlocationapi.integration.config

import com.github.tomakehurst.wiremock.extension.Parameters
import com.github.tomakehurst.wiremock.extension.ServeEventListener
import com.github.tomakehurst.wiremock.stubbing.ServeEvent
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.transaction.support.TransactionTemplate
import uk.gov.justice.digital.hmpps.personlocationapi.event.internal.RefreshPersonMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.integration.wiremock.NomisMigrationMockServer
import uk.gov.justice.digital.hmpps.personlocationapi.integration.wiremock.WiremockConfig.mockServerConfig
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.ExternalMovementRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialEpisodeRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.CustodialSeriesRepository
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.HmppsDomainEventRepository
import uk.gov.justice.digital.hmpps.personlocationapi.service.RefreshExternalMovementIntervals

@TestConfiguration
class TestConfig(
  private val transactionTemplate: TransactionTemplate,
  private val hmppsDomainEventRepository: HmppsDomainEventRepository,
  private val seriesRepository: CustodialSeriesRepository,
  private val movementRepository: ExternalMovementRepository,
  private val episodeRepository: CustodialEpisodeRepository,
) {
  @Bean
  fun resyncCallBackEventListener(refresh: RefreshExternalMovementIntervals): ServeEventListener = object : ServeEventListener {
    override fun getName(): String = "resync-callback-event-listener"
    override fun applyGlobally(): Boolean = false
    override fun afterComplete(serveEvent: ServeEvent, parameters: Parameters) {
      parameters.getString("personIdentifier")?.also {
        refresh.execute(RefreshPersonMovementIntervals(it))
      }
    }
  }

  @Bean(initMethod = "start", destroyMethod = "stop")
  fun nomisMigrationMockServer(resyncCallBackEventListener: ServeEventListener): NomisMigrationMockServer = NomisMigrationMockServer(mockServerConfig(9990).extensions(resyncCallBackEventListener))

  @Bean
  fun hmppsDomainEventOperations(): HmppsDomainEventOperations = HmppsDomainEventOperationsImpl(transactionTemplate, hmppsDomainEventRepository)

  @Bean
  fun custodialSeriesOperations() = CustodialSeriesOperationsImpl(seriesRepository)

  @Bean
  fun externalMovementOperations() = ExternalMovementOperationsImpl(movementRepository)

  @Bean
  fun custodialEpisodeOperations() = CustodialEpisodeOperationsImpl(episodeRepository)
}
