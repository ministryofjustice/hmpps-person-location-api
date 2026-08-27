package uk.gov.justice.digital.hmpps.personlocationapi.domain.values

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "locationType")
sealed interface Location {
  val description: String
}

data class CodedLocation(val code: String, override val description: String) : Location
data class DescriptiveLocation(override val description: String) : Location
