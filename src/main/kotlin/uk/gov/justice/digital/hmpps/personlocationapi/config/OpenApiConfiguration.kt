package uk.gov.justice.digital.hmpps.personlocationapi.config

import io.swagger.v3.core.util.PrimitiveType
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springdoc.core.customizers.OperationCustomizer
import org.springframework.boot.info.BuildProperties
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.expression.BeanFactoryResolver
import org.springframework.expression.spel.SpelEvaluationException
import org.springframework.expression.spel.standard.SpelExpressionParser
import org.springframework.expression.spel.support.StandardEvaluationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.method.HandlerMethod
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags.INTEGRATIONS
import uk.gov.justice.digital.hmpps.personlocationapi.config.OpenApiTags.SYNC
import java.time.LocalTime

object OpenApiTags {
  const val SYNC = "Sync"
  const val INTEGRATIONS = "Integrations"
}

@Configuration
class OpenApiConfiguration(buildProperties: BuildProperties, private val context: ApplicationContext) {
  private val version: String = buildProperties.version!!

  @Bean
  fun customOpenAPI(): OpenAPI = OpenAPI()
    .servers(
      listOf(
        Server().url("https://person-location-api-dev.hmpps.service.justice.gov.uk").description("Development"),
        Server().url("https://person-location-api-preprod.hmpps.service.justice.gov.uk").description("Pre-Production"),
        Server().url("https://person-location-api.hmpps.service.justice.gov.uk").description("Production"),
        Server().url("http://localhost:8080").description("Local"),
      ),
    )
    .tags(
      listOf(
        Tag().name(INTEGRATIONS).description("DPS integration endpoints"),
        Tag().name(SYNC).description("Legacy sync endpoints - not to be use by any other client"),
      ),
    )
    .info(
      Info().title("HMPPS Person Location Api").version(version)
        .contact(Contact().name("HMPPS Digital Studio").email("feedback@digital.justice.gov.uk")),
    )
    .also { PrimitiveType.customClasses()[LocalTime::class.java.name] = PrimitiveType.PARTIAL_TIME }

  @Bean
  fun preAuthorizeCustomizer(): OperationCustomizer = OperationCustomizer { operation: Operation, handlerMethod: HandlerMethod ->
    handlerMethod.preAuthorizeForMethodOrClass()?.let {
      val preAuthExp = SpelExpressionParser().parseExpression(it)
      val evalContext = StandardEvaluationContext()
      evalContext.beanResolver = BeanFactoryResolver(context)
      evalContext.setRootObject(
        @Suppress("unused")
        object {
          fun hasRole(role: String) = listOf(role)
          fun hasAnyRole(vararg roles: String) = roles.toList()
        },
      )

      val roles = try {
        (preAuthExp.getValue(evalContext) as List<*>).filterIsInstance<String>()
      } catch (_: SpelEvaluationException) {
        emptyList()
      }
      if (roles.isNotEmpty()) {
        val rolesDescription = """
            Requires one of the following roles:
            ${roles.joinToString(prefix = "* ", separator = "${System.lineSeparator()}* ")}
        """.trimMargin()
        operation.description = operation.description?.let { des ->
          """
            $des
          """.trimMargin()
        } ?: rolesDescription
        roles.forEach { role ->
          operation.addSecurityItem(SecurityRequirement().addList(role, listOf("read")))
        }
      }
      operation
    }
  }
}

private fun HandlerMethod.preAuthorizeForMethodOrClass() = getMethodAnnotation(PreAuthorize::class.java)?.value
  ?: beanType.getAnnotation(PreAuthorize::class.java)?.value
