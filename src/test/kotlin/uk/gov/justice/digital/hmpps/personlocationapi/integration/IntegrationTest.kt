package uk.gov.justice.digital.hmpps.personlocationapi.integration

import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.RevisionType
import org.hibernate.envers.query.AuditEntity
import org.hibernate.envers.query.AuditEntity.revisionNumber
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import uk.gov.justice.digital.hmpps.personlocationapi.context.RequestContext
import uk.gov.justice.digital.hmpps.personlocationapi.event.domain.DomainEvent
import uk.gov.justice.digital.hmpps.personlocationapi.integration.config.TestConfig
import uk.gov.justice.digital.hmpps.personlocationapi.integration.container.LocalStackContainer
import uk.gov.justice.digital.hmpps.personlocationapi.integration.container.LocalStackContainer.setMiniStackProperties
import uk.gov.justice.digital.hmpps.personlocationapi.integration.container.PostgresContainer
import uk.gov.justice.digital.hmpps.personlocationapi.integration.wiremock.HmppsAuthApiExtension
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.AuditRevision
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.DomainEventPublication
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.Identifiable
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.HmppsDomainEvent
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.publication
import uk.gov.justice.hmpps.kotlin.common.ErrorResponse
import uk.gov.justice.hmpps.sqs.HmppsQueue
import uk.gov.justice.hmpps.sqs.HmppsQueueService
import uk.gov.justice.hmpps.sqs.MissingQueueException
import uk.gov.justice.hmpps.sqs.MissingTopicException
import uk.gov.justice.hmpps.sqs.countAllMessagesOnQueue
import uk.gov.justice.hmpps.sqs.publish
import uk.gov.justice.hmpps.test.kotlin.auth.JwtAuthorisationHelper
import java.time.Duration
import java.time.Instant.now
import java.util.concurrent.TimeUnit
import kotlin.collections.map
import kotlin.jvm.java

@Import(TestConfig::class)
@ExtendWith(HmppsAuthApiExtension::class)
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient
abstract class IntegrationTest {

  @Autowired
  protected lateinit var transactionTemplate: TransactionTemplate

  @Autowired
  protected lateinit var entityManager: EntityManager

  @Autowired
  protected lateinit var webTestClient: WebTestClient

  @Autowired
  protected lateinit var jwtAuthHelper: JwtAuthorisationHelper

  @Autowired
  protected lateinit var jsonMapper: JsonMapper

  @Autowired
  protected lateinit var hmppsQueueService: HmppsQueueService

  protected val domainEventsTopic by lazy {
    hmppsQueueService.findByTopicId("hmppseventtopic")
      ?: throw MissingTopicException("hmppseventtopic not found")
  }

  protected fun sendDomainEvent(event: DomainEvent<*>) {
    domainEventsTopic.publish(event.eventType, jsonMapper.writeValueAsString(event))
  }

  protected val domainEventsQueue by lazy {
    hmppsQueueService.findByQueueId("hmppsdomaineventsqueue")
      ?: throw MissingQueueException("hmppsdomaineventsqueue not found")
  }

  protected val internalEventsQueue by lazy {
    hmppsQueueService.findByQueueId("internalevents")
      ?: throw MissingQueueException("internalevents not found")
  }

  protected fun HmppsQueue.isEmpty() = sqsClient.countAllMessagesOnQueue(queueUrl = queueUrl).get() == 0

  protected fun verifyAudit(
    entity: Identifiable,
    revisionType: RevisionType,
    affectedEntities: Set<String>,
    context: RequestContext = RequestContext(),
  ) {
    transactionTemplate.execute {
      val auditReader = AuditReaderFactory.get(entityManager)
      assertTrue(auditReader.isEntityClassAudited(entity::class.java))

      val revisionNumber =
        auditReader
          .getRevisions(entity::class.java, entity.id)
          .filterIsInstance<Long>()
          .max()

      val entityRevision: Array<*> =
        auditReader
          .createQuery()
          .forRevisionsOfEntity(entity::class.java, false, true)
          .add(revisionNumber().eq(revisionNumber))
          .add(AuditEntity.id().eq(entity.id))
          .resultList
          .first() as Array<*>
      assertThat(entityRevision[2]).isEqualTo(revisionType)

      val auditRevision = entityRevision[1] as AuditRevision
      with(auditRevision) {
        assertThat(this.affectedEntities).containsExactlyInAnyOrderElementsOf(affectedEntities)
        assertThat(username).isEqualTo(context.username)
        assertThat(source).isEqualTo(context.source)
        assertThat(reason).isEqualTo(context.reason)
        assertThat(caseloadId).isEqualTo(context.caseloadId)
      }
    }
  }

  protected fun verifyEventPublications(
    entity: Identifiable,
    events: Set<DomainEventPublication>,
  ) {
    transactionTemplate.execute {
      val auditReader = AuditReaderFactory.get(entityManager)
      assertTrue(auditReader.isEntityClassAudited(entity::class.java))

      val revisionNumber =
        auditReader
          .getRevisions(entity::class.java, entity.id)
          .filterIsInstance<Long>()
          .max()

      val domainEventsPersisted: List<HmppsDomainEvent> =
        auditReader
          .createQuery()
          .forRevisionsOfEntity(HmppsDomainEvent::class.java, true, true)
          .add(revisionNumber().eq(revisionNumber))
          .resultList
          .filterIsInstance<HmppsDomainEvent>()
      domainEventsPersisted.forEach {
        assertThat(it.eventType).isEqualTo(it.event.eventType)
      }
      assertThat(domainEventsPersisted.map { de -> de.event.publication(de.entityId) { !de.published } })
        .containsExactlyInAnyOrderElementsOf(events)
    }
  }

  protected fun waitUntil(
    pollingInterval: Duration = Duration.ofMillis(100),
    maxInterval: Duration = Duration.ofSeconds(1),
    predicate: () -> Boolean,
  ) {
    val end = now().plus(maxInterval)
    var result: Boolean
    do {
      TimeUnit.MILLISECONDS.sleep(pollingInterval.toMillis())
      result = predicate()
    } while (!result && now().isBefore(end))
    if (!result) {
      throw IllegalStateException("Predicate not met before timeout")
    }
  }

  protected fun setAuthorisation(
    username: String? = "AUTH_ADM",
    roles: List<String> = listOf(),
    scopes: List<String> = listOf("read"),
  ): (HttpHeaders) -> Unit = jwtAuthHelper.setAuthorisationHeader(username = username, scope = scopes, roles = roles)

  protected final inline fun <reified T : Any> WebTestClient.ResponseSpec.successResponse(status: HttpStatus = HttpStatus.OK): T = expectStatus().isEqualTo(status)
    .expectBody<T>()
    .returnResult().responseBody!!

  protected final fun WebTestClient.ResponseSpec.errorResponse(status: HttpStatus): ErrorResponse = expectStatus().isEqualTo(status)
    .expectBody<ErrorResponse>()
    .returnResult().responseBody!!

  companion object {
    private val pgContainer = PostgresContainer.instance
    private val localStackContainer = LocalStackContainer.instance
    const val DEFAULT_USERNAME = "P3r50nL0c4710n"

    @JvmStatic
    @DynamicPropertySource
    @Suppress("unused")
    fun properties(registry: DynamicPropertyRegistry) {
      pgContainer?.also {
        registry.add("spring.datasource.url", pgContainer::getJdbcUrl)
        registry.add("spring.datasource.username", pgContainer::getUsername)
        registry.add("spring.datasource.password", pgContainer::getPassword)
      }

      System.setProperty("aws.region", "eu-west-2")
      localStackContainer?.also { setMiniStackProperties(it, registry) }
    }
  }
}
