package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.MovementReason

data class ApplyReason(val reason: MovementReason) : MovementAction {
  override fun changes(entity: ExternalMovement): Boolean = reason != entity.reason
}
