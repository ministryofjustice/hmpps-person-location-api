package uk.gov.justice.digital.hmpps.personlocationapi.event

import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class Notification(
  @JsonProperty("Message") val message: String,
  @JsonProperty("MessageAttributes") val attributes: MessageAttributes = MessageAttributes(),
) {
  val eventType: String? @JsonIgnore get() = attributes["eventType"]?.value
}

data class MessageAttributes(
  @JsonAnyGetter @JsonAnySetter
  private val attributes: MutableMap<String, MessageAttribute> = mutableMapOf(),
) : MutableMap<String, MessageAttribute> by attributes {
  constructor(eventType: String, noTracing: Boolean = false) : this(
    buildMap {
      put("eventType", MessageAttribute("String", eventType))
      if (noTracing) put("noTracing", MessageAttribute("String", "$noTracing"))
    }.toMutableMap(),
  )

  override operator fun get(key: String): MessageAttribute? = attributes[key]
  operator fun set(key: String, value: MessageAttribute) {
    attributes[key] = value
  }
}

data class MessageAttribute(@JsonProperty("Type") val type: String, @JsonProperty("Value") val value: String)
