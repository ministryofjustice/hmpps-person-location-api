package uk.gov.justice.digital.hmpps.personlocationapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import uk.gov.justice.digital.hmpps.personlocationapi.config.ServiceConfig

@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(ServiceConfig::class)
@SpringBootApplication
class PersonLocationApi

fun main(args: Array<String>) {
  runApplication<PersonLocationApi>(*args)
}
