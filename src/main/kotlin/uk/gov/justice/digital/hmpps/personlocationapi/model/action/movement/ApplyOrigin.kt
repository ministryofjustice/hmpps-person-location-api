package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location

data class ApplyOrigin(val origin: Location) : MovementAction {
  override fun changes(entity: ExternalMovement): Boolean = origin != entity.origin
}
