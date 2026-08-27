package uk.gov.justice.digital.hmpps.personlocationapi.domain.externalreference

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import uk.gov.justice.digital.hmpps.personlocationapi.domain.toUuid
import java.util.UUID

class ExternalReference(val service: ExternalReferenceService, val entity: ExternalReferenceEntity, val uuid: UUID) {
  companion object {
    @JvmStatic
    @JsonCreator
    fun fromString(urn: String): ExternalReference {
      val urnParts = urn.split(":")
      check(urnParts.size == 4) {
        "URN should match expected pattern: 'urn:service-name:entity-name:uuid'"
      }
      val service = ExternalReferenceService.fromString(urnParts[1])
      val entity = ExternalReferenceEntity.fromString(urnParts[2]).forService(service)
      val uuid = checkNotNull(urnParts.last().toUuid()) { "A valid uuid is required" }
      return ExternalReference(service, entity, uuid)
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ExternalReference

    if (uuid != other.uuid) return false
    if (service != other.service) return false
    if (entity != other.entity) return false

    return true
  }

  override fun hashCode(): Int {
    var result = uuid.hashCode()
    result = 31 * result + service.hashCode()
    result = 31 * result + entity.hashCode()
    return result
  }

  @JsonValue
  override fun toString() = "urn:${service.code}:${entity.code}:$uuid"
}
