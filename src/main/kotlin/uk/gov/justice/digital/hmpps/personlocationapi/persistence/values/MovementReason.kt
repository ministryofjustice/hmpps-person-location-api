package uk.gov.justice.digital.hmpps.personlocationapi.persistence.values

import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "reasonType")
sealed interface MovementReason {
  val description: String
}

data class CodedReason(val code: String, override val description: String) : MovementReason
data class HierarchicalReason(
  val hierarchy: List<CodedReason>,
  override val description: String = hierarchy.joinToString(" > ") { it.description },
) : MovementReason
