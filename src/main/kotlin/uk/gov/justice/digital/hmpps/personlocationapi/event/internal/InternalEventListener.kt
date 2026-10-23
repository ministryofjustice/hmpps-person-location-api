package uk.gov.justice.digital.hmpps.personlocationapi.event.internal

import io.awspring.cloud.sqs.annotation.SqsListener
import io.sentry.Sentry
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.event.Notification
import uk.gov.justice.digital.hmpps.personlocationapi.service.RefreshAllIntervals
import uk.gov.justice.digital.hmpps.personlocationapi.service.RefreshExternalMovementIntervals

@Component
class InternalEventListener(
  private val jsonMapper: JsonMapper,
  private val refresh: RefreshExternalMovementIntervals,
  private val refreshAll: RefreshAllIntervals,
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
      when (val event: InternalEvent = jsonMapper.readValue(notification.message)) {
        is RefreshPersonMovementIntervals -> refresh.execute(event)
        is RefreshAllMovementIntervals -> refreshAll.execute()
      }
    } catch (ex: Exception) {
      Sentry.captureException(ex)
      throw ex
    } finally {
      RequestContext.clear()
    }
  }
}
