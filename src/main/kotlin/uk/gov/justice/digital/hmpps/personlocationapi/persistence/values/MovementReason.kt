package uk.gov.justice.digital.hmpps.personlocationapi.persistence.values

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.swagger.v3.oas.annotations.media.DiscriminatorMapping
import io.swagger.v3.oas.annotations.media.Schema

const val REASON_TYPE = "reasonType"
const val CODED_REASON = "CodedReason"
const val HIERARCHICAL_REASON = "HierarchicalReason"

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = REASON_TYPE)
@JsonSubTypes(
  JsonSubTypes.Type(value = CodedReason::class, name = CODED_REASON),
  JsonSubTypes.Type(value = HierarchicalReason::class, name = HIERARCHICAL_REASON),
)
@Schema(
  description = "Represents a movement reason",
  discriminatorProperty = REASON_TYPE,
  discriminatorMapping = [
    DiscriminatorMapping(value = CODED_REASON, schema = CodedReason::class),
    DiscriminatorMapping(value = HIERARCHICAL_REASON, schema = HierarchicalReason::class),
  ],
  subTypes = [CodedReason::class, HierarchicalReason::class],
)
sealed interface MovementReason {
  val description: String
}

@Schema(description = "Standard reference data code and description", name = CODED_REASON)
data class CodedReason(val code: String, override val description: String) : MovementReason

@Schema(description = "More complex hierarchies of reference data such as TAP", name = HIERARCHICAL_REASON)
data class HierarchicalReason(
  val hierarchy: List<CodedReason>,
  override val description: String = hierarchy.joinToString(" > ") { it.description },
) : MovementReason
