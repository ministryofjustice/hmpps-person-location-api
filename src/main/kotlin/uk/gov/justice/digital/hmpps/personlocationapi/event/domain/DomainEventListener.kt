package uk.gov.justice.digital.hmpps.personlocationapi.event.domain

import io.awspring.cloud.sqs.annotation.SqsListener
import io.sentry.Sentry
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.context.set
import uk.gov.justice.digital.hmpps.personlocationapi.event.Notification
import uk.gov.justice.digital.hmpps.personlocationapi.service.BookingMovedHandler
import uk.gov.justice.digital.hmpps.personlocationapi.service.PrisonerMergedHandler

@Component
class DomainEventListener(
  private val jsonMapper: JsonMapper,
  private val prisonerMerged: PrisonerMergedHandler,
  private val bookingMoved: BookingMovedHandler,
) {
  @SqsListener("hmppsdomaineventsqueue", factory = "hmppsQueueContainerFactoryProxy")
  fun handleDomainEvent(notification: Notification) {
    try {
      RequestContext().set()
      when (val event = jsonMapper.readValue<DomainEvent<*>>(notification.message)) {
        is PrisonerMerged -> prisonerMerged.handle(event)
        is BookingMoved -> bookingMoved.handle(event)
        else -> { /* no-op */ }
      }
    } catch (ex: Exception) {
      Sentry.captureException(ex)
      throw ex
    } finally {
      RequestContext.clear()
    }
  }
}
