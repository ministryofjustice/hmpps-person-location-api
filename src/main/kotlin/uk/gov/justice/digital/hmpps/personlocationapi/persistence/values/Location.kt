package uk.gov.justice.digital.hmpps.personlocationapi.persistence.values

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "locationType")
sealed interface Location {
  val description: String
}

data class Prison(val code: String, override val description: String) : Location
data class Court(val code: String, override val description: String) : Location
data class SimpleLocation(override val description: String) : Location
