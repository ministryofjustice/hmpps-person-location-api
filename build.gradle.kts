import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  id("uk.gov.justice.hmpps.gradle-spring-boot") version "11.0.6"
  kotlin("plugin.spring") version "2.4.10"
  kotlin("plugin.jpa") version "2.4.10"
}

val hmppsKotlinVersion = "3.0.1"
val sentryVersion = "8.54.0"
val springDocVersion = "3.1.0"
val sqsStarterVersion = "7.4.1"
val swaggerParserVersion = "2.1.47"
val testContainersVersion = "1.21.4"
val uuidGeneratorVersion = "5.2.0"
val wiremockVersion = "3.13.2"

dependencies {
  implementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter:$hmppsKotlinVersion")
  implementation("org.springframework.boot:spring-boot-starter-webclient")
  implementation("uk.gov.justice.service.hmpps:hmpps-sqs-spring-boot-starter:$sqsStarterVersion")
  implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:$springDocVersion")
  implementation("com.fasterxml.uuid:java-uuid-generator:$uuidGeneratorVersion")
  implementation("org.springframework.boot:spring-boot-starter-data-jpa")
  implementation("org.springframework.data:spring-data-envers")
  implementation("io.sentry:sentry-spring-boot-4:$sentryVersion")

  runtimeOnly("org.springframework.boot:spring-boot-starter-flyway")
  runtimeOnly("org.flywaydb:flyway-database-postgresql")
  runtimeOnly("org.postgresql:postgresql")

  testImplementation("org.testcontainers:postgresql:$testContainersVersion")
  testImplementation("uk.gov.justice.service.hmpps:hmpps-kotlin-spring-boot-starter-test:$hmppsKotlinVersion")
  testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
  testImplementation("org.wiremock:wiremock-standalone:$wiremockVersion")
  testImplementation("io.swagger.parser.v3:swagger-parser:$swaggerParserVersion") {
    exclude(group = "io.swagger.core.v3")
  }
}

kotlin {
  jvmToolchain(25)
}

tasks {
  withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions.jvmTarget = JvmTarget.JVM_25
  }
}
