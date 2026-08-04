package uk.gov.justice.digital.hmpps.personlocationapi.domain

import com.fasterxml.uuid.Generators
import java.util.UUID

object IdGenerator {
  fun newUuid(): UUID = Generators.timeBasedEpochGenerator().generate()
}

interface Identifiable {
  val id: UUID
  val version: Int?
}
