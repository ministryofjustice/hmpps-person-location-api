package uk.gov.justice.digital.hmpps.personlocationapi.event

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import uk.gov.justice.digital.hmpps.personlocationapi.domain.DataSource
import uk.gov.justice.digital.hmpps.personlocationapi.domain.IdGenerator.newUuid
import java.time.ZonedDateTime
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "eventType")
@JsonSubTypes(
  value = [
    JsonSubTypes.Type(value = PrisonerMerged::class, name = PrisonerMerged.EVENT_TYPE),
    JsonSubTypes.Type(value = PrisonerUpdated::class, name = PrisonerUpdated.EVENT_TYPE),
    JsonSubTypes.Type(value = PrisonerReceived::class, name = PrisonerReceived.EVENT_TYPE),
  ],
)
sealed interface DomainEvent<T : AdditionalInformation> {
  val occurredAt: ZonedDateTime
    get() = ZonedDateTime.now()

  val eventType: String
  val description: String
  val additionalInformation: T
  val personReference: PersonReference
  val detailUrl: String?
  val version: Int get() = 1

  @get:JsonIgnore
  val id: UUID
    get() = newUuid()

  @JsonIgnore
  fun getPersonIdentifier(): String = checkNotNull(personReference.findPersonIdentifier())
}

data class PersonReference(val identifiers: List<Identifier> = listOf()) {
  operator fun get(key: String): String? = identifiers.find { it.type == key }?.value
  fun findPersonIdentifier() = get(NOMS_NUMBER_TYPE)

  companion object {
    const val NOMS_NUMBER_TYPE = "NOMS"
    fun withIdentifier(personIdentifier: String) = PersonReference(listOf(Identifier(NOMS_NUMBER_TYPE, personIdentifier)))
  }

  data class Identifier(val type: String, val value: String)
}

sealed interface AdditionalInformation

sealed interface SourceInformation : AdditionalInformation {
  val source: DataSource
}

sealed interface IdInformation : AdditionalInformation {
  val id: UUID
}
