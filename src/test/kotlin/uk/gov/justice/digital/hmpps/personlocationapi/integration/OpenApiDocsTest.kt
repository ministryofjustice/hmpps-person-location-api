package uk.gov.justice.digital.hmpps.personlocationapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import tools.jackson.databind.JsonNode
import java.net.URI

class OpenApiDocsTest(
  @Autowired private val buildProperties: BuildProperties,
  @LocalServerPort private val port: Int = 0,
) : IntegrationTest() {

  @Test
  fun `open api docs are available`() {
    webTestClient.get()
      .uri("/swagger-ui/index.html?configUrl=/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
  }

  @Test
  fun `open api docs redirect to correct page`() {
    webTestClient.get()
      .uri("/swagger-ui.html")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().is3xxRedirection
      .expectHeader().value("Location") { it.contains("/swagger-ui/index.html?configUrl=/v3/api-docs/swagger-config") }
  }

  @Test
  fun `the open api json contains documentation`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("paths").isNotEmpty
  }

  @Test
  fun `the swagger json don't contain any duplicate methods`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("*..operationId").value<List<String>> { list ->
        assertThat(list).filteredOn { it.contains("_") }.isEmpty()
      }
  }

  @Test
  fun `the open api json contains the version number`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody().jsonPath("info.version").isEqualTo(buildProperties.version)
  }

  @Test
  fun `the open api json is valid`() {
    val rawJson = URI("http://localhost:$port/v3/api-docs").toURL().readText()
    val root: JsonNode = jsonMapper.readTree(rawJson)

    assertThat(root.path("openapi").asString()).startsWith("3.")
    assertThat(root.path("info").path("title").asString()).isNotBlank()
    assertThat(root.path("paths").isObject).isTrue()
    assertThat(root.path("paths").size()).isGreaterThan(0)
  }

  @Test
  fun `the open api json path security requirements are valid`() {
    val rawJson = URI("http://localhost:$port/v3/api-docs").toURL().readText()
    val root: JsonNode = jsonMapper.readTree(rawJson)

    // Collect top-level security requirement keys (e.g. "bearerAuth", "OAuth2")
    val securityRequirements: Set<String> = root.path("security")
      .flatMap { requirementObj -> requirementObj.propertyNames() }
      .toSet()

    val pathsNode = root.path("paths")
    assertThat(pathsNode.isObject).isTrue()

    pathsNode.properties().forEach { (path, pathItem) ->
      HttpMethod.values().forEach { method ->
        val operation = pathItem.path(method.name())
        if (!operation.isMissingNode && operation.has("security")) {
          val operationSecurityKeys = operation.path("security")
            .flatMap { requirementObj -> requirementObj.propertyNames() }

          assertThat(operationSecurityKeys)
            .`as`("Security requirements for $method $path")
            .isSubsetOf(securityRequirements)
        }
      }
    }
  }

  @Test
  fun `all endpoints have a security scheme defined`() {
    webTestClient.get()
      .uri("/v3/api-docs")
      .accept(MediaType.APPLICATION_JSON)
      .exchange()
      .expectStatus().isOk
      .expectBody()
      .jsonPath("$.paths[*][*][?(!@.security)]").doesNotExist()
  }
}
