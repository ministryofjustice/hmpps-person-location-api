package uk.gov.justice.digital.hmpps.personlocationapi.persistence

import java.util.UUID

fun String.toUuid(): UUID? = try {
  UUID.fromString(this)
} catch (_: IllegalArgumentException) {
  null
}
