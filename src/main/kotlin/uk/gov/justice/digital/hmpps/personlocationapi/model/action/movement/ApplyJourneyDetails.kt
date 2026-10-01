package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalJourney
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.values.externalreference.ExternalReference

data class ApplyJourneyDetails(
  val journeyType: ExternalJourney.Type,
  val scheduleReference: ExternalReference?,
) : MovementAction {
  override fun changes(entity: ExternalMovement): Boolean = journeyType != entity.journeyType || scheduleReference != entity.scheduleReference
}
