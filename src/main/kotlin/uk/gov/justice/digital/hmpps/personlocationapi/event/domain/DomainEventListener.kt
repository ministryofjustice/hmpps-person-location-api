package uk.gov.justice.digital.hmpps.personlocationapi.event.domain

import io.awspring.cloud.sqs.annotation.SqsListener
import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.event.Notification

@Component
class DomainEventListener(
  private val jsonMapper: JsonMapper,
) {

  @SqsListener("hmppsdomaineventsqueue", factory = "hmppsQueueContainerFactoryProxy")
  fun handleDomainEvent(notification: Notification) {
    try {
      RequestContext().set()
    } catch (ex: Exception) {
      Sentry.captureException(ex)
      throw ex
    } finally {
      RequestContext.clear()
    }
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }
}
