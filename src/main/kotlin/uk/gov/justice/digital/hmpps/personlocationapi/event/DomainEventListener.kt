package uk.gov.justice.digital.hmpps.personlocationapi.event

import io.awspring.cloud.sqs.annotation.SqsListener
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.transferschedulerapi.event.Notification

@Component
class DomainEventListener(
  private val jsonMapper: JsonMapper,
) {

  @SqsListener("hmppsdomaineventsqueue", factory = "hmppsQueueContainerFactoryProxy")
  fun handleDomainEvent(notification: Notification) {
    LOG.debug("Received domain event: {}", notification.eventType)
  }

  companion object {
    private val LOG = LoggerFactory.getLogger(this::class.java)
  }
}
