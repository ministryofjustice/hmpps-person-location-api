package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.Location

data class ApplyDestination(val destination: Location?) : MovementAction {
  override fun changes(entity: ExternalMovement): Boolean = destination != entity.destination
}
