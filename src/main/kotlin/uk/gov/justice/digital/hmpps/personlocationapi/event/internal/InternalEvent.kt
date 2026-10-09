package uk.gov.justice.digital.hmpps.personlocationapi.event.internal

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import java.time.LocalDateTime

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes(
  value = [
    JsonSubTypes.Type(value = RefreshPersonMovementIntervals::class, name = RefreshPersonMovementIntervals.EVENT_TYPE),
  ],
)
sealed interface InternalEvent {
  val occurredAt: LocalDateTime
    get() = LocalDateTime.now()
  val type: String
}

data class RefreshPersonMovementIntervals(val personIdentifier: String, val refreshFrom: LocalDateTime? = null) : InternalEvent {
  override val type: String = EVENT_TYPE

  companion object {
    const val EVENT_TYPE = "person.external-movement-intervals.refresh"
  }
}
