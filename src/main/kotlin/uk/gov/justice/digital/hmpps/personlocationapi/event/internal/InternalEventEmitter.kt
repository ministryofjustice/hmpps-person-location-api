package uk.gov.justice.digital.hmpps.personlocationapi.event.internal

import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.BackOffPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry
import software.amazon.awssdk.services.sqs.model.SendMessageBatchResponse
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.personlocationapi.event.MessageAttributes
import uk.gov.justice.digital.hmpps.personlocationapi.event.Notification
import uk.gov.justice.hmpps.sqs.DEFAULT_BACKOFF_POLICY
import uk.gov.justice.hmpps.sqs.DEFAULT_RETRY_POLICY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import java.util.UUID

@Component
class InternalEventEmitter(private val jsonMapper: JsonMapper, private val queueService: HmppsQueueService) {

  private val queue: HmppsQueue by lazy {
    queueService.findByQueueId("internalevents") ?: throw IllegalStateException("Queue not available")
  }

  fun publishInternalEvent(event: InternalEvent) {
    publishInternalEvents(listOf(event))
  }

  fun publishInternalEvents(events: Collection<InternalEvent>) {
    events.asSequence().chunked(10).forEach { queue.publishBatch(it) }
  }

  private fun HmppsQueue.publishBatch(
    events: Collection<InternalEvent>,
    retryPolicy: RetryPolicy = DEFAULT_RETRY_POLICY,
    backOffPolicy: BackOffPolicy = DEFAULT_BACKOFF_POLICY,
  ) {
    val retryTemplate =
      RetryTemplate().apply {
        setRetryPolicy(retryPolicy)
        setBackOffPolicy(backOffPolicy)
      }
    val publishRequest =
      SendMessageBatchRequest
        .builder()
        .queueUrl(queueUrl)
        .entries(
          events.map {
            val notification =
              Notification(jsonMapper.writeValueAsString(it), attributes = MessageAttributes(it.type))
            SendMessageBatchRequestEntry
              .builder()
              .id(UUID.randomUUID().toString())
              .messageBody(jsonMapper.writeValueAsString(notification))
              .messageAttributes(notification.attributes.map { a -> a.key to MessageAttributeValue.builder().dataType(a.value.type).stringValue(a.value.value).build() }.toMap())
              .build()
          },
        ).build()
    retryTemplate.execute<SendMessageBatchResponse, RuntimeException> {
      sqsClient.sendMessageBatch(publishRequest).get()
    }
  }
}
