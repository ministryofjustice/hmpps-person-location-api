package uk.gov.justice.digital.hmpps.personlocationapi.model.action.movement

import uk.gov.justice.digital.hmpps.personlocationapi.model.action.NotesAction
import uk.gov.justice.digital.hmpps.personlocationapi.persistence.entities.ExternalMovement

data class ApplyNotes(override val notes: String?) :
  MovementAction,
  NotesAction {
  override fun changes(entity: ExternalMovement): Boolean = changes(entity.notes)
}
