package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement

data class ApplyType(val type: ExternalMovement.Type) : MovementAction {
  override fun changes(entity: ExternalMovement): Boolean = type != entity.movementType
}
