package uk.gov.justice.digital.hmpps.personlocationapi

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PersonLocationApi

fun main(args: Array<String>) {
  runApplication<PersonLocationApi>(*args)
}
