package uk.gov.justice.digital.hmpps.personlocationapi.event.domain

import org.springframework.data.domain.Pageable
import org.springframework.retry.RetryPolicy
import org.springframework.retry.backoff.BackOffPolicy
import org.springframework.retry.support.RetryTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.sns.model.PublishBatchRequest
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry
import software.amazon.awssdk.services.sns.model.PublishBatchResponse
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.personlocationapi.config.ServiceConfig
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.HmppsDomainEventRepository
import uk.gov.justice.hmpps.sqs.DEFAULT_BACKOFF_POLICY
import uk.gov.justice.hmpps.sqs.DEFAULT_RETRY_POLICY
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.HmppsTopic
import uk.gov.justice.hmpps.sqs.eventTypeSnsMap

@Service
class DomainEventPublisher(
  private val jsonMapper: JsonMapper,
  private val hmppsQueueService: HmppsQueueService,
  private val domainEventRepository: HmppsDomainEventRepository,
  private val serviceConfig: ServiceConfig,
) {
  private val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic") ?: throw IllegalStateException("Domain event topic not found")
  }

  private val domainEventsQueue: HmppsQueue by lazy {
    hmppsQueueService.findByQueueId("hmppsdomaineventsqueue") ?: throw IllegalStateException("Domain event queue not available")
  }

  @Transactional
  fun publishUnpublishedEvents() {
    domainEventRepository.findByPublishedIsFalseOrderById(Pageable.ofSize(serviceConfig.domainEvents.batchSize))
      .takeIf { it.isNotEmpty() }
      ?.also { events -> domainEventsTopic.publishBatch(events) }
      ?.forEach { it.published = true }
  }

  private fun HmppsTopic.publishBatch(
    events: List<HmppsDomainEvent>,
    retryPolicy: RetryPolicy = DEFAULT_RETRY_POLICY,
    backOffPolicy: BackOffPolicy = DEFAULT_BACKOFF_POLICY,
  ) {
    val retryTemplate = RetryTemplate().apply {
      setRetryPolicy(retryPolicy)
      setBackOffPolicy(backOffPolicy)
    }
    val publishRequest = PublishBatchRequest.builder().topicArn(arn).publishBatchRequestEntries(
      events.map {
        PublishBatchRequestEntry.builder()
          .id(it.id.toString())
          .message(jsonMapper.writeValueAsString(it.event))
          .messageAttributes(eventTypeSnsMap(it.eventType, noTracing = true))
          .build()
      },
    ).build()
    retryTemplate.execute<PublishBatchResponse, RuntimeException> {
      snsClient.publishBatch(publishRequest).get()
    }
  }
}
