package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class ApplyOccurredAt(val occurredAt: LocalDateTime) : MovementAction {
  override fun changes(entity: ExternalMovement): Boolean = !occurredAt.truncatedTo(ChronoUnit.SECONDS).isEqual(entity.occurredAt.truncatedTo(ChronoUnit.SECONDS))
}
