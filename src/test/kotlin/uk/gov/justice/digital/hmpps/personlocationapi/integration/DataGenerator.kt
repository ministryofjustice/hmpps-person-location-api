package uk.gov.justice.digital.hmpps.personlocationapi.integration

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.IdGenerator.newUuid
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceEntity
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReferenceService
import java.util.UUID
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.atomic.AtomicLong

object DataGenerator {
  private val id = AtomicLong(1)
  private val letters = ('A'..'Z')
  private val usedPrisonCodes = ConcurrentSkipListSet<String>()

  fun newId(): Long = id.getAndIncrement()
  fun personIdentifier(): String = "${letters.random()}${(1111..9999).random()}${letters.random()}${letters.random()}"
  fun word(length: Int): String = (1..length).joinToString("") { if (it == 1) letters.random().uppercase() else letters.random().lowercase() }

  fun username(): String = (0..12).joinToString("") { letters.random().toString() }
  fun prisonCode(attempts: Int = 10): String {
    if (attempts <= 0) throw IllegalStateException("Ran out of attempts to find a unique prison code")
    val prisonCode = (1..3).map { letters.random() }.joinToString("")
    return if (usedPrisonCodes.add(prisonCode)) {
      prisonCode
    } else {
      prisonCode(attempts - 1)
    }
  }

  fun externalReference(
    entity: ExternalReferenceEntity = ExternalReferenceEntity.entries.random(),
    service: ExternalReferenceService = entity.services.random(),
    uuid: UUID = newUuid(),
  ) = ExternalReference(service, entity, uuid)
}
