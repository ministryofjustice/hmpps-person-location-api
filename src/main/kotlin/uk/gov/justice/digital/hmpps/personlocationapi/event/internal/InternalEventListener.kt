package uk.gov.justice.digital.hmpps.personlocationapi.event.internal

import io.awspring.cloud.sqs.annotation.SqsListener
import io.sentry.Sentry
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.personlocationapi.RefreshExternalMovementIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.event.Notification

@Component
class InternalEventListener(
  private val jsonMapper: JsonMapper,
  private val refresh: RefreshExternalMovementIntervals,
) {
  @SqsListener(
    "internalevents",
    factory = "hmppsQueueContainerFactoryProxy",
    maxConcurrentMessages = "20",
    maxMessagesPerPoll = "10",
  )
  fun handleInternalEvent(notification: Notification) {
    try {
      RequestContext().set()
      when (notification.eventType) {
        RefreshPersonMovementIntervals.EVENT_TYPE -> refresh.execute(jsonMapper.readValue(notification.message))
      }
    } catch (ex: Exception) {
      Sentry.captureException(ex)
      throw ex
    } finally {
      RequestContext.clear()
    }
  }
}
